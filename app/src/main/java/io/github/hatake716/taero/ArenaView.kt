package io.github.hatake716.taero

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import kotlin.math.*
import kotlin.random.Random

/** Only the animated arena is canvas-drawn. Its 40 real child views own disjoint touch targets. */
@SuppressLint("ViewConstructor")
internal class ArenaView(context: Context, private val game: GameView, private val store: GameStore,
    private val illustration: Boolean = false
) : FrameLayout(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colors = intArrayOf(0xff41e8ee.toInt(),0xff8a9bff.toInt(),0xffc788ff.toInt(),0xffff65bf.toInt(),0xffffbc6b.toInt())
    private val lime = 0xffdfff70.toInt()
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var lastVisual = 0L
    private data class Spark(var x: Double, var y: Double, val vx: Double, val vy: Double,
        var life: Float, val total: Float, val color: Int)
    private val sparks = mutableListOf<Spark>()
    private fun geometry() = ArenaLayout(width, height, if (illustration) max(1,height / 9) else game.dp(48f))
    init {
        setWillNotDraw(false)
        if (illustration) importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        else {
            for (i in 0..39) addView(BlockCell(context, i))
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }
    private inner class BlockCell(context: Context, val index: Int) : View(context) {
        init { isFocusable = true; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }
        override fun performClick(): Boolean {
            if (!canRestore(index)) return false
            val restored = game.restoreAccessible(index)
            if (restored) super.performClick()
            return restored
        }
        override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            val available = canRestore(index)
            info.isClickable = available
            info.className = if (available) "android.widget.Button" else "android.view.View"
            info.contentDescription = description(index)
            if (available) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
            else info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
        }
    }
    private fun canRestore(index: Int) = game.screen == GameView.Screen.PLAYING && !game.engine.locked && !game.engine.blocks[index] && !game.engine.finished
    private fun description(index: Int): String = context.getString(when {
        game.engine.blocks[index] -> R.string.block_present
        game.engine.locked -> R.string.block_locked
        game.screen != GameView.Screen.PLAYING -> R.string.block_waiting
        else -> R.string.block_empty
    }, index/8+1, index%8+1)
    fun refreshCells() {
        for (i in 0 until childCount) {
            val cell = getChildAt(i)
            val clickable = canRestore(i)
            if (cell.isClickable != clickable) cell.isClickable = clickable
            val label = description(i)
            if (cell.contentDescription != label) cell.contentDescription = label
        }
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val projection = geometry()
        for (i in 0 until childCount) {
            val b = projection.cell(i)
            getChildAt(i).layout(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt())
        }
    }
    fun cellCenterOnScreen(index: Int): PointF {
        val cell = getChildAt(index); val location = IntArray(2); cell.getLocationOnScreen(location)
        return PointF(location[0] + cell.width/2f, location[1] + cell.height/2f)
    }
    fun burst(event: GameEvent.Burst) {
        val count = if (store.reduced) 6 else if (event.restore) 16 else 32
        repeat(count) {
            val angle = Random.nextDouble(0.0, 2*PI); val speed = Random.nextDouble(60.0, 320.0)
            val life = Random.nextDouble(.25,.8).toFloat()
            sparks += Spark(event.x,event.y,cos(angle)*speed,sin(angle)*speed,life,life,if(event.restore) lime else colors[event.index/8])
        }
        if (sparks.size > 1800) sparks.subList(0,sparks.size-1800).clear()
    }
    fun advanceVisuals() {
        val now = SystemClock.elapsedRealtime()
        val dt = if (lastVisual == 0L) 0f else min(.05f,(now-lastVisual)/1000f)
        lastVisual = now
        sparks.forEach { it.x += it.vx*dt; it.y += it.vy*dt; it.life -= dt }
        sparks.removeAll { it.life <= 0 }
        invalidate()
    }
    private fun alpha(color: Int, a: Int) = (color and 0x00ffffff) or (a.coerceIn(0,255) shl 24)
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (width == 0 || height == 0) return
        val g = geometry()
        c.drawColor(0xff090f20.toInt())
        paint.strokeWidth = game.dp(1f).toFloat(); paint.color = 0xff243654.toInt()
        for (i in 0..6) {
            val y = g.y(440.0+i*80).toFloat(); c.drawLine(0f,y,width.toFloat(),y,paint)
        }
        for (i in 0..39) {
            val b = g.block(i)
            val r = RectF(b.left.toFloat(),b.top.toFloat(),b.right.toFloat(),b.bottom.toFloat())
            val alive = if (illustration) i !in intArrayOf(9,18,22,27,28,35) else game.engine.blocks[i]
            val color = colors[i/8]
            if (alive) {
                paint.shader = LinearGradient(r.left,r.top,r.right,r.bottom,intArrayOf(alpha(color,195),alpha(color,92)),null,Shader.TileMode.CLAMP)
                paint.style = Paint.Style.FILL; c.drawRoundRect(r,game.dp(4f).toFloat(),game.dp(4f).toFloat(),paint); paint.shader = null
                paint.color = color; paint.style = Paint.Style.STROKE; c.drawRoundRect(r,game.dp(4f).toFloat(),game.dp(4f).toFloat(),paint)
                paint.color = alpha(Color.WHITE,110); c.drawLine(r.left+game.dp(4f),r.top+game.dp(3f),r.right-game.dp(4f),r.top+game.dp(3f),paint)
            } else {
                paint.color = 0xff131d31.toInt(); paint.style = Paint.Style.FILL; c.drawRoundRect(r,5f,5f,paint)
                paint.style = Paint.Style.STROKE; paint.color = 0xff6e829f.toInt()
                paint.pathEffect = DashPathEffect(floatArrayOf(game.dp(3f).toFloat(),game.dp(2f).toFloat()),0f)
                c.drawRoundRect(r,5f,5f,paint); paint.pathEffect = null
                val d = game.dp(4f); c.drawLine(r.centerX()-d,r.centerY(),r.centerX()+d,r.centerY(),paint)
                c.drawLine(r.centerX(),r.centerY()-d,r.centerX(),r.centerY()+d,paint)
            }
            paint.style = Paint.Style.FILL
        }
        val e = game.engine
        val balls = if (illustration) listOf(Ball(395.0,490.0,-.6,-.8,0)) else e.balls
        for (ball in balls) {
            val color = if (illustration || e.piercing) colors[3] else if (e.splitting) lime else Color.WHITE
            val tail = if (illustration) 180.0 else min(95.0,18+e.speedMultiplier*9)
            paint.color = alpha(color,100); paint.strokeWidth = game.dp(2f).toFloat(); paint.strokeCap = Paint.Cap.ROUND
            c.drawLine(g.x(ball.x).toFloat(),g.y(ball.y).toFloat(),g.x(ball.x-ball.dx*tail).toFloat(),g.y(ball.y-ball.dy*tail).toFloat(),paint)
            paint.color = alpha(color,35); c.drawCircle(g.x(ball.x).toFloat(),g.y(ball.y).toFloat(),game.dp(7f).toFloat(),paint)
            paint.color = color; c.drawCircle(g.x(ball.x).toFloat(),g.y(ball.y).toFloat(),game.dp(3f).toFloat(),paint)
        }
        for (p in sparks) {
            paint.color = alpha(p.color,(255*p.life/p.total).toInt()); paint.strokeWidth = game.dp(2f).toFloat()
            c.drawLine(g.x(p.x).toFloat(),g.y(p.y).toFloat(),g.x(p.x-p.vx*.03).toFloat(),g.y(p.y-p.vy*.03).toFloat(),paint)
        }
        val paddle = if (illustration) 540.0 else e.paddleX
        paint.color = colors[0]
        c.drawRoundRect(g.x(paddle-63).toFloat(),g.y(974.0).toFloat(),g.x(paddle+63).toFloat(),g.y(974.0).toFloat()+game.dp(6f),5f,5f,paint)
    }
    override fun onInterceptTouchEvent(ev: MotionEvent) = !illustration
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (illustration) return false
        if (game.screen == GameView.Screen.PLAYING) game.tick(SystemClock.elapsedRealtimeNanos())
        val hit = geometry().hit(event.x,event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; moved = false
                parent.requestDisallowInterceptTouchEvent(true)
                game.guard.down(hit,game.screen == GameView.Screen.PLAYING && !game.engine.locked)
            }
            MotionEvent.ACTION_MOVE -> if (hypot(event.x-downX,event.y-downY) > slop) {
                moved = true; game.guard.cancel(); parent.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_UP -> {
                if (!moved && game.screen == GameView.Screen.PLAYING) { game.guard.up(game.engine,hit); game.processEvents() }
                parent.requestDisallowInterceptTouchEvent(false); refreshCells()
            }
            MotionEvent.ACTION_CANCEL -> { game.guard.cancel(); parent.requestDisallowInterceptTouchEvent(false) }
        }
        return true
    }
}
