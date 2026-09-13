package io.github.hatake716.taero

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.os.SystemClock
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import java.util.UUID
import kotlin.math.*
import kotlin.random.Random

@SuppressLint("ViewConstructor")
class GameView(context: Context, private val store: GameStore, private val audio: GameAudio,
    private val rankings: () -> Unit, private val guide: () -> Unit, private val settings: () -> Unit
) : View(context), Choreographer.FrameCallback {
    enum class Screen { TITLE, COUNTDOWN, PLAYING, PAUSED, RESULT }
    var screen = Screen.TITLE
        internal set
    var engine = GameEngine()
        internal set
    private var runId = UUID.randomUUID().toString()
    private var savedRun: Pair<String, GameEngine>? = store.loadRun()
    private var resultRank = 0
    private var bestTicks = store.rankings().firstOrNull()?.ticks ?: 0
    private val guard = TapGuard()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val normal = Typeface.create("sans-serif", Typeface.NORMAL)
    private val bold = Typeface.create("sans-serif", Typeface.BOLD)
    private val digits = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val colors = intArrayOf(0xff41e8ee.toInt(), 0xff8a9bff.toInt(), 0xffc788ff.toInt(), 0xffff65bf.toInt(), 0xffffbc6b.toInt())
    private val cyan = colors[0]
    private val pink = colors[3]
    private val lime = 0xffdfff70.toInt()
    private val white = 0xfff0f5ff.toInt()
    private val muted = 0xff8493b3.toInt()
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var lastFrame = 0L
    private var uiTime = 0.0
    private var countdown = 3.0
    private var active = false
    private var pendingButton: Int? = null
    private var downX = 0f
    private var downY = 0f
    private var shake = 0f
    private var recordError = false
    private var accessibilityScreen: Screen? = null
    private var backgroundTexture: Bitmap? = null
    private val buttons = mutableListOf<UiButton>()
    private data class UiButton(val id: Int, val label: String, val box: RectF, val action: () -> Unit)
    private data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val maxLife: Float, val color: Int, val size: Float)
    private data class Ring(val x: Float, val y: Float, var age: Float, val color: Int, val restore: Boolean)
    private val particles = mutableListOf<Particle>()
    private val rings = mutableListOf<Ring>()

    init {
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "ブロック崩しに耐えろ！ 逆視点の耐久ゲーム"
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        scale = min(w / 900f, h / 1800f)
        offsetX = (w - 900f*scale)/2; offsetY = (h-1800f*scale)/2
    }

    fun beginRun() {
        engine = GameEngine(); runId = UUID.randomUUID().toString()
        particles.clear(); rings.clear(); guard.cancel(); savedRun = null
        store.saveRun(runId, engine)
        recordError = false; countdown = 3.0; screen = Screen.COUNTDOWN
        lastFrame = SystemClock.elapsedRealtimeNanos()
        audio.start(); announce("３秒後にスタート。空いたブロックを１本の指でタップ")
    }

    fun pauseGame() {
        if (screen != Screen.PLAYING && screen != Screen.COUNTDOWN) return
        if (screen == Screen.PLAYING) tick(SystemClock.elapsedRealtimeNanos())
        if (screen == Screen.RESULT) return
        screen = Screen.PAUSED; guard.cancel(); pendingButton = null
        store.saveRun(runId, engine); audio.pause(); invalidate()
        announce("一時停止")
    }

    fun goHome() {
        if (screen == Screen.PAUSED) {
            store.saveRun(runId, engine); savedRun = runId to engine
        }
        screen = Screen.TITLE; guard.cancel(); particles.clear(); rings.clear(); audio.start(); invalidate()
    }

    private fun resumeGame() {
        countdown = 3.0; screen = Screen.COUNTDOWN; guard.cancel()
        lastFrame = SystemClock.elapsedRealtimeNanos(); audio.start(); invalidate()
    }

    fun foreground() {
        if (active) return
        active = true; lastFrame = SystemClock.elapsedRealtimeNanos()
        if (screen != Screen.PAUSED) audio.start()
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun background() {
        pauseGame(); active = false; guard.cancel(); audio.pause()
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun onDetachedFromWindow() { Choreographer.getInstance().removeFrameCallback(this); super.onDetachedFromWindow() }

    override fun doFrame(frameTimeNanos: Long) {
        if (!active) return
        val previousScreen = screen
        tick(SystemClock.elapsedRealtimeNanos())
        if (screen != previousScreen || (screen != Screen.PAUSED && screen != Screen.RESULT)) invalidate()
        // Menus need only a slow ambient pulse. Static overlays do not redraw continuously.
        Choreographer.getInstance().postFrameCallbackDelayed(this,
            if (screen == Screen.PLAYING || screen == Screen.COUNTDOWN) 0L else 80L)
    }

    internal fun tick(now: Long) {
        val nanos = if (lastFrame == 0L) 0 else (now-lastFrame).coerceAtLeast(0)
        lastFrame = now
        val dt = (nanos / 1e9).toFloat()
        uiTime += min(dt, .1f)
        when (screen) {
            Screen.COUNTDOWN -> {
                countdown -= dt
                if (countdown <= 0) { screen = Screen.PLAYING; audio.play("slot"); announce("耐えろ！") }
            }
            Screen.PLAYING -> { engine.advance(nanos); processEvents() }
            else -> Unit
        }
        if (screen != Screen.PAUSED) {
            val visualDt = min(dt, .05f)
            for (p in particles) {
                p.x += p.vx*visualDt; p.y += p.vy*visualDt
                p.vy += 230*visualDt; p.life -= visualDt
            }
            particles.removeAll { it.life <= 0 }
            rings.forEach { it.age += visualDt }; rings.removeAll { it.age > .65f }
            shake = max(0f, shake-visualDt*28)
        }
    }

    private fun processEvents() {
        for (event in engine.events) when (event) {
            is GameEvent.Burst -> {
                explode(event)
                audio.play(if (event.restore) "restore" else "burst")
                if (event.restore && store.vibration) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            is GameEvent.Effect -> { audio.play("slot"); announce(event.effect.label) }
            GameEvent.Cheat -> {
                audio.play("cheat")
                if (store.vibration) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                announce("ズルはダメ！ ３秒間再生できません")
            }
            GameEvent.Bounce -> audio.play("bounce")
            GameEvent.Finish -> {
                screen = Screen.RESULT
                saveResult()
                announce("ゲーム終了。${ScoreFormat.score(engine.ticks)}ポイント")
            }
        }
        engine.events.clear()
    }

    private fun saveResult() {
        runCatching {
            resultRank = store.record(ScoreEntry(runId, engine.ticks, System.currentTimeMillis(), engine.restoredCount, engine.slotCount))
            recordError = false
        }.onFailure { recordError = true }
        bestTicks = max(bestTicks, engine.ticks); savedRun = null
    }

    private fun explode(event: GameEvent.Burst) {
        val color = if (event.restore) lime else colors[event.index/8]
        val count = if (store.reduced) 6 else if (event.restore) 16 else 32
        repeat(count) {
            val angle = Random.nextDouble(0.0, PI*2)
            val speed = Random.nextDouble(60.0, if (event.restore) 165.0 else 340.0)
            val life = Random.nextDouble(.25, .8).toFloat()
            particles += Particle(event.x.toFloat(), event.y.toFloat(), (cos(angle)*speed).toFloat(),
                (sin(angle)*speed).toFloat(), life, life, color, Random.nextDouble(2.0, 5.0).toFloat())
        }
        // Only visual particles are bounded. Gameplay ball count and multipliers are not capped.
        if (particles.size > 1800) particles.subList(0, particles.size-1800).clear()
        rings += Ring(event.x.toFloat(), event.y.toFloat(), 0f, color, event.restore)
        if (rings.size > 80) rings.removeAt(0)
        if (!event.restore && !store.reduced) shake = min(shake+1.3f, 4f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xff080c1c.toInt())
        canvas.save(); canvas.translate(offsetX, offsetY); canvas.scale(scale, scale)
        buttons.clear()
        backgroundArt(canvas)
        if (screen == Screen.TITLE) drawTitle(canvas) else {
            drawGame(canvas)
            when (screen) {
                Screen.COUNTDOWN -> drawCountdown(canvas)
                Screen.PAUSED -> drawPause(canvas)
                Screen.RESULT -> drawResult(canvas)
                else -> Unit
            }
        }
        canvas.restore()
        if (accessibilityScreen != screen) {
            accessibilityScreen = screen
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        }
    }

    private fun backgroundArt(c: Canvas) {
        val texture = backgroundTexture ?: Bitmap.createBitmap(900, 1800, Bitmap.Config.ARGB_8888).also { bitmap ->
            val backdrop = Canvas(bitmap)
            val brush = Paint(Paint.ANTI_ALIAS_FLAG)
            brush.shader = RadialGradient(740f, 340f, 850f,
                intArrayOf(0xff18254a.toInt(), 0xff080c1c.toInt()), null, Shader.TileMode.CLAMP)
            backdrop.drawRect(0f, 0f, 900f, 1800f, brush); brush.shader = null
            brush.color = 0xff202940.toInt()
            for (x in 30..900 step 45) for (y in 30..1800 step 45)
                backdrop.drawCircle(x.toFloat(), y.toFloat(), 1.3f, brush)
            backgroundTexture = bitmap
        }
        paint.color = Color.WHITE; paint.isFilterBitmap = true
        c.drawBitmap(texture, 0f, 0f, paint)
    }

    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int = white,
        face: Typeface = normal, align: Paint.Align = Paint.Align.LEFT, maxWidth: Float = 10000f) {
        paint.shader = null; paint.style = Paint.Style.FILL; paint.color = color
        paint.typeface = face; paint.textSize = size; paint.textAlign = align
        val width = paint.measureText(value)
        if (width > maxWidth) paint.textSize = size*maxWidth/width
        c.drawText(value, x, y, paint)
    }

    private fun panel(c: Canvas, box: RectF, color: Int = 0xff10182e.toInt(), stroke: Int = 0xff2c3a59.toInt(), radius: Float = 22f) {
        paint.style = Paint.Style.FILL; paint.color = color; c.drawRoundRect(box, radius, radius, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = stroke
        c.drawRoundRect(box, radius, radius, paint); paint.style = Paint.Style.FILL
    }

    private fun button(c: Canvas, id: Int, label: String, x: Float, y: Float, w: Float, h: Float,
        primary: Boolean = false, action: () -> Unit) {
        val box = RectF(x, y, x+w, y+h)
        panel(c, box, if (primary) lime else 0xff141e35.toInt(), if (primary) lime else 0xff394761.toInt(), 18f)
        text(c, label, x+w/2, y+h/2+11, 30f, if (primary) 0xff152020.toInt() else white, bold, Paint.Align.CENTER, w-32)
        buttons += UiButton(id, label, box, action)
    }

    private fun drawTitle(c: Canvas) {
        text(c, "REVERSE BREAKOUT", 54f, 80f, 25f, cyan, bold)
        text(c, "NEON SURVIVAL / 01", 54f, 119f, 18f, muted)
        text(c, "ブロック崩しに", 50f, 262f, 65f, white, bold, maxWidth = 800f)
        text(c, "耐えろ！", 44f, 382f, 116f, lime, bold)
        text(c, "壊すのはCPU。よみがえらせるのは、あなた。", 54f, 450f, 26f, 0xffbcc9e2.toInt(), maxWidth = 792f)
        // Code-native hero artwork: the actual 8 x 5 grid, with a divine regeneration target.
        c.save(); c.translate(50f, 516f)
        for (i in 0 until 40) {
            val box = GameEngine.blockBox(i)
            val missing = i in intArrayOf(9, 18, 22, 27, 28, 35)
            drawBlock(c, box, i, !missing, i == 27)
        }
        val hx = 355f; val hy = 315f
        val pulse = (sin(uiTime*2)*.12+.88).toFloat()
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = alpha(lime, 100)
        c.drawCircle(hx, hy, 53f*pulse, paint); c.drawCircle(hx, hy, 73f*pulse, paint)
        paint.style = Paint.Style.FILL
        val path = Path().apply { moveTo(630f, 595f); lineTo(530f, 505f); lineTo(395f, 399f) }
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f; paint.color = alpha(pink, 150); c.drawPath(path, paint)
        paint.style = Paint.Style.FILL; paint.color = pink; c.drawCircle(395f, 399f, 10f, paint)
        paint.color = alpha(pink, 30); c.drawCircle(395f, 399f, 26f, paint)
        panel(c, RectF(548f, 597f, 690f, 609f), cyan, cyan, 6f)
        for (i in 0 until 24) {
            val a = i*2*PI/24
            val distance = 35+(i%5)*9f
            val x = 395+cos(a)*distance; val y = 399+sin(a)*distance
            paint.color = colors[i%5]; c.drawCircle(x.toFloat(), y.toFloat(), (i%3+2).toFloat(), paint)
        }
        c.restore()
        text(c, "指１本で再生。最後の１個まで、あきらめるな。", 450f, 1190f, 25f, white, normal, Paint.Align.CENTER, 790f)
        panel(c, RectF(54f, 1230f, 846f, 1340f))
        text(c, "自己ベスト", 80f, 1273f, 21f, muted)
        text(c, ScoreFormat.score(bestTicks), 820f, 1310f, 47f, cyan, digits, Paint.Align.RIGHT, 540f)
        text(c, "SCORE = 生存秒数²", 80f, 1310f, 19f, muted)
        if (savedRun != null) {
            button(c, 1, "つづきから耐える  →", 54f, 1370f, 792f, 100f, true) {
                savedRun?.let { runId = it.first; engine = it.second }; resumeGame()
            }
            button(c, 6, "新しくはじめる", 54f, 1490f, 384f, 90f) { beginRun() }
            button(c, 2, "ランキング", 460f, 1490f, 386f, 90f) { rankings() }
        } else {
            button(c, 1, "神になって耐える  →", 54f, 1370f, 792f, 108f, true) { beginRun() }
            button(c, 2, "耐久ランキング  TOP 100", 54f, 1500f, 792f, 90f) { rankings() }
        }
        button(c, 3, "遊び方", 54f, 1610f, 384f, 82f) { guide() }
        button(c, 4, "音と演出", 460f, 1610f, 386f, 82f) { settings() }
        text(c, "40 BLOCKS     ·     1 FINGER     ·     ENDLESS SCORE", 450f, 1750f, 18f, muted, normal, Paint.Align.CENTER)
    }

    private fun drawBlock(c: Canvas, box: Box, index: Int, alive: Boolean, highlighted: Boolean = false) {
        val r = RectF(box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat())
        val color = if (highlighted) lime else colors[index/8]
        if (alive) {
            paint.color = alpha(color, 13); c.drawRoundRect(RectF(r.left-4, r.top-4, r.right+4, r.bottom+4), 12f, 12f, paint)
            paint.color = Color.WHITE
            paint.shader = LinearGradient(r.left, r.top, r.right, r.bottom,
                intArrayOf(alpha(color, 195), alpha(color, 92)), null, Shader.TileMode.CLAMP)
            c.drawRoundRect(r, 8f, 8f, paint); paint.shader = null
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = alpha(color, 240)
            c.drawRoundRect(r, 8f, 8f, paint)
            paint.color = alpha(Color.WHITE, 110); c.drawLine(r.left+10, r.top+5, r.right-10, r.top+5, paint)
            paint.style = Paint.Style.FILL
        } else {
            paint.color = 0xff131d31.toInt(); c.drawRoundRect(r, 8f, 8f, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.7f; paint.color = if (highlighted) lime else 0xff50627c.toInt()
            paint.pathEffect = DashPathEffect(floatArrayOf(6f,5f), 0f)
            c.drawRoundRect(r, 8f, 8f, paint); paint.pathEffect = null
            c.drawLine(r.centerX()-8, r.centerY(), r.centerX()+8, r.centerY(), paint)
            c.drawLine(r.centerX(), r.centerY()-8, r.centerX(), r.centerY()+8, paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawGame(c: Canvas) {
        val e = engine
        text(c, "ブロック崩しに耐えろ！", 50f, 62f, 26f, white, bold)
        if (screen == Screen.PLAYING) button(c, 10, "Ⅱ", 744f, 22f, 106f, 70f) { pauseGame() }
        text(c, "SURVIVAL SCORE", 50f, 120f, 19f, muted, bold)
        text(c, ScoreFormat.score(e.ticks), 45f, 218f, 94f, white, digits, maxWidth = 790f)
        text(c, "TIME", 52f, 264f, 20f, muted, bold)
        text(c, ScoreFormat.seconds(e.ticks) + " s", 128f, 269f, 35f, cyan, digits, maxWidth = 430f)
        text(c, "生存秒数²", 845f, 265f, 21f, muted, normal, Paint.Align.RIGHT)
        val effectAge = (e.elapsedNanos-e.lastEffectNanos)/1e9
        if (e.lastEffect != null && effectAge < 2.8 && e.lastEffectNanos >= 0) {
            val color = effectColor(e.lastEffect!!)
            panel(c, RectF(50f, 298f, 850f, 390f), alpha(color, 28), color, 14f)
            text(c, e.lastEffect!!.symbol, 75f, 360f, 38f, color, bold, maxWidth = 112f)
            text(c, e.lastEffect!!.label, 218f, 354f, 38f, white, bold, maxWidth = 600f)
        } else {
            text(c, if (e.remaining <= 8) "危険！ 最後の${e.remaining}個を守れ！" else "＋ の空きブロックをタップして再生", 450f, 352f, 28f,
                if (e.remaining <= 8) pink else 0xffb7c6df.toInt(), bold, Paint.Align.CENTER, 780f)
        }
        panel(c, RectF(47f, 406f, 853f, 1466f), 0xff090f20.toInt(), if (e.remaining <= 8) pink else 0xff2b415e.toInt(), 22f)
        c.save(); c.translate(50f, 413f)
        // Field hit coordinates stay fixed even while the decorative explosions shake.
        text(c, "REGEN FIELD", 16f, 39f, 18f, muted, bold)
        text(c, "${e.remaining} / 40", 784f, 42f, 25f, if (e.remaining <= 8) pink else cyan, digits, Paint.Align.RIGHT)
        for (i in 0 until 40) drawBlock(c, GameEngine.blockBox(i), i, e.blocks[i])
        for (y in 460..940 step 60) {
            paint.color = 0xff152138.toInt(); paint.strokeWidth = 1f
            c.drawLine(12f, y.toFloat(), 788f, y.toFloat(), paint)
        }
        // Traces and sparks are clipped to the arena; they never cover score or slot controls.
        c.save(); c.clipRect(0f, 50f, 800f, 1050f)
        for (b in e.balls) {
            val color = if (e.piercing) pink else if (e.splitting) lime else white
            val tail = min(95.0, 18+e.speedMultiplier*9)
            paint.strokeCap = Paint.Cap.ROUND
            for (i in 3 downTo 1) {
                paint.strokeWidth = (11-i*2).toFloat(); paint.color = alpha(color, 28+(3-i)*23)
                c.drawLine(b.x.toFloat(), b.y.toFloat(), (b.x-b.dx*tail*i/3).toFloat(), (b.y-b.dy*tail*i/3).toFloat(), paint)
            }
            paint.color = alpha(color, 35); c.drawCircle(b.x.toFloat(), b.y.toFloat(), 17f, paint)
            paint.color = color; c.drawCircle(b.x.toFloat(), b.y.toFloat(), 7f, paint)
            paint.color = Color.WHITE; c.drawCircle(b.x.toFloat()-1.5f, b.y.toFloat()-1.5f, 3f, paint)
        }
        c.save()
        if (shake > 0) c.translate((sin(uiTime*73)*shake).toFloat(), (cos(uiTime*67)*shake).toFloat())
        for (p in particles) {
            paint.color = alpha(p.color, (255*p.life/p.maxLife).toInt().coerceIn(0,255))
            paint.strokeWidth = p.size; paint.strokeCap = Paint.Cap.ROUND
            c.drawLine(p.x, p.y, p.x-p.vx*.025f, p.y-p.vy*.025f, paint)
        }
        for (r in rings) {
            val a = (1-r.age/.65f).coerceIn(0f, 1f)
            paint.style = Paint.Style.STROKE; paint.color = alpha(r.color, (180*a).toInt()); paint.strokeWidth = 3*a
            c.drawCircle(r.x, r.y, 12+r.age*120, paint); paint.style = Paint.Style.FILL
            if (r.restore) text(c, "+ REGEN", r.x, r.y-r.age*60-18, 18f, alpha(lime,(255*a).toInt()), bold, Paint.Align.CENTER)
        }
        c.restore(); c.restore()
        val px = e.paddleX.toFloat()
        paint.color = alpha(cyan, 35); c.drawRoundRect(px-70, 967f, px+70, 997f, 15f, 15f, paint)
        panel(c, RectF(px-63, 974f, px+63, 986f), cyan, cyan, 6f)
        text(c, "CPU / AUTO", px, 1024f, 16f, muted, bold, Paint.Align.CENTER)
        c.restore()
        drawStatusAndSlot(c)
        if (e.locked && (screen == Screen.PLAYING || screen == Screen.COUNTDOWN)) {
            panel(c, RectF(76f, 870f, 824f, 1130f), 0xf21e1030.toInt(), pink, 24f)
            text(c, "ズルはダメ！", 450f, 970f, 68f, pink, bold, Paint.Align.CENTER, 690f)
            text(c, "再生禁止  ${"%.1f".format(java.util.Locale.US, (e.lockedUntil-e.elapsedNanos)/1e9)} 秒", 450f, 1035f, 35f, white, bold, Paint.Align.CENTER)
            text(c, "指は１本だけ。玉は止まらない！", 450f, 1084f, 24f, 0xffd0b8d5.toInt(), normal, Paint.Align.CENTER)
        }
    }

    private fun drawStatusAndSlot(c: Canvas) {
        val e = engine
        panel(c, RectF(50f, 1492f, 594f, 1730f))
        text(c, "CPU STATUS", 75f, 1531f, 18f, muted, bold)
        text(c, "玉 ${e.balls.size}", 75f, 1590f, 38f, cyan, digits, maxWidth = 225f)
        val speed = if (e.speedMultiplier < 1e9) "×${e.speedMultiplier.toLong()}" else "×${"%.1e".format(java.util.Locale.US,e.speedMultiplier)}"
        text(c, speed, 560f, 1590f, 40f, pink, digits, Paint.Align.RIGHT, 245f)
        val p = max(0.0, (e.pierceUntil-e.elapsedNanos)/1e9)
        val s = max(0.0, (e.splitUntil-e.elapsedNanos)/1e9)
        text(c, "貫通 ${"%.1f".format(java.util.Locale.US,p)}s    分裂 ${"%.1f".format(java.util.Locale.US,s)}s", 75f, 1645f, 26f,
            if (p+s>0) lime else muted, bold, maxWidth = 486f)
        text(c, "再生 ${e.restoredCount}回  ·  効果 ${e.slotCount}回", 75f, 1697f, 22f, muted, maxWidth = 485f)
        val effectAge = (e.elapsedNanos-e.lastEffectNanos)/1e9
        val stopped = e.lastEffect != null && effectAge < 1.15 && e.lastEffectNanos >= 0
        panel(c, RectF(614f, 1492f, 850f, 1730f), 0xff17162d.toInt(), if (stopped) effectColor(e.lastEffect!!) else 0xff685487.toInt())
        text(c, "CPU SLOT", 732f, 1531f, 18f, muted, bold, Paint.Align.CENTER)
        c.save(); c.clipRect(627f, 1550f, 837f, 1673f)
        if (stopped) {
            text(c, e.lastEffect!!.symbol, 732f, 1634f, 60f, effectColor(e.lastEffect!!), bold, Paint.Align.CENTER, 195f)
        } else {
            val pos = (e.elapsedNanos/1e9*12)
            val base = floor(pos).toInt()
            val shift = ((pos-floor(pos))*112).toFloat()
            for (i in -1..1) {
                val effect = SlotEffect.entries[Math.floorMod(base+i, SlotEffect.entries.size)]
                text(c, effect.symbol, 732f, 1635f+i*112-shift, 53f, effectColor(effect), bold, Paint.Align.CENTER, 190f)
            }
        }
        c.restore()
        val next = max(0.0, (e.nextSlotNanos-e.elapsedNanos)/1e9)
        text(c, if (stopped) "効果発動！" else "あと ${"%.1f".format(java.util.Locale.US,next)} s", 732f, 1700f, 23f,
            if (stopped) lime else white, bold, Paint.Align.CENTER)
        text(c, "７秒ごとに運命が回る。長く耐えるほど、スコアは加速。", 450f, 1770f, 19f, muted, normal, Paint.Align.CENTER, 800f)
    }

    private fun dim(c: Canvas) { paint.color = 0xda060a18.toInt(); c.drawRect(0f,0f,900f,1800f,paint) }

    private fun drawCountdown(c: Canvas) {
        dim(c)
        text(c, "${ceil(countdown).toInt().coerceAtLeast(1)}", 450f, 900f, 220f, lime, digits, Paint.Align.CENTER)
        text(c, "空いたブロックを、指１本で再生", 450f, 1000f, 34f, white, bold, Paint.Align.CENTER, 790f)
        text(c, "全40個を壊されたら終了", 450f, 1060f, 26f, muted, normal, Paint.Align.CENTER)
    }

    private fun drawPause(c: Canvas) {
        dim(c)
        text(c, "PAUSED", 450f, 604f, 26f, cyan, bold, Paint.Align.CENTER)
        text(c, "ひと休み。", 450f, 704f, 70f, white, bold, Paint.Align.CENTER)
        text(c, "時間も、スロットも、ここで停止中。", 450f, 779f, 27f, muted, normal, Paint.Align.CENTER)
        button(c, 20, "３秒後に再開  →", 130f, 860f, 640f, 110f, true) { resumeGame() }
        button(c, 21, "音と演出", 130f, 996f, 640f, 94f) { settings() }
        button(c, 22, "保存してタイトルへ", 130f, 1116f, 640f, 94f) { goHome() }
    }

    private fun drawResult(c: Canvas) {
        dim(c)
        text(c, "ALL BLOCKS DESTROYED", 450f, 366f, 24f, pink, bold, Paint.Align.CENTER)
        text(c, "よく、耐えた。", 450f, 466f, 69f, white, bold, Paint.Align.CENTER, 800f)
        panel(c, RectF(80f, 535f, 820f, 990f), 0xff101a31.toInt(), 0xff425577.toInt(), 26f)
        text(c, "SURVIVAL SCORE", 450f, 600f, 23f, muted, bold, Paint.Align.CENTER)
        text(c, ScoreFormat.score(engine.ticks), 450f, 719f, 84f, lime, digits, Paint.Align.CENTER, 684f)
        text(c, "${ScoreFormat.seconds(engine.ticks)} 秒", 450f, 793f, 40f, cyan, digits, Paint.Align.CENTER, 680f)
        text(c, "${ScoreFormat.seconds(engine.ticks)}² = SCORE", 450f, 843f, 21f, muted, normal, Paint.Align.CENTER, 680f)
        text(c, if (recordError) "保存できませんでした" else if (resultRank > 0) "耐久ランキング  第${resultRank}位" else "TOP 100 まで、あと少し！",
            450f, 921f, 34f, if (recordError) pink else white, bold, Paint.Align.CENTER, 680f)
        text(c, "再生 ${engine.restoredCount}回     /     CPU効果 ${engine.slotCount}回", 450f, 1050f, 26f, muted, normal, Paint.Align.CENTER)
        if (recordError) button(c, 35, "記録の保存を再試行", 130f, 1100f, 640f, 80f) { saveResult() }
        button(c, 30, "もう一度、耐える  →", 100f, 1220f, 700f, 110f, true) { beginRun() }
        button(c, 31, "ランキングを見る", 100f, 1360f, 700f, 96f) { rankings() }
        button(c, 32, "タイトルへ", 100f, 1486f, 700f, 92f) { goHome() }
    }

    private fun effectColor(effect: SlotEffect): Int = when (effect) {
        SlotEffect.RESET_SPEED, SlotEffect.RESET_BALLS -> lime
        SlotEffect.SPEED_DOUBLE, SlotEffect.SPEED_TRIPLE, SlotEffect.PIERCE -> pink
        SlotEffect.ADD_BALLS -> cyan
        SlotEffect.SPLIT -> 0xffc788ff.toInt()
    }
    private fun alpha(color: Int, alpha: Int): Int = (color and 0x00ffffff) or (alpha.coerceIn(0,255) shl 24)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (screen == Screen.PLAYING) tick(SystemClock.elapsedRealtimeNanos())
        val x = (event.x-offsetX)/scale; val y = (event.y-offsetY)/scale
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x; downY = y
                pendingButton = buttons.firstOrNull { it.box.contains(x,y) }?.id
                if (screen == Screen.PLAYING) guard.down(GameEngine.blockAt((x-50).toDouble(),(y-413).toDouble()), !engine.locked)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                pendingButton = null
                if (screen == Screen.PLAYING) { guard.multiple(engine); processEvents() }
            }
            MotionEvent.ACTION_MOVE -> {
                if (hypot(x-downX,y-downY) > 25) { guard.cancel(); pendingButton = null }
                if (event.pointerCount > 1 && screen == Screen.PLAYING) { guard.multiple(engine); processEvents() }
            }
            MotionEvent.ACTION_UP -> {
                if (screen == Screen.PLAYING) {
                    guard.up(engine, GameEngine.blockAt((x-50).toDouble(),(y-413).toDouble())); processEvents()
                }
                val b = buttons.firstOrNull { it.id == pendingButton && it.box.contains(x,y) }
                pendingButton = null; b?.action?.invoke(); performClick()
            }
            MotionEvent.ACTION_CANCEL -> { guard.cancel(); pendingButton = null }
        }
        invalidate(); return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }

    @Suppress("DEPRECATION")
    private fun announce(message: String) { announceForAccessibility(message) }

    // Expose actual menu actions and single-block restore actions to Android accessibility tools.
    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider = object : AccessibilityNodeProvider() {
        @Suppress("DEPRECATION")
        override fun createAccessibilityNodeInfo(id: Int): AccessibilityNodeInfo? {
            if (id == HOST_VIEW_ID) return AccessibilityNodeInfo.obtain(this@GameView).apply {
                onInitializeAccessibilityNodeInfo(this)
                buttons.forEach { addChild(this@GameView,it.id) }
                if (screen == Screen.PLAYING) for (i in 0..39) addChild(this@GameView,100+i)
            }
            val b = buttons.firstOrNull { it.id == id }
            val index = id-100
            val box: RectF
            val label: String
            if (b != null) { box = b.box; label = b.label }
            else if (screen == Screen.PLAYING && index in 0..39) {
                val r = GameEngine.blockBox(index)
                box = RectF(r.left.toFloat()+50,r.top.toFloat()+413,r.right.toFloat()+50,r.bottom.toFloat()+413)
                label = "${index/8+1}行${index%8+1}列、" + if (engine.blocks[index]) "ブロックあり" else "タップで再生"
            } else return null
            val location = IntArray(2); getLocationOnScreen(location)
            return AccessibilityNodeInfo.obtain().apply {
                setSource(this@GameView,id); setParent(this@GameView)
                className = "android.widget.Button"; packageName = context.packageName
                contentDescription = label; text = label
                isClickable = true; isEnabled = true; isVisibleToUser = true; isFocusable = true
                setBoundsInScreen(Rect((box.left*scale+offsetX+location[0]).toInt(), (box.top*scale+offsetY+location[1]).toInt(),
                    (box.right*scale+offsetX+location[0]).toInt(),(box.bottom*scale+offsetY+location[1]).toInt()))
                addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
            }
        }
        override fun performAction(id: Int, action: Int, arguments: Bundle?): Boolean {
            if (action != AccessibilityNodeInfo.ACTION_CLICK) return false
            buttons.firstOrNull { it.id == id }?.let { it.action(); invalidate(); return true }
            if (screen == Screen.PLAYING && id-100 in 0..39) {
                tick(SystemClock.elapsedRealtimeNanos()); engine.restore(id-100); processEvents(); invalidate(); return true
            }
            return false
        }
    }
}
