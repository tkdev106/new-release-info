// おすすめアーティストを LLM に聞く中継。LLM の API キーをアプリに入れないために置く。
// 呼び出し側（Android・将来の iOS）に依存しない、JSON を POST するだけの形にしている。
// どの LLM を使うかは llm/provider.js の LlmProvider 越しにしか知らない。

// 守り（地域・回数・アプリの確認）は guard/ にあり、ここはその形だけを使う。
// ログは log/logger.js の Logger 越しに出す。

import { bodyHash, createAttestation } from "./guard/playIntegrity.js";
import { createDailyQuota, secondsUntilNextDay } from "./guard/quota.js";
import { isBlockedRegion } from "./guard/region.js";
import { createProvider } from "./llm/provider.js";
import { createJsonLogger, errorText } from "./log/logger.js";
import { buildPrompt, cacheKey, parseRequest, sanitizeSuggestions } from "./suggest.js";

export { DailyQuotaCounter } from "./guard/quota.js";

const CACHE_TTL_SECONDS = 7 * 24 * 60 * 60;
// 好きなアーティスト 30 件と除く名前 50 件が日本語の長い名前でも収まる大きさ
const MAX_BODY_BYTES = 16 * 1024;
const DEFAULT_DAILY_LLM_LIMIT = 500;

// アクセストークンを呼び出しをまたいで使い回すため、同じ鍵のあいだは同じものを持っておく
let attestation = { key: undefined, value: null };
function attestationFor(env) {
  if (attestation.key !== env.PLAY_INTEGRITY_SA_KEY) {
    attestation = { key: env.PLAY_INTEGRITY_SA_KEY, value: createAttestation(env) };
  }
  return attestation.value;
}

/**
 * 中継の本体を組み立てる。ログの書き先は外から渡せるようにし、テストでは中身を確かめる
 * @param {{ logger?: import("./log/logger.js").Logger, now?: () => number }} [deps]
 */
export function createWorker({ logger = createJsonLogger(), now = Date.now } = {}) {
  return {
    async fetch(request, env, ctx) {
      const started = now();
      // 1 回の呼び出しについて分かったことをここに集め、最後に 1 件の「request」ログにまとめる。
      // 1 件にまとめると、Workers Logs で結果の種類ごとの件数や所要時間をそのまま集計できる
      const trace = {};
      const log = logger.with({
        request_id: request.headers.get("cf-ray") ?? undefined,
        country: request.cf?.country,
        colo: request.cf?.colo,
      });
      let res;
      try {
        res = await handle(request, env, ctx, trace, log, now);
      } catch (e) {
        trace.outcome = "internal_error";
        trace.error = errorText(e);
        res = json({ suggestions: [], error: "internal error" }, 500);
      }
      const fields = { ...trace, status: res.status, duration_ms: now() - started };
      const level = res.status >= 500 ? "error" : res.status === 401 || res.status === 429 ? "warn" : "info";
      log[level]("request", fields);
      return res;
    },
  };
}

export default createWorker();

