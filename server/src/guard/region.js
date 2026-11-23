// Gemini の無料枠の規約で、EEA・英国・スイスの利用者には有料枠しか使えないため、その地域からの呼び出しを断る。
// Play の配信国からも外しているが、旅行中の端末や APK で入れた端末からも呼べるので、中継側でも止める。

// EU 27 か国 + EEA（アイスランド・リヒテンシュタイン・ノルウェー）+ 英国・スイス（ISO 3166-1 alpha-2）
export const BLOCKED_COUNTRIES = new Set([
  "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE",
  "IT", "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE",
  "IS", "LI", "NO",
  "GB", "CH",
]);

/** Cloudflare が付ける国コード（request.cf.country）で判断する。分からないとき（手元の開発など）は通す */
export function isBlockedRegion(country) {
  return typeof country === "string" && BLOCKED_COUNTRIES.has(country.toUpperCase());
}
