// おすすめアーティストを LLM に聞く中継。LLM の API キーをアプリに入れないために置く。
// 呼び出し側（Android・将来の iOS）に依存しない、JSON を POST するだけの形にしている。
// どの LLM を使うかは llm/provider.js の LlmProvider 越しにしか知らない。

// 守り（地域・回数・アプリの確認）は guard/ にあり、ここはその形だけを使う。

import { bodyHash, createAttestation } from "./guard/playIntegrity.js";
import { createDailyQuota, secondsUntilNextDay } from "./guard/quota.js";
import { isBlockedRegion } from "./guard/region.js";
import { createProvider } from "./llm/provider.js";
import { buildPrompt, cacheKey, parseRequest, sanitizeSuggestions } from "./suggest.js";

export { DailyQuotaCounter } from "./guard/quota.js";

const CACHE_TTL_SECONDS = 7 * 24 * 60 * 60;
const MAX_BODY_BYTES = 8 * 1024;
const DEFAULT_DAILY_LLM_LIMIT = 500;

// アクセストークンを呼び出しをまたいで使い回すため、同じ鍵のあいだは同じものを持っておく
let attestation = { key: undefined, value: null };
function attestationFor(env) {
  if (attestation.key !== env.PLAY_INTEGRITY_SA_KEY) {
    attestation = { key: env.PLAY_INTEGRITY_SA_KEY, value: createAttestation(env) };
  }
  return attestation.value;
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (url.pathname !== "/v1/suggestions") return json({ error: "not found" }, 404);
    if (request.method !== "POST") return json({ error: "method not allowed" }, 405);

    if (isBlockedRegion(request.cf?.country)) return json({ suggestions: [], error: "unavailable in this region" }, 451);

    const verifier = attestationFor(env);
    if (!env.APP_TOKEN && !verifier) return json({ error: "server not configured" }, 500);

    // 回数制限は設定されていれば使う（wrangler.toml の ratelimits）。
    // 連打は 10 秒の窓で、長く続く呼び出しは 1 分の窓で止める。片方だけだと、
    // 1 分 10 回の枠を数秒で使い切る連打を最初の数回で止められないため。
    // アプリの確認（Google への問い合わせ）より前に置き、確認の枠も食い尽くされないようにする
    const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
    for (const [limiter, period] of [[env.BURST_LIMITER, "10"], [env.RATE_LIMITER, "60"]]) {
      if (!limiter) continue;
      const { success } = await limiter.limit({ key: ip });
      if (!success) return json({ error: "too many requests" }, 429, { "Retry-After": period });
    }

    const raw = await request.text();
    if (raw.length > MAX_BODY_BYTES) return json({ error: "body too large" }, 413);

    // 呼べるのは次のどちらか。
    // - トークン: 開発版や APK で配った版。トークンは APK から抜けるので、配る相手は限る
    // - Play Integrity: Play から入れた版。トークンを持たない
    // LLM の設定状況は、確認を通った呼び出し側にだけ見せる
    const auth = await authorize(request, raw, env, verifier);
    if (auth !== "ok") return auth;

    const provider = createProvider(env);
    if (!provider) return json({ error: "server not configured" }, 500);

    let body;
    try {
      body = JSON.parse(raw);
    } catch {
      return json({ error: "invalid json" }, 400);
    }
    const req = parseRequest(body);
    if (!req.ok) return json({ error: req.error }, 400);

    const cache = caches.default;
    const cacheUrl = new URL(`/cache/${await cacheKey(req.artists, provider.id)}`, url.origin);
    const hit = await cache.match(cacheUrl);
    if (hit) return hit;

    const quota = createDailyQuota(env);
    if (quota) {
      const limit = Number(env.DAILY_LLM_LIMIT) || DEFAULT_DAILY_LLM_LIMIT;
      let allowed;
      try {
        allowed = await quota.tryConsume(limit);
      } catch (e) {
        // 数えられないときは止める側に倒す。無料枠を守るのが目的のため
        console.error("quota failed", String(e));
        allowed = false;
      }
      if (!allowed) {
        return json({ suggestions: [], error: "daily limit reached" }, 503, { "Retry-After": String(secondsUntilNextDay()) });
      }
    }

    let suggestions;
    try {
      const candidates = await provider.suggestArtists(buildPrompt(req.artists));
      suggestions = sanitizeSuggestions(candidates, req.artists);
    } catch (e) {
      // 理由はログにだけ残す。呼び出し側は「今は出せない」とだけ分かればよい
      console.error("llm failed", provider.id, String(e));
      return json({ suggestions: [], error: "upstream unavailable" }, 502);
    }

    const res = json({ suggestions }, 200, { "Cache-Control": `max-age=${CACHE_TTL_SECONDS}` });
    // 空の結果は一時的な不調のことがあるので覚えない
    if (suggestions.length > 0) ctx.waitUntil(cache.put(cacheUrl, res.clone()));
    return res;
  },
};

/** @returns {Promise<"ok" | Response>} */
async function authorize(request, raw, env, verifier) {
  const header = request.headers.get("Authorization");
  if (env.APP_TOKEN && header && (await tokenMatches(header, env.APP_TOKEN))) return "ok";
  const integrityToken = request.headers.get("X-Play-Integrity");
  if (verifier && integrityToken) {
    let verdict;
    try {
      verdict = await verifier.verify(integrityToken, await bodyHash(raw));
    } catch (e) {
      console.error("integrity check failed", String(e));
      return json({ suggestions: [], error: "verification unavailable" }, 503);
    }
    if (verdict.ok) return "ok";
    console.warn("integrity rejected", verdict.reason);
  }
  return json({ error: "unauthorized" }, 401);
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
