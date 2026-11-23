# Google Play に出す手順

## 版の番号

`app/build.gradle.kts` の `appVersionName` だけを上げる。`versionCode` はそこから決まる（`1.2.3` → `10203`）。
Play は前より大きい `versionCode` しか受け付けないので、出すたびに必ず上げる。各桁は 99 まで。

## アップロード鍵（本人だけが持つ）

Play には「Play アプリ署名」を使う。利用者の端末に届くアプリは Google が持つ鍵で署名され、
こちらが持つのは Play に上げるときの**アップロード鍵**だけ。なくしても Play Console から作り直しを申請できるが、時間がかかるので失くさない。

1. 鍵を作る（一度だけ）。リポジトリの外に置く
   ```
   keytool -genkeypair -v -keystore ~/keys/new-releases-upload.jks \
     -alias upload -keyalg RSA -keysize 4096 -validity 10000
   ```
2. 次のものをパスワード管理アプリ（1Password など）に保管する
   - `new-releases-upload.jks` ファイルそのもの
   - ストアのパスワード・鍵のエイリアス（`upload`）・鍵のパスワード
3. リポジトリ直下に `keystore.properties` を作る（`.gitignore` 済み。**コミットしない**）
   ```
   storeFile=/Users/<名前>/keys/new-releases-upload.jks
   storePassword=...
   keyAlias=upload
   keyPassword=...
   ```
   CI で署名したくなったら、同じ値を環境変数 `RELEASE_STORE_FILE`・`RELEASE_STORE_PASSWORD`・`RELEASE_KEY_ALIAS`・`RELEASE_KEY_PASSWORD` で渡せる

鍵が無いときは署名なしで組み上がる（CI はこの状態で `bundleRelease` が通ることだけを確かめる）。

## 組み上げる

```
./gradlew verify bundleRelease
```

`app/build/outputs/bundle/release/app-release.aab` を Play Console に上げる。

## おすすめの中継への名乗り方

| 版 | 組み上げ | 名乗り方 |
| --- | --- | --- |
| Play 版 | `bundleRelease` | Play Integrity（`local.properties` の `play.cloudProjectNumber`）。トークンは入れない |
| APK で配る版・開発版 | `assembleDebug` | `local.properties` の `suggest.token` |

Play Integrity の準備（Cloud プロジェクトのリンク・サービスアカウントの鍵）は `server/README.md` の「Play Integrity のセットアップ」。
`play.cloudProjectNumber` が無いまま `bundleRelease` すると、Play 版ではおすすめが出ない。

APK で配った版（デバッグ鍵で署名）と Play 版は署名が違うので、上書きでは入れ替えられない。Play 版に移る人は、APK 版をアンインストールしてから入れる（登録したアーティストは消える）。
