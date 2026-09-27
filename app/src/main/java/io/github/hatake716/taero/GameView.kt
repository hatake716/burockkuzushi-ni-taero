package io.github.hatake716.taero

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Choreographer
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.widget.*
import java.util.Locale
import java.util.UUID
import kotlin.math.*

/** Native, scalable text and controls surround the animated arcade arena. */
@SuppressLint("ViewConstructor")
class GameView(context: Context, private val store: GameStore, private val audio: GameAudio,
    private val rankings: () -> Unit, private val guide: () -> Unit, private val settings: () -> Unit,
    private val confirmReplace: (() -> Unit) -> Unit,
    private val awake: (Boolean) -> Unit
) : LinearLayout(context), Choreographer.FrameCallback {
    enum class Screen { TITLE, COUNTDOWN, PLAYING, PAUSED, RESULT }
    var screen = Screen.TITLE
        internal set(value) {
            if (field == value) return
            val wasGame = field == Screen.PLAYING || field == Screen.COUNTDOWN
            field = value
            syncAwake()
            if (wasGame && (value == Screen.PLAYING || value == Screen.COUNTDOWN)) updateUi(true)
            else buildScreen()
        }
    var engine = GameEngine()
        internal set
    private var runId = UUID.randomUUID().toString()
    private var savedRun = store.loadRun()
    private var resultRank = 0
    private var bestTicks = store.rankings().firstOrNull()?.ticks ?: 0
    internal val guard = TapGuard()
    private var lastFrame = 0L
    private var countdown = 3.0
    private var active = false
    private var multipleGesture = false
    private var recordError = false
    private var lastVisibleUpdate = 0L
    private var lastStatusUpdate = 0L
    private var lastStatusEvent = 0L
    private val cadence = StatusCadence()
    private val gameFont = resources.getFont(R.font.dot_gothic)
    private var arena: ArenaView? = null
    private var scoreText: TextView? = null
    private var timeText: TextView? = null
    private var statusText: TextView? = null
    private var liveText: LiveStatusView? = null
    private var lastSpokenStatus = ""
    private var effectText: TextView? = null
    private var slotText: TextView? = null
    private var countdownText: TextView? = null
    private var pauseButton: Button? = null
    private val white = 0xfff0f5ff.toInt()
    private val cyan = 0xff41e8ee.toInt()
    private val lime = 0xffdfff70.toInt()
    private val pink = 0xffff65bf.toInt()
    private val muted = 0xffb1bdd5.toInt()

    init {
        orientation = VERTICAL
        setBackgroundColor(0xff080c1c.toInt())
        buildScreen()
    }
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)
    internal fun dp(value: Float) = ceil(value * resources.displayMetrics.density).toInt()
    private fun syncAwake() = awake(active && (screen == Screen.PLAYING || screen == Screen.COUNTDOWN))

    internal fun beginRun() {
        engine = GameEngine(); runId = UUID.randomUUID().toString()
        guard.cancel(); savedRun = null
        store.saveRun(runId, engine)
        recordError = false; countdown = 3.0
        cadence.reset(SystemClock.elapsedRealtime())
        screen = Screen.COUNTDOWN
        lastFrame = SystemClock.elapsedRealtimeNanos()
        audio.start(); announce(s(R.string.start_announcement))
    }
    private fun requestNewRun() {
        if (savedRun != null) confirmReplace { beginRun() } else beginRun()
    }
    fun pauseGame() {
        if (screen != Screen.PLAYING && screen != Screen.COUNTDOWN) return
        if (screen == Screen.PLAYING) tick(SystemClock.elapsedRealtimeNanos())
        if (screen == Screen.RESULT) return
        guard.cancel(); screen = Screen.PAUSED
        store.saveRun(runId, engine); audio.pause()
        announce(s(R.string.paused_announcement))
    }
    fun goHome() {
        if (screen == Screen.PAUSED) { store.saveRun(runId, engine); savedRun = runId to engine }
        guard.cancel(); screen = Screen.TITLE; audio.start()
    }
    private fun resumeGame() {
        countdown = 3.0; guard.cancel(); cadence.reset(SystemClock.elapsedRealtime())
        screen = Screen.COUNTDOWN; lastFrame = SystemClock.elapsedRealtimeNanos(); audio.start()
    }
    fun foreground() {
        if (active) { syncAwake(); return }
        active = true; syncAwake(); lastFrame = SystemClock.elapsedRealtimeNanos()
        if (screen != Screen.PAUSED) audio.start()
        Choreographer.getInstance().postFrameCallback(this)
    }
    fun background() {
        pauseGame(); active = false; syncAwake(); guard.cancel(); audio.pause()
        Choreographer.getInstance().removeFrameCallback(this)
    }
    override fun onDetachedFromWindow() {
        active = false; syncAwake(); Choreographer.getInstance().removeFrameCallback(this)
        super.onDetachedFromWindow()
    }
    override fun doFrame(frameTimeNanos: Long) {
        if (!active) return
        tick(SystemClock.elapsedRealtimeNanos())
        updateUi()
        arena?.advanceVisuals()
        Choreographer.getInstance().postFrameCallbackDelayed(this,
            if (screen == Screen.PLAYING || screen == Screen.COUNTDOWN) 0 else 100)
    }
    internal fun tick(now: Long) {
        val nanos = if (lastFrame == 0L) 0 else (now - lastFrame).coerceAtLeast(0)
        lastFrame = now
        when (screen) {
            Screen.COUNTDOWN -> {
                countdown -= nanos / 1e9
                if (countdown <= 0) {
                    screen = Screen.PLAYING; audio.play("slot"); announce(s(R.string.survive_announcement))
                }
            }
            Screen.PLAYING -> { engine.advance(nanos); processEvents() }
            else -> Unit
        }
    }
    internal fun processEvents() {
        // A screen transition can rebuild the arena while events are processed.
        for (event in engine.events) when (event) {
            is GameEvent.Burst -> {
                arena?.burst(event)
                audio.play(if (event.restore) "restore" else "burst")
                if (event.restore && store.vibration) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            is GameEvent.Effect -> { audio.play("slot"); announce(s(event.effect.text.label)); cadence.reset(SystemClock.elapsedRealtime()) }
            GameEvent.Cheat -> {
                audio.play("cheat")
                if (store.vibration) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                announce(s(R.string.cheat_announcement)); cadence.reset(SystemClock.elapsedRealtime())
            }
            GameEvent.Bounce -> audio.play("bounce")
            GameEvent.Finish -> {
                saveResult(); screen = Screen.RESULT
                announce(s(R.string.finish_announcement, ScoreFormat.score(engine.ticks)))
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

    private fun panel(color: Int = 0xff141e35.toInt(), stroke: Int = 0xff394761.toInt()) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(10f).toFloat(); setStroke(dp(1f), stroke)
    }
    private fun label(value: String, sp: Float = 16f, color: Int = white) = TextView(context).apply {
        text = value; textSize = sp; typeface = gameFont; setTextColor(color)
        setPadding(0, dp(3f), 0, dp(3f))
        // No auto-size/maxLines: system SP scaling, wrapping and parent scrolling preserve all copy.
    }
    private fun column(padding: Int = dp(16f)) = LinearLayout(context).apply {
        orientation = VERTICAL; setPadding(padding, padding, padding, padding)
    }
    private fun LinearLayout.addLabel(value: String, sp: Float = 16f, color: Int = white): TextView =
        label(value, sp, color).also { addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)) }
    private fun LinearLayout.action(value: String, primary: Boolean = false, click: () -> Unit): Button {
        val button = Button(context).apply {
            text = value; textSize = 18f; typeface = gameFont; isAllCaps = false
            minHeight = dp(56f); minimumHeight = dp(56f); minWidth = dp(48f)
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            setTextColor(if (primary) 0xff152020.toInt() else white)
            background = panel(if (primary) lime else 0xff141e35.toInt(), if (primary) lime else 0xff394761.toInt())
            setOnClickListener { click() }
        }
        addView(button, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10f) })
        return button
    }
    private fun scrollColumn(padding: Int = dp(16f), weighted: Boolean = false): LinearLayout {
        val body = column(padding)
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            addView(body, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, if (weighted) 0 else LayoutParams.MATCH_PARENT, if (weighted) 1f else 0f))
        return body
    }
    private fun buildScreen() {
        removeAllViews(); arena = null; scoreText = null; timeText = null; statusText = null
        liveText = null; effectText = null; slotText = null; countdownText = null; pauseButton = null
        when (screen) {
            Screen.TITLE -> buildTitle()
            Screen.COUNTDOWN, Screen.PLAYING -> buildGame()
            Screen.PAUSED -> buildPause()
            Screen.RESULT -> buildResult()
        }
        updateUi(true)
    }
    private fun buildTitle() {
        val body = scrollColumn()
        body.addLabel(s(R.string.reverse_breakout), 13f, cyan)
        body.addLabel(s(R.string.title_line_one), 30f)
        body.addLabel(s(R.string.title_line_two), 52f, lime)
        body.addLabel(s(R.string.title_tagline), 16f, muted)
        body.addView(ArenaView(context, this, store, true), LayoutParams(LayoutParams.MATCH_PARENT, dp(220f)))
        body.addLabel(s(R.string.title_hint), 16f)
        body.addLabel(s(R.string.personal_best) + "  " + ScoreFormat.score(bestTicks), 22f, cyan)
        body.addLabel(s(R.string.score_formula), 14f, muted)
        if (savedRun != null) {
            body.action(s(R.string.continue_run), true) {
                savedRun?.let { runId = it.first; engine = it.second }; resumeGame()
            }
            body.action(s(R.string.new_game)) { requestNewRun() }
        } else body.action(s(R.string.start_game), true) { requestNewRun() }
        body.action(s(R.string.top_100)) { rankings() }
        body.action(s(R.string.how_to_play)) { guide() }
        body.action(s(R.string.settings)) { settings() }
        body.addLabel(s(R.string.title_footer), 12f, muted)
    }
    private fun buildGame() {
        // Pause stays outside the scrolling game content, including at 200% text size.
        val header = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.TOP; setPadding(dp(12f), dp(4f), dp(8f), 0) }
        val numbers = object : LinearLayout(context) {
            override fun onInitializeAccessibilityNodeInfo(info: android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(info)
                info.contentDescription = statusDescription()
            }
        }.apply {
            orientation = VERTICAL; id = R.id.game_status_header
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            isFocusable = true; contentDescription = s(R.string.cpu_status)
        }
        numbers.addLabel(s(R.string.survival_score), 12f, muted).importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        scoreText = numbers.addLabel("", 32f).apply { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        timeText = numbers.addLabel("", 16f, cyan).apply { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        header.addView(numbers, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val actions = column(0)
        pauseButton = actions.action("Ⅱ") { pauseGame() }.apply { contentDescription = s(R.string.pause_action) }
        header.addView(actions, LayoutParams(dp(64f), LayoutParams.WRAP_CONTENT))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val body = scrollColumn(dp(4f), true)
        countdownText = body.addLabel("", 26f, lime).apply { gravity = Gravity.CENTER }
        // Reserve the largest banner before play: effect/penalty text must not move tap targets.
        val banner = FrameLayout(context)
        effectText = label(s(R.string.restore_hint),16f,cyan).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8f),dp(4f),dp(8f),dp(4f))
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val bannerSp = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,26f,resources.displayMetrics)
        banner.addView(effectText,FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT))
        body.addView(banner,LayoutParams(LayoutParams.MATCH_PARENT,ceil(bannerSp*3.6f).toInt()+dp(8f)))
        liveText = LiveStatusView(context).apply { id = R.id.game_live_status }
        body.addView(liveText, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val field = ArenaView(context, this, store).apply { minimumWidth = ArenaLayout.minimumWidth(dp(48f)) }
        arena = field
        val horizontal = HorizontalScrollView(context).apply {
            isFillViewport = true
            addView(field, FrameLayout.LayoutParams(ArenaLayout.minimumWidth(dp(48f)), dp(480f)))
        }
        body.addView(horizontal, LayoutParams(LayoutParams.MATCH_PARENT, dp(480f)))
        body.addLabel(s(R.string.field_scroll_hint), 12f, muted)
        val bottom = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.TOP }
        statusText = QuietStatusText(context).apply {
            id = R.id.game_status; textSize = 16f; typeface = gameFont; setTextColor(white)
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f)); background = panel()
        }
        bottom.addView(statusText, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val slot = column(dp(8f)).apply { background = panel(0xff17162d.toInt(), 0xff685487.toInt()) }
        slot.addLabel(s(R.string.cpu_slot), 12f, muted).apply { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        slotText = slot.addLabel("", 22f, lime).apply { gravity = Gravity.CENTER; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        bottom.addView(slot, LayoutParams(dp(104f), LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(6f) })
        body.addView(bottom, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        body.action(s(R.string.read_status)) { announce(statusDescription()) }
    }
    private fun buildPause() {
        val body = scrollColumn()
        body.addLabel(s(R.string.paused_label), 16f, cyan)
        body.addLabel(s(R.string.pause_title), 36f)
        body.addLabel(s(R.string.pause_hint), 18f, muted)
        body.addLabel(statusDescription(), 16f)
        body.action(s(R.string.resume_game), true) { resumeGame() }
        body.action(s(R.string.settings)) { settings() }
        body.action(s(R.string.save_and_home)) { goHome() }
    }
    private fun buildResult() {
        val body = scrollColumn()
        body.addLabel(s(R.string.all_destroyed), 16f, pink)
        body.addLabel(s(R.string.result_title), 34f)
        body.addLabel(s(R.string.survival_score), 16f, muted)
        body.addLabel(ScoreFormat.score(engine.ticks), 40f, lime)
        body.addLabel(s(R.string.elapsed_seconds, ScoreFormat.seconds(engine.ticks)), 24f, cyan)
        body.addLabel(if (recordError) s(R.string.save_error) else if (resultRank > 0) s(R.string.result_rank, resultRank) else s(R.string.outside_top_100), 20f)
        body.addLabel(s(R.string.result_counts, engine.restoredCount, engine.slotCount), 16f, muted)
        if (recordError) body.action(s(R.string.retry_save)) { saveResult(); buildScreen() }
        body.action(s(R.string.play_again), true) { requestNewRun() }
        body.action(s(R.string.view_rankings)) { rankings() }
        body.action(s(R.string.back_to_title)) { goHome() }
    }
    internal fun statusDescription(): String {
        val e = engine
        fun seconds(until: Long) = ceil(max(0.0, (until - e.elapsedNanos) / 1e9)).toLong()
        val speed = if (e.speedMultiplier < 1e9) e.speedMultiplier.toLong().toString() else "%.1e".format(Locale.US, e.speedMultiplier)
        return s(R.string.status_description, (e.elapsedNanos / 1_000_000_000L).toString(),
            ScoreFormat.score(e.ticks), e.remaining, e.balls.size, speed,
            seconds(e.nextSlotNanos).toString(), seconds(e.pierceUntil).toString(),
            seconds(e.splitUntil).toString(), seconds(e.lockedUntil).toString())
    }
    private fun updateUi(force: Boolean = false) {
        if (screen != Screen.PLAYING && screen != Screen.COUNTDOWN) return
        val now = SystemClock.elapsedRealtime()
        arena?.refreshCells()
        pauseButton?.isEnabled = true
        countdownText?.visibility = if (screen == Screen.COUNTDOWN) VISIBLE else GONE
        if (screen == Screen.COUNTDOWN) {
            val message = s(R.string.countdown_accessible, ceil(countdown).toInt().coerceAtLeast(1))
            if (countdownText?.text?.toString() != message) countdownText?.text = message
        }
        if (force || now - lastVisibleUpdate >= 100) {
            lastVisibleUpdate = now
            liveText?.update(s(R.string.live_status, engine.remaining, engine.balls.size))
            scoreText?.text = ScoreFormat.score(engine.ticks)
            timeText?.text = s(R.string.elapsed_seconds, ScoreFormat.seconds(engine.ticks))
            val e = engine
            val age = (e.elapsedNanos - e.lastEffectNanos) / 1e9
            effectText?.textSize = if (e.locked) 26f else 16f
            effectText?.text = when {
                e.locked -> s(R.string.cheat_title) + "  " + s(R.string.cheat_timer, "%.1f".format(Locale.US, (e.lockedUntil - e.elapsedNanos) / 1e9))
                e.lastEffect != null && age < 2.8 -> s(e.lastEffect!!.text.label)
                e.remaining <= 8 -> s(R.string.danger_hint, e.remaining)
                else -> s(R.string.restore_hint)
            }
            effectText?.setTextColor(if (e.locked || e.remaining <= 8) pink else cyan)
            val effect = if (e.lastEffect != null && age < 1.15) e.lastEffect!! else SlotEffect.entries[(e.elapsedNanos / 100_000_000 % SlotEffect.entries.size).toInt()]
            slotText?.text = s(effect.text.symbol) + (effect.text.secondary?.let { "\n" + s(it) } ?: "")
        }
        if (force || now - lastStatusUpdate >= 1000) {
            lastStatusUpdate = now
            val e = engine
            fun remaining(until: Long) = ceil(max(0.0,(until-e.elapsedNanos)/1e9)).toLong().toString()
            val speed = if (e.speedMultiplier < 1e9) e.speedMultiplier.toLong().toString() else "%.1e".format(Locale.US,e.speedMultiplier)
            statusText?.text = s(R.string.visible_cpu_status,e.balls.size,speed,remaining(e.nextSlotNanos),
                remaining(e.pierceUntil),remaining(e.splitUntil),remaining(e.lockedUntil))
        }
        if (force || now - lastStatusEvent >= 5000) {
            lastStatusEvent = now
            statusText?.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
            // Invalidate the service's cached header node without announcing every score tick.
            findViewById<View>(R.id.game_status_header)?.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        }
        if (force) {
            lastSpokenStatus=s(R.string.live_status,engine.remaining,engine.balls.size)
            cadence.reset(now)
        } else if (screen == Screen.PLAYING && cadence.due(now)) {
            val message=s(R.string.live_status,engine.remaining,engine.balls.size)
            if(message!=lastSpokenStatus) { liveText?.publish();lastSpokenStatus=message }
        }
    }
    // Visible detail refreshes once a second; automatic speech is a compact, 5-second live region.
    private inner class QuietStatusText(context: Context) : TextView(context) {
        override fun sendAccessibilityEventUnchecked(event: AccessibilityEvent) {
            if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) super.sendAccessibilityEventUnchecked(event)
        }
        override fun onInitializeAccessibilityNodeInfo(info: android.view.accessibility.AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.text = statusDescription()
        }
    }
    internal fun restoreAccessible(index: Int): Boolean {
        if (screen != Screen.PLAYING) return false
        tick(SystemClock.elapsedRealtimeNanos())
        if (screen != Screen.PLAYING || !engine.restore(index)) return false
        processEvents(); updateUi(); return true
    }
    internal fun blockCenterOnScreen(index: Int): PointF = requireNotNull(arena).cellCenterOnScreen(index)
    internal fun blockCell(index: Int): View = requireNotNull(arena).getChildAt(index)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) multipleGesture = false
        if (screen == Screen.PLAYING && event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            tick(SystemClock.elapsedRealtimeNanos()); guard.multiple(engine); processEvents(); multipleGesture = true
            val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            super.dispatchTouchEvent(cancel); cancel.recycle()
        }
        if (multipleGesture) return true
        return super.dispatchTouchEvent(event)
    }
    @Suppress("DEPRECATION")
    private fun announce(message: String) { announceForAccessibility(message) }
}
