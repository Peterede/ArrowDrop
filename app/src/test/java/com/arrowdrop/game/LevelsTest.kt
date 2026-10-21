package com.arrowdrop.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelsTest {

    @Test
    fun boardSizeGrowsByChapter() {
        assertEquals(4, Levels.specFor(1).cols)
        assertEquals(4, Levels.specFor(8).cols)
        assertEquals(5, Levels.specFor(9).cols)
        assertEquals(6, Levels.specFor(17).cols)
        assertEquals(7, Levels.specFor(25).cols)
        assertEquals(Levels.MAX_SIZE, Levels.specFor(400).cols)
        assertEquals(Levels.MAX_SIZE, Levels.specFor(400).rows)
    }

    @Test
    fun densityRampsInsideAChapterAndResetsOnTheNextBoard() {
        val first = Levels.specFor(1)
        val last = Levels.specFor(8)
        assertTrue(last.arrowCount > first.arrowCount)

        val newChapter = Levels.specFor(9)
        assertEquals(5, newChapter.cols)
        assertTrue(newChapter.arrowCount > last.arrowCount)
    }

    @Test
    fun difficultyKeepsRisingOnceTheBoardStopsGrowing() {
        var previous = 0
        for (level in 25..80) {
            val count = Levels.specFor(level).arrowCount
            assertTrue("level $level dropped from $previous to $count arrows", count >= previous)
            previous = count
        }
    }

    @Test
    fun specsAreSquareAndWithinTheBoard() {
        for (level in 1..200) {
            val spec = Levels.specFor(level)
            assertEquals(spec.cols, spec.rows)
            assertTrue("level $level arrow count", spec.arrowCount in 1..spec.cells)
        }
    }

    @Test
    fun generationIsDeterministicForALevel() {
        val a = Levels.generate(12)
        val b = Levels.generate(12)
        assertEquals(a, b)
    }

    @Test
    fun differentLevelsGetDifferentLayouts() {
        val a = Levels.generate(3)
        val b = Levels.generate(4)
        assertNotEquals(a, b)
    }

    @Test
    fun everyGeneratedLevelFitsOnTheBoard() {
        for (level in 1..120) {
            val spec = Levels.specFor(level)
            val arrows = Levels.generate(spec)
            val cells = HashSet<Pair<Int, Int>>()
            for (a in arrows) {
                assertTrue(a.x in 0 until spec.cols && a.y in 0 until spec.rows)
                assertTrue("duplicate cell at level $level", cells.add(a.x to a.y))
            }
            assertEquals(arrows.size, arrows.map { it.id }.toSet().size)
        }
    }

    @Test
    fun generatedLevelsAreAlwaysSolvable() {
        for (level in 1..150) {
            val board = Board.of(Levels.specFor(level))
            assertTrue("level $level started empty", board.remaining > 0)
            // Removing an arrow only opens space, so any greedy order must clear the board.
            while (true) {
                val next = board.removableArrows().firstOrNull() ?: break
                board.removeAt(next.x, next.y)
            }
            assertEquals("level $level got stuck", 0, board.remaining)
        }
    }

    @Test
    fun levelsUseMoreThanOneDirection() {
        for (level in 1..30) {
            val dirs = Levels.generate(level).map { it.dir }.toSet()
            assertTrue("level $level has only $dirs", dirs.size >= 2)
        }
    }

    @Test
    fun fullBoardsHaveSomeArrowsThatCanLeave() {
        for (level in 1..60) {
            val board = Board.of(Levels.specFor(level))
            assertTrue("level $level has no move", board.removableArrows().isNotEmpty())
        }
    }

    @Test
    fun firstLevelHasNoWrappingArrows() {
        assertEquals(0f, Levels.wrapChanceFor(1))
        assertTrue(Levels.generate(1).none { it.wrap })
    }

    @Test
    fun laterLevelsIntroduceWrappingArrows() {
        val wraps = (2..40).sumOf { level -> Levels.generate(level).count { it.wrap } }
        assertTrue("expected wrapping arrows above level 1, found $wraps", wraps > 20)
    }

    @Test
    fun everyLevelFromFourHasAtLeastOneWrappingArrow() {
        for (level in 4..60) {
            assertTrue("level $level has no wrapping arrow", Levels.generate(level).any { it.wrap })
        }
    }

    @Test
    fun wrappingChanceRisesWithTheLevel() {
        assertTrue(Levels.wrapChanceFor(20) > Levels.wrapChanceFor(2))
        assertTrue(Levels.wrapChanceFor(500) <= 0.35f)
    }

    @Test
    fun generatedArrowCountMatchesTheSpecForEarlyLevels() {
        for (level in 1..40) {
            val spec = Levels.specFor(level)
            val arrows = Levels.generate(spec)
            assertEquals("level $level", spec.arrowCount, arrows.size)
        }
    }
}
