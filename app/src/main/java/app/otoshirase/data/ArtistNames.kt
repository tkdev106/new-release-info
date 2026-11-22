package app.otoshirase.data

/**
 * 表示名の選び方。日本ストアは作品に付くアーティスト名を、海外勢ではカタカナにする（Ride → ライド）。
 * 一方でアーティスト自身の名前は Apple 公式の英字表記で返る。
 * 海外か日本かを API から見分ける手がかりは無いので、カタカナだけの名前は英字表記を既定にし、
 * スピッツのような日本のバンドは利用者がアーティストごとにカタカナへ戻せるようにする。
 */
object ArtistNames {
    private val KANA_ONLY = Regex("^[\\u30A0-\\u30FF\\uFF65\\s]+$")
    private val JAPANESE = Regex("[\\u3040-\\u30FF\\u4E00-\\u9FFF]")

    /** カタカナ表記と英字表記の両方があり、どちらで出すか選べるか */
    fun switchable(local: String?, official: String?): Boolean =
        local != null && official != null && local != official &&
            KANA_ONLY.matches(local) && !JAPANESE.containsMatchIn(official)

    /** @param local 作品に付いている日本ストアの表記 @param official Apple 公式の表記 */
    fun choose(local: String?, official: String?, preferLocal: Boolean): String? = when {
        switchable(local, official) -> if (preferLocal) local else official
        else -> local ?: official
    }
}
