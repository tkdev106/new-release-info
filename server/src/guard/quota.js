// 全利用者を合わせた 1 日の LLM 呼び出し回数の上限。Gemini の無料枠（1 日あたりの回数）を使い切って
// 全員が使えなくなる前に、こちらで止める。キャッシュから返したものは数えない。
//
// 本体（index.js）は DailyQuota の形だけを知っている。今は Durable Object で数えている。

/**
 * @typedef {object} DailyQuota
 * @property {(limit: number) => Promise<{ ok: boolean, used?: number }>} tryConsume
 *   今日の回数を 1 増やす。上限を超えるなら増やさず ok: false。used は今日使った回数（残りの見張りに使う）
 */

/** UTC の日付で区切る。次の区切りまでの秒数は Retry-After に使う */
export function dayKey(now = Date.now()) {
  return new Date(now).toISOString().slice(0, 10);
}

export function secondsUntilNextDay(now = Date.now()) {
  const next = new Date(now);
  next.setUTCHours(24, 0, 0, 0);
  return Math.max(1, Math.ceil((next.getTime() - now) / 1000));
}

/** @returns {DailyQuota | null} 設定が無ければ null（上限なし） */
export function createDailyQuota(env) {
  const ns = env.DAILY_QUOTA;
  if (!ns) return null;
  // 全体で 1 つの数にしたいので、名前を固定して 1 つのインスタンスに集める
  const stub = ns.get(ns.idFromName("global"));
  return {
    async tryConsume(limit) {
      const r = await stub.fetch("https://quota/consume", { method: "POST", body: JSON.stringify({ limit, day: dayKey() }) });
      if (!r.ok) throw new Error(`quota HTTP ${r.status}`);
      const data = await r.json();
      return { ok: data.ok === true, used: typeof data.used === "number" ? data.used : undefined };
    },
  };
}

/**
 * 回数を数える Durable Object。1 つのインスタンスへの呼び出しは順番に処理されるので、
 * 読んでから書くまでの間に他の呼び出しが割り込むことはない。
 */
export class DailyQuotaCounter {
  constructor(state) {
    this.storage = state.storage;
  }

  async fetch(request) {
    const { limit, day } = await request.json();
    const saved = (await this.storage.get("count")) ?? { day, n: 0 };
    const current = saved.day === day ? saved : { day, n: 0 };
    if (current.n >= limit) return Response.json({ ok: false, used: current.n });
    await this.storage.put("count", { day, n: current.n + 1 });
    return Response.json({ ok: true, used: current.n + 1 });
  }
}
