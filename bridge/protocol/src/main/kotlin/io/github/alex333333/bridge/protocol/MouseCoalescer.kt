package io.github.alex333333.bridge.protocol

/**
 * Merges the many small finger movements of a touchpad into at most one [InputEvent.MouseMove] per
 * [intervalMillis]. [now] is a monotonic clock in milliseconds.
 */
class MouseCoalescer(private val now: () -> Long, private val intervalMillis: Long = 16) {
    private var pendingX = 0
    private var pendingY = 0
    private var lastEmit: Long? = null

    /** Adds a movement. Returns the summed move if the interval has passed since the last one, otherwise null. */
    @Synchronized
    fun add(dx: Int, dy: Int): InputEvent.MouseMove? {
        pendingX += dx
        pendingY += dy
        val time = now()
        val last = lastEmit
        if (last != null && time - last < intervalMillis) return null
        return take()?.also { lastEmit = time }
    }

    /** The pending sum right now (for example when the finger lifts), or null if there is nothing to send. */
    @Synchronized
    fun flush(): InputEvent.MouseMove? = take()?.also { lastEmit = now() }

    private fun take(): InputEvent.MouseMove? {
        if (pendingX == 0 && pendingY == 0) return null
        val move = InputEvent.MouseMove(pendingX, pendingY)
        pendingX = 0
        pendingY = 0
        return move
    }
}
