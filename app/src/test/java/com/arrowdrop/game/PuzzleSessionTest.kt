package com.arrowdrop.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleSessionTest {

    private var nextId = 0

    private fun arrow(x: Int, y: Int, dir: Dir) = Arrow(nextId++, x, y, dir)

    private fun spec(cols: Int, rows: Int) =
        LevelSpec(number = 1, cols = cols, rows = rows, arrowCount = cols * rows, seed = 0L)

    @Test
    fun tappingAFreeArrowRemovesIt() {
        val a = arrow(0, 0, Dir.RIGHT)
        val session = PuzzleSession(spec(2, 2), listOf(a))

        val result = session.tap(0, 0)
        assertTrue(result is TapResult.Removed)
        assertEquals(0, session.board.remaining)
        assertEquals(1, session.taps)
        assertEquals(1, session.clearedCount)
        assertTrue(session.cleared)
    }

    @Test
    fun tappingAnEmptyCellIsFree() {
        val session = PuzzleSession(spec(4, 4), listOf(arrow(0, 0, Dir.RIGHT)))
        assertEquals(TapResult.Empty, session.tap(3, 3))
        assertEquals(0, session.taps)
        assertEquals(0, session.misses)
    }

    @Test
    fun blockedTapCostsAHeart() {
        val front = arrow(1, 0, Dir.LEFT)
        val back = arrow(0, 0, Dir.RIGHT)
        val session = PuzzleSession(spec(2, 1), listOf(front, back))

        val result = session.tap(0, 0)
        assertTrue(result is TapResult.Blocked)
        assertEquals(front, (result as TapResult.Blocked).blocker)
        assertEquals(1, session.misses)
        assertEquals(2, session.hearts)
        assertEquals(2, session.board.remaining)
        assertEquals(1, session.taps)
    }

    @Test
    fun threeMistakesEndTheLevelAndFurtherTapsDoNothing() {
        val front = arrow(1, 0, Dir.LEFT)
        val back = arrow(0, 0, Dir.RIGHT)
        val session = PuzzleSession(spec(2, 1), listOf(front, back))

        repeat(3) { session.tap(0, 0) }
        assertTrue(session.failed)
        assertEquals(0, session.hearts)
        assertEquals(3, session.misses)

        assertEquals(TapResult.Empty, session.tap(0, 0))
        assertEquals(3, session.taps)
        assertEquals(2, session.board.remaining)
    }

    @Test
    fun aMistakeDoesNotThrowAwayClearedProgress() {
        val a = arrow(0, 0, Dir.UP)
        val front = arrow(2, 1, Dir.LEFT)
        val back = arrow(1, 1, Dir.RIGHT)
        val session = PuzzleSession(spec(3, 2), listOf(a, front, back))

        assertTrue(session.tap(0, 0) is TapResult.Removed)
        assertTrue(session.tap(1, 1) is TapResult.Blocked)
        assertEquals(1, session.clearedCount)
        assertEquals(3, session.totalArrows)
    }

    @Test
    fun progressReachesOneWhenTheBoardIsEmpty() {
        val session = PuzzleSession(spec(2, 2), listOf(arrow(0, 0, Dir.DOWN)))
        assertEquals(0f, session.progress)
        session.tap(0, 0)
        assertEquals(1f, session.progress)
    }

    @Test
    fun hintPointsAtARemovableArrowAndRunsOut() {
        val session = PuzzleSession(Levels.specFor(6))

        val first = session.revealHint()
        assertNotNull(first)
        assertNull(session.board.blockerOf(first!!))
        assertEquals(1, session.hintsLeft)

        val second = session.revealHint()
        assertNotNull(second)
        assertEquals(0, session.hintsLeft)

        assertNull(session.revealHint())
        assertNull(session.revealHint())
    }

    @Test
    fun hintChoosesTheShortestLane() {
        val near = arrow(2, 0, Dir.RIGHT) // one cell to the edge
        val far = arrow(0, 0, Dir.DOWN) // four cells to the edge
        val session = PuzzleSession(spec(4, 4), listOf(far, near))
        assertEquals(near, session.revealHint())
    }

    @Test
    fun aWatchedAdGivesOneHeartBackAndResumesPlay() {
        val front = arrow(1, 0, Dir.LEFT)
        val back = arrow(0, 0, Dir.RIGHT)
        val session = PuzzleSession(spec(2, 1), listOf(front, back))
        repeat(3) { session.tap(0, 0) }
        assertTrue(session.failed)

        assertTrue(session.grantLife())
        assertEquals(1, session.hearts)
        assertEquals(2, session.misses)
        assertEquals(1, session.adLives)
        assertFalse(session.failed)

        // The level is playable again, and the same arrow still blocks.
        assertEquals(3, session.taps)
        assertTrue(session.tap(0, 0) is TapResult.Blocked)
        assertEquals(4, session.taps)
        assertEquals(0, session.hearts)
    }

    @Test
    fun anAdCannotHealAPlayerWhoIsStillAlive() {
        val front = arrow(1, 0, Dir.LEFT)
        val back = arrow(0, 0, Dir.RIGHT)
        val session = PuzzleSession(spec(2, 1), listOf(front, back))

        assertFalse(session.grantLife())
        assertEquals(0, session.adLives)
        assertEquals(3, session.hearts)

        session.tap(0, 0)
        assertFalse(session.grantLife())
        assertEquals(2, session.hearts)
    }

    @Test
    fun everyTripToZeroLivesCanBeBoughtBack() {
        val front = arrow(1, 0, Dir.LEFT)
        val back = arrow(0, 0, Dir.RIGHT)
        val session = PuzzleSession(spec(2, 1), listOf(front, back))

        repeat(2) { round ->
            val missesToLose = 3 - session.misses
            repeat(missesToLose) { session.tap(0, 0) }
            assertTrue("round $round", session.failed)
            assertTrue(session.grantLife())
            assertEquals(1, session.hearts)
        }
        assertEquals(2, session.adLives)
        assertEquals(1, session.hearts)
    }

    @Test
    fun anAdLeavesTheBoardAlone() {
        val front = arrow(1, 0, Dir.LEFT)
        val back = arrow(0, 0, Dir.RIGHT)
        val free = arrow(0, 1, Dir.LEFT)
        val session = PuzzleSession(spec(2, 2), listOf(front, back, free))
        assertTrue(session.tap(0, 1) is TapResult.Removed)
        repeat(3) { session.tap(0, 0) }
        assertTrue(session.failed)

        assertTrue(session.grantLife())
        assertEquals(2, session.board.remaining)
        assertEquals(1, session.clearedCount)
    }

    @Test
    fun heartsDropWithMistakesButStopAtZero() {
        val front = arrow(1, 0, Dir.LEFT)
        val back = arrow(0, 0, Dir.RIGHT)
        val session = PuzzleSession(spec(2, 1), listOf(front, back))
        assertEquals(3, session.hearts)

        session.tap(0, 0)
        assertEquals(2, session.hearts)
        session.tap(0, 0)
        assertEquals(1, session.hearts)
        session.tap(0, 0)
        assertEquals(0, session.hearts)
        assertFalse(session.cleared)
    }
}
