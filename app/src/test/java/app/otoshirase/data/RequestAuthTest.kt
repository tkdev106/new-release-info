package app.otoshirase.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestAuthTest {
    @Test
    fun `本文のハッシュは中継と同じ SHA-256 の 16 進になる`() {
        // server/test/guard.test.js と同じ値
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", requestHash("abc"))
        assertEquals(64, requestHash("""{"artists":["羊文学"]}""").length)
    }

    @Test
    fun `トークンの版は Bearer で名乗る`() = runBlocking {
        assertEquals(mapOf("Authorization" to "Bearer t"), TokenAuth("t").headers("{}"))
    }
}
