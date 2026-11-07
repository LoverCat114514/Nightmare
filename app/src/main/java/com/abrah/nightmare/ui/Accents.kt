package com.abrah.nightmare.ui

import androidx.compose.ui.graphics.Color

/**
 * ⭐⭐ The accent colours (a tester's wish, 2026-10-24: "the current purple style is too monotonous"; the user let
 * Claude pick the set). Each preset is the violet scheme with its hue turned: the fills, accent and accent text are
 * set for contrast (white on [fill] 4.8:1, [text] 6.2:1 on a dialog, [lightText] 5.6:1 on white), and the tinted
 * neutrals keep violet's LUMINANCE, so every layer and every text pair holds the contrast `Theme.kt` documents.
 * Generated, then checked, by a script; violet is the original values exactly.
 *
 * ⚠ The accent never touches a node CATEGORY or model FAMILY colour ([familyColor], [VideoColor]), nor success,
 * warning or error: those mean the same on every phone, so a shared screenshot still reads.
 */
internal class AccentPalette(
    val fill: Color,
    val accent: Color,
    val text: Color,
    val lightFill: Color,
    val lightText: Color,
    val surface: Color,
    val card: Color,
    val raised: Color,
    val lowest: Color,
    val low: Color,
    val highest: Color,
    val container: Color,
    val border: Color,
    val outlineVar: Color,
    val onMuted: Color,
    val on: Color,
    val controlBorder: Color,
    val bLow: Color,
    val bCont: Color,
    val bHigh: Color,
    val bHighest: Color,
    val canvasSel: Color,
    val lightContainer: Color,
    val lightOnContainer: Color,
    val canvasStroke: Color,
    val lightCanvasStroke: Color,
    val lightCanvasSel: Color,
)

/** ⭐ Violet Studio, the default: the values `Theme.kt` always had. */
internal val VioletPalette = AccentPalette(
    fill = Color(0xFF8B4DEE),
    accent = Color(0xFF985CFF),
    text = Color(0xFFB08AFF),
    lightFill = Color(0xFF7C4DFF),
    lightText = Color(0xFF6E3BD8),
    surface = Color(0xFF0D0914),
    card = Color(0xFF1B1428),
    raised = Color(0xFF251B36),
    lowest = Color(0xFF09060F),
    low = Color(0xFF140F1F),
    highest = Color(0xFF2E2242),
    container = Color(0xFF35204F),
    border = Color(0xFF3A2B50),
    outlineVar = Color(0xFF2C2140),
    onMuted = Color(0xFFB6A6C8),
    on = Color(0xFFF4EFFA),
    controlBorder = Color(0xFF74609A),
    bLow = Color(0xFF0A080E),
    bCont = Color(0xFF131018),
    bHigh = Color(0xFF1C1724),
    bHighest = Color(0xFF26202F),
    canvasSel = Color(0xFF26263A),
    lightContainer = Color(0xFFEDE5FF),
    lightOnContainer = Color(0xFF21143F),
    canvasStroke = Color(0xFF9B6BFF),
    lightCanvasStroke = Color(0xFF7C4DFF),
    lightCanvasSel = Color(0xFFF1ECFF),
)

