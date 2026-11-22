package app.otoshirase.data

import java.time.LocalDate

/** 通知と NEW ラベルをどの作品に付けるかの判断。端末に依存させずテストするため切り出す */
object ReleaseRules {
    /** NEW ラベルを付ける、発売からの日数 */
    const val NEW_LABEL_DAYS = 30L

    /**
     * 見つけたときに通知するか。知らせるのは発売前（発売予定が分かったとき）と発売日だけにする。
     * 発売済みの作品があとからカタログに載っても、いまさら知らせると新譜が出たように見えて紛らわしい
     */
    fun notifyOnFind(date: LocalDate?, today: LocalDate): Boolean = date != null && !date.isBefore(today)

    /** 一覧の NEW ラベル。発売から 1 か月以内のものだけに付ける（発売予定は見出しで分かるので付けない） */
    fun isNew(date: LocalDate?, today: LocalDate): Boolean =
        date != null && !date.isAfter(today) && !date.isBefore(today.minusDays(NEW_LABEL_DAYS))
}
