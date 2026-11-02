package io.github.alex333333.bridge.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MouseCoalescerTest {
    @Test
    fun `the first move after construction is emitted at once`() {
        var time = 1000L
        val coalescer = MouseCoalescer({ time })
        assertEquals(InputEvent.MouseMove(3, -2), coalescer.add(3, -2))
    }

    @Test
    fun `an emission sums everything since the previous one`() {
        var time = 0L
        val coalescer = MouseCoalescer({ time }, intervalMillis = 16)
        coalescer.add(10, 10) // emitted at once
        time = 4
        assertNull(coalescer.add(2, 3))
        time = 8
        assertNull(coalescer.add(4, -1))
        time = 16
        assertEquals(InputEvent.MouseMove(7, 3), coalescer.add(1, 1))
    }

    @Test
    fun `nothing is emitted again until the interval has passed`() {
        var time = 0L
        val coalescer = MouseCoalescer({ time }, intervalMillis = 16)
        coalescer.add(1, 1)
        time = 15
        assertNull(coalescer.add(1, 1))
        time = 16
        assertEquals(InputEvent.MouseMove(1, 1), coalescer.add(0, 0))
    }

    @Test
    fun `flush emits the pending sum once`() {
        var time = 0L
        val coalescer = MouseCoalescer({ time })
        coalescer.add(1, 1)
        time = 3
        coalescer.add(2, 2)
        assertEquals(InputEvent.MouseMove(2, 2), coalescer.flush())
        assertNull(coalescer.flush())
    }

    @Test
    fun `flush without pending moves is null`() {
        assertNull(MouseCoalescer({ 0L }).flush())
    }

    @Test
    fun `a pending sum that cancels out is not emitted`() {
        var time = 0L
        val coalescer = MouseCoalescer({ time })
        coalescer.add(1, 1)
        time = 3
        coalescer.add(2, 2)
        coalescer.add(-2, -2)
        assertNull(coalescer.flush())
    }
}
