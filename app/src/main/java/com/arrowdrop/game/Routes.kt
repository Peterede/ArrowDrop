package com.arrowdrop.game

/**
 * Exit routes.
 *
 * A plain arrow crosses the cells in front of it and leaves through that edge. A wrapping arrow
 * leaves through the edge too, but instead of escaping it turns clockwise and runs along the border
 * until the next corner, so the border stretch behind the edge is part of its lane as well.
 *
 * The walker is shared by the board rules, the generator and the removal animation, so all three
 * always agree on what is in the way.
 */
object Routes {

    /** The direction a wrapping arrow turns to when it reaches the board edge. */
    fun turn(dir: Dir): Dir = when (dir) {
        Dir.UP -> Dir.RIGHT
        Dir.RIGHT -> Dir.DOWN
        Dir.DOWN -> Dir.LEFT
        Dir.LEFT -> Dir.UP
    }

    /**
     * Visits every cell on the route in travel order, starting in front of the arrow and excluding
     * its own cell. Stops early when [visit] returns true.
     */
    inline fun walk(
        cols: Int,
        rows: Int,
        x0: Int,
        y0: Int,
        dir: Dir,
        wrap: Boolean,
        visit: (x: Int, y: Int) -> Boolean
    ) {
        var x = x0 + dir.dx
        var y = y0 + dir.dy
        while (x in 0 until cols && y in 0 until rows) {
            if (visit(x, y)) return
            x += dir.dx
            y += dir.dy
        }
        if (!wrap) return

        // Clockwise turn at the edge: slide along the border towards the next corner.
        when (dir) {
            Dir.RIGHT -> for (yy in y0 + 1 until rows) if (visit(cols - 1, yy)) return
            Dir.DOWN -> for (xx in x0 - 1 downTo 0) if (visit(xx, rows - 1)) return
            Dir.LEFT -> for (yy in y0 - 1 downTo 0) if (visit(0, yy)) return
            Dir.UP -> for (xx in x0 + 1 until cols) if (visit(xx, 0)) return
        }
    }

    /** Every cell on the route, in travel order. Handy for tests and debugging. */
    fun cells(cols: Int, rows: Int, arrow: Arrow): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        walk(cols, rows, arrow.x, arrow.y, arrow.dir, arrow.wrap) { x, y ->
            out.add(x to y)
            false
        }
        return out
    }
}
