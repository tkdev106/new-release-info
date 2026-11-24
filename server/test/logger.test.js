import { test } from "node:test";
import assert from "node:assert/strict";
import { createJsonLogger, errorText } from "../src/log/logger.js";

test("1 件を 1 つのオブジェクトにし、付け足したフィールドを引き継ぐ", () => {
  const out = [];
  const log = createJsonLogger({ write: (level, r) => out.push([level, r]) }).with({ request_id: "r1", country: undefined });
  log.warn("llm_attempt", { attempt: 1, skipped: undefined });
  assert.deepEqual(out, [["warn", { message: "llm_attempt", level: "warn", request_id: "r1", attempt: 1 }]]);
});

test("書き出しに失敗しても例外を投げない", () => {
  const log = createJsonLogger({
    write: () => {
      throw new Error("x");
    },
  });
  assert.doesNotThrow(() => log.error("request"));
});

test("例外は短い文字列にする", () => {
  assert.equal(errorText(new TypeError("bad")), "TypeError: bad");
  assert.equal(errorText("x".repeat(500)).length, 300);
});
