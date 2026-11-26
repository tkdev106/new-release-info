package app.otoshirase.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutTest {

    @Test
    fun `スマホの幅では左右の余白はいつもの24dp`() {
        assertEquals(24.dp, sideGutter(411.dp))
    }

    @Test
    fun `開いた折りたたみ端末では中身を600dpに収めて中央に寄せる`() {
        val width = 841.dp
        val gutter = sideGutter(width)
        assertEquals(ContentMaxWidth, width - gutter * 2)
    }

    @Test
    fun `幅が広がっても余白は途中で跳ねない`() {
        assertEquals(24.dp, sideGutter(648.dp))
        assertEquals(25.dp, sideGutter(650.dp))
    }
}
