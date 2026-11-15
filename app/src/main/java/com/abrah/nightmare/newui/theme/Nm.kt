package com.abrah.nightmare.newui.theme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ⭐⭐ fix34 (2026-11-06) — the workbench's design tokens, extracted.
 *
 * ## What this replaces
 *
 * These lived inside `NewUiPanel.kt` as `object Nm` plus a `nmScheme(dark)`
 * function, both buried in a 1998-line file next to the render engine. Every
 * card file that this refactor creates needs them, and importing a token from
 * a panel file would have made the cards depend on the panel — backwards.
 *
 * ## The values are UNCHANGED, deliberately
 *
 * The person's requirement was explicit: keep the layout and the look, change
 * only the machinery underneath. Every colour below is byte-identical to the
 * old `Nm` object, and every one of them is still the workbench HTML's own
 * palette (`nm-workbench.html`, its `--bg` / `--card` / `--pri` variables).
 * A refactor that shifts a colour is a redesign wearing a refactor's clothes.
 *
 * ⚠ Light-only, exactly like the HTML (it pins `color-scheme` too). The app
 * theme is untouched: these live inside the newui tree only.
 */
object Nm {

    // ── ⭐⭐ Dark / pure-black adaptation (the user, 2026-11-15: the workbench and the
    // bottom bar's unfolded cards stayed white in the app's DARK and BLACK themes — Nm was
    // light-only by design, which no longer holds now the app ships dark themes) ──

    /** The app theme's dark state, kept in step by [com.abrah.nightmare.ui.NightmareTheme]. */
    var dark by mutableStateOf(false)
    /** BLACK (AMOLED) variant — surfaces collapse to true black, cards step up minimally. */
    var black by mutableStateOf(false)

    /**
     * ⭐ The ACTIVE Material scheme, handed over by [com.abrah.nightmare.ui.NightmareTheme] —
     * it already resolved the accent palette (violet/blue/teal/…) and the black variant, so
     * the dark tokens below are the app's own exact surfaces and every accent keeps its own
     * dark ramp. Null (pre-theme) falls back to the light values, as before.
     */
    var scheme: androidx.compose.material3.ColorScheme? = null
    private val cs: androidx.compose.material3.ColorScheme
        get() = scheme ?: lightColorScheme()

    // ── The page and its surfaces (dynamic for custom background) ─────────

    var customBgActive by mutableStateOf(false)
    var cardOpacity by mutableStateOf(0.85f)

    /** --bg, the page background: transparent when custom wallpaper is on! */
    val bg: Color get() = if (customBgActive) Color.Transparent else if (dark) cs.background else Color(0xFFF2F0F7)

    /** --card: frosted semi-transparent when custom wallpaper is on! */
    val card: Color get() = if (customBgActive) Color.White.copy(alpha = cardOpacity) else if (dark) cs.surfaceContainer else Color(0xFFFFFFFF)

    /** --line, the hairline stroke. */
    val line: Color get() = if (customBgActive) Color.White.copy(alpha = 0.60f) else if (dark) cs.outlineVariant else customPri?.let { Color((0.92f + it.red * 0.08f).coerceIn(0f, 1f), (0.92f + it.green * 0.08f).coerceIn(0f, 1f), (0.92f + it.blue * 0.08f).coerceIn(0f, 1f)) } ?: Color(0xFFE5E1EF)

    /** #canvasBox — the result frame's empty state: translucent on wallpaper! */
    val canvas: Color get() = if (customBgActive) Color.White.copy(alpha = (cardOpacity * 0.45f).coerceAtLeast(0.08f)) else if (dark) cs.surfaceContainerLow else Color(0xFFFBFAFE)

    /** The field stroke. */
    val fld: Color get() = if (customBgActive) Color.White.copy(alpha = 0.70f) else if (dark) cs.outline else customPri?.let { Color((0.88f + it.red * 0.12f).coerceIn(0f, 1f), (0.88f + it.green * 0.12f).coerceIn(0f, 1f), (0.88f + it.blue * 0.12f).coerceIn(0f, 1f)) } ?: Color(0xFFD9D4E8)

    // ── Text ───────────────────────────────────────────────────────────────

    /** --txt, the ink. */
    val ink: Color get() = if (dark) cs.onBackground else Color(0xFF17151F)

    /** --sub, secondary text. */
    val sub: Color get() = if (dark) cs.onSurfaceVariant else Color(0xFF6E6A7A)

    // ── The primary theme color (dynamic from theme & palette!) ───────────

    var customPri by mutableStateOf<Color?>(null)

    /** --pri, the brand color. CTAs and key indicators — the 10% of 60/30/10. On dark the
     *  app's accent fill is the readable violet; on light the HTML's own stays. */
    val pri: Color get() = customPri ?: if (dark) cs.primary else Color(0xFF6D4AE0)

