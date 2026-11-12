package com.abrah.nightmare.reverse

import org.json.JSONArray
import org.json.JSONObject

/**
 * ⭐⭐⭐ Module 4b — hard-rule sanitizer that guarantees the model's JSON
 * compiles through [com.abrah.nightmare.MossLayoutCompiler].
 *
 * The compiler rejects (returns null → plain-prompt fallback) on:
 *   - width/height not positive multiples of 16;
 *   - bbox_coord_space != "abs" or bbox_format != "xyxy";
 *   - items[].label not exactly object/character/text;
 *   - item without caption or bbox, text item without non-empty text;
 *   - bbox not a 4-number array, or violating 0 <= x1 < x2 <= w, 0 <= y1 < y2 <= h.
 *
 * This sanitizer strips <think>...</think> blocks and ``` fences, locates the
 * JSON object, then repairs every near-miss above. Anything unrecoverable is
 * dropped item-by-item; an empty-but-valid layout degrades to a plain caption.
 */
object MossJsonSanitizer {

    /** Removes reasoning blocks Qwen3 models emit, plus stray fences. */
    fun stripThinking(text: String): String {
        var t = Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL).replace(text, "")
        t = Regex("<think>.*\\z", RegexOption.DOT_MATCHES_ALL).replace(t, "") // unterminated tail
        return t.trim()
    }

    /**
     * Main entry: raw model output → compiler-ready JSON string, or null when
     * no JSON object could be recovered at all.
     */
    fun sanitize(raw: String): String? {
        val extracted = extractJsonObject(stripThinking(raw)) ?: return null

        // Some models return a bare array of items — wrap it into the required shape.
        val obj: JSONObject = if (extracted.trimStart().startsWith("[")) {
            try {
                JSONObject()
                    .put("width", 1024).put("height", 1024)
                    .put("bbox_coord_space", "abs").put("bbox_format", "xyxy")
                    .put("summary", JSONObject().put("caption", ""))
                    .put("items", JSONArray(extracted))
            } catch (t: Throwable) { return null }
        } else {
            try { JSONObject(extracted) } catch (t: Throwable) { return null }
        }

        // ---- width/height: positive multiples of 16 ----
        var w = obj.optInt("width", 0)
        var h = obj.optInt("height", 0)
        if (w <= 0) w = 1024
        if (h <= 0) h = 1024
        w = snap16(w); h = snap16(h)
        obj.put("width", w).put("height", h)

        // ---- coord space / format: the compiler only accepts the draft form ----
        obj.put("bbox_coord_space", "abs").put("bbox_format", "xyxy")

        // ---- summary: always present with caption (empty is legal) ----
        val summary = obj.optJSONObject("summary") ?: JSONObject()
        if (!summary.has("caption")) summary.put("caption", "")
        obj.put("summary", summary)

        // ---- items: repair or drop each one ----
        val itemsIn = obj.optJSONArray("items") ?: JSONArray()
        val itemsOut = JSONArray()
        for (i in 0 until itemsIn.length()) {
            val it = itemsIn.optJSONObject(i) ?: continue
            val repaired = repairItem(it, w, h) ?: continue
            itemsOut.put(repaired)
        }
        obj.put("items", itemsOut)

        // Empty layout + blank caption would compile to an empty prompt — give it a floor.
        if (itemsOut.length() == 0 && summary.optString("caption", "").trim().isEmpty()) {
            summary.put("caption", "high quality, detailed")
        }
        return obj.toString()
    }

    /** Repairs one item against the compiler's checks; null = drop the item. */
    private fun repairItem(it: JSONObject, w: Int, h: Int): JSONObject? {
        // label: strict three-value vocabulary, tolerant of common synonyms
        val label = when (it.optString("label", "").trim().lowercase()) {
            "character", "char", "person", "human", "girl", "boy", "people" -> "character"
            "text", "typography", "lettering", "word", "words" -> "text"
            "object", "prop", "item", "thing" -> "object"
            else -> return null
        }

        // bbox: must be a 4-number array (numbers or numeric strings)
        val bbox = it.optJSONArray("bbox") ?: return null
        if (bbox.length() != 4) return null
        val n = DoubleArray(4)
        for (i in 0..3) {
            n[i] = when (val v = bbox.opt(i)) {
                is Number -> v.toDouble()
                is String -> v.trim().toDoubleOrNull() ?: return null
                else -> return null
            }
        }

        // Models love normalized [0..1] boxes despite instructions — rescale.
        val normalized = n.all { it >= 0.0 && it <= 1.0 }
        var x1 = n[0]; var y1 = n[1]; var x2 = n[2]; var y2 = n[3]
        if (normalized) { x1 *= w; y1 *= h; x2 *= w; y2 *= h }
        if (x1 > x2) { val t = x1; x1 = x2; x2 = t }
        if (y1 > y2) { val t = y1; y1 = y2; y2 = t }
        x1 = x1.coerceIn(0.0, w.toDouble()); x2 = x2.coerceIn(0.0, w.toDouble())
        y1 = y1.coerceIn(0.0, h.toDouble()); y2 = y2.coerceIn(0.0, h.toDouble())

        // Round to ints, then guarantee the compiler's strict ordering.
        var ix1 = Math.round(x1).toInt().coerceIn(0, w)
        var iy1 = Math.round(y1).toInt().coerceIn(0, h)
        var ix2 = Math.round(x2).toInt().coerceIn(0, w)
        var iy2 = Math.round(y2).toInt().coerceIn(0, h)
        if (ix1 >= ix2) ix1 = (ix2 - 1).takeIf { it >= 0 } ?: return null
        if (iy1 >= iy2) iy1 = (iy2 - 1).takeIf { it >= 0 } ?: return null
        if (!(ix1 >= 0 && ix1 < ix2 && ix2 <= w && iy1 >= 0 && iy1 < iy2 && iy2 <= h)) return null

        // caption: required to EXIST; text items fall back to their text field
        var caption = it.optString("caption", "").trim()
        if (label == "text") {
            var text = it.optString("text", "").trim()
            if (text.isEmpty()) {
                if (caption.isEmpty()) return null // compiler demands non-empty text
                text = caption
            }
            it.put("text", text)
            if (caption.isEmpty()) caption = text
        }
        if (caption.isEmpty()) caption = if (label == "character") "character" else "object"

        it.put("label", label)
        it.put("caption", caption)
        it.put("bbox", JSONArray().put(ix1).put(iy1).put(ix2).put(iy2))
        return it
    }

    private fun snap16(v: Int): Int = maxOf(16, (v + 8) / 16 * 16)

    /**
     * Locates a JSON object/array in prose: whole-string → ```fenced``` →
     * first balanced-brace scan (quote-aware, so braces inside strings and
     * string content with escapes don't break the depth count).
     */
    private fun extractJsonObject(text: String): String? {
        val t = stripThinking(text)
        val trimmed = t.trim()
        // Even a leading '{' may have trailing prose after the closing brace —
        // always go through the balanced scan instead of trusting the fast path.
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            scanBalanced(trimmed)?.let { return it }
        }
        val fence = Regex("```(?:json)?\\s*\\n?(.*?)```", RegexOption.DOT_MATCHES_ALL).find(t)
        if (fence != null) {
            val inner = fence.groupValues[1].trim()
            if (inner.startsWith("{") || inner.startsWith("[")) {
                scanBalanced(inner)?.let { return it }
                return inner
            }
        }
        return scanBalanced(t)
    }

    /** Quote-aware balanced-brace scan: returns the first complete {...} or [...]. */
    private fun scanBalanced(t: String): String? {
        var start = -1
        var depth = 0
        var inStr = false
        var esc = false
        for (i in t.indices) {
            val c = t[i]
            if (inStr) {
                when {
                    esc -> esc = false
                    c == '\\' -> esc = true
                    c == '"' -> inStr = false
                }
            } else when (c) {
                '"' -> inStr = true
                '{', '[' -> { if (depth == 0) start = i; depth++ }
                '}', ']' -> {
                    depth--
                    if (depth == 0 && start >= 0) return t.substring(start, i + 1)
                    if (depth < 0) return null
                }
            }
        }
        return null
    }
}