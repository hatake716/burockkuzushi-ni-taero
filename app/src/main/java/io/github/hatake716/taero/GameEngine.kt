package io.github.hatake716.taero

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.*
import kotlin.random.Random

enum class SlotEffect {
    ADD_BALLS, SPEED_DOUBLE, SPEED_TRIPLE, PIERCE, SPLIT, RESET_BALLS, RESET_SPEED,
    ADD_FIVE_PIERCE, TRIPLE_SPLIT
}

data class Ball(var x: Double, var y: Double, var dx: Double, var dy: Double, val id: Long)
data class Box(val left: Double, val top: Double, val right: Double, val bottom: Double)
sealed interface GameEvent {
    data class Burst(val index: Int, val x: Double, val y: Double, val restore: Boolean = false) : GameEvent
    data class Effect(val effect: SlotEffect) : GameEvent
    data object Cheat : GameEvent
    data object Finish : GameEvent
    data object Bounce : GameEvent
}

/** Android-free simulation. Coordinates and time do not depend on display size or frame rate. */
class GameEngine(private val random: Random = Random.Default) {
    companion object {
        const val WIDTH = 800.0
        const val HEIGHT = 1050.0
        const val COLUMNS = 8
        const val ROWS = 5
        const val RADIUS = 7.0
        const val BASE_SPEED = 440.0
        const val PADDLE_Y = 974.0
        const val PADDLE_WIDTH = 126.0
        const val PADDLE_SPEED = 4200.0
        const val SLOT_NANOS = 7_000_000_000L
        const val EFFECT_NANOS = 5_000_000_000L
        const val PENALTY_NANOS = 3_000_000_000L
        // Small AI planning steps + continuous swept collisions prevent tunnelling at stacked speeds.
        const val STEP_NANOS = 4_000_000L
        fun blockBox(index: Int): Box {
            val x = 12.0 + (index % COLUMNS) * 98.0
            val y = 88.0 + (index / COLUMNS) * 66.0
            return Box(x, y, x + 90.0, y + 54.0)
        }
        fun blockAt(x: Double, y: Double): Int? {
            if (x < 8 || x >= 792 || y < 82 || y >= 412) return null
            val col = floor((x - 8) / 98).toInt()
            val row = floor((y - 82) / 66).toInt()
            return (row * COLUMNS + col).takeIf { col in 0..7 && row in 0..4 }
        }
        fun reflectX(x: Double): Double {
            val span = WIDTH - RADIUS * 2
            val folded = ((x - RADIUS) % (2 * span) + 2 * span) % (2 * span)
            return RADIUS + if (folded <= span) folded else 2 * span - folded
        }
    }

    val blocks = BooleanArray(40) { true }
    val balls = mutableListOf<Ball>()
    val events = mutableListOf<GameEvent>()
    var elapsedNanos = 0L
        internal set
    var speedMultiplier = 1.0
        internal set
    var pierceUntil = 0L
        internal set
    var splitUntil = 0L
        internal set
    var lockedUntil = 0L
        internal set
    var nextSlotNanos = SLOT_NANOS
        internal set
    var paddleX = WIDTH / 2
        internal set
    var finished = false
        internal set
    var restoredCount = 0
        internal set
    var destroyedCount = 0
        internal set
    var slotCount = 0
        internal set
    var lastEffect: SlotEffect? = null
        internal set
    var lastEffectNanos = -SLOT_NANOS
        internal set
    var serveAt = 0L
        internal set
    var nextBallId = 0L
        internal set
    val remaining: Int get() = blocks.count { it }
    val locked: Boolean get() = elapsedNanos < lockedUntil
    val piercing: Boolean get() = elapsedNanos < pierceUntil
    val splitting: Boolean get() = elapsedNanos < splitUntil
    val ticks: Long get() = elapsedNanos / 100_000L

    init { serve() }

