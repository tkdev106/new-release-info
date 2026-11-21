# new-release-info

新譜通知 Android アプリ「New Releases」。

登録したアーティストの新譜（アルバム・EP・シングル、発売予定を含む）を通知する Android アプリ。

- 新譜データ: Apple の公開カタログ API（iTunes Search API、日本ストア）。6時間おきに端末内でチェック
- アーティスト登録: 名前で検索、または「よく聴く」タブ（音楽アプリの再生中情報から集計。通知へのアクセス権が必要）
- ホーム画面ウィジェット（Jetpack Glance）
- データはすべて端末内（`files/state.json`）に保存

## ビルド

```bash
./gradlew verify          # lint・単体テスト・APK のビルドをまとめて
./gradlew assembleDebug   # APK だけ
```

JDK 21 が必要（Java 25 では Gradle 8.14 が動かない）。push すると GitHub Actions が同じ検証を走らせ、APK を成果物（app-debug）に置く。

APK は `app/build/outputs/apk/debug/app-debug.apk` に出力される。

## テスト

```bash
./gradlew testDebugUnitTest
```

`CheckerTest` は実際の Apple カタログ API に問い合わせる。