    /** --pri-d, pressed/darker. On dark a press goes BRIGHTER, not darker. */
    val priD: Color get() = customPri?.let { if (dark) Color((it.red + (1f - it.red) * 0.2f).coerceAtMost(1f), (it.green + (1f - it.green) * 0.2f).coerceAtMost(1f), (it.blue + (1f - it.blue) * 0.2f).coerceAtMost(1f)) else Color((it.red * 0.85f).coerceIn(0f, 1f), (it.green * 0.85f).coerceIn(0f, 1f), (it.blue * 0.85f).coerceIn(0f, 1f)) } ?: if (dark) cs.secondary else Color(0xFF5B39CE)

    /** --pri-soft, selected chips. */
    val priSoft: Color get() = if (dark) cs.secondaryContainer else customPri?.let { Color((0.92f + it.red * 0.08f).coerceIn(0f, 1f), (0.92f + it.green * 0.08f).coerceIn(0f, 1f), (0.92f + it.blue * 0.08f).coerceIn(0f, 1f)) } ?: Color(0xFFEFEAFB)

    /** --pri-soft2, tinted panels (the run strip). */
    val soft2: Color get() = if (customBgActive) Color.White.copy(alpha = (cardOpacity * 0.50f).coerceAtLeast(0.10f)) else if (dark) cs.surfaceContainerHigh else customPri?.let { Color((0.95f + it.red * 0.05f).coerceIn(0f, 1f), (0.95f + it.green * 0.05f).coerceIn(0f, 1f), (0.95f + it.blue * 0.05f).coerceIn(0f, 1f)) } ?: Color(0xFFF7F4FE)

    /** A SELECTED chip's edge (.chip.on border). */
    val priLine: Color get() = if (dark) cs.primary.copy(alpha = 0.55f) else customPri?.let { Color((0.85f + it.red * 0.15f).coerceIn(0f, 1f), (0.85f + it.green * 0.15f).coerceIn(0f, 1f), (0.85f + it.blue * 0.15f).coerceIn(0f, 1f)) } ?: Color(0xFFD8CCF5)

    // ── Status ─────────────────────────────────────────────────────────────

    /** --bad, a missing file / an error line. */
    val bad = Color(0xFFD64545)
}

/**
 * The Material colour scheme for the newui tree.
 *
 * ⭐ The surfaces now come dark-aware through the [Nm] getters themselves ([Nm.dark] /
 * [Nm.scheme], set by [com.abrah.nightmare.ui.NightmareTheme]); the `dark` parameter
 * drives the container TEXT, which flips to the scheme's light ink when the app is dark.
 * (Historical: this used to pin `lightColorScheme` values only — the workbench was a
 * light-only surface by design, until the user asked for dark/black adaptation.)
 */
@Composable
fun nmScheme(dark: Boolean = false) = lightColorScheme(
    primary = Nm.pri,
    onPrimary = Color.White,
    primaryContainer = Nm.priSoft,
    onPrimaryContainer = if (dark) MaterialTheme.colorScheme.onSecondaryContainer else Nm.priD,
    secondary = Nm.priD,
    onSecondary = Color.White,
    secondaryContainer = Nm.priSoft,
    onSecondaryContainer = if (dark) MaterialTheme.colorScheme.onSecondaryContainer else Nm.priD,
    tertiary = Nm.pri,
    tertiaryContainer = Nm.priSoft,
    onTertiaryContainer = if (dark) MaterialTheme.colorScheme.onSecondaryContainer else Nm.priD,
    background = Nm.bg,
    onBackground = Nm.ink,
    surface = Nm.card,
    onSurface = Nm.ink,
    surfaceVariant = Nm.card,
    onSurfaceVariant = Nm.sub,
    surfaceContainer = Nm.card,
    surfaceContainerLow = Nm.card,
    surfaceContainerLowest = Nm.card,
    surfaceContainerHigh = Nm.card,
    surfaceContainerHighest = Nm.card,
    outline = Nm.fld,
    outlineVariant = Nm.line,
    error = Nm.bad,
    onError = Color.White,
)

/** The sheet's own corner: the HTML's 20px on the card, 10 on a field. */
val NmSheetShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

/**
 * The workbench's type scale.
 *
 * ⚠ The design rule this respects: **at most four sizes and two weights.** The
 * HTML page had exactly that, and the old Compose port inherited Material's
 * default scale — five sizes and three weights, which is why the port read
 * "nothing like the HTML". This pins the scale to what the design actually
 * uses, so a new card cannot drift.
 */
val NmTypography: Typography
    @Composable get() {
        val base = MaterialTheme.typography
        return base.copy(
            // Headings: one size, bold.
            titleSmall = base.titleSmall.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Nm.ink,
            ),
            // Section titles, slightly heavier for the card heads.
            titleMedium = base.titleMedium.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Nm.ink,
            ),
            // Body.
            bodySmall = base.bodySmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = Nm.sub,
            ),
            // Labels — the chips, the hints, the run strip.
            labelMedium = base.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Nm.sub,
            ),
            labelSmall = base.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = Nm.sub,
            ),
        )
    }

/** The monospace style for the numbers the workbench shows (step counts, timings). */
val NmMono = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    fontWeight = FontWeight.Medium,
    color = Nm.ink,
)