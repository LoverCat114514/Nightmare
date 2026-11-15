package com.abrah.nightmare

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ⭐ 1.6.297 port: a saved flow keeps no draft (it opens as saved); a recipe's does while the switch is on. */
class KeepsDraftTest {

    /** ⭐ Saved #8050: a saved flow keeps no draft (it opens as saved); a recipe's does while the switch is on. */
    @Test
    fun onlyARecipeKeepsADraftAndOnlyWhileSwitchedOn() {
        assertTrue(keepsDraft("recipe:t2i:sd15", keep = true))
        assertFalse(keepsDraft("recipe:t2i:sd15", keep = false))
        assertFalse(keepsDraft("saved:fibo edit", keep = true))
        assertFalse(keepsDraft(null, keep = true))
    }
}
