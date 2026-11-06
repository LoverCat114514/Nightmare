package com.abrah.nightmare.newui.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * ⭐⭐ fix34 (2026-11-06) — the workbench's parameter state, rebuilt.
 *
 * ## What this replaces, and why
 *
 * The old `WbState` was a plain class whose every scalar was a `mutableStateOf`
 * — but the list of LoRAs (`loras`) was NOT state, and the file compensated
 * with a hand-rolled `modelVer` counter that callers had to bump by hand
 * whenever the list changed. That counter was bumped in six different places,
 * and every place that forgot it left the UI showing stale LoRAs. A state
 * container where one of its fields is "not really state" is the bug factory
 * this refactor exists to remove.
 *
 * Here **every field is state**, `loras` included. There is no version
 * counter, no manual invalidation, no mirror. If a value can change at
 * runtime, reading it during composition subscribes the reader, full stop.
 *
 * ## Why not `StateFlow` + `ViewModel`
 *
 * `mutableStateOf` IS the correct primitive for values that are read during
 * composition: it participates in Compose's snapshot system, so a write
 * invalidates exactly the composables that read it. A `StateFlow` collected
 * into a `mutableStateOf` would add a coroutine and a collection hop in front
 * of the same result — strictly more moving parts for the same behaviour, and
 * one more place for a frame to be dropped.
 *
 * These fields are UI-owned: they are written by the cards' own callbacks and
 * read by the same composition. They never outlive the screen, so there is
 * nothing for a ViewModel to own. The *engine* is the long-lived thing, and it
 * lives in [RunController] — a real object with a real lifecycle, instead of
 * the file-level singletons it replaces.
 *
 * ## Mutation rules
 *
 * - Read a field in composition → you are subscribed to it. No mirrors.
 * - Write a field from a callback → every reader recomposes. No manual bumps.
 * - `loras` is an `ImmutableList`: a change is a NEW list, so a `remember`
 *   keyed on it re-runs only when the list actually differs. A mutable
 *   `MutableList` mutated in place would look unchanged to Compose's equality
 *   check — the same trap the old code fell into.
 */
