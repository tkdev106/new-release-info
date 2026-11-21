# new-release-info

登録したアーティストの新譜（アルバム・EP・シングル、発売予定を含む）を通知する Android アプリ「New Releases」。
ライブ情報は規約の理由で扱わない。アプリの内部 ID（パッケージ名）は `app.otoshirase` のまま（旧称の名残。変えると端末で入れ直しになる）。

| 知りたいこと | 場所 |
| --- | --- |
| 新譜の取得・判定 | `app/src/main/java/app/otoshirase/data/AppleCatalog.kt`, `work/Checker.kt` |
| 保存データ（端末内の JSON） | `data/Models.kt`, `data/Store.kt` |
| 画面 | `ui/MainActivity.kt`, `ui/Theme.kt` |
| ウィジェット | `widget/` |
| 通知 | `notify/Notifier.kt` |
| よく聴くアーティストの集計 | `playback/NowPlayingService.kt` |
| 文言 | `res/values/strings.xml`（英語・既定）, `res/values-ja/strings.xml`（日本語） |

## コマンド

```
./gradlew verify   # 完了の定義。lint → 単体テスト → assembleDebug
```

- **完了の定義は `./gradlew verify` が緑になること。** CI（`.github/workflows/ci.yml`）が同じものを走らせ、デバッグ APK を成果物に出す
- このプロジェクトの Gradle 8.14 は Java 25 では動かない。手元では JDK 21 を使う: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`
- テストを skip・無効化して緑にしない。lint の規則を緩めて緑にしない
- 画面の確認はエミュレーター（AVD `Medium_Phone_API_37.0`、`~/Library/Android/sdk/platform-tools/adb`）で行う。見た目を変えたらライト・ダーク、日本語・英語の両方で確かめる

## 技術

- Kotlin、Jetpack Compose（Material 3）、ウィジェットは Glance、定期チェックは WorkManager、通信は OkHttp、保存は kotlinx.serialization で `files/state.json`
- 新譜データは Apple の iTunes Search API（日本ストア、無料・認証なし）。発売前の作品も載るので「発売予定」が分かる
- アーティスト画像は Apple Music の公開ページの `og:image`（`data/ArtistImages.kt`）。公式 API ではないので、取れなければ最新作のジャケット → 頭文字の順で代用する
- 新譜をタップしたら、再生回数がいちばん多い音楽アプリの検索画面で開く（`data/MusicApps.kt`）。開けなければ Apple Music の URL

## 決めたこと（覆すときはユーザーに聞く）

- **費用がかかるものは使わない。** Spotify Web API（開発者の Premium 必須・利用者 5 人まで・発売予定が取れない）と Apple Music API（年 $99）は採用しない
- よく聴くアーティストは「通知へのアクセス」で音楽アプリの再生中情報（MediaSession）だけを読んで集計する。通知の中身は読まない
- 色は端末の壁紙から取る（dynamic color）。書体は端末標準。ダークモード対応
- 文言は短く分かりやすく。既定（`values/`）は英語、`values-ja/` に日本語。タブ名（New Releases / Artists / Most Played）とアプリ名は全言語で英語（`translatable="false"`）
- アイコンは白地に Google ブルーのベル、音符はベルから穴として抜く。テーマアイコン用の単色版と通知アイコンも同じ形
- ウィジェットはジャケットを新しい順に横並び。背景は「標準／透過・白い文字／透過・黒い文字」をウィジェットごとに選べる
- 実在のジャケット画像やアーティスト画像をリポジトリに入れない（公開リポジトリのため）

## 守ること

- コード上の識別子は英語。コメントと文書は日本語。コメントには「なぜそうしたか」だけを書く
- 画面の文字は必ず文字列リソースに置き、英語と日本語の両方を書く
- 依存ライブラリを足すときは止まって聞く
- `main` に直接コミットして push する（使うのは本人だけのため）。コミットメッセージは日本語。`main` の履歴は書き換えない

## テスト

- `CheckerTest` は実際の Apple カタログ API に問い合わせる結合テスト（通信が要る）
- `MusicAppsTest`・`ArtistImagesTest` は Android に依存しない判断を JVM で確かめる。Android に依存しない判断はこのように切り出してテストする
- テスト名は振る舞いを日本語の文で書く（例: `` fun `再生回数がいちばん多いアプリを選ぶ`() ``）
- 通知・ウィジェット・再生中情報の集計は端末でしか確かめられない。変えたらエミュレーターで実際に動かす
