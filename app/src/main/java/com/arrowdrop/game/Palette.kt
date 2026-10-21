package com.arrowdrop.game

/** Colours for the whole game in one place: warm paper, navy ink, blue accents, red lives. */
object Palette {
    const val BG_TOP = 0xFFFBF8F2.toInt()
    const val BG_BOTTOM = 0xFFEDF2FA.toInt()
    const val PANEL = 0xFFFFFFFF.toInt()
    const val PANEL_STROKE = 0xFFE5EAF3.toInt()
    const val CELL = 0xFFF3F6FC.toInt()
    const val CELL_EMPTY = 0xFFFCFDFF.toInt()
    const val INK = 0xFF1B2340.toInt()
    const val ARROW = 0xFF232B48.toInt()
    const val TEXT = 0xFF1B2340.toInt()
    const val TEXT_DIM = 0xFF8A93A8.toInt()
    const val BLUE = 0xFF2E86ED.toInt()
    const val BLUE_SOFT = 0xFFDCEBFF.toInt()
    const val RED = 0xFFE8505B.toInt()
    const val TRACK = 0xFFE6ECF5.toInt()

    /** Same colour at a new alpha (0..1). */
    fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        return (color and 0x00FFFFFF) or (a shl 24)
    }
}