class WbParams(
    modelId: String = "",
    scheduler: String = "",
    loras: List<LoraPick> = emptyList(),
    w: Int = 832,
    h: Int = 1216,
    steps: Int = 29,
    cfg: Float = 5.0f,
    seed: Long = 0L,
    fixedSeed: Boolean = false,
    denoise: Float = 0.40f,
    mossOn: Boolean = false,
    mode: String = MODE_TXT2IMG,
    autosave: Boolean = true,
    pUps: Boolean = false,
    pUps2: Boolean = false,
    pFace: Boolean = false,
    pHands: Boolean = false,
    pColor: Boolean = false,
    adjOn: Boolean = false,
    adjBr: Int = 0,
    adjCo: Int = 0,
    adjSa: Int = 0,
    warm: Int = 0,
) {
    var modelId by mutableStateOf(modelId)
    var scheduler by mutableStateOf(scheduler)
    var loras by mutableStateOf(loras)
    var w by mutableStateOf(w)
    var h by mutableStateOf(h)
    var steps by mutableStateOf(steps)
    var cfg by mutableStateOf(cfg)
    var seed by mutableStateOf(seed)
    var fixedSeed by mutableStateOf(fixedSeed)
    var denoise by mutableStateOf(denoise)
    var mossOn by mutableStateOf(mossOn)
    var mode by mutableStateOf(mode)
    var autosave by mutableStateOf(autosave)
    var pUps by mutableStateOf(pUps)
    var pUps2 by mutableStateOf(pUps2)
    var pFace by mutableStateOf(pFace)
    var pHands by mutableStateOf(pHands)
    var pColor by mutableStateOf(pColor)
    var adjOn by mutableStateOf(adjOn)
    var adjBr by mutableStateOf(adjBr)
    var adjCo by mutableStateOf(adjCo)
    var adjSa by mutableStateOf(adjSa)
    var warm by mutableStateOf(warm)

    companion object {
        const val MODE_TXT2IMG = "txt2img"
        const val MODE_IMG2IMG = "img2img"

        private const val PREFS = "newui_wb"

        /**
         * The saved choices, as a [WbParams]. Unknown/blank values fall back to
         * the defaults above — the constructor's job, so there is one place
         * where a default lives.
         */
        fun load(ctx: Context): WbParams {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return WbParams(
                modelId = p.getString(KEY_MODEL, "") ?: "",
                scheduler = p.getString(KEY_SCHEDULER, "") ?: "",
                loras = LoraPick.decode(p.getString(KEY_LORAS, "") ?: ""),
                w = p.getInt(KEY_W, 832),
                h = p.getInt(KEY_H, 1216),
                steps = p.getInt(KEY_STEPS, 29),
                cfg = p.getFloat(KEY_CFG, 5.0f),
                seed = p.getLong(KEY_SEED, 0L),
                fixedSeed = p.getBoolean(KEY_FIXED_SEED, false),
                denoise = p.getFloat(KEY_DENOISE, 0.40f),
                mossOn = p.getBoolean(KEY_MOSS, false),
                mode = p.getString(KEY_MODE, MODE_TXT2IMG) ?: MODE_TXT2IMG,
                autosave = p.getBoolean(KEY_AUTOSAVE, true),
                pUps = p.getBoolean(KEY_P_UPS, false),
                pUps2 = p.getBoolean(KEY_P_UPS2, false),
                pFace = p.getBoolean(KEY_P_FACE, false),
                pHands = p.getBoolean(KEY_P_HANDS, false),
                pColor = p.getBoolean(KEY_P_COLOR, false),
                adjOn = p.getBoolean(KEY_ADJ_ON, false),
                adjBr = p.getInt(KEY_ADJ_BR, 0),
                adjCo = p.getInt(KEY_ADJ_CO, 0),
                adjSa = p.getInt(KEY_ADJ_SA, 0),
                warm = p.getInt(KEY_WARM, 0),
            )
        }

        /**
         * Persist. ⚠ Call this off the interaction path — a slider drag writes
         * per frame, and this is a whole prefs transaction. [WbPersister] owns
         * the debounce; nothing else should call this directly.
         */
        fun save(ctx: Context, s: WbParams) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_MODEL, s.modelId)
                .putString(KEY_SCHEDULER, s.scheduler)
                .putString(KEY_LORAS, LoraPick.encode(s.loras))
                .putInt(KEY_W, s.w)
                .putInt(KEY_H, s.h)
                .putInt(KEY_STEPS, s.steps)
                .putFloat(KEY_CFG, s.cfg)
                .putLong(KEY_SEED, s.seed)
                .putBoolean(KEY_FIXED_SEED, s.fixedSeed)
                .putFloat(KEY_DENOISE, s.denoise)
                .putBoolean(KEY_MOSS, s.mossOn)
                .putString(KEY_MODE, s.mode)
                .putBoolean(KEY_AUTOSAVE, s.autosave)
                .putBoolean(KEY_P_UPS, s.pUps)
                .putBoolean(KEY_P_UPS2, s.pUps2)
                .putBoolean(KEY_P_FACE, s.pFace)
                .putBoolean(KEY_P_HANDS, s.pHands)
                .putBoolean(KEY_P_COLOR, s.pColor)
                .putBoolean(KEY_ADJ_ON, s.adjOn)
                .putInt(KEY_ADJ_BR, s.adjBr)
                .putInt(KEY_ADJ_CO, s.adjCo)
                .putInt(KEY_ADJ_SA, s.adjSa)
                .putInt(KEY_WARM, s.warm)
                .apply()
        }

        // ⚠ Keys are the OLD WbState's, verbatim. A refactor must not silently
        // reset a person's saved parameters — those strings are on-disk data.
        private const val KEY_MODEL = "model"
        private const val KEY_SCHEDULER = "scheduler"
        private const val KEY_LORAS = "loras"
        private const val KEY_W = "w"
        private const val KEY_H = "h"
        private const val KEY_STEPS = "steps"
        private const val KEY_CFG = "cfg"
        private const val KEY_SEED = "seed"
        private const val KEY_FIXED_SEED = "fixedSeed"
        private const val KEY_DENOISE = "denoise"
        private const val KEY_MOSS = "mossOn"
        private const val KEY_MODE = "mode"
        private const val KEY_AUTOSAVE = "autosave"
        private const val KEY_P_UPS = "pUps"
        private const val KEY_P_UPS2 = "pUps2"
        private const val KEY_P_FACE = "pFace"
        private const val KEY_P_HANDS = "pHands"
        private const val KEY_P_COLOR = "pColor"
        private const val KEY_ADJ_ON = "adjOn"
        private const val KEY_ADJ_BR = "adjBr"
        private const val KEY_ADJ_CO = "adjCo"
        private const val KEY_ADJ_SA = "adjSa"
        private const val KEY_WARM = "warm"
    }
}

/**
 * One LoRA and its weight — the pair the engine's spec string carries.
 *
 * ⭐ A real type instead of `Pair<String, Float>`: the old code spread
 * `(name, weight)` destructuring through every call site, so "which of the two
 * is the name" was positional knowledge held in twenty places. It is also the
 * reason a weight and a name could be transposed without a compile error.
 */
data class LoraPick(val name: String, val weight: Float) {

    /** The engine's `<lora:name:weight>` wire form — no weight when it is 1.0. */
    fun toSpec(): String =
        if (weight == 1.0f) name else "$name@" + formatWeight(weight)

    companion object {
        /** `a|b@0.8` → the list. The stored form, unchanged from the old code. */
        fun decode(raw: String): List<LoraPick> =
            raw.split('|')
                .filter { it.isNotBlank() }
                .map { entry ->
                    val parts = entry.split('@')
                    LoraPick(
                        name = parts.getOrNull(0) ?: entry,
                        weight = parts.getOrNull(1)?.toFloatOrNull() ?: 0.8f,
                    )
                }

        fun encode(list: List<LoraPick>): String =
            list.joinToString("|") { "${it.name}@${it.weight}" }

        /** Trim a float to at most two decimals, without a trailing ".0". */
        fun formatWeight(w: Float): String {
            val s = String.format(java.util.Locale.US, "%.2f", w)
            return s.trimEnd('0').trimEnd('.')
        }
    }
}