internal val BluePalette = AccentPalette(
    fill = Color(0xFF1F6FD9),
    accent = Color(0xFF4E90E6),
    text = Color(0xFF6FA1E3),
    lightFill = Color(0xFF1F6FD9),
    lightText = Color(0xFF1C65C5),
    surface = Color(0xFF070C10),
    card = Color(0xFF111822),
    raised = Color(0xFF16202D),
    lowest = Color(0xFF04070B),
    low = Color(0xFF0C121A),
    highest = Color(0xFF1D2938),
    container = Color(0xFF1B2C42),
    border = Color(0xFF253346),
    outlineVar = Color(0xFF1C2837),
    onMuted = Color(0xFF9EAEC3),
    on = Color(0xFFEBF1F9),
    controlBorder = Color(0xFF556B89),
    bLow = Color(0xFF07090C),
    bCont = Color(0xFF0F1116),
    bHigh = Color(0xFF141920),
    bHighest = Color(0xFF1D232B),
    canvasSel = Color(0xFF212832),
    lightContainer = Color(0xFFE3EEFF),
    lightOnContainer = Color(0xFF0F1D2F),
    canvasStroke = Color(0xFF4E90E6),
    lightCanvasStroke = Color(0xFF1F6FD9),
    lightCanvasSel = Color(0xFFF0F6FF),
)
internal val TealPalette = AccentPalette(
    fill = Color(0xFF0E7E82),
    accent = Color(0xFF129EA2),
    text = Color(0xFF1DB0B5),
    lightFill = Color(0xFF0E7E82),
    lightText = Color(0xFF0D7477),
    surface = Color(0xFF060D0D),
    card = Color(0xFF0D1A1A),
    raised = Color(0xFF122223),
    lowest = Color(0xFF030808),
    low = Color(0xFF0A1314),
    highest = Color(0xFF172B2C),
    container = Color(0xFF132F30),
    border = Color(0xFF1E3737),
    outlineVar = Color(0xFF16292A),
    onMuted = Color(0xFF89B4B6),
    on = Color(0xFFE0F5F5),
    controlBorder = Color(0xFF477173),
    bLow = Color(0xFF050A0A),
    bCont = Color(0xFF0C1313),
    bHigh = Color(0xFF121B1B),
    bHighest = Color(0xFF192525),
    canvasSel = Color(0xFF1C2B2B),
    lightContainer = Color(0xFFDDF4F4),
    lightOnContainer = Color(0xFF0A2020),
    canvasStroke = Color(0xFF129EA2),
    lightCanvasStroke = Color(0xFF0E7E82),
    lightCanvasSel = Color(0xFFEEFAFA),
)
internal val OrangePalette = AccentPalette(
    fill = Color(0xFFBF5010),
    accent = Color(0xFFEC6416),
    text = Color(0xFFE7864E),
    lightFill = Color(0xFFBF5010),
    lightText = Color(0xFFAD490E),
    surface = Color(0xFF0F0A07),
    card = Color(0xFF201610),
    raised = Color(0xFF2A1D15),
    lowest = Color(0xFF0B0604),
    low = Color(0xFF18100C),
    highest = Color(0xFF34241B),
    container = Color(0xFF3C2518),
    border = Color(0xFF412E23),
    outlineVar = Color(0xFF34231B),
    onMuted = Color(0xFFC0A79A),
    on = Color(0xFFF9F0EA),
    controlBorder = Color(0xFF836352),
    bLow = Color(0xFF0C0807),
    bCont = Color(0xFF15110E),
    bHigh = Color(0xFF1E1813),
    bHighest = Color(0xFF29211C),
    canvasSel = Color(0xFF302620),
    lightContainer = Color(0xFFFFE9DC),
    lightOnContainer = Color(0xFF2A180D),
    canvasStroke = Color(0xFFEC6416),
    lightCanvasStroke = Color(0xFFBF5010),
    lightCanvasSel = Color(0xFFFFF3EC),
)
internal val PinkPalette = AccentPalette(
    fill = Color(0xFFD51E86),
    accent = Color(0xFFE759AA),
    text = Color(0xFFE67DB8),
    lightFill = Color(0xFFD51E86),
    lightText = Color(0xFFC21C7A),
    surface = Color(0xFF12080E),
    card = Color(0xFF23121C),
    raised = Color(0xFF301826),
    lowest = Color(0xFF0E050A),
    low = Color(0xFF1C0D16),
    highest = Color(0xFF3C1F2F),
    container = Color(0xFF451C34),
    border = Color(0xFF49283B),
    outlineVar = Color(0xFF391D2E),
    onMuted = Color(0xFFC6A2B6),
    on = Color(0xFFFAEEF4),
    controlBorder = Color(0xFF905978),
    bLow = Color(0xFF0D080A),
    bCont = Color(0xFF171014),
    bHigh = Color(0xFF22151C),
    bHighest = Color(0xFF2D1E26),
    canvasSel = Color(0xFF35232C),
    lightContainer = Color(0xFFFFE3F1),
    lightOnContainer = Color(0xFF321023),
    canvasStroke = Color(0xFFE759AA),
    lightCanvasStroke = Color(0xFFD51E86),
    lightCanvasSel = Color(0xFFFFF0F7),
)

data class PresetThemeColor(
    val name: String,
    val color: Color,
    val argb: Long,
)

val PRESET_THEME_COLORS = listOf(
    PresetThemeColor("经典紫", Color(0xFF8B4DEE), 0xFF8B4DEEL),
    PresetThemeColor("晨曦蓝", Color(0xFF1F6FD9), 0xFF1F6FD9L),
    PresetThemeColor("翡翠绿", Color(0xFF0E7E82), 0xFF0E7E82L),
    PresetThemeColor("珊瑚橙", Color(0xFFBF5010), 0xFFBF5010L),
    PresetThemeColor("樱花粉", Color(0xFFD51E86), 0xFFD51E86L),
    PresetThemeColor("薄荷青", Color(0xFF10B981), 0xFF10B981L),
    PresetThemeColor("枫叶红", Color(0xFFE11D48), 0xFFE11D48L),
    PresetThemeColor("曜石金", Color(0xFFD97706), 0xFFD97706L),
    PresetThemeColor("极光青", Color(0xFF06B6D4), 0xFF06B6D4L),
    PresetThemeColor("鸢尾紫", Color(0xFF6366F1), 0xFF6366F1L),
)

