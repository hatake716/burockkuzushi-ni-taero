package io.github.hatake716.taero

import kotlin.math.ceil

/** Display-only projection. Physics keeps its original coordinates and timing. */
internal class ArenaLayout(val width: Int, val height: Int, val target: Int) {
    companion object {
        fun minimumWidth(target: Int) = ceil(800.0 * (target + 1) / 98.0).toInt()
    }
    private val top = target / 3.0
    private val gridBottom = top + target * 5
    fun x(value: Double) = value * width / 800.0
    fun y(value: Double): Double = when {
        value < 82 -> value / 82 * top
        value < 412 -> top + (value - 82) / 66 * target
        else -> gridBottom + (value - 412) / 638 * (height - gridBottom)
    }
    fun cell(index: Int): Box {
        val column = index % 8
        val row = index / 8
        return Box(x(8.0 + column * 98), top + row * target,
            x(8.0 + (column + 1) * 98), top + (row + 1) * target)
    }
    fun block(index: Int): Box = GameEngine.blockBox(index).let { Box(x(it.left), y(it.top), x(it.right), y(it.bottom)) }
    fun hit(px: Float, py: Float): Int? = (0..39).firstOrNull {
        val b = cell(it)
        px >= b.left && px < b.right && py >= b.top && py < b.bottom
    }
}

/** Periodic spoken summaries use wall time, not the rendering or simulation rate. */
internal class StatusCadence(private val interval: Long = 5_000L) {
    private var previous = Long.MIN_VALUE
    fun reset(now: Long) { previous = now }
    fun due(now: Long): Boolean {
        if (previous != Long.MIN_VALUE && now - previous < interval) return false
        previous = now
        return true
    }
}
