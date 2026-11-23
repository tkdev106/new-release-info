import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// おすすめの中継の接続先。公開リポジトリに入れないよう local.properties（Git に入らない）から読む
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun localProp(key: String): String = (localProps.getProperty(key) ?: "").replace("\\", "\\\\").replace("\"", "\\\"")

// Play に上げる版の署名鍵（アップロード鍵）。鍵とパスワードは本人だけが持つので、Git に入らない
// keystore.properties か環境変数（CI 用）から読む。どちらも無ければ署名なしで組み上げる
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    keystoreProps.getProperty(key)?.takeIf { it.isNotBlank() } ?: System.getenv(env)?.takeIf { it.isNotBlank() }
val releaseStoreFile = signingValue("storeFile", "RELEASE_STORE_FILE")

// versionCode は versionName から決める（1.2.3 → 10203）。Play は上がっていない版を受け付けないので、
// 名前を上げ忘れて番号だけ同じ、ということが起きないよう 1 か所にまとめる
val appVersionName = "0.1.0"
val appVersionCode = appVersionName.split(".").map(String::toInt).let { (major, minor, patch) ->
    require(minor < 100 && patch < 100) { "versionName の各桁は 99 まで" }
    major * 10_000 + minor * 100 + patch
}

android {
    namespace = "app.otoshirase"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.otoshirase"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "SUGGEST_URL", "\"${localProp("suggest.url")}\"")
        buildConfigField("String", "SUGGEST_TOKEN", "\"${localProp("suggest.token")}\"")
        // Play Integrity を使う Google Cloud プロジェクトの番号（秘密ではない）。Play 版で使う
        buildConfigField("long", "PLAY_CLOUD_PROJECT_NUMBER", "${localProp("play.cloudProjectNumber").toLongOrNull() ?: 0}L")
    }
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("storePassword", "RELEASE_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "RELEASE_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "RELEASE_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            // 鍵が無いとき（CI など）は署名しない。デバッグ鍵で署名した版を Play に上げてしまわないため
            signingConfig = signingConfigs.findByName("release")
            // Play 版にはトークンを入れない（APK から抜き出せるため）。代わりに Play Integrity で名乗る。
            // APK で配る版は debug で組み上げる
            buildConfigField("String", "SUGGEST_TOKEN", "\"\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Play から入れた本物のアプリだと中継に示すため（2026-11-23 本人了承）
    implementation("com.google.android.play:integrity:1.6.0")
    constraints {
        // Play Integrity が連れてくる fragment は古く（1.1）、registerForActivityResult が正しく動かない
        implementation("androidx.fragment:fragment:1.8.9")
    }

    testImplementation("junit:junit:4.13.2")
}
