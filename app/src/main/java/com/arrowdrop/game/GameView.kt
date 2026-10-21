package com.arrowdrop.game

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The whole game surface: board, arrows, exit animations, HUD, buttons and the level cards.
 * Everything is drawn with Canvas; the puzzle rules live in [PuzzleSession].
 */
class GameView(context: Context) : View(context) {

    private val save = Save(context)
    private val density = resources.displayMetrics.density
    private val art = ArrowArt(density)

    private var level: Int = save.level.coerceAtLeast(1)
    private var session = PuzzleSession(Levels.specFor(level))

    private var clearAt = 0L
    private var failAt = 0L

    private var overlay: Overlay? = null
    private var overlayShownAt = 0L

    private val exits = ArrayList<ExitFx>()
    private val shakes = HashMap<Int, Long>()
    private val flashes = HashMap<Int, Long>()
    private var hint: HintFx? = null

    private var insetTop = 0
    private var insetBottom = 0

    private var geometryDirty = true
    private var lastWidth = 0
    private var lastHeight = 0

    private var cell = 0f
    private val grid = RectF()
    private val panel = RectF()
    private val bar = RectF()
    private val hintRect = RectF()
    private val restartRect = RectF()
    private val cardRect = RectF()
    private val adRect = RectF()

    /** Rewarded ad that buys back a life when the player is out. */
    private val ads = RewardedLives(context)

    init {
        ads.onChanged = { invalidate() }
        ads.preload()
    }

