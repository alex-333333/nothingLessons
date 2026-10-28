package io.github.alex333333.lessons.core

/**
 * A tiny 5×7 dot-matrix "font" for the countdown digits and the app title, in the spirit of Nothing's
 * dot typefaces. Each glyph is a list of rows; '#' is a lit dot.
 */
object DotMatrix {
    const val ROWS = 7

    private val glyphs: Map<Char, List<String>> = mapOf(
        '0' to listOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
        '1' to listOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
        '2' to listOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
        '3' to listOf("####.", "....#", "....#", ".###.", "....#", "....#", "####."),
        '4' to listOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
        '5' to listOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
        '6' to listOf("..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."),
        '7' to listOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
        '8' to listOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
        '9' to listOf(".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."),
        ':' to listOf(".", ".", "#", ".", "#", ".", "."),
        '-' to listOf(".....", ".....", ".....", "#####", ".....", ".....", "....."),
        // Cyrillic capitals for the app title.
        'У' to listOf("#...#", "#...#", "#...#", ".####", "....#", "#...#", ".###."),
        'Р' to listOf("####.", "#...#", "#...#", "####.", "#....", "#....", "#...."),
        'О' to listOf(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
        'К' to listOf("#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"),
        'И' to listOf("#...#", "#...#", "#..##", "#.#.#", "##..#", "#...#", "#...#"),
        ' ' to listOf(".....", ".....", ".....", ".....", ".....", ".....", "....."),
    )

    val supported: Set<Char> get() = glyphs.keys

    /** Lays out [text] left to right with one blank column between glyphs. */
    fun layout(text: String): DotGrid {
        val parts = text.map { glyphs[it] ?: glyphs.getValue(' ') }
        val width = parts.sumOf { it.first().length } + (parts.size - 1).coerceAtLeast(0)
        val dots = BooleanArray(width * ROWS)
        var x0 = 0
        for (glyph in parts) {
            for ((y, row) in glyph.withIndex()) {
                for ((dx, c) in row.withIndex()) if (c == '#') dots[y * width + x0 + dx] = true
            }
            x0 += glyph.first().length + 1
        }
        return DotGrid(width, ROWS, dots)
    }
}

class DotGrid(val width: Int, val height: Int, private val dots: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = dots[y * width + x]
}
