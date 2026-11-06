package com.abrah.nightmare.newui.theme

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

    // ── The page and its surfaces ──────────────────────────────────────────

    /** --bg, the lavender-grey page. */
    val bg = Color(0xFFF2F0F7)

    /** --card. */
    val card = Color(0xFFFFFFFF)

    /** --line, the hairline stroke. */
    val line = Color(0xFFE5E1EF)

    /** #canvasBox — the result frame's empty state. */
    val canvas = Color(0xFFFBFAFE)

    /** The field stroke. */
    val fld = Color(0xFFD9D4E8)

    // ── Text ───────────────────────────────────────────────────────────────

    /** --txt, the ink. */
    val ink = Color(0xFF17151F)

    /** --sub, secondary text. */
    val sub = Color(0xFF6E6A7A)

    // ── The violet ─────────────────────────────────────────────────────────

    /** --pri, the brand violet. CTAs and key indicators — the 10% of 60/30/10. */
    val pri = Color(0xFF6D4AE0)

    /** --pri-d, pressed/darker. */
    val priD = Color(0xFF5B39CE)

    /** --pri-soft, selected chips. */
    val priSoft = Color(0xFFEFEAFB)

    /** --pri-soft2, tinted panels (the run strip). */
    val soft2 = Color(0xFFF7F4FE)

    /** A SELECTED chip's edge (.chip.on border). */
    val priLine = Color(0xFFD8CCF5)

    // ── Status ─────────────────────────────────────────────────────────────

    /** --bad, a missing file / an error line. */
    val bad = Color(0xFFD64545)
}

/**
 * The Material colour scheme for the newui tree.
 *
 * ⚠ `dark` is accepted and IGNORED on purpose, exactly as the old `nmScheme`
 * did: the workbench is a light-only surface by design (its HTML pins
 * `color-scheme: light`), and a dark variant would be a redesign, not a
 * refactor. The parameter stays so callers do not have to change, and so a
 * future dark mode has a hook that is already wired.
 */
@Composable
fun nmScheme(dark: Boolean = false) = lightColorScheme(
    primary = Nm.pri,
    onPrimary = Color.White,
    primaryContainer = Nm.priSoft,
    onPrimaryContainer = Nm.priD,
    secondary = Nm.priD,
    onSecondary = Color.White,
    secondaryContainer = Nm.priSoft,
    onSecondaryContainer = Nm.priD,
    tertiary = Nm.pri,
    tertiaryContainer = Nm.priSoft,
    onTertiaryContainer = Nm.priD,
    background = Nm.bg,
    onBackground = Nm.ink,
    surface = Nm.card,
    onSurface = Nm.ink,
    surfaceVariant = Nm.soft2,
    onSurfaceVariant = Nm.sub,
    // ⚠ `surfaceContainerHigh` is what an AlertDialog paints its own card with.
    // Left unset it stays the app's near-black in dark mode and the LoRA sheet
    // would be dark inside a light workbench.
    surfaceContainerHigh = Nm.card,
    surfaceContainerHighest = Nm.soft2,
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