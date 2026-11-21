# おすすめアーティストの中継（Cloudflare Worker）

アプリから受け取った「好きなアーティスト名」を Gemini に渡し、おすすめの**名前だけ**を返す。
Gemini の API キーをアプリに入れないために置いている。

返す名前は実在の保証がない。アプリ側で Apple のカタログと照合し、見つからないものは捨てる前提。

## API

```
POST /v1/suggestions
Authorization: Bearer <APP_TOKEN>
Content-Type: application/json

{"artists": ["羊文学", "YOASOBI"]}        // 1〜30 件、1 件 100 文字まで
```

| 状態 | 本文 |
| --- | --- |
| 200 | `{"suggestions": ["never young beach", ...]}`（最大 15 件。入力に含まれる名前は除いてある） |
| 400 / 413 | 入力が不正・大きすぎる |
| 401 | トークンが違う |
| 429 | 回数制限（1 IP あたり 1 分 10 回） |
| 502 | `{"suggestions": [], "error": "upstream unavailable"}`。Gemini が失敗・タイムアウト |

呼び出し側は 200 以外をすべて「今は提案なし」として扱えばよい。Android 以外（将来の iOS など）からも同じ形で呼べる。

- Gemini は 10 秒で打ち切り、429・5xx のときだけ 1 回やり直す
- 同じアーティストの組み合わせ（順番・表記ゆれは無視）への結果は 7 日キャッシュする。空の結果は覚えない

## 初回のセットアップ（本人の作業）

1. **Gemini の API キーを作る。** [Google AI Studio](https://aistudio.google.com/apikey) で、**課金を有効にしていない** Google Cloud プロジェクトにキーを作る。課金が無いので、使われすぎても無料枠が尽きるだけで請求は来ない
2. **Cloudflare にログインする。** `server/` で `npx wrangler login`（アカウントが無ければ無料で作る）
3. **秘密の値を登録する。** どちらも `wrangler secret put` で入れ、ファイルやリポジトリには書かない
   ```
   cd server
   npx wrangler secret put GEMINI_API_KEY   # 1 で作ったキー
   npx wrangler secret put APP_TOKEN        # 好きな長いランダム文字列。例: openssl rand -hex 32 の出力
   ```
4. **デプロイする。** `npm run deploy`。表示された `https://new-releases-suggest.<名前>.workers.dev` が URL
5. **動作確認。**
   ```
   curl -s https://new-releases-suggest.<名前>.workers.dev/v1/suggestions \
     -H "Authorization: Bearer <APP_TOKEN>" -H "Content-Type: application/json" \
     -d '{"artists":["羊文学","never young beach"]}'
   ```
6. **アプリに接続先を書く。** リポジトリ直下の `local.properties`（Git に入らない）に足して、アプリをビルドし直す
   ```
   suggest.url=https://new-releases-suggest.<名前>.workers.dev
   suggest.token=<APP_TOKEN と同じ値>
   ```
   書いていないビルド（CI の APK など）では、おすすめ自体が出ない。トークンは APK の中に入るので、APK は人に配らない

手元で試すときは `server/.dev.vars` に `GEMINI_API_KEY=...` と `APP_TOKEN=...` を書いて `npm run dev`。

## 開発

```
npm test   # Node の node:test だけを使う。依存パッケージは無い
```

`wrangler` は `npx` で都度使い、依存には入れていない。
