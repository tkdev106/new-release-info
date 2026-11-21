package app.otoshirase.data

/** Most Played に出す（まだ登録も非表示もしていない）アーティスト。画面と通知の判定で同じものを使う */
fun AppState.unregisteredPlays(): List<PlayStat> =
    plays.filter { p -> p.name !in ignored && artists.none { it.matches(p.name) } }

/** 「新譜の通知を受け取りませんか？」を出す先。通知の仕組みに依存させず判定をテストするため */
interface PlaysPromptNotifier {
    /** 通知を出せる状態か（通知の権限があるか） */
    fun canNotify(): Boolean
    fun showPlaysPrompt(artistName: String)
}

/**
 * Most Played に新しいアーティストが2組たまるたびに、登録を促す通知を出す。
 * 同じ顔ぶれで何度も出すと邪魔なので、出したときの顔ぶれを [AppState.promptedPlays] に残し、
 * それ以外のアーティストだけを数える。
 */
object PlaysPrompt {
    const val THRESHOLD = 2

    private const val MAX_REMEMBERED = 500

    /** @return 通知に名前を出すアーティスト（新顔のうち直近に再生した人）。まだ出さないなら null */
    fun artistToMention(s: AppState): String? {
        val counted = s.promptedPlays.toSet()
        val fresh = s.unregisteredPlays().filterNot { it.name.lowercase() in counted }
        if (fresh.size < THRESHOLD) return null
        return fresh.maxByOrNull { it.lastPlayed }?.name
    }

    /** 今の顔ぶれを数え済みにする */
    fun markPrompted(s: AppState): AppState {
        val names = s.unregisteredPlays().map { it.name.lowercase() }
        return s.copy(promptedPlays = (s.promptedPlays + names).distinct().takeLast(MAX_REMEMBERED))
    }

    suspend fun check(store: StateStore, notifier: PlaysPromptNotifier) {
        // 権限が無いときに数え済みにすると、許可したあとにその顔ぶれで出せなくなる
        if (!notifier.canNotify()) return
        if (artistToMention(store.current()) == null) return
        var name: String? = null
        // 再生の記録は並行して届くので、判定と「出した」印を同じ更新の中で行い二重に出さない
        store.update { s ->
            name = artistToMention(s)
            if (name == null) s else markPrompted(s)
        }
        name?.let(notifier::showPlaysPrompt)
    }
}
