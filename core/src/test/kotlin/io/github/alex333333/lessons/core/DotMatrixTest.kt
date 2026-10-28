package io.github.alex333333.lessons.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DotMatrixTest {

    @Test
    fun `digits are five dots wide with one dot of spacing`() {
        val grid = DotMatrix.layout("23")
        assertEquals(DotMatrix.ROWS, grid.height)
        assertEquals(5 + 1 + 5, grid.width)
    }

    @Test
    fun `colon is a single narrow column`() {
        assertEquals(5 + 1 + 1 + 1 + 5 + 1 + 5, DotMatrix.layout("1:05").width)
    }

    @Test
    fun `one has its stem in the middle column`() {
        val grid = DotMatrix.layout("1")
        for (row in 0 until DotMatrix.ROWS - 1) assertTrue(grid[2, row])
        assertFalse(grid[0, 3])
    }

    @Test
    fun `unsupported characters render as blank space`() {
        val grid = DotMatrix.layout("?")
        assertEquals(5, grid.width)
        for (x in 0 until grid.width) for (y in 0 until grid.height) assertFalse(grid[x, y])
    }

    @Test
    fun `every glyph has the same height`() {
        DotMatrix.supported.forEach { assertEquals(DotMatrix.ROWS, DotMatrix.layout(it.toString()).height) }
    }
}
