package com.abrah.nightmare.newui

/**
 * ⭐ fix35k — the workbench prompt bridge.
 *
 * The Results "use on the current flow" transfer lists the CANVAS's prompt
 * nodes only; the workbench's own prompt boxes were invisible to it, so a
 * canvas without a `core.prompt` node answered "no node can take it". While
 * the workbench screen is ALIVE it publishes its positive and negative texts
 * here and hangs write-back hooks, so the transfer can offer
 * 「工作台 · 正面/负面提示词」as targets and land the text in the boxes.
 *
 * ⚠ Lifecycle: set by [NewUiScreen] in a `DisposableEffect`, cleared on
 * dispose — a bridge from a dead screen would write into nothing.
 */
class WbPromptBridge {
    @Volatile var positive: String = ""
    @Volatile var negative: String = ""

    /** Set by the screen; the transfer calls them on the MAIN thread. */
    @Volatile var onPositive: ((String) -> Unit)? = null
    @Volatile var onNegative: ((String) -> Unit)? = null
}
