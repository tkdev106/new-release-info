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

test("LLM の設定が無くてもトークンが違えば先に断る", async () => {
  const r = await worker.fetch(
    new Request("https://w.example/v1/suggestions", { method: "POST", headers: { Authorization: "Bearer wrong" }, body: "{}" }),
    { ...env, GEMINI_API_KEY: "" },
    ctx,
  );
  assert.equal(r.status, 401);
});

test("トークンが違えば Gemini を呼ばずに断る", async () => {
  assert.equal((await call({ artists: ["a"] }, "wrong")).status, 401);
  assert.equal((await call({ artists: ["a"] }, null)).status, 401);
  assert.equal(geminiCalls, 0);
});

test("壊れた入力は Gemini を呼ばずに断る", async () => {
  assert.equal((await call("{")).status, 400);
  assert.equal((await call({ artists: [] })).status, 400);
  assert.equal((await call("x".repeat(17000))).status, 413);
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

test("LLM の設定が足りなければ断る", async () => {
  for (const e of [{ ...env, LLM_PROVIDER: "unknown" }, { ...env, GEMINI_API_KEY: "" }]) {
    const r = await worker.fetch(
      new Request("https://w.example/v1/suggestions", { method: "POST", headers: { Authorization: "Bearer secret" }, body: '{"artists":["a"]}' }),
      e,
      ctx,
    );
    assert.equal(r.status, 500);
  }
  assert.equal(geminiCalls, 0);
});

test("LLM が配列以外を返しても空の候補にする", async () => {
  geminiReplies.push(new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: '{"suggestions":"x"}' }] } }] })));
  const r = await call({ artists: ["a"] });
  assert.equal(r.status, 200);
  assert.deepEqual(await r.json(), { suggestions: [] });
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

test("連打を止める制限を超えたら 1 分の枠を使わずに断る", async () => {
  let minuteCalls = 0;
  const limited = {
    ...env,
    BURST_LIMITER: { limit: async ({ key }) => ({ success: key !== "1.2.3.4" }) },
    RATE_LIMITER: { limit: async () => (minuteCalls++, { success: true }) },
  };
  const r = await worker.fetch(
    new Request("https://w.example/v1/suggestions", {
      method: "POST",
      headers: { Authorization: "Bearer secret", "CF-Connecting-IP": "1.2.3.4" },
      body: JSON.stringify({ artists: ["a"] }),
    }),
    limited,
    ctx,
  );
  assert.equal(r.status, 429);
  assert.equal(r.headers.get("Retry-After"), "10");
  assert.equal(minuteCalls, 0);
  assert.equal(geminiCalls, 0);
});

test("決まった道以外は受け付けない", async () => {
  assert.equal((await call(null, "secret", "GET")).status, 405);
  const r = await worker.fetch(new Request("https://w.example/other", { method: "POST" }), env, ctx);
  assert.equal(r.status, 404);
});

const callFrom = (country, extraEnv = {}, headers = { Authorization: "Bearer secret" }, body = { artists: ["羊文学"] }) => {
  const req = new Request("https://w.example/v1/suggestions", { method: "POST", headers, body: JSON.stringify(body) });
  if (country) Object.defineProperty(req, "cf", { value: { country } });
  return worker.fetch(req, { ...env, ...extraEnv }, ctx);
};

test("EEA・英国・スイスからは Gemini を呼ばずに 451 で断る", async () => {
  for (const c of ["DE", "GB", "CH"]) assert.equal((await callFrom(c)).status, 451);
  assert.equal(geminiCalls, 0);
  geminiReplies.push(ok(["never young beach"]));
  assert.equal((await callFrom("JP")).status, 200);
});

function fakeQuota(limit) {
  let used = 0;
  return {
    idFromName: (n) => n,
    get: () => ({
      fetch: async (_u, init) => {
        const { limit: l } = JSON.parse(init.body);
        assert.equal(l, limit);
        if (used >= l) return Response.json({ ok: false });
        used++;
        return Response.json({ ok: true });
      },
    }),
  };
}

test("1 日の上限を超えたら Gemini を呼ばずに 503 を返し、キャッシュにあるものは返す", async () => {
  const quota = { DAILY_QUOTA: fakeQuota(1), DAILY_LLM_LIMIT: "1" };
  geminiReplies.push(ok(["never young beach"]));
  assert.equal((await callFrom(null, quota)).status, 200);
  const over = await callFrom(null, quota, undefined, { artists: ["YOASOBI"] });
  assert.equal(over.status, 503);
  assert.ok(Number(over.headers.get("Retry-After")) > 0);
  assert.equal(geminiCalls, 1);
  assert.equal((await callFrom(null, quota)).status, 200);
});

test("トークンの無い Play 版は Play Integrity が通れば使え、通らなければ断る", async () => {
  const { generateKeyPairSync, createHash } = await import("node:crypto");
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const key = JSON.stringify({ client_email: "sa@x", private_key: privateKey.export({ type: "pkcs8", format: "pem" }) });
  const body = { artists: ["羊文学"] };
  const hash = createHash("sha256").update(JSON.stringify(body)).digest("hex");
  const gemini = globalThis.fetch;
  let decoded = 0;
  globalThis.fetch = async (url, init) => {
    if (url.startsWith("https://oauth2.")) return Response.json({ access_token: "at", expires_in: 3600 });
    if (url.startsWith("https://playintegrity.")) {
      decoded++;
      const verdict = JSON.parse(init.body).integrity_token === "real" ? "PLAY_RECOGNIZED" : "UNRECOGNIZED_VERSION";
      return Response.json({
        tokenPayloadExternal: {
          requestDetails: { requestPackageName: "app.otoshirase", requestHash: hash, timestampMillis: String(Date.now()) },
          appIntegrity: { appRecognitionVerdict: verdict },
          deviceIntegrity: { deviceRecognitionVerdict: ["MEETS_DEVICE_INTEGRITY"] },
        },
      });
    }
    return gemini(url, init);
  };
  const playOnly = { APP_TOKEN: "", PLAY_INTEGRITY_SA_KEY: key };
  geminiReplies.push(ok(["never young beach"]));
  assert.equal((await callFrom(null, playOnly, { "X-Play-Integrity": "real" }, body)).status, 200);
  assert.equal((await callFrom(null, playOnly, { "X-Play-Integrity": "repackaged" }, body)).status, 401);
  assert.equal((await callFrom(null, playOnly, {}, body)).status, 401);
  assert.equal(decoded, 2);
  // APK で配った版のトークンも、Play Integrity と並べて使い続けられる
  assert.equal((await callFrom(null, { PLAY_INTEGRITY_SA_KEY: key }, { Authorization: "Bearer secret" }, body)).status, 200);
});
