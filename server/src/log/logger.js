// ログの境目。本体や LLM の実装はこの Logger の形だけを知っていて、どこへ書くかは知らない。
// 今は 1 件を 1 つの JSON にして console に出し、Cloudflare の Workers Logs（wrangler.toml の observability）が
// フィールドごとに拾って検索・集計できるようにしている。送り先を変えるときはここに実装を足せばよい。
//
// 個人を特定できる値（IP・トークン・Play Integrity のトークン）は渡さない。好きなアーティストも件数だけにする。
// 失敗の調べ物には件数と結果の種類で足り、利用者の好みを残す理由が無いため。

/**
 * @typedef {Record<string, unknown>} LogFields
 * @typedef {object} Logger
 * @property {(event: string, fields?: LogFields) => void} info
 * @property {(event: string, fields?: LogFields) => void} warn
 * @property {(event: string, fields?: LogFields) => void} error
 * @property {(fields: LogFields) => Logger} with 毎回付けるフィールドを足したロガーを返す（リクエストの ID など）
 */

/**
 * @param {{ write?: (level: "info" | "warn" | "error", record: LogFields) => void, base?: LogFields }} [options]
 * @returns {Logger}
 */
export function createJsonLogger({ write = writeToConsole, base = {} } = {}) {
  const emit = (level) => (event, fields = {}) => {
    // 値の無いフィールドは落とし、検索画面で「undefined」が並ばないようにする
    const record = { message: event, level };
    for (const [k, v] of Object.entries({ ...base, ...fields })) if (v !== undefined) record[k] = v;
    try {
      write(level, record);
    } catch {
      // ログが書けなくても応答は返す
    }
  };
  return {
    info: emit("info"),
    warn: emit("warn"),
    error: emit("error"),
    with: (fields) => createJsonLogger({ write, base: { ...base, ...fields } }),
  };
}

// Workers Logs はオブジェクトをそのまま渡すとフィールドとして取り込む。level で console のメソッドも分け、
// ダッシュボードの絞り込み（Error だけ等）が効くようにする
function writeToConsole(level, record) {
  console[level](record);
}

/** テストや、ログを出したくない場所で使う */
export const silentLogger = createJsonLogger({ write: () => {} });

/** 例外を、スタックや秘密を含まない短い文字列にする */
export function errorText(e) {
  if (e instanceof Error) return `${e.name}: ${e.message}`.slice(0, 300);
  return String(e).slice(0, 300);
}