internal fun dynamicPaletteFromColor(seed: Color): AccentPalette {
    val r = seed.red
    val g = seed.green
    val b = seed.blue

    val lightFill = seed
    val lightText = Color(r * 0.85f, g * 0.85f, b * 0.85f, 1f)
    val lightContainer = Color(0.92f + r * 0.07f, 0.92f + g * 0.07f, 0.92f + b * 0.07f, 1f)
    val lightOnContainer = Color(r * 0.35f, g * 0.35f, b * 0.35f, 1f)
    val lightCanvasSel = Color(0.95f + r * 0.04f, 0.95f + g * 0.04f, 0.95f + b * 0.04f, 1f)

    val fill = seed
    val accent = Color((r + 0.15f).coerceAtMost(1f), (g + 0.15f).coerceAtMost(1f), (b + 0.15f).coerceAtMost(1f), 1f)
    val text = Color((r + 0.30f).coerceAtMost(1f), (g + 0.30f).coerceAtMost(1f), (b + 0.30f).coerceAtMost(1f), 1f)

    val surface = Color(0.04f + r * 0.04f, 0.04f + g * 0.04f, 0.05f + b * 0.05f, 1f)
    val card = Color(0.08f + r * 0.06f, 0.08f + g * 0.06f, 0.10f + b * 0.07f, 1f)
    val raised = Color(0.12f + r * 0.08f, 0.12f + g * 0.08f, 0.15f + b * 0.09f, 1f)
    val lowest = Color(0.02f + r * 0.02f, 0.02f + g * 0.02f, 0.03f + b * 0.03f, 1f)
    val low = Color(0.06f + r * 0.04f, 0.06f + g * 0.04f, 0.08f + b * 0.05f, 1f)
    val highest = Color(0.16f + r * 0.10f, 0.16f + g * 0.10f, 0.20f + b * 0.12f, 1f)
    val container = Color(0.18f + r * 0.15f, 0.18f + g * 0.15f, 0.22f + b * 0.18f, 1f)
    val border = Color(0.20f + r * 0.12f, 0.20f + g * 0.12f, 0.24f + b * 0.14f, 1f)
    val outlineVar = Color(0.15f + r * 0.08f, 0.15f + g * 0.08f, 0.18f + b * 0.10f, 1f)
    val onMuted = Color(0.70f + r * 0.15f, 0.70f + g * 0.15f, 0.75f + b * 0.15f, 1f)
    val on = Color(0.95f + r * 0.05f, 0.95f + g * 0.05f, 0.97f + b * 0.03f, 1f)
    val controlBorder = Color(0.40f + r * 0.20f, 0.40f + g * 0.20f, 0.48f + b * 0.20f, 1f)

    val bLow = Color(0.03f + r * 0.02f, 0.03f + g * 0.02f, 0.04f + b * 0.02f, 1f)
    val bCont = Color(0.06f + r * 0.03f, 0.06f + g * 0.03f, 0.08f + b * 0.04f, 1f)
    val bHigh = Color(0.10f + r * 0.04f, 0.10f + g * 0.04f, 0.12f + b * 0.05f, 1f)
    val bHighest = Color(0.14f + r * 0.05f, 0.14f + g * 0.05f, 0.16f + b * 0.06f, 1f)
    val canvasSel = Color(0.12f + r * 0.06f, 0.12f + g * 0.06f, 0.16f + b * 0.08f, 1f)

    return AccentPalette(
        fill = fill,
        accent = accent,
        text = text,
        lightFill = lightFill,
        lightText = lightText,
        surface = surface,
        card = card,
        raised = raised,
        lowest = lowest,
        low = low,
        highest = highest,
        container = container,
        border = border,
        outlineVar = outlineVar,
        onMuted = onMuted,
        on = on,
        controlBorder = controlBorder,
        bLow = bLow,
        bCont = bCont,
        bHigh = bHigh,
        bHighest = bHighest,
        canvasSel = canvasSel,
        lightContainer = lightContainer,
        lightOnContainer = lightOnContainer,
        canvasStroke = accent,
        lightCanvasStroke = lightFill,
        lightCanvasSel = lightCanvasSel,
    )
}
