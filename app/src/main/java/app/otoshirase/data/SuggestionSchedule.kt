package app.otoshirase.data

import kotlinx.serialization.Serializable

/** おすすめを裏で取り直して通知する頻度。null の days はオフ */
@Serializable
enum class SuggestionFrequency(val days: Int?) {
    Off(null), Daily(1), Every3Days(3), Weekly(7),
}

/** おすすめの定期通知の判断。Android に依存しないので JVM で確かめる */
object SuggestionSchedule {
    /** 中継に送る「除いてほしい名前」の上限（中継の MAX_EXCLUDE と同じ） */
    const val MAX_EXCLUDE = 50
    /** 覚えておくおすすめの数。除く名前より多めに持ち、通知の重複判定に使う */
    const val MAX_HISTORY = 200
    /** 地域で断られたあと、裏で問い合わせない期間。旅行から戻れば使えるので、ずっとは止めない */
    const val REGION_BLOCK_MS = 7L * 24 * 60 * 60 * 1000

    fun excludeNames(history: List<Suggestion>): List<String> = history.take(MAX_EXCLUDE).map { it.name }

    /** 今回見つかったうち、これまでに一度も出していないもの */
    fun fresh(found: List<Suggestion>, history: List<Suggestion>): List<Suggestion> {
        val seen = history.map { it.artistId }.toSet()
        return found.filterNot { it.artistId in seen }
    }

    /** 新しいものを先頭にし、同じ人は 1 つにまとめて上限で切る */
    fun mergeHistory(found: List<Suggestion>, history: List<Suggestion>): List<Suggestion> =
        (found + history).distinctBy { it.artistId }.take(MAX_HISTORY)

    /** 裏で問い合わせてよいか。通知できないのに通信だけするのは避ける */
    fun shouldFetchInBackground(state: AppState, canNotify: Boolean, now: Long): Boolean =
        state.suggestionFrequency != SuggestionFrequency.Off &&
            state.artists.isNotEmpty() &&
            canNotify &&
            now - state.suggestionsRegionBlockedAt >= REGION_BLOCK_MS

    /** 通知で知らせたものを先に、NEW の印を付けて並べる */
    fun ordered(shown: List<Suggestion>, newIds: Set<Long>): List<Suggestion> =
        shown.sortedByDescending { it.artistId in newIds }
}
