package com.arrowdrop.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardTest {

    private var nextId = 0

    private fun arrow(x: Int, y: Int, dir: Dir, wrap: Boolean = false) =
        Arrow(nextId++, x, y, dir, wrap)

    @Test
    fun arrowAtTheEdgeCanAlwaysLeave() {
        val a = arrow(0, 2, Dir.LEFT)
        val board = Board(3, 3, listOf(a))
        assertNull(board.blockerOf(a))
        assertTrue(board.canRemove(a))
    }

    @Test
    fun arrowWithAClearLaneLeavesTheBoard() {
        val a = arrow(1, 1, Dir.RIGHT)
        val board = Board(4, 4, listOf(a))
        assertNull(board.blockerOf(a))
        assertSame(a, board.removeAt(1, 1))
        assertEquals(0, board.remaining)
        assertNull(board.at(1, 1))
    }

    @Test
    fun arrowPointingAtAnotherArrowIsBlocked() {
        val front = arrow(2, 1, Dir.RIGHT)
        val back = arrow(1, 1, Dir.RIGHT)
        val board = Board(3, 3, listOf(front, back))

        assertSame(front, board.blockerOf(back))
        assertFalse(board.canRemove(back))
        assertNull(board.removeAt(1, 1))
        assertEquals(2, board.remaining)
    }

    @Test
    fun clearingTheBlockerOpensTheLane() {
        val front = arrow(2, 1, Dir.UP)
        val back = arrow(0, 1, Dir.RIGHT)
        val board = Board(3, 3, listOf(front, back))

        assertSame(front, board.blockerOf(back))
        board.removeAt(2, 1)
        assertNull(board.blockerOf(back))
        assertSame(back, board.removeAt(0, 1))
        assertEquals(0, board.remaining)
    }

    @Test
    fun arrowsOnlyBlockAlongTheirOwnRowOrColumn() {
        val diagonal = arrow(2, 2, Dir.UP)
        val a = arrow(0, 0, Dir.RIGHT)
        val board = Board(3, 3, listOf(diagonal, a))
        assertNull(board.blockerOf(a))
    }

    @Test
    fun removingAnEmptyCellDoesNothing() {
        val board = Board(3, 3, listOf(arrow(1, 1, Dir.UP)))
        assertNull(board.removeAt(0, 0))
        assertEquals(1, board.remaining)
    }

    @Test
    fun restorePutsAnArrowBack() {
        val a = arrow(1, 1, Dir.DOWN)
        val board = Board(3, 3, listOf(a))
        board.removeAt(1, 1)
        assertEquals(0, board.remaining)
        assertTrue(board.restore(a))
        assertEquals(1, board.remaining)
        assertSame(a, board.at(1, 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun twoArrowsCannotShareACell() {
        Board(2, 2, listOf(arrow(1, 1, Dir.UP), arrow(1, 1, Dir.DOWN)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun arrowsCannotSitOffTheBoard() {
        Board(2, 2, listOf(arrow(2, 0, Dir.UP)))
    }

    @Test
    fun exitDistanceCountsCellsUpToTheEdge() {
        val board = Board(5, 4, emptyList())
        assertEquals(1, board.exitDistance(Arrow(0, 0, 0, Dir.UP)))
        assertEquals(5, board.exitDistance(Arrow(1, 0, 0, Dir.RIGHT)))
        assertEquals(4, board.exitDistance(Arrow(2, 3, 0, Dir.LEFT)))
        assertEquals(4, board.exitDistance(Arrow(3, 0, 0, Dir.DOWN)))
        assertEquals(3, board.exitDistance(Arrow(4, 4, 1, Dir.DOWN)))
    }

    @Test
    fun copyKeepsOnlyArrowsStillOnTheBoard() {
        val a = arrow(0, 0, Dir.RIGHT)
        val b = arrow(1, 0, Dir.RIGHT)
        val board = Board(3, 3, listOf(a, b))
        assertSame(b, board.removeAt(1, 0))
        val copy = board.copy()
        assertEquals(1, copy.remaining)
        assertSame(a, copy.at(0, 0))
    }

    @Test
    fun wrapArrowIsBlockedByAnArrowAroundTheCorner() {
        val wrapper = arrow(1, 1, Dir.RIGHT, wrap = true)
        val cornerBlocker = arrow(3, 3, Dir.UP)
        val board = Board(4, 4, listOf(wrapper, cornerBlocker))

        assertSame(cornerBlocker, board.blockerOf(wrapper))
        assertFalse(board.canRemove(wrapper))
        assertTrue(board.canRemove(cornerBlocker))

        board.removeAt(3, 3)
        assertTrue(board.canRemove(wrapper))
    }

    @Test
    fun plainArrowIgnoresTheBorderStretch() {
        val plain = arrow(1, 1, Dir.RIGHT)
        val border = arrow(3, 3, Dir.UP)
        val board = Board(4, 4, listOf(plain, border))

        assertNull(board.blockerOf(plain))
        assertTrue(board.canRemove(plain))
    }

    @Test
    fun wrapArrowOnTheEdgeStillTurnsTheCorner() {
        val wrapper = arrow(3, 1, Dir.RIGHT, wrap = true)
        val borderBlocker = arrow(3, 3, Dir.UP)
        val board = Board(4, 4, listOf(wrapper, borderBlocker))

        assertSame(borderBlocker, board.blockerOf(wrapper))
        assertFalse(board.canRemove(wrapper))
    }

    @Test
    fun wrapArrowInTheCornerHasAnEmptyRoute() {
        val wrapper = arrow(3, 3, Dir.RIGHT, wrap = true)
        val board = Board(4, 4, listOf(wrapper))
        assertTrue(board.canRemove(wrapper))
    }

    @Test
    fun straightArrowIsMetBeforeTheBorderStretch() {
        val wrapper = arrow(1, 1, Dir.RIGHT, wrap = true)
        val straight = arrow(2, 1, Dir.LEFT)
        val border = arrow(3, 2, Dir.LEFT)
        val board = Board(4, 4, listOf(wrapper, straight, border))

        assertSame(straight, board.blockerOf(wrapper))
    }

    @Test
    fun copyKeepsTheWrapFlag() {
        val wrapper = arrow(1, 1, Dir.DOWN, wrap = true)
        val copy = Board(4, 4, listOf(wrapper)).copy()
        assertTrue(copy.at(1, 1)!!.wrap)
    }

    @Test
    fun removableArrowsFindsEveryOpenLane() {
        val a = arrow(0, 0, Dir.RIGHT)
        val b = arrow(2, 0, Dir.RIGHT) // free lane to the right edge
        val c = arrow(1, 3, Dir.DOWN) // one cell from the bottom edge
        val board = Board(4, 4, listOf(a, b, c))

        assertSame(b, board.blockerOf(a))
        val removable = board.removableArrows().map { it.id }.toSet()
        assertEquals(setOf(b.id, c.id), removable)
    }
}
