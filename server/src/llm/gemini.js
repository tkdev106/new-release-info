// Gemini（Google AI Studio の API キー）での LlmProvider の実装

import { MAX_SUGGESTIONS } from "../suggest.js";

const TIMEOUT_MS = 10_000;
const RETRY_DELAY_MS = 800;
const DEFAULT_MODEL = "gemini-3.5-flash-lite";

// Gemini の responseSchema は OpenAPI の一部で、型名は大文字
const RESPONSE_SCHEMA = {
  type: "OBJECT",
  properties: {
    suggestions: {
      type: "ARRAY",
      maxItems: MAX_SUGGESTIONS,
      items: { type: "STRING", description: "Artist name exactly as listed on Apple Music (Japan store)" },
    },
  },
  required: ["suggestions"],
};

/** @returns {import("./provider.js").LlmProvider} */
export function createGeminiProvider({ apiKey, model = DEFAULT_MODEL, fetchImpl = (...a) => fetch(...a) }) {
  model ||= DEFAULT_MODEL;
  const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`;

  return {
    id: `gemini:${model}`,
    async suggestArtists(prompt) {
      const init = {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
        body: JSON.stringify(buildRequest(prompt)),
      };
      for (let attempt = 0; ; attempt++) {
        const r = await fetchImpl(endpoint, { ...init, signal: AbortSignal.timeout(TIMEOUT_MS) });
        if (r.ok) return parseResponse(await r.json());
        // 混雑・一時的な失敗のときだけ 1 回やり直す。それ以外はやり直しても変わらない
        if (attempt === 0 && (r.status === 429 || r.status >= 500)) {
          await new Promise((resolve) => setTimeout(resolve, RETRY_DELAY_MS));
          continue;
        }
        throw new Error(`gemini HTTP ${r.status}`);
      }
    },
  };
}

export function buildRequest(prompt) {
  return {
    systemInstruction: { parts: [{ text: prompt.system }] },
    contents: [{ role: "user", parts: [{ text: prompt.user }] }],
    generationConfig: {
      responseMimeType: "application/json",
      responseSchema: RESPONSE_SCHEMA,
      temperature: 0.4,
    },
  };
}

/** 応答の外側（candidates…text）を剥がして suggestions を取り出す。中身の検証は呼び出し側 */
export function parseResponse(data) {
  const text = data?.candidates?.[0]?.content?.parts?.[0]?.text;
  if (typeof text !== "string") throw new Error("gemini: no text in response");
  const parsed = JSON.parse(text); // 崩れていれば SyntaxError
  return parsed?.suggestions;
}
