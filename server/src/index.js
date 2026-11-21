// おすすめアーティストを LLM に聞く中継。LLM の API キーをアプリに入れないために置く。
// 呼び出し側（Android・将来の iOS）に依存しない、JSON を POST するだけの形にしている。
// どの LLM を使うかは llm/provider.js の LlmProvider 越しにしか知らない。

import { createProvider } from "./llm/provider.js";
import { buildPrompt, cacheKey, parseRequest, sanitizeSuggestions } from "./suggest.js";

const CACHE_TTL_SECONDS = 7 * 24 * 60 * 60;
const MAX_BODY_BYTES = 8 * 1024;

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (url.pathname !== "/v1/suggestions") return json({ error: "not found" }, 404);
    if (request.method !== "POST") return json({ error: "method not allowed" }, 405);

    if (!env.APP_TOKEN) return json({ error: "server not configured" }, 500);
    // LLM の設定状況は、トークンを持つ呼び出し側にだけ見せる
    if (!(await tokenMatches(request.headers.get("Authorization"), env.APP_TOKEN))) {
      return json({ error: "unauthorized" }, 401);
    }
    const provider = createProvider(env);
    if (!provider) return json({ error: "server not configured" }, 500);

    // 回数制限は設定されていれば使う（wrangler.toml の ratelimits）
    if (env.RATE_LIMITER) {
      const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
      const { success } = await env.RATE_LIMITER.limit({ key: ip });
      if (!success) return json({ error: "too many requests" }, 429);
    }

    const raw = await request.text();
    if (raw.length > MAX_BODY_BYTES) return json({ error: "body too large" }, 413);
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

    let suggestions;
    try {
      const raw = await provider.suggestArtists(buildPrompt(req.artists));
      suggestions = sanitizeSuggestions(raw, req.artists);
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
