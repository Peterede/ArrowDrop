package com.arrowdrop.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws the flat, ink-outlined arrow glyph, the loop marker used by wrapping arrows, and the small
 * heart used by the HUD. Shapes are built once in unit space and scaled on the canvas, so drawing a
 * full board allocates nothing.
 */
class ArrowArt(private val density: Float) {

    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val soft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 0.30f
    }

    private val loop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 0.05f
    }

    /** Arrow pointing up, one unit long, centred on the origin. */
    private val arrowPath = Path().apply {
        val halfLength = 0.50f
        val halfWidth = 0.30f
        val shaft = 0.095f
        val headBase = -0.20f
        moveTo(0f, -halfLength)
        lineTo(halfWidth, headBase)
        lineTo(shaft, headBase - 0.02f)
        lineTo(shaft, halfLength)
        lineTo(-shaft, halfLength)
        lineTo(-shaft, headBase - 0.02f)
        lineTo(-halfWidth, headBase)
        close()
    }

    /** The wrap marker is a clockwise loop: a 250 degree arc with a small head. */
    private val loopOval = RectF(-0.46f, -0.46f, 0.46f, 0.46f)

    private val loopTip = Path()

    private val heartPath = Path().apply {
        addCircle(-0.22f, -0.16f, 0.26f, Path.Direction.CW)
        addCircle(0.22f, -0.16f, 0.26f, Path.Direction.CW)
        moveTo(-0.47f, -0.07f)
        lineTo(0.47f, -0.07f)
        lineTo(0f, 0.46f)
        close()
    }

    init {
        val end = Math.toRadians(190.0)
        val px = (cos(end) * 0.46).toFloat()
        val py = (sin(end) * 0.46).toFloat()
        val tx = (-sin(end)).toFloat()
        val ty = cos(end).toFloat()
        val tipX = px + tx * 0.10f
        val tipY = py + ty * 0.10f
        val baseX = px - tx * 0.01f
        val baseY = py - ty * 0.01f
        val perpX = -ty
        val perpY = tx
        loopTip.moveTo(tipX, tipY)
        loopTip.lineTo(baseX + perpX * 0.06f, baseY + perpY * 0.06f)
        loopTip.lineTo(baseX - perpX * 0.06f, baseY - perpY * 0.06f)
        loopTip.close()
    }

    /**
     * Draws an arrow of [size] pixels at (cx, cy). Every arrow uses the same navy ink, so the
     * picture never gives away whether it can move.
     */
    fun drawArrow(canvas: Canvas, cx: Float, cy: Float, size: Float, dir: Dir, color: Int) {
        val alpha = ((color ushr 24) and 0xFF) / 255f
        if (alpha <= 0f) return
        val core = color or (0xFF shl 24)
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(dir.degrees)
        canvas.scale(size, size)

        soft.color = Palette.withAlpha(Palette.INK, 0.10f * alpha)
        soft.strokeWidth = 0.18f
        canvas.save()
        canvas.translate(0.02f, 0.045f)
        canvas.drawPath(arrowPath, soft)
        canvas.restore()

        body.color = Palette.withAlpha(core, alpha)
        body.strokeWidth = 0.052f
        canvas.drawPath(arrowPath, body)

        canvas.restore()
    }

    /** Blue clockwise loop drawn around a wrapping arrow. Call before [drawArrow]. */
    fun drawWrapMark(canvas: Canvas, cx: Float, cy: Float, size: Float, dir: Dir) {
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(dir.degrees)
        canvas.scale(size, size)

        loop.color = Palette.withAlpha(Palette.BLUE, 0.85f)
        loop.strokeWidth = 0.05f
        canvas.drawArc(loopOval, 300f, 250f, false, loop)

        body.style = Paint.Style.FILL
        body.color = Palette.BLUE
        canvas.drawPath(loopTip, body)
        body.style = Paint.Style.FILL_AND_STROKE

        canvas.restore()
    }

    fun drawHeart(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(size, size)
        body.style = Paint.Style.FILL_AND_STROKE
        body.strokeWidth = 0.06f
        body.color = color
        canvas.drawPath(heartPath, body)
        canvas.restore()
    }

    /** Ring used for hints and the "this arrow is in the way" flash. */
    fun drawRing(canvas: Canvas, cx: Float, cy: Float, radius: Float, width: Float, color: Int) {
        soft.color = color
        soft.strokeWidth = width
        canvas.drawCircle(cx, cy, radius, soft)
    }

    fun dp(value: Float): Float = value * density
}
