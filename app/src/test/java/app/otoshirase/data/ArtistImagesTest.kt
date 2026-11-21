package app.otoshirase.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtistImagesTest {

    @Test
    fun `og_imageのURLを取り出す`() {
        val html = """<head><meta property="og:title" content="x"><meta property="og:image" content="https://is1-ssl.mzstatic.com/image/thumb/a/b.png/1200x630cw.png"></head>"""
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/a/b.png/1200x630cw.png", ArtistImages.parse(html))
    }

    @Test
    fun `Appleの画像サーバー以外のURLは使わない`() {
        assertNull(ArtistImages.parse("""<meta property="og:image" content="https://example.com/a.png">"""))
        assertNull(ArtistImages.parse("<head></head>"))
    }

    @Test
    fun `横長の寸法指定を正方形の切り抜きに差し替える`() {
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/a/b.png/300x300cc.jpg",
            ArtistImages.square("https://is1-ssl.mzstatic.com/image/thumb/a/b.png/1200x630cw.png"),
        )
    }
}
