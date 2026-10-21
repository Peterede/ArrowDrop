package com.arrowdrop.game

/** The four directions an arrow can point. [degrees] is the rotation used to draw it. */
enum class Dir(val dx: Int, val dy: Int, val degrees: Float) {
    UP(0, -1, 0f),
    RIGHT(1, 0, 90f),
    DOWN(0, 1, 180f),
    LEFT(-1, 0, 270f);

    val isHorizontal: Boolean get() = this == RIGHT || this == LEFT
}

/**
 * One arrow sitting on a cell of the board.
 *
 * A plain arrow slides in [dir] until it leaves the board. A [wrap] arrow that reaches the edge
 * keeps going: it turns clockwise and runs along the border to the next corner before leaving, so
 * arrows on that border stretch stand in its way too.
 */
data class Arrow(val id: Int, val x: Int, val y: Int, val dir: Dir, val wrap: Boolean = false)

/** A level layout request: board size, how many arrows to place and a seed for the layout. */
data class LevelSpec(
    val number: Int,
    val cols: Int,
    val rows: Int,
    val arrowCount: Int,
    val seed: Long
) {
    val cells: Int get() = cols * rows
}

/** What happened when the player touched a cell. */
sealed class TapResult {
    /** Nothing on that cell. No effect at all. */
    object Empty : TapResult()

    /** The arrow is boxed in by [blocker]; it stays on the board. */
    data class Blocked(val arrow: Arrow, val blocker: Arrow) : TapResult()

    /** The arrow had a clear path and left the board. */
    data class Removed(
        val arrow: Arrow,
        val levelCleared: Boolean
    ) : TapResult()
}
