// LLM を差し替えられるようにするための境目。
// 中継の本体（index.js）はこの形だけを知っていて、どの LLM かは環境変数 LLM_PROVIDER で選ぶ。

import { createGeminiProvider } from "./gemini.js";

/**
 * @typedef {object} LlmProvider
 * @property {string} id キャッシュのキーに使う。モデルを変えたら変わるようにする（例: "gemini:gemini-2.5-flash"）
 * @property {(prompt: import("../suggest.js").SuggestPrompt) => Promise<unknown>} suggestArtists
 *   候補の名前を返す。戻り値は信用せず、呼び出し側（sanitizeSuggestions）が検証する。
 *   通信の失敗・タイムアウト・形の崩れた応答は例外にする。
 */

/** 新しい LLM を足すときは、ここに 1 行足して llm/ に実装を置く */
const factories = {
  gemini: (env) =>
    env.GEMINI_API_KEY ? createGeminiProvider({ apiKey: env.GEMINI_API_KEY, model: env.GEMINI_MODEL }) : null,
};

/** @returns {LlmProvider | null} 設定が足りなければ null */
export function createProvider(env) {
  const make = factories[env.LLM_PROVIDER || "gemini"];
  return make ? make(env) : null;
}
