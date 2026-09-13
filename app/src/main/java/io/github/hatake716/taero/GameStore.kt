package io.github.hatake716.taero

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class GameStore(context: Context) {
    private val prefs = context.getSharedPreferences("taero.v1", Context.MODE_PRIVATE)
    var music: Boolean
        get() = prefs.getBoolean("music", true)
        set(value) { prefs.edit().putBoolean("music", value).apply() }
    var sound: Boolean
        get() = prefs.getBoolean("sound", true)
        set(value) { prefs.edit().putBoolean("sound", value).apply() }
    var vibration: Boolean
        get() = prefs.getBoolean("vibration", true)
        set(value) { prefs.edit().putBoolean("vibration", value).apply() }
    var reduced: Boolean
        get() = prefs.getBoolean("reduced", false)
        set(value) { prefs.edit().putBoolean("reduced", value).apply() }

    fun rankings(): List<ScoreEntry> = runCatching {
        val array = JSONArray(prefs.getString("ranking", "[]"))
        (0 until array.length()).map { i ->
            val j = array.getJSONObject(i)
            ScoreEntry(j.getString("id"), j.getLong("ticks"), j.getLong("date"), j.getInt("restores"), j.getInt("slots"))
        }.filter { it.ticks >= 0 }.sortedByDescending { it.ticks }.take(100)
    }.getOrDefault(emptyList())

    fun record(entry: ScoreEntry): Int {
        val sorted = Ranking.insert(rankings(), entry)
        val array = JSONArray()
        sorted.forEach { e -> array.put(JSONObject().put("id", e.id).put("ticks", e.ticks)
            .put("date", e.dateMillis).put("restores", e.restores).put("slots", e.slots)) }
        // The ranking and completed-run removal share one durable preferences transaction.
        check(prefs.edit().putString("ranking", array.toString()).remove("run").commit())
        return sorted.indexOfFirst { it.id == entry.id }.let { if (it < 0) 0 else it + 1 }
    }

    fun discardRun() { prefs.edit().remove("run").commit() }

    fun saveRun(id: String, engine: GameEngine) {
        if (engine.finished) return
        val e = engine
        val j = JSONObject().put("id", id).put("elapsed", e.elapsedNanos).put("speed", e.speedMultiplier)
            .put("pierce", e.pierceUntil).put("split", e.splitUntil).put("lock", e.lockedUntil)
            .put("nextSlot", e.nextSlotNanos).put("paddle", e.paddleX)
            .put("restores", e.restoredCount).put("destroyed", e.destroyedCount).put("slots", e.slotCount)
            .put("effect", e.lastEffect?.name).put("effectAt", e.lastEffectNanos)
            .put("serveAt", e.serveAt).put("nextId", e.nextBallId)
            .put("blocks", JSONArray(e.blocks.toList()))
        val balls = JSONArray()
        e.balls.forEach { balls.put(JSONArray(listOf(it.x, it.y, it.dx, it.dy, it.id))) }
        j.put("balls", balls)
        check(prefs.edit().putString("run", j.toString()).commit())
    }

    fun loadRun(): Pair<String, GameEngine>? = runCatching {
        val raw = prefs.getString("run", null) ?: return null
        val j = JSONObject(raw)
        val e = GameEngine()
        e.elapsedNanos = j.getLong("elapsed")
        e.speedMultiplier = j.getDouble("speed")
        e.pierceUntil = j.getLong("pierce"); e.splitUntil = j.getLong("split")
        e.lockedUntil = j.getLong("lock"); e.nextSlotNanos = j.getLong("nextSlot")
        e.paddleX = j.getDouble("paddle"); e.restoredCount = j.getInt("restores")
        e.destroyedCount = j.getInt("destroyed"); e.slotCount = j.getInt("slots")
        e.lastEffect = j.optString("effect").takeIf { it.isNotEmpty() }?.let(SlotEffect::valueOf)
        e.lastEffectNanos = j.getLong("effectAt"); e.serveAt = j.getLong("serveAt")
        e.nextBallId = j.getLong("nextId")
        val blocks = j.getJSONArray("blocks")
        require(blocks.length() == 40)
        for (i in 0 until 40) e.blocks[i] = blocks.getBoolean(i)
        e.balls.clear()
        val balls = j.getJSONArray("balls")
        for (i in 0 until balls.length()) {
            val b = balls.getJSONArray(i)
            val ball = Ball(b.getDouble(0), b.getDouble(1), b.getDouble(2), b.getDouble(3), b.getLong(4))
            require(listOf(ball.x, ball.y, ball.dx, ball.dy).all { it.isFinite() })
            e.balls += ball
        }
        require(e.elapsedNanos >= 0 && e.nextSlotNanos > e.elapsedNanos && e.remaining > 0)
        require(e.speedMultiplier.isFinite() && e.speedMultiplier >= 1)
        j.getString("id") to e
    }.getOrElse { discardRun(); null }
}
