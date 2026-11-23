package app.otoshirase.data

import java.security.MessageDigest

/**
 * 中継に「このアプリからの呼び出し」だと示すための付け足し。
 * 開発版・APK で配った版はトークン、Play 版は Play Integrity と、配り方で中身を替える。
 */
interface RequestAuth {
    /** 送る本文に合わせたヘッダーを返す。取れなければ例外（呼び出し側は失敗として扱う） */
    suspend fun headers(body: String): Map<String, String>
}

/** APK に入るので抜き出せる。配る相手を限った版だけで使う */
class TokenAuth(private val token: String) : RequestAuth {
    override suspend fun headers(body: String) = mapOf("Authorization" to "Bearer $token")
}

/** 中継（server/src/guard/playIntegrity.js の bodyHash）と同じ作り方。トークンを別の本文に使い回させないため */
fun requestHash(body: String): String =
    MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
