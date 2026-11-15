package com.abrah.nightmare

import org.junit.Assert.assertEquals
import org.junit.Test

/** ⭐ The run's line in the notification shade ([RunNotice], upstream 2026-11-14). */
class RunNoticeTest {
    @Test
    fun theLineIsTheStepThenTheTimeSoFar() {
        // ⚠ The clock is the app's own `formatMs` ("1m04s" style), not upstream's "1 min 3 s".
        assertEquals("5/8 · 62% · 1m03s", RunNotice.textFor("5/8 · 62%", 63_000))
        assertEquals("12.4s", RunNotice.textFor(null, 12_400))
    }
}