    fun restore(index: Int): Boolean {
        if (finished || locked || index !in blocks.indices || blocks[index]) return false
        blocks[index] = true
        restoredCount++
        val box = blockBox(index)
        events += GameEvent.Burst(index, (box.left + box.right) / 2, (box.top + box.bottom) / 2, true)
        return true
    }

    fun penalize() {
        if (finished || locked) return
        lockedUntil = elapsedNanos + PENALTY_NANOS
        events += GameEvent.Cheat
    }

    fun applyEffect(effect: SlotEffect) {
        if (finished) return
        when (effect) {
            SlotEffect.ADD_BALLS -> repeat(3) { serve() }
            SlotEffect.SPEED_DOUBLE -> speedMultiplier *= 2.0
            SlotEffect.SPEED_TRIPLE -> speedMultiplier *= 3.0
            SlotEffect.PIERCE -> pierceUntil = max(elapsedNanos, pierceUntil) + EFFECT_NANOS
            SlotEffect.SPLIT -> splitUntil = max(elapsedNanos, splitUntil) + EFFECT_NANOS
            SlotEffect.RESET_BALLS -> {
                if (balls.isEmpty()) serve()
                while (balls.size > 1) balls.removeAt(balls.lastIndex)
            }
            SlotEffect.RESET_SPEED -> speedMultiplier = 1.0
            SlotEffect.ADD_FIVE_PIERCE -> {
                repeat(5) { serve() }
                pierceUntil = max(elapsedNanos, pierceUntil) + EFFECT_NANOS
            }
            SlotEffect.TRIPLE_SPLIT -> {
                speedMultiplier *= 3.0
                splitUntil = max(elapsedNanos, splitUntil) + EFFECT_NANOS
            }
        }
        lastEffect = effect
        lastEffectNanos = elapsedNanos
        slotCount++
        events += GameEvent.Effect(effect)
    }

    private fun serve() {
        val angle = random.nextDouble(-0.7, 0.7)
        balls += Ball(paddleX, PADDLE_Y - RADIUS - 2, sin(angle), -cos(angle), nextBallId++)
    }

    /** Advance the complete duration; never discard a slow frame's elapsed time. */
    fun advance(nanos: Long) {
        require(nanos >= 0)
        var remainingNanos = nanos
        while (remainingNanos > 0 && !finished) {
            if (elapsedNanos >= nextSlotNanos) {
                applyEffect(SlotEffect.entries[random.nextInt(SlotEffect.entries.size)])
                nextSlotNanos += SLOT_NANOS
            }
            var step = min(remainingNanos, min(STEP_NANOS, nextSlotNanos - elapsedNanos))
            if (piercing) step = min(step, pierceUntil - elapsedNanos)
            if (splitting) step = min(step, splitUntil - elapsedNanos)
            if (balls.isEmpty()) {
                if (serveAt == 0L) serveAt = elapsedNanos + 650_000_000L
                if (elapsedNanos >= serveAt) { serve(); serveAt = 0 }
                else step = min(step, serveAt - elapsedNanos)
            }
            val consumed = simulate(step / 1e9)
            elapsedNanos += if (finished) (consumed * 1e9).roundToLong().coerceIn(0, step) else step
            remainingNanos -= step
        }
        if (!finished && elapsedNanos == nextSlotNanos) {
            applyEffect(SlotEffect.entries[random.nextInt(SlotEffect.entries.size)])
            nextSlotNanos += SLOT_NANOS
        }
    }

    /** Select the earliest reachable intercept, including side-wall reflections. */
    internal fun targetX(): Double {
        var bestTime = Double.POSITIVE_INFINITY
        var target = paddleX
        val speed = BASE_SPEED * speedMultiplier
        for (b in balls) {
            if (b.dy <= 0 || b.y > PADDLE_Y - RADIUS) continue
            val time = (PADDLE_Y - RADIUS - b.y) / (b.dy * speed)
            val intercept = reflectX(b.x + b.dx * speed * time)
            val travel = max(0.0, abs(intercept - paddleX) - PADDLE_WIDTH / 2 + RADIUS)
            if (time < bestTime && travel <= PADDLE_SPEED * time) {
                target = intercept; bestTime = time
            }
        }
        if (bestTime.isInfinite()) {
            val b = balls.filter { it.dy > 0 && it.y < PADDLE_Y }.maxByOrNull { it.y }
            if (b != null) {
                val time = max(0.0, (PADDLE_Y - RADIUS - b.y) / (b.dy * speed))
                target = reflectX(b.x + b.dx * speed * time)
            }
        }
        return target.coerceIn(PADDLE_WIDTH / 2, WIDTH - PADDLE_WIDTH / 2)
    }

