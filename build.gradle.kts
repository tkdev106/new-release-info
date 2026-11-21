plugins {
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20" apply false
}

// 完了の定義（CLAUDE.md）。lint → 単体テスト → デバッグ APK の順に走らせる
tasks.register("verify") {
    group = "verification"
    description = "lint・単体テスト・デバッグ APK のビルドをまとめて走らせる"
    dependsOn(":app:lintDebug", ":app:testDebugUnitTest", ":app:assembleDebug")
}

gradle.projectsEvaluated {
    val lint = project(":app").tasks.named("lintDebug")
    val test = project(":app").tasks.named("testDebugUnitTest")
    val assemble = project(":app").tasks.named("assembleDebug")
    test.configure { mustRunAfter(lint) }
    assemble.configure { mustRunAfter(test) }
}
