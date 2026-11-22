package app.otoshirase.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistNamesTest {
    @Test
    fun `カタカナだけの名前は英字表記で出す`() {
        assertEquals("Ride", ArtistNames.choose("ライド", "Ride", preferLocal = false))
        assertEquals("Red Hot Chili Peppers", ArtistNames.choose("レッド・ホット・チリ・ペッパーズ", "Red Hot Chili Peppers", preferLocal = false))
    }

    @Test
    fun `カタカナを選んだアーティストはカタカナで出す`() {
        assertEquals("スピッツ", ArtistNames.choose("スピッツ", "Spitz", preferLocal = true))
    }

    @Test
    fun `漢字や英字の名前は作品に付いた表記のまま出す`() {
        assertEquals("羊文学", ArtistNames.choose("羊文学", "Hitsujibungaku", preferLocal = false))
        assertEquals("THE 1975", ArtistNames.choose("THE 1975", "The 1975", preferLocal = false))
        assertFalse(ArtistNames.switchable("羊文学", "Hitsujibungaku"))
    }

    @Test
    fun `作品が無ければ公式表記で出す`() {
        assertEquals("Ride", ArtistNames.choose(null, "Ride", preferLocal = false))
    }

    @Test
    fun `表記が両方あるときだけ切り替えられる`() {
        assertTrue(ArtistNames.switchable("サニーデイ・サービス", "Sunny Day Service"))
        assertFalse(ArtistNames.switchable("ライド", null))
    }
}
