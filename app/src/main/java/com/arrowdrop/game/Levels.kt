package com.arrowdrop.game

import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Level curve and layout generator.
 *
 * Layouts are built by placing arrows one at a time onto an empty board, always choosing a cell and
 * a direction whose whole exit route is clear *at that moment*. Because later arrows never sit on
 * an earlier arrow's route at the time it was placed, replaying the placements backwards is always
 * a valid clearing order, so every generated level is solvable. Removing an arrow can only ever
 * open space, which means a player cannot get a level into a dead end either.
 *
 * Some arrows are generated as wrapping arrows: their route bends around the board edge, so they
 * cross two lanes and stay stuck until both are clear.
 */
object Levels {

    /** Levels per board-size chapter. */
    // (MAX_SIZE - 4) chapters take the board from 4x4 to 7x7.
    const val CHAPTER = 8

    /** Largest board, in cells per side. */
    const val MAX_SIZE = 7

    fun specFor(level: Int): LevelSpec {
        require(level >= 1) { "levels start at 1" }
        val chapter = (level - 1) / CHAPTER
        val step = (level - 1) % CHAPTER
        val size = minOf(4 + chapter, MAX_SIZE)
        val cells = size * size
        // Density ramps from 52% to 73% inside each chapter, then drops back for a bigger board.
        // There is no board bigger than 7x7, so later chapters hold the top density instead of
        // resetting to 52% and making the game suddenly easier again.
        val density =
            if (chapter <= MAX_SIZE - 4) 0.52f + 0.03f * step
            else 0.73f
        val target = (cells * density).roundToInt().coerceAtLeast(6)
        return LevelSpec(
            number = level,
            cols = size,
            rows = size,
            arrowCount = minOf(target, cells),
            seed = 1_000_003L * level + 7919L
        )
    }

    /** How likely a new arrow is to wrap, once wrapping has been introduced. */
    fun wrapChanceFor(level: Int): Float =
        if (level <= 1) 0f else (0.10f + 0.005f * level).coerceAtMost(0.35f)

    fun generate(level: Int): List<Arrow> = generate(specFor(level))

    /**
     * Builds the layout for [spec]. Greedy placement can box itself out an arrow or two short on
     * dense boards, so a few deterministic alternate seeds are tried and the fullest layout wins.
     */
    fun generate(spec: LevelSpec): List<Arrow> {
        var best = attempt(spec, spec.seed)
        var tries = 1
        while (best.size < spec.arrowCount && tries < ATTEMPTS) {
            val next = attempt(spec, spec.seed + RESEED * tries)
            if (next.size > best.size) best = next
            tries++
        }
        return best
    }

    private fun attempt(spec: LevelSpec, seed: Long): List<Arrow> {
        val rng = Random(seed)
        val occupied = BooleanArray(spec.cells)
        val out = ArrayList<Arrow>(spec.arrowCount)
        val wrapChance = wrapChanceFor(spec.number)
        val plain = ArrayList<Dir>(4)
        val wrapping = ArrayList<Dir>(4)
        val free = ArrayList<Int>(spec.cells)
        var id = 0
        // From level 4 on, the first chance to place a wrapping arrow is taken, so the mechanic
        // always shows up; after that wrapping is random.
        var wantsGuaranteedWrap = spec.number >= 4

        while (out.size < spec.arrowCount) {
            free.clear()
            for (cell in 0 until spec.cells) if (!occupied[cell]) free.add(cell)

            // Try random free cells until one can hold a new arrow. A cell that cannot be used
            // now can never be used later either (arrows are only added, so lanes only close),
            // so it is dropped from this round's candidate list. This keeps filling the board
            // even when the last few cells are almost boxed in.
            var placed = false
            while (free.isNotEmpty()) {
                val pick = rng.nextInt(free.size)
                val cell = free[pick]
                val x = cell % spec.cols
                val y = cell / spec.cols

                plain.clear()
                wrapping.clear()
                for (dir in Dir.values()) {
                    if (!routeClear(occupied, spec, x, y, dir, wrap = false)) continue
                    plain.add(dir)
                    if (wrapChance > 0f &&
                        addsBorderCells(dir, x, y, spec.cols, spec.rows) &&
                        routeClear(occupied, spec, x, y, dir, wrap = true)
                    ) {
                        wrapping.add(dir)
                    }
                }
                if (plain.isEmpty()) {
                    free.removeAt(pick)
                    continue
                }

                val wrap = when {
                    wrapping.isEmpty() -> false
                    wantsGuaranteedWrap -> {
                        wantsGuaranteedWrap = false
                        true
                    }
                    else -> rng.nextFloat() < wrapChance
                }
                val dir = if (wrap) {
                    wrapping[rng.nextInt(wrapping.size)]
                } else {
                    plain[rng.nextInt(plain.size)]
                }

                occupied[cell] = true
                out.add(Arrow(id = id++, x = x, y = y, dir = dir, wrap = wrap))
                placed = true
                break
            }
            if (!placed) break
        }
        return out
    }

    /** Alternate seeds tried when a dense board cannot be filled to [LevelSpec.arrowCount]. */
    private const val ATTEMPTS = 12

    /** Seed step between those attempts. */
    private const val RESEED = 7_919L

    /** A wrap only counts when the clockwise turn actually adds border cells to the route. */
    private fun addsBorderCells(dir: Dir, x: Int, y: Int, cols: Int, rows: Int): Boolean = when (dir) {
        Dir.RIGHT -> y < rows - 1
        Dir.DOWN -> x > 0
        Dir.LEFT -> y > 0
        Dir.UP -> x < cols - 1
    }

    /** True when no placed arrow stands on the route (straight, plus the border turn when wrapping). */
    private fun routeClear(
        occupied: BooleanArray,
        spec: LevelSpec,
        x: Int,
        y: Int,
        dir: Dir,
        wrap: Boolean
    ): Boolean {
        var clear = true
        Routes.walk(spec.cols, spec.rows, x, y, dir, wrap) { cx, cy ->
            if (occupied[cy * spec.cols + cx]) {
                clear = false
                true
            } else {
                false
            }
        }
        return clear
    }
}