    private data class Hit(val time: Double, val nx: Double, val ny: Double, val block: Int = -1, val paddle: Boolean = false)

    // Swept point versus radius-expanded rectangle (Minkowski sum).
    private fun sweep(b: Ball, vx: Double, vy: Double, box: Box, limit: Double): Hit? {
        val l = box.left - RADIUS; val r = box.right + RADIUS
        val t = box.top - RADIUS; val bot = box.bottom + RADIUS
        if (abs(vx) < 1e-12 && b.x !in l..r) return null
        if (abs(vy) < 1e-12 && b.y !in t..bot) return null
        val tx1 = if (abs(vx) < 1e-12) Double.NEGATIVE_INFINITY else (l - b.x) / vx
        val tx2 = if (abs(vx) < 1e-12) Double.POSITIVE_INFINITY else (r - b.x) / vx
        val ty1 = if (abs(vy) < 1e-12) Double.NEGATIVE_INFINITY else (t - b.y) / vy
        val ty2 = if (abs(vy) < 1e-12) Double.POSITIVE_INFINITY else (bot - b.y) / vy
        val entryX = min(tx1, tx2); val entryY = min(ty1, ty2)
        val entry = max(entryX, entryY)
        val leave = min(max(tx1, tx2), max(ty1, ty2))
        if (leave < max(0.0, entry) || entry > limit || leave < 0) return null
        return if (entryX > entryY) Hit(max(0.0, entry), -sign(vx), 0.0)
        else Hit(max(0.0, entry), 0.0, -sign(vy))
    }