    // ---------------------------------------------------------------- paints

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Palette.PANEL
    }
    private val panelStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Palette.PANEL_STROKE
        strokeWidth = dp(1.6f)
    }
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Palette.TRACK
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val buttonStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val scrimPaint = Paint().apply { style = Paint.Style.FILL }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Palette.PANEL
    }
    private val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val labelTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val boldTypeface = Typeface.create("sans-serif-black", Typeface.BOLD)

    // ---------------------------------------------------------------- state types

    private class ExitFx(val arrow: Arrow, val start: Long, val travel: Int)

    private class ExitPose(val x: Float, val y: Float, val dir: Dir)

    private class HintFx(val arrow: Arrow, val start: Long)

    private sealed class Overlay {
        object Cleared : Overlay()
        object Failed : Overlay()
    }

    // ---------------------------------------------------------------- lifecycle

    init {
        contentDescription = "Arrow Drop board"
        isClickable = true
    }

    fun onActivated() {
        running = true
        scheduleFrame()
        invalidate()
    }

    fun onDeactivated() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        scheduled = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        scheduleFrame()
    }

    override fun onDetachedFromWindow() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        scheduled = false
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        geometryDirty = true
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        var top = insets.systemWindowInsetTop
        var bottom = insets.systemWindowInsetBottom
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val cutout = insets.displayCutout
            if (cutout != null) top = max(top, cutout.safeInsetTop)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            bottom = max(bottom, insets.systemGestureInsets.bottom)
        }
        if (top != insetTop || bottom != insetBottom) {
            insetTop = top
            insetBottom = bottom
            geometryDirty = true
            invalidate()
        }
        return super.onApplyWindowInsets(insets)
    }

    private var running = false
    private var scheduled = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            scheduled = false
            if (!running) return
            tick(SystemClock.elapsedRealtime())
            invalidate()
            scheduleFrame()
        }
    }

    private fun scheduleFrame() {
        if (scheduled || !running) return
        scheduled = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    // ---------------------------------------------------------------- gameplay

    private fun startLevel(number: Int) {
        level = number.coerceAtLeast(1)
        session = PuzzleSession(Levels.specFor(level))
        overlay = null
        exits.clear()
        shakes.clear()
        flashes.clear()
        hint = null
        clearAt = 0L
        failAt = 0L
        geometryDirty = true
        save.level = level
        ads.preload()
        invalidate()
    }

    private fun tick(now: Long) {
        if (exits.isNotEmpty()) {
            exits.removeAll { now - it.start > EXIT_WRAP_MS + 120L }
        }
        hint?.let { if (now - it.start > HINT_MS) hint = null }

        if (session.cleared && overlay == null && clearAt > 0L && now - clearAt >= CLEAR_DELAY) {
            finishLevel(now)
        }
        if (session.failed && overlay == null && failAt > 0L && now - failAt >= FAIL_DELAY) {
            overlay = Overlay.Failed
            overlayShownAt = now
            // Ask for an ad now so the card can offer a life as soon as one is ready.
            ads.preload()
            invalidate()
        }
    }

    private fun finishLevel(now: Long) {
        save.level = level + 1
        overlay = Overlay.Cleared
        overlayShownAt = now
        haptic(HapticFeedbackConstants.VIRTUAL_KEY)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            handleTap(event.x, event.y)
        }
        return true
    }

    private fun handleTap(x: Float, y: Float) {
        ensureGeometry()
        val now = SystemClock.elapsedRealtime()

        val shown = overlay
        if (shown != null) {
            if (now - overlayShownAt < OVERLAY_GUARD) return
            if (shown == Overlay.Failed && adRect.contains(x, y)) {
                // The button is only live while an ad is ready; otherwise the tap does nothing.
                if (ads.ready) requestRewardedLife()
                return
            }
            startLevel(if (shown == Overlay.Cleared) level + 1 else level)
            haptic(HapticFeedbackConstants.VIRTUAL_KEY)
            return
        }

        if (restartRect.contains(x, y)) {
            startLevel(level)
            haptic(HapticFeedbackConstants.VIRTUAL_KEY)
            return
        }
        if (hintRect.contains(x, y)) {
            useHint(now)
            return
        }
        if (session.cleared || session.failed) return
        if (!grid.contains(x, y)) return

        val col = ((x - grid.left) / cell).toInt()
        val row = ((y - grid.top) / cell).toInt()
        if (!session.board.inBounds(col, row)) return

        when (val result = session.tap(col, row)) {
            is TapResult.Empty -> Unit

            is TapResult.Blocked -> {
                shakes[result.arrow.id] = now
                flashes[result.blocker.id] = now
                hint = null
                haptic(HapticFeedbackConstants.LONG_PRESS)
                if (session.failed) failAt = now
            }

            is TapResult.Removed -> {
                exits.add(
                    ExitFx(
                        arrow = result.arrow,
                        start = now,
                        travel = session.board.exitDistance(result.arrow)
                    )
                )
                if (hint?.arrow?.id == result.arrow.id) hint = null
                haptic(HapticFeedbackConstants.VIRTUAL_KEY)
                if (result.levelCleared) clearAt = now
            }
        }
        invalidate()
    }

    /** Shows a rewarded ad and, if the player earns it, resumes the level with one heart back. */
    private fun requestRewardedLife() {
        val activity = context as? Activity ?: return
        val shown = ads.show(activity) {
            if (session.grantLife()) {
                overlay = null
                overlayShownAt = 0L
                failAt = 0L
                hint = null
                haptic(HapticFeedbackConstants.VIRTUAL_KEY)
                invalidate()
            }
        }
        if (shown) haptic(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun useHint(now: Long) {
        val arrow = session.revealHint() ?: return
        hint = HintFx(arrow, now)
        haptic(HapticFeedbackConstants.VIRTUAL_KEY)
        invalidate()
    }

    private fun haptic(type: Int) {
        performHapticFeedback(type)
    }

    // ---------------------------------------------------------------- geometry

    private fun dp(value: Float): Float = value * density

    private fun ensureGeometry() {
        if (width <= 0 || height <= 0) return
        if (!geometryDirty && width == lastWidth && height == lastHeight) return
        lastWidth = width
        lastHeight = height
        geometryDirty = false

        val pad = dp(20f)
        val topLimit = insetTop + dp(100f)
        val bottomLimit = height - insetBottom - dp(88f)
        val cols = session.spec.cols
        val rows = session.spec.rows
        val availW = (width - pad * 2f).coerceAtLeast(dp(40f))
        val availH = (bottomLimit - topLimit).coerceAtLeast(dp(40f))

        cell = min(availW / cols, availH / rows)
        val gridW = cell * cols
        val gridH = cell * rows
        val left = (width - gridW) / 2f
        val top = topLimit + (availH - gridH) / 2f
        grid.set(left, top, left + gridW, top + gridH)
        panel.set(left - dp(11f), top - dp(11f), left + gridW + dp(11f), top + gridH + dp(11f))

        bar.set(pad, insetTop + dp(80f), width - pad, insetTop + dp(87f))

        val buttonHeight = dp(50f)
        val gap = dp(12f)
        val totalW = min(width - pad * 2f, dp(360f))
        val buttonW = (totalW - gap) / 2f
        val buttonLeft = (width - totalW) / 2f
        val buttonTop = height - insetBottom - dp(20f) - buttonHeight
        hintRect.set(buttonLeft, buttonTop, buttonLeft + buttonW, buttonTop + buttonHeight)
        restartRect.set(
            buttonLeft + buttonW + gap, buttonTop,
            buttonLeft + totalW, buttonTop + buttonHeight
        )

        val cardW = min(width - dp(48f), dp(330f))
        val cardH = dp(292f)
        val cardCx = width / 2f
        val cardCy = height * 0.46f
        cardRect.set(
            cardCx - cardW / 2f, cardCy - cardH / 2f,
            cardCx + cardW / 2f, cardCy + cardH / 2f
        )
        adRect.set(
            cardRect.left + dp(24f), cardRect.top + dp(168f),
            cardRect.right - dp(24f), cardRect.top + dp(216f)
        )

        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            Palette.BG_TOP, Palette.BG_BOTTOM, Shader.TileMode.CLAMP
        )
        glowPaint.shader = RadialGradient(
            width / 2f, height * 0.24f, max(width, height) * 0.62f,
            intArrayOf(Palette.withAlpha(Palette.BLUE, 0.07f), Palette.withAlpha(Palette.BLUE, 0f)),
            null, Shader.TileMode.CLAMP
        )
        barPaint.shader = LinearGradient(
            bar.left, 0f, bar.right, 0f,
            intArrayOf(Palette.BLUE, 0xFF7FB6FF.toInt()), null, Shader.TileMode.CLAMP
        )
    }

    // ---------------------------------------------------------------- drawing

    override fun onDraw(canvas: Canvas) {
        ensureGeometry()
        val now = SystemClock.elapsedRealtime()
        drawBackground(canvas)
        drawHeader(canvas)
        drawBoard(canvas, now)
        drawFooter(canvas)
        drawOverlay(canvas, now)
    }

    private fun drawBackground(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowPaint)
    }

    private fun text(
        canvas: Canvas,
        value: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        align: Paint.Align,
        bold: Boolean = false,
        spacing: Float = 0f
    ) {
        textPaint.textSize = size
        textPaint.color = color
        textPaint.textAlign = align
        textPaint.typeface = if (bold) boldTypeface else labelTypeface
        textPaint.letterSpacing = spacing
        canvas.drawText(value, x, y, textPaint)
    }

    private fun drawHeader(canvas: Canvas) {
        val pad = dp(20f)
        text(
            canvas, "LEVEL", pad, insetTop + dp(20f), dp(11f), Palette.BLUE,
            Paint.Align.LEFT, bold = true, spacing = 0.18f
        )
        text(
            canvas, level.toString(), pad, insetTop + dp(53f), dp(32f), Palette.TEXT,
            Paint.Align.LEFT, bold = true
        )

        val heartY = insetTop + dp(40f)
        val heartGap = dp(23f)
        val right = width - pad - dp(9f)
        for (i in 0 until PuzzleSession.MAX_MISSES) {
            val filled = i < session.hearts
            art.drawHeart(
                canvas,
                right - (PuzzleSession.MAX_MISSES - 1 - i) * heartGap,
                heartY,
                dp(17f),
                if (filled) Palette.RED else Palette.withAlpha(Palette.TEXT_DIM, 0.25f)
            )
        }

        text(
            canvas, "${session.clearedCount} / ${session.totalArrows}", width - pad,
            insetTop + dp(74f), dp(10f), Palette.TEXT_DIM, Paint.Align.RIGHT
        )

        val radius = bar.height() / 2f
        canvas.drawRoundRect(bar, radius, radius, trackPaint)
        if (session.progress > 0f) {
            canvas.save()
            canvas.clipRect(bar.left, bar.top, bar.left + bar.width() * session.progress, bar.bottom)
            canvas.drawRoundRect(bar, radius, radius, barPaint)
            canvas.restore()
        }
    }

    private fun drawBoard(canvas: Canvas, now: Long) {
        shadowPaint.color = Palette.withAlpha(Palette.INK, 0.06f)
        canvas.drawRoundRect(
            panel.left, panel.top + dp(5f), panel.right, panel.bottom + dp(5f),
            dp(22f), dp(22f), shadowPaint
        )
        canvas.drawRoundRect(panel, dp(22f), dp(22f), panelPaint)
        canvas.drawRoundRect(panel, dp(22f), dp(22f), panelStrokePaint)

        val cols = session.spec.cols
        val rows = session.spec.rows
        val inset = dp(2.4f)
        val radius = min(dp(9f), cell * 0.20f)

        for (y in 0 until rows) {
            for (x in 0 until cols) {
                cellPaint.color =
                    if (session.board.at(x, y) != null) Palette.CELL else Palette.CELL_EMPTY
                val l = grid.left + x * cell + inset
                val t = grid.top + y * cell + inset
                val r = grid.left + (x + 1) * cell - inset
                val b = grid.top + (y + 1) * cell - inset
                canvas.drawRoundRect(l, t, r, b, radius, radius, cellPaint)
            }
        }

        hint?.let { h ->
            val age = now - h.start
            if (age in 0..HINT_MS) {
                val pulse = 0.78f + 0.22f * sin((age / 145f).toFloat())
                val cx = grid.left + (h.arrow.x + 0.5f) * cell
                val cy = grid.top + (h.arrow.y + 0.5f) * cell
                val hintRadius = cell * (0.42f + 0.03f * pulse)
                art.drawRing(
                    canvas, cx, cy, hintRadius, dp(5f),
                    Palette.withAlpha(Palette.BLUE, 0.95f * pulse)
                )
                art.drawRing(
                    canvas, cx, cy, hintRadius + dp(7f), dp(2f),
                    Palette.withAlpha(Palette.BLUE, 0.35f * pulse)
                )
            }
        }

        for (arrow in session.board.arrows()) {
            var cx = grid.left + (arrow.x + 0.5f) * cell
            var cy = grid.top + (arrow.y + 0.5f) * cell

            shakes[arrow.id]?.let { start ->
                val t = (now - start).toFloat() / SHAKE_MS
                if (t < 1f) {
                    val offset = sin((t * 3f * PI).toFloat()) * dp(7f) * (1f - t)
                    cx += -arrow.dir.dy * offset
                    cy += arrow.dir.dx * offset
                } else {
                    shakes.remove(arrow.id)
                }
            }

            if (arrow.wrap) {
                art.drawWrapMark(canvas, cx, cy, cell * 0.94f, arrow.dir)
            }
            art.drawArrow(canvas, cx, cy, cell * 0.94f, arrow.dir, Palette.ARROW)

            flashes[arrow.id]?.let { start ->
                val t = (now - start).toFloat() / FLASH_MS
                if (t < 1f) {
                    art.drawRing(
                        canvas, cx, cy, cell * 0.43f, dp(2.6f),
                        Palette.withAlpha(Palette.RED, 0.9f * (1f - t))
                    )
                } else {
                    flashes.remove(arrow.id)
                }
            }
        }

        for (fx in exits) {
            drawExit(canvas, fx, now)
        }
    }

    private fun drawExit(canvas: Canvas, fx: ExitFx, now: Long) {
        val duration = if (fx.arrow.wrap) EXIT_WRAP_MS else EXIT_MS
        val t = ((now - fx.start).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        val color = Palette.ARROW
        val fadeStart = if (fx.arrow.wrap) 0.72f else 0.55f
        val fade = if (t < fadeStart) 1f else (1f - (t - fadeStart) / (1f - fadeStart)).coerceIn(0f, 1f)

        // two fading ghosts behind the arrow
        for (i in 1..2) {
            val gt = (t - i * 0.07f).coerceAtLeast(0f)
            val pose = exitPose(fx, gt)
            art.drawArrow(
                canvas, pose.x, pose.y, cell * 0.94f, pose.dir,
                Palette.withAlpha(color, 0.13f / i)
            )
        }

        val pose = exitPose(fx, t)
        art.drawArrow(
            canvas, pose.x, pose.y, cell * 0.94f, pose.dir,
            Palette.withAlpha(color, fade)
        )
    }

    /** Where the leaving arrow is at [t] (0..1). Wrapping arrows bend around the edge corner. */
    private fun exitPose(fx: ExitFx, t: Float): ExitPose {
        val arrow = fx.arrow
        val startX = grid.left + (arrow.x + 0.5f) * cell
        val startY = grid.top + (arrow.y + 0.5f) * cell
        if (!arrow.wrap) {
            val distance = (fx.travel + 0.8f) * cell * (t * t)
            return ExitPose(
                startX + arrow.dir.dx * distance,
                startY + arrow.dir.dy * distance,
                arrow.dir
            )
        }

        val turn = Routes.turn(arrow.dir)
        val straight = (fx.travel - 0.5f) * cell
        val borderCells = when (arrow.dir) {
            Dir.RIGHT -> session.spec.rows - arrow.y - 0.5f
            Dir.DOWN -> arrow.x + 0.5f
            Dir.LEFT -> arrow.y + 0.5f
            Dir.UP -> session.spec.cols - arrow.x - 0.5f
        }
        val border = (borderCells + 0.8f) * cell
        val split = 0.55f
        return if (t <= split) {
            val p = t / split
            val distance = straight * p * p
            ExitPose(
                startX + arrow.dir.dx * distance,
                startY + arrow.dir.dy * distance,
                arrow.dir
            )
        } else {
            val p = (t - split) / (1f - split)
            val edgeX = startX + arrow.dir.dx * straight
            val edgeY = startY + arrow.dir.dy * straight
            val distance = border * p
            ExitPose(edgeX + turn.dx * distance, edgeY + turn.dy * distance, turn)
        }
    }

    private fun drawFooter(canvas: Canvas) {
        val hintsAvailable = session.hintsLeft > 0 && !session.cleared && !session.failed
        val hintLabel = if (session.hintsLeft > 0) "HINT  ·  ${session.hintsLeft}" else "HINT"
        drawButton(canvas, hintRect, hintLabel, hintsAvailable)
        drawButton(canvas, restartRect, "RESTART", true)
    }

    private fun drawButton(canvas: Canvas, r: RectF, label: String, enabled: Boolean) {
        shadowPaint.color = Palette.withAlpha(Palette.INK, 0.05f)
        canvas.drawRoundRect(
            r.left, r.top + dp(3f), r.right, r.bottom + dp(3f),
            r.height() / 2f, r.height() / 2f, shadowPaint
        )
        buttonPaint.color = Palette.PANEL
        canvas.drawRoundRect(r, r.height() / 2f, r.height() / 2f, buttonPaint)
        buttonStrokePaint.color =
            if (enabled) Palette.withAlpha(Palette.BLUE, 0.40f) else Palette.withAlpha(Palette.TEXT_DIM, 0.22f)
        buttonStrokePaint.strokeWidth = dp(1.4f)
        canvas.drawRoundRect(r, r.height() / 2f, r.height() / 2f, buttonStrokePaint)

        textPaint.textSize = dp(12f)
        textPaint.typeface = labelTypeface
        textPaint.letterSpacing = 0.14f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = if (enabled) Palette.BLUE else Palette.withAlpha(Palette.TEXT_DIM, 0.65f)
        val baseline = r.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(label, r.centerX(), baseline, textPaint)
    }

    private fun drawOverlay(canvas: Canvas, now: Long) {
        val shown = overlay ?: return
        val t = ((now - overlayShownAt) / 280f).coerceIn(0f, 1f)
        val fade = easeOut(t)

        scrimPaint.color = Palette.withAlpha(Palette.INK, 0.32f * fade)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        val cardW = cardRect.width()
        val cardH = cardRect.height()
        val cx = cardRect.centerX()
        val cy = cardRect.centerY()
        val scale = 0.86f + 0.14f * easeOutBack(t)

        canvas.save()
        canvas.scale(scale, scale, cx, cy)
        val left = cardRect.left
        val top = cardRect.top
        val right = cardRect.right
        val bottom = cardRect.bottom

        shadowPaint.color = Palette.withAlpha(Palette.INK, 0.12f * fade)
        canvas.drawRoundRect(
            left, top + dp(6f), right, bottom + dp(6f), dp(28f), dp(28f), shadowPaint
        )
        cardPaint.color = Palette.PANEL
        canvas.drawRoundRect(left, top, right, bottom, dp(28f), dp(28f), cardPaint)
        cardStrokePaint.color = Palette.withAlpha(Palette.INK, 0.08f * fade)
        canvas.drawRoundRect(left, top, right, bottom, dp(28f), dp(28f), cardStrokePaint)

        if (shown == Overlay.Cleared) {
            drawClearedCard(canvas, left, top, cardW, cardH, fade)
        } else {
            drawFailedCard(canvas, left, top, cardW, cardH, fade)
        }
        canvas.restore()
    }

    private fun drawClearedCard(
        canvas: Canvas,
        left: Float,
        top: Float,
        cardW: Float,
        cardH: Float,
        fade: Float
    ) {
        val center = left + cardW / 2f
        text(
            canvas, "LEVEL CLEARED", center, top + dp(68f), dp(23f),
            Palette.withAlpha(Palette.TEXT, fade), Paint.Align.CENTER, bold = true
        )

        for (i in 0 until PuzzleSession.MAX_MISSES) {
            val kept = i < session.hearts
            art.drawHeart(
                canvas,
                center + (i - 1) * dp(32f),
                top + dp(118f),
                dp(23f),
                if (kept) Palette.withAlpha(Palette.RED, fade)
                else Palette.withAlpha(Palette.TEXT_DIM, 0.25f * fade)
            )
        }

        text(
            canvas, "TAP TO CONTINUE", center, top + cardH - dp(32f), dp(12f),
            Palette.withAlpha(Palette.BLUE, fade), Paint.Align.CENTER, bold = true, spacing = 0.12f
        )
    }

    private fun drawFailedCard(
        canvas: Canvas,
        left: Float,
        top: Float,
        cardW: Float,
        cardH: Float,
        fade: Float
    ) {
        val center = left + cardW / 2f
        text(
            canvas, "OUT OF LIVES", center, top + dp(70f), dp(23f),
            Palette.withAlpha(Palette.RED, fade), Paint.Align.CENTER, bold = true
        )
        text(
            canvas, "An arrow was in the way.", center, top + dp(106f), dp(13f),
            Palette.withAlpha(Palette.TEXT_DIM, fade), Paint.Align.CENTER
        )

        for (i in 0 until PuzzleSession.MAX_MISSES) {
            art.drawHeart(
                canvas, center + (i - 1) * dp(30f), top + dp(146f), dp(22f),
                Palette.withAlpha(Palette.TEXT_DIM, 0.30f * fade)
            )
        }

        drawAdButton(canvas, fade)

        text(
            canvas, "TAP TO RETRY", center, top + cardH - dp(24f), dp(11f),
            Palette.withAlpha(Palette.TEXT_DIM, fade), Paint.Align.CENTER, bold = true, spacing = 0.12f
        )
    }

    /** Blue call to action that buys one life back, or a quiet "not ready" state while loading. */
    private fun drawAdButton(canvas: Canvas, fade: Float) {
        val ready = ads.ready
        val radius = adRect.height() / 2f
        shadowPaint.color = Palette.withAlpha(Palette.INK, 0.10f * fade)
        canvas.drawRoundRect(
            adRect.left, adRect.top + dp(3f), adRect.right, adRect.bottom + dp(3f),
            radius, radius, shadowPaint
        )
        buttonPaint.color =
            if (ready) Palette.withAlpha(Palette.BLUE, fade)
            else Palette.withAlpha(Palette.TRACK, fade)
        canvas.drawRoundRect(adRect, radius, radius, buttonPaint)

        text(
            canvas,
            if (ready) "WATCH AD  ·  +1 LIFE" else "AD NOT READY",
            adRect.centerX(),
            adRect.centerY() + dp(4.5f),
            dp(13f),
            if (ready) Palette.withAlpha(Palette.PANEL, fade)
            else Palette.withAlpha(Palette.TEXT_DIM, fade),
            Paint.Align.CENTER,
            bold = true,
            spacing = 0.06f
        )
    }

    // ---------------------------------------------------------------- helpers

    private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)

    private fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val p = t - 1f
        return 1f + c3 * p * p * p + c1 * p * p
    }

    private companion object {
        const val EXIT_MS = 260L
        const val EXIT_WRAP_MS = 460L
        const val SHAKE_MS = 260f
        const val FLASH_MS = 320f
        const val HINT_MS = 4000L
        const val CLEAR_DELAY = 620L
        const val FAIL_DELAY = 380L
        const val OVERLAY_GUARD = 300L
    }
}
