package com.arrowdrop.game

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {

    @Test
    fun wrapsTurnClockwise() {
        assertEquals(Dir.RIGHT, Routes.turn(Dir.UP))
        assertEquals(Dir.DOWN, Routes.turn(Dir.RIGHT))
        assertEquals(Dir.LEFT, Routes.turn(Dir.DOWN))
        assertEquals(Dir.UP, Routes.turn(Dir.LEFT))
    }

    @Test
    fun plainRouteIsJustTheStraightLane() {
        val a = Arrow(0, 2, 0, Dir.RIGHT)
        assertEquals(listOf(3 to 0), Routes.cells(4, 4, a))
    }

    @Test
    fun wrapRouteRunsToTheEdgeThenAlongTheBorder() {
        val a = Arrow(0, 2, 0, Dir.RIGHT, wrap = true)
        assertEquals(
            listOf(3 to 0, 3 to 1, 3 to 2, 3 to 3),
            Routes.cells(4, 4, a)
        )
    }

    @Test
    fun wrapRouteTurnsBeforeTouchingItsOwnCell() {
        val a = Arrow(0, 2, 3, Dir.DOWN, wrap = true)
        assertEquals(listOf(1 to 3, 0 to 3), Routes.cells(4, 4, a))
    }

    @Test
    fun wrapRouteGoesRoundTheTopRightCorner() {
        val a = Arrow(0, 0, 0, Dir.UP, wrap = true)
        assertEquals(listOf(1 to 0, 2 to 0, 3 to 0), Routes.cells(4, 4, a))
    }

    @Test
    fun wrapRouteNeverVisitsACellTwice() {
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                for (dir in Dir.values()) {
                    val cells = Routes.cells(4, 4, Arrow(0, x, y, dir, wrap = true))
                    assertEquals("$x,$y $dir repeats a cell", cells.size, cells.toSet().size)
                    assertEquals("$x,$y $dir includes its own cell", false, cells.contains(x to y))
                }
            }
        }
    }
}