    /** Resolve all collisions chronologically, including simultaneous balls and new split children. */
    private fun simulate(duration: Double): Double {
        var timeLeft = duration
        var consumed = 0.0
        val target = targetX()
        val paddleV = ((target - paddleX) / duration).coerceIn(-PADDLE_SPEED, PADDLE_SPEED)
        val speed = BASE_SPEED * speedMultiplier
        while (timeLeft > 1e-12 && !finished) {
            var earliest: Hit? = null
            var hitBall: Ball? = null
            for (ball in balls) {
                val vx = ball.dx * speed; val vy = ball.dy * speed
                var hit: Hit? = null
                fun consider(candidate: Hit?) {
                    if (candidate != null && candidate.time >= -1e-10 && candidate.time <= timeLeft + 1e-10 &&
                        (hit == null || candidate.time < hit!!.time)) hit = candidate.copy(time = max(0.0, candidate.time))
                }
                if (vx < 0) consider(Hit((RADIUS - ball.x) / vx, 1.0, 0.0))
                if (vx > 0) consider(Hit((WIDTH - RADIUS - ball.x) / vx, -1.0, 0.0))
                if (vy < 0) consider(Hit((RADIUS - ball.y) / vy, 0.0, 1.0))
                if (vy > 0 && ball.y <= PADDLE_Y - RADIUS + 1e-8) {
                    val t = (PADDLE_Y - RADIUS - ball.y) / vy
                    val x = ball.x + vx * t
                    val px = paddleX + paddleV * t
                    if (abs(x - px) <= PADDLE_WIDTH / 2 + RADIUS)
                        consider(Hit(t, 0.0, -1.0, paddle = true))
                }
                if (min(ball.y, ball.y + vy * timeLeft) <= 413) {
                    for (i in blocks.indices) if (blocks[i]) {
                        consider(sweep(ball, vx, vy, blockBox(i), timeLeft)?.copy(block = i))
                    }
                }
                if (hit != null && (earliest == null || hit!!.time < earliest.time)) {
                    earliest = hit; hitBall = ball
                }
            }
            val dt = (earliest?.time ?: timeLeft).coerceAtMost(timeLeft)
            balls.forEach { it.x += it.dx * speed * dt; it.y += it.dy * speed * dt }
            paddleX = (paddleX + paddleV * dt).coerceIn(PADDLE_WIDTH / 2, WIDTH - PADDLE_WIDTH / 2)
            consumed += dt; timeLeft -= dt
            if (earliest == null) break
            val b = hitBall!!
            val hit = earliest
            if (hit.block >= 0) {
                blocks[hit.block] = false
                destroyedCount++
                val box = blockBox(hit.block)
                events += GameEvent.Burst(hit.block, (box.left + box.right) / 2, (box.top + box.bottom) / 2)
                if (!piercing) { if (hit.nx != 0.0) b.dx = -b.dx else b.dy = -b.dy }
                if (splitting) {
                    // A ball becomes two balls, so one child is added. Directions fan apart.
                    val a = atan2(b.dy, b.dx)
                    val childAngle = a + 0.19
                    b.dx = cos(a - 0.19); b.dy = sin(a - 0.19)
                    balls += Ball(b.x, b.y, cos(childAngle), sin(childAngle), nextBallId++)
                }
                if (remaining == 0) { finished = true; events += GameEvent.Finish }
            } else if (hit.paddle) {
                // Aim at surviving blocks so isolated corner blocks cannot stall a run forever.
                val live = blocks.indices.filter { blocks[it] }
                val box = blockBox(live[random.nextInt(live.size)])
                val tx = (box.left + box.right) / 2
                val ty = (box.top + box.bottom) / 2
                val angle = atan2(tx - b.x, b.y - ty).coerceIn(-1.04, 1.04)
                b.dx = sin(angle); b.dy = -cos(angle)
                events += GameEvent.Bounce
            } else {
                if (hit.nx != 0.0) b.dx = -b.dx else b.dy = -b.dy
            }
            // Nudge away from the contact plane to avoid repeated zero-time wall collisions.
            if (hit.block < 0 || !piercing) { b.x += b.dx * 1e-6; b.y += b.dy * 1e-6 }
        }
        balls.removeAll { it.y > HEIGHT + RADIUS }
        return consumed
    }
}

/** A restore is committed on UP only after the entire gesture proved to be single-touch. */
class TapGuard {
    private var pending: Int? = null
    private var rejected = false
    fun down(index: Int?, allowed: Boolean) { pending = if (allowed) index else null; rejected = false }
    fun multiple(engine: GameEngine) { if (!rejected) engine.penalize(); rejected = true; pending = null }
    fun cancel() { pending = null; rejected = true }
    fun up(engine: GameEngine, index: Int?): Boolean {
        val target = pending
        val restored = !rejected && target != null && target == index && engine.restore(target)
        cancel()
        return restored
    }
}

object ScoreFormat {
    fun seconds(ticks: Long): String = "${ticks / 10000}.${(ticks % 10000).toString().padStart(4, '0')}"
    fun exact(ticks: Long): BigDecimal = BigDecimal(BigInteger.valueOf(ticks).pow(2), 8)
    fun score(ticks: Long): String = DecimalFormat("#,##0.0000", DecimalFormatSymbols(Locale.US))
        .format(exact(ticks).setScale(4, RoundingMode.DOWN))
}

data class ScoreEntry(val id: String, val ticks: Long, val dateMillis: Long, val restores: Int, val slots: Int)
object Ranking {
    fun insert(entries: List<ScoreEntry>, entry: ScoreEntry): List<ScoreEntry> =
        (entries.filterNot { it.id == entry.id } + entry)
            .sortedWith(compareByDescending<ScoreEntry> { it.ticks }.thenBy { it.dateMillis }.thenBy { it.id }).take(100)
}
