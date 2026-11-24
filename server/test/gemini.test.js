import { test } from "node:test";
import assert from "node:assert/strict";
import { createGeminiProvider, parseResponse } from "../src/llm/gemini.js";
import { buildPrompt } from "../src/suggest.js";

const reply = (body, status = 200) => new Response(typeof body === "string" ? body : JSON.stringify(body), { status });
const wrap = (text) => ({ candidates: [{ content: { parts: [{ text }] } }] });

function provider(replies) {
  const calls = [];
  const p = createGeminiProvider({
    apiKey: "key",
    model: "m",
    fetchImpl: async (url, init) => {
      calls.push({ url, init });
      const next = replies.shift();
      if (next instanceof Error) throw next;
      return next;
    },
  });
  return { p, calls };
}

test("キーはヘッダーで送り、指示とデータを分けて構造化出力を頼む", async () => {
  const { p, calls } = provider([reply(wrap('{"suggestions":["A"]}'))]);
  assert.deepEqual(await p.suggestArtists(buildPrompt(["羊文学"])), ["A"]);
  assert.equal(calls[0].init.headers["x-goog-api-key"], "key");
  assert.ok(!calls[0].url.includes("key"));
  const body = JSON.parse(calls[0].init.body);
  assert.equal(body.generationConfig.responseMimeType, "application/json");
  assert.deepEqual(JSON.parse(body.contents[0].parts[0].text), { likedArtists: ["羊文学"], alreadySuggested: [] });
});

test("モデル名がキャッシュのキーに入るようにする", () => {
  assert.equal(createGeminiProvider({ apiKey: "k", model: "x" }).id, "gemini:x");
  assert.equal(createGeminiProvider({ apiKey: "k", model: "" }).id, "gemini:gemini-3.5-flash-lite");
});

test("一時的な失敗は1回だけやり直す", async () => {
  const { p, calls } = provider([reply("busy", 503), reply(wrap('{"suggestions":["B"]}'))]);
  assert.deepEqual(await p.suggestArtists(buildPrompt(["a"])), ["B"]);
  assert.equal(calls.length, 2);
});

test("やり直しても駄目なときや400系は例外にする", async () => {
  await assert.rejects(provider([reply("", 500), reply("", 500)]).p.suggestArtists(buildPrompt(["a"])));
  const bad = provider([reply("", 400)]);
  await assert.rejects(bad.p.suggestArtists(buildPrompt(["a"])));
  assert.equal(bad.calls.length, 1);
});

test("崩れた応答は例外にする", () => {
  assert.throws(() => parseResponse(wrap("{suggestions: [")));
  assert.throws(() => parseResponse({}));
  assert.equal(parseResponse(wrap('{"other":1}')), undefined);
});
