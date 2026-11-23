// 地域・1 日の上限・Play Integrity の判断を、Workers や Google に頼らずに確かめる
import { test } from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync } from "node:crypto";
import { isBlockedRegion } from "../src/guard/region.js";
import { DailyQuotaCounter, dayKey, secondsUntilNextDay } from "../src/guard/quota.js";
import { bodyHash, createPlayIntegrity, judgeVerdict } from "../src/guard/playIntegrity.js";

test("EEA・英国・スイスは断り、それ以外と国が分からないときは通す", () => {
  for (const c of ["DE", "FR", "NO", "IS", "LI", "GB", "CH", "de"]) assert.equal(isBlockedRegion(c), true, c);
  for (const c of ["JP", "US", "TR", "UA", undefined, null, ""]) assert.equal(isBlockedRegion(c), false, String(c));
});

test("1 日の区切りは UTC で、次の区切りまでの秒数を返す", () => {
  const t = Date.UTC(2026, 10, 23, 23, 59, 0);
  assert.equal(dayKey(t), "2026-11-23");
  assert.equal(secondsUntilNextDay(t), 60);
});

function counter() {
  const data = new Map();
  const storage = { get: async (k) => data.get(k), put: async (k, v) => void data.set(k, v) };
  const c = new DailyQuotaCounter({ storage });
  return (limit, day) =>
    c.fetch(new Request("https://quota/consume", { method: "POST", body: JSON.stringify({ limit, day }) })).then((r) => r.json());
}

test("上限までは数え、超えたら断り、日が変わったらまた数え直す", async () => {
  const consume = counter();
  assert.equal((await consume(2, "2026-11-23")).ok, true);
  assert.equal((await consume(2, "2026-11-23")).ok, true);
  assert.equal((await consume(2, "2026-11-23")).ok, false);
  assert.deepEqual(await consume(2, "2026-11-24"), { ok: true, used: 1 });
});

const NOW = Date.UTC(2026, 10, 23, 0, 0, 0);
const good = (over = {}) => ({
  requestDetails: { requestPackageName: "app.otoshirase", requestHash: "h", timestampMillis: String(NOW - 1000), ...over.requestDetails },
  appIntegrity: { appRecognitionVerdict: "PLAY_RECOGNIZED", ...over.appIntegrity },
  deviceIntegrity: { deviceRecognitionVerdict: ["MEETS_DEVICE_INTEGRITY"], ...over.deviceIntegrity },
});
const judge = (p) => judgeVerdict(p, { packageName: "app.otoshirase", requestHash: "h", now: NOW });

test("Play から入れたアプリで、正しい端末からの新しいトークンだけを通す", () => {
  assert.deepEqual(judge(good()), { ok: true });
  assert.equal(judge(good({ requestDetails: { requestPackageName: "other.app" } })).ok, false);
  assert.equal(judge(good({ requestDetails: { requestHash: "other" } })).ok, false);
  assert.equal(judge(good({ requestDetails: { timestampMillis: String(NOW - 10 * 60 * 1000) } })).ok, false);
  assert.equal(judge(good({ appIntegrity: { appRecognitionVerdict: "UNRECOGNIZED_VERSION" } })).ok, false);
  assert.equal(judge(good({ deviceIntegrity: { deviceRecognitionVerdict: [] } })).ok, false);
  assert.equal(judge(undefined).ok, false);
});

test("本文のハッシュは SHA-256 の 16 進", async () => {
  assert.equal(await bodyHash("abc"), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
});

test("サービスアカウントで Google に問い合わせ、アクセストークンは使い回す", async () => {
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const sa = { client_email: "sa@example.iam.gserviceaccount.com", private_key: privateKey.export({ type: "pkcs8", format: "pem" }) };
  const calls = [];
  const fetchImpl = async (url, init) => {
    calls.push(url);
    if (url.startsWith("https://oauth2.googleapis.com/")) {
      const assertion = new URLSearchParams(init.body).get("assertion");
      const claims = JSON.parse(Buffer.from(assertion.split(".")[1], "base64url").toString());
      assert.equal(claims.iss, sa.client_email);
      assert.equal(claims.scope, "https://www.googleapis.com/auth/playintegrity");
      return Response.json({ access_token: "at", expires_in: 3600 });
    }
    assert.equal(init.headers.Authorization, "Bearer at");
    if (JSON.parse(init.body).integrity_token === "bad") return new Response("{}", { status: 400 });
    return Response.json({ tokenPayloadExternal: good() });
  };
  const pi = createPlayIntegrity({ serviceAccount: sa, packageName: "app.otoshirase", fetchImpl, now: () => NOW });
  assert.deepEqual(await pi.verify("tok", "h"), { ok: true });
  assert.equal((await pi.verify("bad", "h")).ok, false);
  assert.equal(calls.filter((u) => u.startsWith("https://oauth2")).length, 1);
  assert.match(calls[1], /\/v1\/app\.otoshirase:decodeIntegrityToken$/);
});
