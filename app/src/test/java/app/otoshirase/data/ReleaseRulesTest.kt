package app.otoshirase.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReleaseRulesTest {

    private val today = LocalDate.of(2026, 11, 22)

    @Test
    fun `発売前に見つけた作品は通知する`() {
        assertTrue(ReleaseRules.notifyOnFind(today.plusDays(10), today))
    }

    @Test
    fun `発売日に見つけた作品は通知する`() {
        assertTrue(ReleaseRules.notifyOnFind(today, today))
    }

    @Test
    fun `発売済みの作品をあとから見つけても通知しない`() {
        assertFalse(ReleaseRules.notifyOnFind(today.minusDays(1), today))
    }

    @Test
    fun `発売日が分からない作品は通知しない`() {
        assertFalse(ReleaseRules.notifyOnFind(null, today))
    }

    @Test
    fun `発売から1か月以内の作品にNEWを付ける`() {
        assertTrue(ReleaseRules.isNew(today, today))
        assertTrue(ReleaseRules.isNew(today.minusDays(30), today))
    }

    @Test
    fun `発売から1か月を過ぎた作品にはNEWを付けない`() {
        assertFalse(ReleaseRules.isNew(today.minusDays(31), today))
    }

    @Test
    fun `発売予定の作品にはNEWを付けない`() {
        assertFalse(ReleaseRules.isNew(today.plusDays(1), today))
    }
}
