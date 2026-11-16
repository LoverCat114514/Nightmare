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
 * This sanitizer strips <think>...</think> blocks and ``` fences, then repairs
 * two failure classes before parsing:
 *   1. [repair] — character-level structural rebuild (⭐ vc682) for the field
 *      failure of 2026-11-16: missing leading '{', items entries without
 *      their '{' / separating ',', max_tokens truncation mid-string;
 *   2. [extractJsonObject] — the old quote-aware balanced-substring scan,
 *      kept as fallback.
 * Anything unrecoverable is dropped item-by-item; an empty-but-valid layout
 * degrades to a plain caption.
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
        val stripped = stripThinking(raw)
        // ⭐ vc682: structural rebuild first — it sees the WHOLE document
        // (patched), while the balanced scan only ever returns a closed
        // sub-block and misses everything after the first unclosed bracket.
        repair(stripped)?.let { parsed -> parseCandidate(parsed)?.let { return finish(it) } }
        val extracted = extractJsonObject(stripped) ?: return null
        val obj = parseCandidate(extracted) ?: return null
        return finish(obj)
    }

    /** Bare array of items → wrapped; otherwise plain parse. null = unparseable. */
    private fun parseCandidate(candidate: String): JSONObject? {
        return try {
            if (candidate.trimStart().startsWith("[")) {
                JSONObject()
                    .put("width", 1024).put("height", 1024)
                    .put("bbox_coord_space", "abs").put("bbox_format", "xyxy")
                    .put("summary", JSONObject().put("caption", ""))
                    .put("items", JSONArray(candidate))
            } else {
                JSONObject(candidate)
            }
        } catch (t: Throwable) { null }
    }

    // ------------------------------------------------------------------
    // ⭐⭐ vc682 — structural repair
    //
    // Field failure (2026-11-16, 9B reverse of a crowded street scene): the
    // model omitted the leading '{' (output began `"summary":{`), wrote items
    // array entries WITHOUT their '{' and WITHOUT separating commas
    // (`"items":[ "label": "character" "caption": ...`), and the run ended
    // mid-string on the 1024-token budget. The balanced-brace extractor only
    // ever sees a closed sub-block, so sanitize returned null and the user
    // got the raw-dump error dialog.
    //
    // This state machine re-emits the document character by character with
    // patches at the defect points:
    //   - leading prose skipped; a bare `"key":` at the start opens a
    //     patched '{' (missing document brace);
    //   - `"key":` inside an array opens a patched '{' (items without braces);
    //   - a key at comma-position gets the missing ',' inserted;
    //   - a missing colon is tolerated (key string → value continues);
    //   - mismatched closers close the nearest open frame with its own match;
    //   - truncation: unterminated string closed, dangling `"key":` cut back
    //     to its start, trailing comma cut, every open frame closed in order;
    //   - trailing prose after the final closer is dropped.
    // Pure string work — no org.json — so plain-JVM unit tests cover it.
    // ------------------------------------------------------------------

    private const val PHASE_KEY = 0     // object: expecting "key"
    private const val PHASE_COLON = 1   // object: key seen, expecting ':'
    private const val PHASE_VALUE = 2   // expecting a value (inside object after ':' / array element)
    private const val PHASE_COMMA = 3   // value complete; expecting ',' (then key / next element)

    private class Frame(val open: Char) {
        val isObj = open == '{'
        val close = if (isObj) '}' else ']'
        var phase = if (isObj) PHASE_KEY else PHASE_VALUE
        var outStart = 0   // out.length just AFTER the open bracket was appended
        var keyStart = -1  // out.length at the current key (its comma included); -1 = none
        var keyEnd = -1    // out.length after the current key's closing quote
        var valStart = -1  // out.length right after the current key's ':'
        var patched = false               // '{' we synthesized (array item / doc brace)
        val seen = HashSet<String>()      // keys already emitted in this object
    }

    /** True when the quote at [from] closes into `"key":` (colon right after). */
    private fun isKeyStart(t: String, from: Int): Boolean {
        var j = from + 1
        while (j < t.length) {
            val c = t[j]
            if (c == '\\') { j += 2; continue }
            if (c == '"') break
            j++
        }
        if (j >= t.length) return false // unterminated → a value, not a key
        j++
        while (j < t.length && t[j].isWhitespace()) j++
        return j < t.length && t[j] == ':'
    }

    /**
     * Rebuilds a damaged JSON document into a balanced, parseable string.
     * Well-formed input passes through byte-identical. Returns null when no
     * JSON structure at all could be located.
     */
    internal fun repair(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty()) return null

        // Document start: first '{' / '[' / bare `"key":` — leading prose
        // ("Here is the JSON:") is skipped. Fenced input starts inside the
        // fence naturally this way.
        var start = -1
        for (i in t.indices) {
            val c = t[i]
            if (c == '{' || c == '[' || (c == '"' && isKeyStart(t, i))) { start = i; break }
        }
        if (start < 0) return null

        val out = StringBuilder()
        val stack = ArrayDeque<Frame>()
        var inStr = false
        var esc = false
        var isKeyStr = false   // the string being read was detected as a key
        var primitive = false  // a number/true/false/null run is in progress
        var finished = false   // document closed — trailing prose must not leak

        fun completePrimitive() {
            if (!primitive) return
            primitive = false
            stack.lastOrNull()?.let { f -> f.phase = PHASE_COMMA }
        }

        fun closeString() {
            stack.lastOrNull()?.let { f ->
                if (f.isObj && isKeyStr) {
                    f.keyEnd = out.length
                    f.phase = PHASE_COLON
                    if (f.keyStart >= 0) {
                        f.seen.add(out.substring(f.keyStart + 1, f.keyEnd - 1))
                    }
                } else {
                    f.phase = when {
                        f.isObj && f.phase == PHASE_VALUE -> PHASE_COMMA
                        !f.isObj -> PHASE_COMMA
                        else -> f.phase
                    }
                }
            }
            isKeyStr = false
        }

        fun endsWithComma(): Boolean {
            var j = out.length - 1
            while (j >= 0 && out[j].isWhitespace()) j--
            return j >= 0 && out[j] == ','
        }

        fun cutTrailingComma() {
            if (!endsWithComma()) return
            var j = out.length - 1
            while (j >= 0 && out[j].isWhitespace()) j--
            out.setLength(j)
        }

        /**
         * Drops a dangling `"key":` (no value yet) — but only when NOTHING
         * was emitted beyond the colon/key except whitespace. A truncated
         * nested value (the common case: `"summary": { "caption": "unfin…`)
         * has real content and must be KEPT; it is closed by the unwind
         * below instead.
         */
        fun cutDanglingKey(f: Frame) {
            if (!f.isObj || f.keyStart < 0) return
            val from = when (f.phase) {
                PHASE_VALUE -> if (f.valStart >= 0) f.valStart else f.keyStart
                PHASE_COLON -> if (f.keyEnd >= 0) f.keyEnd else f.keyStart
                else -> return
            }
            for (j in from until out.length) {
                if (!out[j].isWhitespace()) return // partial value present — keep it
            }
            out.setLength(f.keyStart)
            cutTrailingComma()
        }

        /** The key string's content for a quote starting at [from]. */
        fun keyName(t: String, from: Int): String {
            var j = from + 1
            while (j < t.length) {
                val c = t[j]
                if (c == '\\') { j += 2; continue }
                if (c == '"') break
                j++
            }
            return t.substring(from + 1, j)
        }

        /**
         * The model started ANOTHER array item but omitted both its '{' and
         * (often) the separating ',': close the current patched item, emit the
         * element separator, open the next patched item the same way.
         */
        fun closePatchedAndOpenNext(top: Frame) {
            stack.removeLast()
            cutTrailingComma()
            out.append(top.close)      // close the current patched item
            val parent = stack.lastOrNull()
            out.append(',')            // array element separator (input rarely has one)
            if (parent != null && !parent.isObj) parent.phase = PHASE_VALUE
            val f = Frame('{')
            f.patched = true
            out.append('{')
            f.outStart = out.length
            f.keyStart = f.outStart
            stack.addLast(f)
            isKeyStr = true
        }

        fun openFrame(open: Char, fromKey: Boolean) {
            completePrimitive()
            val parent = stack.lastOrNull()
            if (parent != null && parent.isObj && parent.phase != PHASE_VALUE) {
                parent.phase = PHASE_VALUE // the opener IS the pending key's value
            }
            val f = Frame(open)
            out.append(open)
            f.outStart = out.length
            if (fromKey && f.isObj) { f.keyStart = f.outStart; f.patched = true }
            stack.addLast(f)
        }

        var i = start
        while (i < t.length && !finished) {
            val c = t[i]
            i++
            if (inStr) {
                out.append(c)
                if (esc) esc = false
                else if (c == '\\') esc = true
                else if (c == '"') { inStr = false; closeString() }
                continue
            }
            if (c.isWhitespace()) { out.append(c); continue }
            when (c) {
                '"' -> {
                    primitive = false
                    val key = isKeyStart(t, i - 1)
                    val top = stack.lastOrNull()
                    if (top == null) {
                        if (key) { openFrame('{', true); isKeyStr = true }
                        else finished = true // bare prose after the document
                    } else if (top.isObj) {
                        val repeat = top.patched && key && keyName(t, i - 1) in top.seen
                        when {
                            repeat -> closePatchedAndOpenNext(top)
                            top.phase == PHASE_KEY || top.phase == PHASE_COLON ->
                                if (key) { top.keyStart = out.length; isKeyStr = true }
                                else top.phase = PHASE_VALUE // value without colon — tolerate
                            top.phase == PHASE_COMMA ->
                                if (key) {
                                    out.append(','); top.keyStart = out.length
                                    isKeyStr = true; top.phase = PHASE_KEY
                                } else top.phase = PHASE_VALUE
                            else -> { /* PHASE_VALUE: plain value string */ }
                        }
                    } else if (key) {
                        openFrame('{', true) // patched item object inside an array
                        isKeyStr = true      // …and this string is its first key
                    }
                    inStr = true; esc = false; out.append('"')
                }
                '{', '[' -> openFrame(c, false)
                ':' -> {
                    primitive = false
                    val top = stack.lastOrNull()
                    if (top != null && top.isObj && top.phase == PHASE_COLON) top.phase = PHASE_VALUE
                    out.append(':')
                    if (top != null && top.isObj && top.phase == PHASE_VALUE) top.valStart = out.length
                }
                ',' -> {
                    completePrimitive()
                    val top = stack.lastOrNull()
                    when {
                        top == null -> { /* comma outside the document — skip */ }
                        top.isObj && top.phase == PHASE_VALUE -> { /* dangling colon then comma — skip */ }
                        !top.isObj && top.phase == PHASE_VALUE && endsWithComma() -> { /* ", ," — skip */ }
                        else -> {
                            out.append(',')
                            top.phase = if (top.isObj) PHASE_KEY else PHASE_VALUE
                        }
                    }
                }
                '}', ']' -> {
                    completePrimitive()
                    val top = stack.removeLastOrNull()
                    if (top == null) { /* stray closer — skip */ }
                    else {
                        cutDanglingKey(top)
                        if (!top.isObj && top.phase == PHASE_VALUE) {
                            cutTrailingComma() // ",]" with no element after the comma
                        }
                        out.append(top.close)
                        stack.lastOrNull()?.let { p -> p.phase = PHASE_COMMA }
                        if (stack.isEmpty()) finished = true
                    }
                }
                else -> {
                    if (!primitive) {
                        primitive = true
                        stack.lastOrNull()?.let { f ->
                            if (f.isObj && f.phase != PHASE_VALUE) f.phase = PHASE_VALUE
                        }
                    }
                    out.append(c)
                }
            }
        }

        // ---- EOF: salvage a truncated tail ----
        if (inStr) { out.append('"'); closeString() } // cut mid-string
        completePrimitive()
        while (stack.isNotEmpty()) {
            val f = stack.last()
            cutDanglingKey(f)
            if (f.phase == PHASE_COMMA) {
                cutTrailingComma()
            }
            out.append(f.close)
            stack.removeLast()
        }

        val result = out.toString().trim()
        return if (result.isEmpty()) null else result
    }

    // ------------------------------------------------------------------
    // Post-parse field repair (unchanged behaviour)
    // ------------------------------------------------------------------

    /** Applies the compiler-contract repairs to a parsed layout object. */
    private fun finish(obj: JSONObject): String {
        // ---- width/height: positive multiples of 16 ----
        // ⭐ v2 normalized draft (2026-11-13): no width/height of its own plus
        // the mandatory [0,0,1000,1000] photo_background marker. Its bboxes
        // live on a [0,1000] SCALE, not a canvas — repair on a virtual
        // 1000×1000 canvas, then strip width/height so the compiler detects
        // the marker and rescales onto the sampler's real canvas. Never infer
        // a 1008×1008 canvas from the extent here: the extent IS the scale.
        var w = obj.optInt("width", 0)
        var h = obj.optInt("height", 0)
        val itemsIn0 = obj.optJSONArray("items") ?: JSONArray()
        var maxX2 = 0
        var maxY2 = 0
        var normMarker = false
        for (i in 0 until itemsIn0.length()) {
            val it0 = itemsIn0.optJSONObject(i) ?: continue
            val b = it0.optJSONArray("bbox") ?: continue
            if (b.length() != 4) continue
            val bx1 = (b.opt(0) as? Number)?.toInt() ?: continue
            val by1 = (b.opt(1) as? Number)?.toInt() ?: continue
            val bx2 = (b.opt(2) as? Number)?.toInt() ?: continue
            val by2 = (b.opt(3) as? Number)?.toInt() ?: continue
            if (bx2 > maxX2) maxX2 = bx2
            if (by2 > maxY2) maxY2 = by2
            if (bx1 == 0 && by1 == 0 && bx2 == 1000 && by2 == 1000 &&
                it0.optString("caption", "").trim().startsWith("photo_background")
            ) normMarker = true
        }
        val normalized = w <= 0 && h <= 0 && normMarker
        if (normalized) {
            w = 1000; h = 1000 // virtual canvas for repair only
        } else {
            if (w <= 0 && maxX2 > 0) w = maxX2
            if (h <= 0 && maxY2 > 0) h = maxY2
            if (w > 0 || h > 0) {
                if (w <= 0) w = h
                if (h <= 0) h = w
                obj.put("width", snap16(w)).put("height", snap16(h))
            } else {
                obj.remove("width")
                obj.remove("height")
            }
            w = obj.optInt("width", 0)
            h = obj.optInt("height", 0)
        }

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
