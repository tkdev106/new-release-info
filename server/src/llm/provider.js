// LLM を差し替えられるようにするための境目。
// 中継の本体（index.js）はこの形だけを知っていて、どの LLM かは環境変数 LLM_PROVIDER で選ぶ。

import { silentLogger } from "../log/logger.js";
import { createGeminiProvider } from "./gemini.js";

/**
 * @typedef {object} LlmProvider
 * @property {string} id キャッシュのキーに使う。モデルを変えたら変わるようにする（例: "gemini:gemini-3.5-flash-lite"）
 * @property {(prompt: import("../suggest.js").SuggestPrompt) => Promise<unknown>} suggestArtists
 *   候補の名前を返す。戻り値は信用せず、呼び出し側（sanitizeSuggestions）が検証する。
 *   通信の失敗・タイムアウト・形の崩れた応答は例外にする。
 */

/** 新しい LLM を足すときは、ここに 1 行足して llm/ に実装を置く */
// 実装には logger を渡し、やり直しの回数や上流の状態・使ったトークン数など、実装の中でしか分からないことを残させる
const factories = {
  gemini: (env, { logger }) =>
    env.GEMINI_API_KEY ? createGeminiProvider({ apiKey: env.GEMINI_API_KEY, model: env.GEMINI_MODEL, logger }) : null,
};

/**
 * @param {{ logger?: import("../log/logger.js").Logger }} [deps]
 * @returns {LlmProvider | null} 設定が足りなければ null
 */
export function createProvider(env, { logger = silentLogger } = {}) {
  const make = factories[env.LLM_PROVIDER || "gemini"];
  return make ? make(env, { logger }) : null;
}
