package app.otoshirase.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max

/** 一覧や説明文を広げてよい幅。開いた折りたたみ端末やタブレットで、行が横に間延びしないようにする */
val ContentMaxWidth = 600.dp

/** スマホでの左右の余白 */
val PhoneGutter = 24.dp

/**
 * 画面の幅に応じた左右の余白。
 * 一覧そのものは画面いっぱいに置いたまま余白で中央に寄せる。幅を絞ると、両脇の空きをなぞっても一覧が動かなくなるため
 */
fun sideGutter(screenWidth: Dp): Dp = max(PhoneGutter, (screenWidth - ContentMaxWidth) / 2)

/** 今の画面での左右の余白。各画面の一覧と見出しがそろって同じ余白を使う */
val LocalGutter = compositionLocalOf { PhoneGutter }
