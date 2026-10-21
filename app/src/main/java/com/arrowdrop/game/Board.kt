package com.arrowdrop.game

/**
 * A rectangular grid of arrows. An arrow can leave the board only when nothing sits on its route:
 * the straight lane to the edge for a plain arrow, plus the border stretch to the corner for a
 * wrapping arrow. Until then it is stuck behind whatever is in the way.
 */
class Board(val cols: Int, val rows: Int, arrows: List<Arrow>) {

    private val grid = arrayOfNulls<Arrow>(cols * rows)
    private val all = ArrayList<Arrow>(arrows.size)

    /** Arrows still on the board. */
    var remaining: Int = 0
        private set

    init {
        require(cols > 0 && rows > 0) { "board must be at least 1x1" }
        for (a in arrows) {
            require(inBounds(a.x, a.y)) { "arrow ${a.id} at ${a.x},${a.y} is off a ${cols}x${rows} board" }
            val index = a.y * cols + a.x
            require(grid[index] == null) { "two arrows on cell ${a.x},${a.y}" }
            grid[index] = a
            all.add(a)
        }
        remaining = all.size
    }

    fun inBounds(x: Int, y: Int): Boolean = x in 0 until cols && y in 0 until rows

    fun at(x: Int, y: Int): Arrow? = if (inBounds(x, y)) grid[y * cols + x] else null

    /** The [Arrow]s still on the board, in a stable order. */
    fun arrows(): List<Arrow> = all.filter { at(it.x, it.y)?.id == it.id }

    /** Does this arrow still sit on the board? */
    fun isOnBoard(a: Arrow): Boolean = at(a.x, a.y)?.id == a.id

    /**
     * The first arrow standing on [a]'s exit route, or null when the way out is clear.
     * The arrow's own cell never counts as a blocker.
     */
    fun blockerOf(a: Arrow): Arrow? {
        var found: Arrow? = null
        Routes.walk(cols, rows, a.x, a.y, a.dir, a.wrap) { x, y ->
            val hit = at(x, y)
            if (hit != null) {
                found = hit
                true
            } else {
                false
            }
        }
        return found
    }

    fun canRemove(a: Arrow): Boolean = isOnBoard(a) && blockerOf(a) == null

    /** Removes the arrow on that cell when its route is clear. */
    fun removeAt(x: Int, y: Int): Arrow? {
        val a = at(x, y) ?: return null
        if (blockerOf(a) != null) return null
        grid[y * cols + x] = null
        remaining--
        return a
    }

    /** Puts a previously removed arrow back (used by the tests and by a fresh level reset). */
    fun restore(a: Arrow): Boolean {
        if (!inBounds(a.x, a.y)) return false
        val index = a.y * cols + a.x
        if (grid[index] != null) return false
        grid[index] = a
        remaining++
        if (all.none { it.id == a.id }) all.add(a)
        return true
    }

    /** Cells this arrow crosses before it is out, including the one it stands on. */
    fun exitDistance(a: Arrow): Int = when (a.dir) {
        Dir.UP -> a.y + 1
        Dir.DOWN -> rows - a.y
        Dir.LEFT -> a.x + 1
        Dir.RIGHT -> cols - a.x
    }

    /** Every arrow on the board that can leave right now. */
    fun removableArrows(): List<Arrow> = arrows().filter { blockerOf(it) == null }

    fun copy(): Board = Board(cols, rows, arrows())

    companion object {
        /** Loads a level's arrows onto a fresh board. */
        fun of(spec: LevelSpec): Board = Board(spec.cols, spec.rows, Levels.generate(spec))
    }
}
