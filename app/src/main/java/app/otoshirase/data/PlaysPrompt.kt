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
 * Most Played にアーティストがたまったら、一度だけ登録を促す通知を出す。
 * 何度も出すと邪魔なので、出したら [AppState.playsPrompted] を立てて二度と出さない。
 */
object PlaysPrompt {
    const val THRESHOLD = 2

    /** @return 通知に名前を出すアーティスト（直近に再生した人）。まだ出さないなら null */
    fun artistToMention(s: AppState): String? {
        if (s.playsPrompted) return null
        val plays = s.unregisteredPlays()
        if (plays.size < THRESHOLD) return null
        return plays.maxByOrNull { it.lastPlayed }?.name
    }

    suspend fun check(store: StateStore, notifier: PlaysPromptNotifier) {
        // 権限が無いときに「出した」ことにすると、許可したあとも二度と出せなくなる
        if (!notifier.canNotify()) return
        if (artistToMention(store.current()) == null) return
        var name: String? = null
        // 再生の記録は並行して届くので、判定と「出した」印を同じ更新の中で行い二重に出さない
        store.update { s ->
            name = artistToMention(s)
            if (name == null) s else s.copy(playsPrompted = true)
        }
        name?.let(notifier::showPlaysPrompt)
    }
}
