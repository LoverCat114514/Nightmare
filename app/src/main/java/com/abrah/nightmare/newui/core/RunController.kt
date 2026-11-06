package com.abrah.nightmare.newui.core

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ⭐⭐ fix34 (2026-11-06) — the render engine's owner, rebuilt.
 *
 * ## What this replaces
 *
 * The old code kept the run's live state in SIX file-level singletons:
 * `RUN_PROGRESS`, `RUN_LOG`, `RUN_CANCEL` (an `@Volatile Boolean`), `LAST_SHOT`,
 * `LAST_SHOT_VERSION`, `SESSION`, `SESSION_VERSION`, plus `NewUiRun.busy` — a
 * `MutableState` living in an `object`, i.e. a process-wide singleton.
 *
 * That is a resource leak with a lifetime bug attached: every one of them
 * survives the screen that owns it. Leaving the workbench and coming back does
 * not reset them; a second screen instance reads the first one's leftovers.
 * `NewUiRun.busy` in particular could never be released, because an `object`
 * has no teardown. Worse, the UI learned about progress by POLLING those
 * atomics every 100 ms from a `LaunchedEffect` — up to 10 wakeups a second to
 * discover that nothing had changed, and the progress bar was always up to
 * 100 ms stale by construction.
 *
 * ## What this does instead
 *
 * - **Owned, not global.** One instance per screen, created in `remember`,
 *   torn down in `DisposableEffect`. [dispose] cancels the scope, so nothing
 *   keeps running after the screen is gone.
 * - **Pushed, not polled.** The engine's callback writes a `StateFlow`. There
 *   is no polling loop and no 100 ms floor; a step lands on screen the frame it
 *   arrives.
 * - **The engine's thread never touches Compose state.** `core.run` blocks, so
 *   it runs on a dedicated dispatcher. Only the values it publishes cross back,
 *   through `MutableStateFlow` (thread-safe by contract), never through a
 *   `MutableState` (which is not).
 *
 * ## Why `StateFlow` here and `mutableStateOf` in [WbParams]
 *
 * Opposite call from [WbParams], for a specific reason: these values are
 * written from a background thread and read from composition. `MutableState` is
 * snapshot state — it belongs to one thread's snapshot and is not meant to be
 * written from an arbitrary worker. `MutableStateFlow` is explicitly
 * thread-safe and is the right channel for a producer off the main thread.
 * `collectAsState` bridges it into composition, which is the one hop that is
 * genuinely needed rather than decorative.
 */
class RunController(private val appContext: Context) {

    /** Where the run stands. The UI renders from this and nothing else. */
    data class Phase(
        val running: Boolean = false,
        val step: Int = 0,
        val total: Int = 0,
        val message: String = "",
        val error: String? = null,
    ) {
        /** 0f..1f for the bar; null when there is no known total (indeterminate). */
        val fraction: Float?
            get() = if (total > 0) step.toFloat() / total else null
    }

    private val _phase = MutableStateFlow(Phase())
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    /**
     * Cancellation. ⚠ A plain `@Volatile` flag, not state: the engine's thread
     * reads it, and nothing renders from it — making it state would invalidate
     * composition on every poll of a value no composable shows.
     */
    @Volatile private var cancelled = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The render thread. One run at a time — that is the engine's own contract. */
    private val engine = Dispatchers.IO.limitedParallelism(1)

    /** Non-render work (apply-now tools, LoRA import) so it never queues behind a run. */
    private val sideWork = Dispatchers.IO.limitedParallelism(1)

    private var runJob: Job? = null

    val isRunning: Boolean get() = _phase.value.running

    /** Ask the engine to stop. Idempotent; safe to call when idle. */
    fun cancel() {
        cancelled = true
        _phase.value = _phase.value.copy(message = "cancelling…")
    }

    /**
     * Run [block] on the side-work dispatcher — "apply now", LoRA import.
     *
     * ⭐ These used to share the render thread, so an apply-now tapped during a
     * generation sat in the queue until the picture finished and read as a dead
     * button. A separate single-threaded lane keeps them live.
     */
    fun runSideWork(block: suspend () -> Unit) {
        scope.launch { withContext(sideWork) { block() } }
    }

    /**
     * Start a run.
     *
     * [block] receives a callback that publishes a step, and must return the
     * finished result. Everything that touches the engine happens inside
     * [engine]; the phase writes are the only thing that crosses back, and
     * `MutableStateFlow` is safe for that.
     *
     * ⚠ The `finally` publishes "not running" on EVERY exit path — success,
     * failure, and cancellation. The old code had the same guarantee, but it
     * could be skipped if the executor rejected the task; here the coroutine's
     * `finally` cannot be bypassed.
     */
    fun start(
        block: suspend (onStep: (step: Int, total: Int) -> Unit, isCancelled: () -> Boolean) -> Result,
    ) {
        if (_phase.value.running) return
        cancelled = false
        _phase.value = Phase(running = true, message = "starting…")
        runJob = scope.launch {
            try {
                val result = withContext(engine) {
                    block(
                        { step, total -> _phase.value = _phase.value.copy(step = step, total = total, message = "") },
                        { cancelled },
                    )
                }
                // ⭐ One place maps a Result to the published Phase, so the UI
                // reads a single shape whatever path the run took.
                _phase.value = when (result) {
                    is Result.Success -> _phase.value.copy(
                        running = false, error = null, message = "%.1fs".format(result.elapsedMs / 1000.0),
                    )
                    is Result.Failure -> _phase.value.copy(running = false, error = result.message)
                    is Result.Empty -> _phase.value.copy(running = false, error = result.message)
                    Result.Cancelled -> _phase.value.copy(running = false, message = "cancelled")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // ⚠ Rethrow: a cancelled coroutine must stay cancelled, or the
                // structured-concurrency contract is broken and the caller
                // sees success where it should see cancellation.
                _phase.value = _phase.value.copy(running = false, message = "cancelled")
                throw e
            } catch (e: Exception) {
                _phase.value = _phase.value.copy(
                    running = false,
                    error = e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }

    /**
     * What a run produced.
     *
     * ⭐ A sealed result instead of the old "write to five different atomics
     * and hope": the caller gets one value that says what happened, and the
     * compiler forces every branch to be handled.
     */
    sealed interface Result {
        /** The render finished; [file] is the engine's output on disk. */
        data class Success(val file: java.io.File, val elapsedMs: Long) : Result

        /** The engine reported an error (its own message, not an exception). */
        data class Failure(val message: String) : Result

        /** Cancelled by the person. */
        data object Cancelled : Result

        /** Finished, but nothing usable came out. */
        data class Empty(val message: String) : Result
    }

    /**
     * Publish a status line while a run is on (post-processing, saving…).
     * ⚠ Coalesced by the caller, not here — a per-pixel write would be a
     * recomposition per pixel.
     */
    fun note(message: String) {
        _phase.value = _phase.value.copy(message = message)
    }

    /**
     * ⚠ MUST be called from `DisposableEffect`'s `onDispose`. Cancels the scope,
     * which stops the run job and the side-work lane — the teardown the old
     * `object` singletons could not perform.
     */
    fun dispose() {
        runJob?.cancel()
        scope.cancel()
    }
}