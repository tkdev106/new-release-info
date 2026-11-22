package app.otoshirase.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.ceil

/** 透過ウィジェット用に、影を付けた 1 行の文字を画像にする。幅に収まらなければ末尾を「…」にする */
object ShadowText {
    fun render(ctx: Context, text: String, sp: Float, color: Int, shadow: Int, bold: Boolean, widthDp: Int): Bitmap {
        val res = ctx.resources
        val density = res.displayMetrics.density
        // 端末の文字サイズの設定に合わせる
        val size = sp * density * res.configuration.fontScale
        val blur = 2.5f * density
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
            setShadowLayer(blur, 0f, 0.5f * density, shadow)
        }
        val width = (widthDp * density).toInt().coerceAtLeast(1)
        val line = TextUtils.ellipsize(text, paint, width - blur * 2, TextUtils.TruncateAt.END).toString()
        val fm = paint.fontMetrics
        val height = ceil(fm.descent - fm.ascent + blur * 2).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(line, blur, blur - fm.ascent, paint)
        return bitmap
    }
}
