package com.abrah.nightmare

import com.abrah.nightmare.reverse.MossJsonSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

/**
 * Pure-JVM tests for [MossJsonSanitizer.repair] (⭐ vc682) — the structural
 * JSON rebuild added after the 2026-11-16 field failure: a 9B reverse of a
 * crowded street scene omitted the leading '{', wrote items without their
 * '{' / commas, and truncated mid-string on the token budget. No org.json
 * here on purpose: repair() is pure string work, so these run on the local
 * aarch64 JVM where Robolectric cannot.
 */
class MossJsonSanitizerRepairTest {

    /** String-aware balance check: braces/brackets outside strings must nest. */
    private fun balanced(s: String): Boolean {
        val stack = ArrayDeque<Char>()
        var inStr = false
        var esc = false
        for (c in s) {
            if (inStr) {
                when {
                    esc -> esc = false
                    c == '\\' -> esc = true
                    c == '"' -> inStr = false
                }
                continue
            }
            when (c) {
                '"' -> inStr = true
                '{', '[' -> stack.addLast(c)
                '}', ']' -> {
                    val open = stack.removeLastOrNull() ?: return false
                    if ((c == '}') != (open == '{')) return false
                }
            }
        }
        return !inStr && stack.isEmpty()
    }

    @Test
    fun wellFormedJsonPassesThroughByteIdentical() {
        val raw = """{"summary": {"meta": ["anime", "street"], "caption": "a girl"}, "items": []}"""
        assertEquals(raw, MossJsonSanitizer.repair(raw))
    }

    @Test
    fun fieldSampleWithMissingBraceAndBracesAndTruncationRecovers() {
        // The 2026-11-16 field failure (error-dialog dump, abridged): no
        // leading '{', items entries without '{' or ',', truncated mid-string.
        val raw = """
            "summary":{
            "meta": ["anime illustration", "lively outdoor street scene"],
            "caption": "A cheerful anime-style girl is live-streaming herself in a crowded outdoor shopping district.",
            "items":[
            "label": "character"
            "caption": "A young woman with light green hair. She wears
        """.trimIndent()
        val out = MossJsonSanitizer.repair(raw).also { println("DBG-out: [$it]") }
        assertNotNull(out)
        assertTrue(out!!.startsWith("{"))
        assertTrue(out.endsWith("}"))
        assertTrue(balanced(out))
        assertTrue(out.contains("\"label\": \"character\""))
        assertTrue(out.contains("\"caption\": \"A young woman with light green hair. She wears\""))
        assertTrue(out.contains("\"summary\""))
    }

    @Test
    fun itemsArrayWithoutBracesOrCommasIsPatched() {
        val raw = """{"items": [ "label": "object" "caption": "a cup" , "label": "text" "text": "hi" ]}"""
        val out = MossJsonSanitizer.repair(raw).also { println("DBG-out: [$it]") }
        assertNotNull(out)
        assertTrue(balanced(out!!))
        // org.json is stubbed in JVM unit tests — assert the patched shape directly:
        // brace restored on item 1, brace+comma restored on item 2, text value kept.
        assertTrue(out.contains("{\"label\": \"object\""))
        assertTrue(out.contains("{\"label\": \"text\""))
        assertTrue(out.contains("\"text\": \"hi\""))
    }

    @Test
    fun missingLeadingBraceAloneIsPatched() {
        val raw = """"summary": {"caption": "a quiet room"}"""
        val out = MossJsonSanitizer.repair(raw).also { println("DBG-out: [$it]") }
        assertNotNull(out)
        assertTrue(balanced(out!!))
        assertTrue(out.startsWith("{"))
        assertTrue(out.contains("\"summary\": {\"caption\": \"a quiet room\"}"))
    }

    @Test
    fun danglingKeyAtTruncationIsCut() {
        val raw = """{"a": 1, "b": """
        assertEquals("""{"a": 1}""", MossJsonSanitizer.repair(raw))
    }

    @Test
    fun truncatedBboxArrayIsClosed() {
        val raw = """{"items": [{"label": "object", "bbox": [0, 0"""
        val out = MossJsonSanitizer.repair(raw).also { println("DBG-out: [$it]") }
        assertNotNull(out)
        assertTrue(balanced(out!!))
        assertTrue(out.contains("\"bbox\": [0, 0]"))
    }

    @Test
    fun trailingProseAfterTheDocumentIsDropped() {
        val raw = """{"a": 1} Here is some extra prose about the image."""
        assertEquals("""{"a": 1}""", MossJsonSanitizer.repair(raw))
    }

    @Test
    fun leadingProseBeforeTheDocumentIsSkipped() {
        val raw = """Sure! Here is the JSON you asked for:
            {"summary": {"caption": "x"}}
        """.trimIndent()
        assertEquals("""{"summary": {"caption": "x"}}""", MossJsonSanitizer.repair(raw))
    }

    @Test
    fun unterminatedStringIsClosedAtEof() {
        val raw = """{"summary": {"caption": "unfinished sentenc"""
        val out = MossJsonSanitizer.repair(raw)
        assertNotNull(out)
        val o = out!!
        println("DBG1: [$o]")
        println("DBG2: last2=${o.takeLast(2).toList()} ends=" + o.endsWith("\"}"))
        assertEquals("{\"summary\": {\"caption\": \"unfinished sentenc\"}}", o)
    }

    @Test
    fun fencedAndThinkingAreHandledUpstreamOfRepair() {
        val raw = "<think>reasoning here</think>\n```json\n{\"a\": 1}\n```"
        val stripped = MossJsonSanitizer.stripThinking(raw)
        assertTrue(stripped.contains("\"a\": 1"))
    }
}