async function handle(request, env, ctx, trace, log, now) {
  const url = new URL(request.url);
  const done = (outcome, res) => ((trace.outcome = outcome), res);
  if (url.pathname !== "/v1/suggestions") return done("not_found", json({ error: "not found" }, 404));
  if (request.method !== "POST") return done("method_not_allowed", json({ error: "method not allowed" }, 405));

  if (isBlockedRegion(request.cf?.country)) {
    return done("region_blocked", json({ suggestions: [], error: "unavailable in this region" }, 451));
  }

  const verifier = attestationFor(env);
  if (!env.APP_TOKEN && !verifier) return done("not_configured", json({ error: "server not configured" }, 500));

  // 回数制限は設定されていれば使う（wrangler.toml の ratelimits）。
  // 連打は 10 秒の窓で、長く続く呼び出しは 1 分の窓で止める。片方だけだと、
  // 1 分 10 回の枠を数秒で使い切る連打を最初の数回で止められないため。
  // アプリの確認（Google への問い合わせ）より前に置き、確認の枠も食い尽くされないようにする
  const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
  for (const [limiter, period, name] of [[env.BURST_LIMITER, "10", "burst"], [env.RATE_LIMITER, "60", "minute"]]) {
    if (!limiter) continue;
    const { success } = await limiter.limit({ key: ip });
    if (!success) {
      trace.rate_limit = name;
      return done("rate_limited", json({ error: "too many requests" }, 429, { "Retry-After": period }));
    }
  }

  const raw = await request.text();
  trace.body_bytes = raw.length;
  if (raw.length > MAX_BODY_BYTES) return done("body_too_large", json({ error: "body too large" }, 413));

  // 呼べるのは次のどちらか。
  // - トークン: 開発版や APK で配った版。トークンは APK から抜けるので、配る相手は限る
  // - Play Integrity: Play から入れた版。トークンを持たない
  // LLM の設定状況は、確認を通った呼び出し側にだけ見せる
  const auth = await authorize(request, raw, env, verifier, trace, now);
  if (auth === "unavailable") {
    return done("integrity_unavailable", json({ suggestions: [], error: "verification unavailable" }, 503));
  }
  if (auth === "denied") return done("unauthorized", json({ error: "unauthorized" }, 401));

  const provider = createProvider(env, { logger: log });
  if (!provider) return done("not_configured", json({ error: "server not configured" }, 500));
  trace.llm_provider = provider.id;

  let body;
  try {
    body = JSON.parse(raw);
  } catch {
    return done("bad_request", json({ error: "invalid json" }, 400));
  }
  const req = parseRequest(body);
  if (!req.ok) {
    trace.error = req.error;
    return done("bad_request", json({ error: req.error }, 400));
  }
  trace.artists_count = req.artists.length;
  trace.exclude_count = req.exclude.length;

  const cache = caches.default;
  const cacheUrl = new URL(`/cache/${await cacheKey(req.artists, provider.id, req.exclude)}`, url.origin);
  const hit = await cache.match(cacheUrl);
  trace.cache = hit ? "hit" : "miss";
  if (hit) return done("ok", hit);

  const quota = createDailyQuota(env);
  if (quota) {
    const limit = Number(env.DAILY_LLM_LIMIT) || DEFAULT_DAILY_LLM_LIMIT;
    trace.quota_limit = limit;
    let allowed;
    try {
      const r = await quota.tryConsume(limit);
      allowed = r.ok;
      trace.quota_used = r.used;
    } catch (e) {
      // 数えられないときは止める側に倒す。無料枠を守るのが目的のため
      trace.error = `quota: ${errorText(e)}`;
      allowed = false;
    }
    if (!allowed) {
      return done(
        "daily_limit",
        json({ suggestions: [], error: "daily limit reached" }, 503, { "Retry-After": String(secondsUntilNextDay()) }),
      );
    }
  }

  let suggestions;
  const llmStarted = now();
  try {
    const candidates = await provider.suggestArtists(buildPrompt(req.artists, req.exclude));
    trace.llm_candidates = Array.isArray(candidates) ? candidates.length : 0;
    suggestions = sanitizeSuggestions(candidates, req.artists, req.exclude);
  } catch (e) {
    // 理由はログにだけ残す。呼び出し側は「今は出せない」とだけ分かればよい
    trace.llm_ms = now() - llmStarted;
    trace.error = errorText(e);
    return done("llm_failed", json({ suggestions: [], error: "upstream unavailable" }, 502));
  }
  trace.llm_ms = now() - llmStarted;
  trace.suggestions_count = suggestions.length;

  const res = json({ suggestions }, 200, { "Cache-Control": `max-age=${CACHE_TTL_SECONDS}` });
  // 空の結果は一時的な不調のことがあるので覚えない
  if (suggestions.length > 0) {
    ctx.waitUntil(cache.put(cacheUrl, res.clone()).catch((e) => log.warn("cache_put_failed", { error: errorText(e) })));
  }
  return done(suggestions.length > 0 ? "ok" : "empty", res);
}

/** @returns {Promise<"ok" | "denied" | "unavailable">} */
async function authorize(request, raw, env, verifier, trace, now) {
  const header = request.headers.get("Authorization");
  if (env.APP_TOKEN && header && (await tokenMatches(header, env.APP_TOKEN))) {
    trace.auth = "token";
    return "ok";
  }
  const integrityToken = request.headers.get("X-Play-Integrity");
  if (verifier && integrityToken) {
    trace.auth = "play_integrity";
    const started = now();
    let verdict;
    try {
      verdict = await verifier.verify(integrityToken, await bodyHash(raw));
    } catch (e) {
      trace.error = `integrity: ${errorText(e)}`;
      return "unavailable";
    } finally {
      trace.integrity_ms = now() - started;
    }
    if (verdict.ok) return "ok";
    trace.integrity_reason = verdict.reason;
  }
  return "denied";
}

/** 文字列比較の時間差からトークンを推測されないよう、ハッシュ同士を比べる */
async function tokenMatches(header, token) {
  const given = header?.startsWith("Bearer ") ? header.slice(7) : "";
  const enc = new TextEncoder();
  const [a, b] = await Promise.all([
    crypto.subtle.digest("SHA-256", enc.encode(given)),
    crypto.subtle.digest("SHA-256", enc.encode(token)),
  ]);
  return crypto.subtle.timingSafeEqual(a, b);
}

function json(data, status, headers = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", ...headers },
  });
}
