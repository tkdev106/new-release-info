// Play Integrity（標準リクエスト）でのアプリ確認。Play から入れた本物のアプリかどうかを Google に確かめてもらう。
// APK に入ったトークンは抜き出せるので、Play 版はトークンを持たず、毎回これで確かめる。
//
// Google の API はサービスアカウントの鍵（JSON）で OAuth のアクセストークンを取って呼ぶ。
// 依存パッケージを増やさないよう、JWT の署名は WebCrypto で行う。

const SCOPE = "https://www.googleapis.com/auth/playintegrity";
const TOKEN_URL = "https://oauth2.googleapis.com/token";
const TIMEOUT_MS = 10_000;
// 端末で取ってから届くまでの余裕。古いトークンの使い回しを防ぐ
const MAX_TOKEN_AGE_MS = 5 * 60 * 1000;

/**
 * @typedef {object} AppAttestation
 * @property {(token: string, requestHash: string) => Promise<{ ok: boolean, reason?: string }>} verify
 */

/** @returns {AppAttestation} */
export function createPlayIntegrity({ serviceAccount, packageName, fetchImpl = (...a) => fetch(...a), now = Date.now }) {
  let cached = null; // { token, expiresAt }

  async function accessToken() {
    if (cached && cached.expiresAt - 60_000 > now()) return cached.token;
    const assertion = await signJwt(serviceAccount, now());
    const r = await fetchImpl(TOKEN_URL, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }).toString(),
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    if (!r.ok) throw new Error(`oauth HTTP ${r.status}`);
    const data = await r.json();
    cached = { token: data.access_token, expiresAt: now() + (data.expires_in ?? 3600) * 1000 };
    return cached.token;
  }

  return {
    async verify(integrityToken, requestHash) {
      const url = `https://playintegrity.googleapis.com/v1/${encodeURIComponent(packageName)}:decodeIntegrityToken`;
      const r = await fetchImpl(url, {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${await accessToken()}` },
        body: JSON.stringify({ integrity_token: integrityToken }),
        signal: AbortSignal.timeout(TIMEOUT_MS),
      });
      // 壊れた・偽のトークンは 400 になる。呼び出し側は「確認できなかった」とだけ扱う
      if (r.status === 400) return { ok: false, reason: "invalid token" };
      if (!r.ok) throw new Error(`playintegrity HTTP ${r.status}`);
      return judgeVerdict((await r.json()).tokenPayloadExternal, { packageName, requestHash, now: now() });
    },
  };
}

/**
 * 判定の中身。通信と切り離して確かめられるように分けてある。
 * 端末の検査は MEETS_DEVICE_INTEGRITY（Play 開発者サービスが正しく入った端末）までにとどめ、
 * 強い判定（STRONG）は求めない。古い端末の利用者を締め出さないため。
 */
export function judgeVerdict(payload, { packageName, requestHash, now }) {
  const req = payload?.requestDetails;
  if (req?.requestPackageName !== packageName) return { ok: false, reason: "package mismatch" };
  if (req?.requestHash !== requestHash) return { ok: false, reason: "request hash mismatch" };
  const ts = Number(req?.timestampMillis);
  if (!Number.isFinite(ts) || Math.abs(now - ts) > MAX_TOKEN_AGE_MS) return { ok: false, reason: "stale token" };
  if (payload?.appIntegrity?.appRecognitionVerdict !== "PLAY_RECOGNIZED") return { ok: false, reason: "app not recognized" };
  const device = payload?.deviceIntegrity?.deviceRecognitionVerdict ?? [];
  if (!device.includes("MEETS_DEVICE_INTEGRITY")) return { ok: false, reason: "device integrity" };
  return { ok: true };
}

async function signJwt(sa, nowMs) {
  const iat = Math.floor(nowMs / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const claims = { iss: sa.client_email, scope: SCOPE, aud: TOKEN_URL, iat, exp: iat + 3600 };
  const unsigned = `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(claims))}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToDer(sa.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned));
  return `${unsigned}.${b64url(new Uint8Array(sig))}`;
}

function pemToDer(pem) {
  const body = pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  return Uint8Array.from(atob(body), (c) => c.charCodeAt(0));
}

function b64url(data) {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : data;
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** アプリと同じ作り方の requestHash（本文の SHA-256 を 16 進で）。トークンを別の本文に使い回させないため */
export async function bodyHash(raw) {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(raw));
  return [...new Uint8Array(d)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** @returns {AppAttestation | null} 鍵が無ければ null（Play 版の確認はできない） */
export function createAttestation(env) {
  if (!env.PLAY_INTEGRITY_SA_KEY) return null;
  let sa;
  try {
    sa = JSON.parse(env.PLAY_INTEGRITY_SA_KEY);
  } catch {
    return null;
  }
  return createPlayIntegrity({ serviceAccount: sa, packageName: env.PLAY_PACKAGE_NAME || "app.otoshirase" });
}
