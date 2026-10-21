package com.arrowdrop.game

/**
 * One level in progress: the board plus the mistake and hint bookkeeping.
 * Pure Kotlin, no Android classes, so it is fully unit testable.
 */
class PuzzleSession(val spec: LevelSpec, arrows: List<Arrow> = Levels.generate(spec)) {

    val board = Board(spec.cols, spec.rows, arrows)
    val totalArrows = arrows.size

    var clearedCount = 0
        private set

    var taps = 0
        private set

    var misses = 0
        private set

    var hintsLeft = 2
        private set

    /** Lives bought back by watching a rewarded ad on this level. */
    var adLives = 0
        private set

    /** True once every arrow has left the board. */
    val cleared: Boolean get() = board.remaining == 0

    /** True once the player has used up all three mistakes. */
    val failed: Boolean get() = misses >= MAX_MISSES

    val hearts: Int get() = (MAX_MISSES - misses).coerceAtLeast(0)

    val progress: Float get() = if (totalArrows == 0) 1f else clearedCount.toFloat() / totalArrows

    fun tap(x: Int, y: Int): TapResult {
        if (cleared || failed) return TapResult.Empty
        val arrow = board.at(x, y) ?: return TapResult.Empty
        taps++

        val blocker = board.blockerOf(arrow)
        if (blocker != null) {
            misses++
            return TapResult.Blocked(arrow, blocker)
        }

        board.removeAt(x, y)
        clearedCount++
        return TapResult.Removed(arrow, cleared)
    }

    /**
     * Gives one heart back after a rewarded ad, but only once the player is actually out of lives,
     * so a stray reward callback cannot heal a level in progress. Returns false when there was
     * nothing to restore.
     */
    fun grantLife(): Boolean {
        if (!failed) return false
        misses--
        adLives++
        return true
    }

    /**
     * A removable arrow to point at, or null when the hint button is spent or nothing is left.
     * Prefers the arrow with the shortest straight lane so the hint is easy to follow.
     */
    fun revealHint(): Arrow? {
        if (hintsLeft <= 0 || cleared || failed) return null
        val candidates = board.removableArrows()
        if (candidates.isEmpty()) return null
        hintsLeft--
        return candidates.minWithOrNull(
            compareBy<Arrow> { board.exitDistance(it) }.thenBy { it.id }
        )
    }

    companion object {
        const val MAX_MISSES = 3
    }
}
