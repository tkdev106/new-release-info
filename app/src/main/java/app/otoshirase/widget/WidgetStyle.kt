package app.otoshirase.widget

import androidx.annotation.StringRes
import androidx.datastore.preferences.core.stringPreferencesKey
import app.otoshirase.R

/**
 * ウィジェットの背景。ウィジェットごとに選べる（Glance の状態に保存する）。
 * 透過では文字が壁紙の上に直接乗るので、壁紙の明るさに合わせて文字色を選んでもらう。
 */
enum class WidgetStyle(@StringRes val label: Int) {
    Standard(R.string.widget_style_standard),
    ClearWhite(R.string.widget_style_clear_white),
    ClearBlack(R.string.widget_style_clear_black);

    companion object {
        val KEY = stringPreferencesKey("style")
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: Standard
    }
}
