package app.otoshirase.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppleCatalogLangTest {
    @Test
    fun `日本語の端末ではジャンル名を日本語で取る`() {
        assertEquals("ja_jp", AppleCatalog.lang(Locale.JAPAN))
    }

    @Test
    fun `日本語以外の端末ではジャンル名を英語で取る`() {
        assertEquals("en_us", AppleCatalog.lang(Locale.US))
        assertEquals("en_us", AppleCatalog.lang(Locale.FRANCE))
    }
}
