// Workers の実行環境にある caches・timingSafeEqual・fetch を差し替えて、中継全体の振る舞いを確かめる
import { beforeEach, test } from "node:test";
import assert from "node:assert/strict";
import { timingSafeEqual } from "node:crypto";
import worker from "../src/index.js";

crypto.subtle.timingSafeEqual ??= (a, b) => timingSafeEqual(Buffer.from(a), Buffer.from(b));

let store;
let geminiCalls;
let geminiReplies;
const ctx = { waitUntil: (p) => p };
const env = { APP_TOKEN: "secret", GEMINI_API_KEY: "key", GEMINI_MODEL: "test-model" };

beforeEach(() => {
  store = new Map();
  globalThis.caches = {
    default: {
      match: async (u) => store.get(String(u))?.clone(),
      put: async (u, r) => void store.set(String(u), r),
    },
  };
  geminiCalls = 0;
  geminiReplies = [];
  globalThis.fetch = async (url, init) => {
    geminiCalls++;
    assert.equal(init.headers["x-goog-api-key"], "key");
    const next = geminiReplies.shift();
    if (next instanceof Error) throw next;
    return next;
  };
});

const ok = (names) =>
  new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: JSON.stringify({ suggestions: names }) }] } }] }));

const call = (body, token = "secret", method = "POST") =>
  worker.fetch(
    new Request("https://w.example/v1/suggestions", {
      method,
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: method === "POST" ? (typeof body === "string" ? body : JSON.stringify(body)) : undefined,
    }),
    env,
    ctx,
  );

test("トークンが違えば Gemini を呼ばずに断る", async () => {
  assert.equal((await call({ artists: ["a"] }, "wrong")).status, 401);
  assert.equal((await call({ artists: ["a"] }, null)).status, 401);
  assert.equal(geminiCalls, 0);
});

test("壊れた入力は Gemini を呼ばずに断る", async () => {
  assert.equal((await call("{")).status, 400);
  assert.equal((await call({ artists: [] })).status, 400);
  assert.equal((await call("x".repeat(9000))).status, 413);
  assert.equal(geminiCalls, 0);
});

test("候補を返し、同じ入力の2回目はキャッシュから返す", async () => {
  geminiReplies.push(ok(["never young beach", "羊文学"]));
  const r1 = await call({ artists: ["羊文学"] });
  assert.equal(r1.status, 200);
  assert.deepEqual(await r1.json(), { suggestions: ["never young beach"] });
  const r2 = await call({ artists: ["羊文学"] });
  assert.deepEqual(await r2.json(), { suggestions: ["never young beach"] });
  assert.equal(geminiCalls, 1);
});

test("一時的な失敗は1回だけやり直す", async () => {
  geminiReplies.push(new Response("busy", { status: 503 }), ok(["b"]));
  const r = await call({ artists: ["a"] });
  assert.deepEqual(await r.json(), { suggestions: ["b"] });
  assert.equal(geminiCalls, 2);
});

test("Gemini が使えないときは空の候補と502を返し、覚えない", async () => {
  geminiReplies.push(new Response("", { status: 500 }), new Response("", { status: 500 }));
  const r = await call({ artists: ["a"] });
  assert.equal(r.status, 502);
  assert.deepEqual((await r.json()).suggestions, []);
  assert.equal(store.size, 0);
});

test("タイムアウトや通信エラーでも空の候補を返す", async () => {
  geminiReplies.push(new DOMException("timed out", "TimeoutError"));
  const r = await call({ artists: ["a"] });
  assert.equal(r.status, 502);
  assert.deepEqual((await r.json()).suggestions, []);
});

test("回数制限を超えたら断る", async () => {
  const limited = { ...env, RATE_LIMITER: { limit: async () => ({ success: false }) } };
  const r = await worker.fetch(
    new Request("https://w.example/v1/suggestions", { method: "POST", headers: { Authorization: "Bearer secret" }, body: "{}" }),
    limited,
    ctx,
  );
  assert.equal(r.status, 429);
  assert.equal(geminiCalls, 0);
});

test("決まった道以外は受け付けない", async () => {
  assert.equal((await call(null, "secret", "GET")).status, 405);
  const r = await worker.fetch(new Request("https://w.example/other", { method: "POST" }), env, ctx);
  assert.equal(r.status, 404);
});
