package io.github.hatake716.taero

import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AccessibilityRegressionTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private val context get()=ins.targetContext
    private val device=UiDevice.getInstance(ins)
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var activity: MainActivity
    private fun main(action: (MainActivity)->Unit) { ins.runOnMainSync { action(activity) } }
    private fun s(id: Int)=activity.getString(id)
    private fun awaitLayout() { ins.waitForIdleSync(); SystemClock.sleep(100) }
    private fun awake()=(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0
    private fun textViews(view: View): List<TextView> = when(view) {
        is TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { textViews(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun click(value: String) {
        repeat(12) {
            val node=device.findObject(By.text(java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(value),java.util.regex.Pattern.CASE_INSENSITIVE)))
            if(node!=null && node.visibleBounds.height()>=48*activity.resources.displayMetrics.density) { node.click(); awaitLayout(); return }
            device.findObject(By.scrollable(true))?.scroll(Direction.DOWN,.65f,400)
            // Let scroll/fling settle before locating a moving button for the next tap.
            SystemClock.sleep(500);awaitLayout()
        }
        shot("unreachable-${value.hashCode()}-${activity.resources.configuration.fontScale}")
        fail("Not reachable by scrolling: $value")
    }
    private fun shot(name: String) {
        SystemClock.sleep(250)
        val folder=File(context.getExternalFilesDir(null),"accessibility").apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(folder,"$name.png")))
        device.dumpWindowHierarchy(File(folder,"$name.xml"))
    }
    @Before fun setup() {
        Configurator.getInstance().waitForIdleTimeout=0
        context.getSharedPreferences("taero.v1",0).edit().clear().commit()
        scenario=ActivityScenario.launch(MainActivity::class.java);scenario.onActivity { activity=it };awaitLayout()
    }
    @After fun finish() { main { it.gameView.pauseGame() };scenario.close() }
    private fun fixture() {
        main {
            val v=it.gameView;v.beginRun();v.screen=GameView.Screen.PLAYING
            v.engine.balls.clear();v.engine.serveAt=Long.MAX_VALUE;v.engine.nextSlotNanos=Long.MAX_VALUE
        }
        awaitLayout()
    }
    @Test fun visibleStatusHeaderPublishesTimeBallsBlocksSpeedAndEffectTimers() {
        fixture()
        main {
            val e=it.gameView.engine
            e.blocks[9]=false;e.blocks[10]=false
            e.applyEffect(SlotEffect.ADD_FIVE_PIERCE);e.applyEffect(SlotEffect.TRIPLE_SPLIT)
            e.balls.forEach { ball->ball.dx=0.0;ball.dy=0.0 }
            e.nextSlotNanos=e.elapsedNanos+7_000_000_000
        }
        awaitLayout()
        val node=device.findObject(By.res(context.packageName,"game_status_header"))
        assertNotNull(node)
        val copy=node.contentDescription
        val ja=activity.resources.configuration.locales[0].language=="ja"
        for(word in if(ja) listOf("経過","スコア","38 / 40","玉 5","速度 3","次の抽選","貫通","分裂","再生禁止")
            else listOf("Elapsed","Score","Blocks 38 of 40","Balls 5","Speed 3","Next slot","Pierce","Split","Rebuild lock"))
            assertTrue("Missing status: $word in $copy",copy.contains(word))
        main { assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE,it.findViewById<View>(R.id.game_live_status).accessibilityLiveRegion) }
        shot("status-${if(ja) "ja" else "en"}-${activity.resources.configuration.fontScale}")
    }
    @Test fun onlyEmptyUnlockedBlocksOfferClickAndStaleActionsCannotRestore() {
        fixture()
        main {
            val v=it.gameView;val full=v.blockCell(9).createAccessibilityNodeInfo()
            assertFalse(full.isClickable)
            assertFalse(full.actionList.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK))
            assertFalse(v.blockCell(9).performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK,null))
            v.engine.blocks[9]=false;v.engine.blocks[10]=false
        }
        awaitLayout()
        main {
            val v=it.gameView;val empty=v.blockCell(9).createAccessibilityNodeInfo()
            assertTrue(empty.isClickable)
            assertTrue(v.blockCell(9).performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK,null))
            assertTrue(v.engine.blocks[9]);assertFalse(v.engine.blocks[10])
            v.engine.penalize()
        }
        awaitLayout()
        main {
            val v=it.gameView
            assertFalse(v.blockCell(10).createAccessibilityNodeInfo().isClickable)
            assertFalse(v.blockCell(10).performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK,null))
            assertFalse(v.engine.blocks[10])
            v.engine.elapsedNanos=v.engine.lockedUntil
        }
        awaitLayout()
        main { assertTrue(it.gameView.blockCell(10).createAccessibilityNodeInfo().isClickable) }
    }
    @Test fun blockAndPauseTargetsMeasureAtLeast48Dp() {
        fixture()
        main {
            val v=it.gameView;val min=48*it.resources.displayMetrics.density
            for(i in 0..39) {
                val cell=v.blockCell(i)
                assertTrue("Block $i width ${cell.width} < $min",cell.width>=min)
                assertTrue("Block $i height ${cell.height} < $min",cell.height>=min)
                for(j in i+1..39) {
                    val other=v.blockCell(j)
                    assertFalse(Rect.intersects(Rect(cell.left,cell.top,cell.right,cell.bottom),Rect(other.left,other.top,other.right,other.bottom)))
                }
            }
        }
        val pause=device.findObject(By.desc(s(R.string.pause_action)))
        assertNotNull(pause)
        val r=pause.visibleBounds;val density=activity.resources.displayMetrics.density
        assertTrue(r.width()/density>=48);assertTrue(r.height()/density>=48)
        pause.click();awaitLayout();main { assertEquals(GameView.Screen.PAUSED,it.gameView.screen) }
    }
    @Test fun cancelAndBackFromNewGameConfirmationKeepExactSavedRun() {
        fixture()
        main { it.gameView.engine.blocks[9]=false;it.gameView.pauseGame();it.gameView.goHome() }
        awaitLayout()
        val prefs=context.getSharedPreferences("taero.v1",0);val before=prefs.getString("run",null)
        click(s(R.string.new_game));assertNotNull(device.findObject(By.text(s(R.string.replace_run_title))))
        shot("confirmation-${activity.resources.configuration.fontScale}")
        assertEquals(before,prefs.getString("run",null))
        click(s(R.string.cancel));assertEquals(before,prefs.getString("run",null))
        click(s(R.string.new_game));device.pressBack();awaitLayout();assertEquals(before,prefs.getString("run",null))
        scenario.recreate();scenario.onActivity { activity=it };awaitLayout()
        click(s(R.string.continue_run));main { assertFalse(it.gameView.engine.blocks[9]);it.gameView.pauseGame();it.gameView.goHome() }
        awaitLayout();val saved=GameStore(context).loadRun()!!.first
        click(s(R.string.new_game));click(s(R.string.replace_run_confirm))
        main { assertTrue(it.gameView.screen in listOf(GameView.Screen.COUNTDOWN,GameView.Screen.PLAYING)) }
        val replacement=GameStore(context).loadRun()!!
        assertNotEquals(saved,replacement.first)
        // Inspect the persisted replacement, independent of how long UI synchronization took.
        assertTrue(replacement.second.blocks.all { b->b });assertEquals(0L,replacement.second.elapsedNanos)
    }
    @Test fun screenStaysAwakeOnlyWhileForegroundGameplayOrCountdown() {
        main { assertFalse(awake()) }
        click(s(R.string.how_to_play))
        assertTrue(device.wait(Until.hasObject(By.text(s(R.string.guide_title))),2000))
        main { assertFalse(awake()) };device.pressBack();awaitLayout()
        click(s(R.string.settings));main { assertFalse(awake()) };device.pressBack();awaitLayout()
        // Scroll title back to its initial controls.
        main { it.gameView.beginRun();assertTrue(awake());it.gameView.screen=GameView.Screen.PLAYING;assertTrue(awake())
            it.gameView.pauseGame();assertFalse(awake());it.gameView.goHome();assertFalse(awake()) }
        click(s(R.string.top_100));main { assertFalse(awake()) };device.pressBack();awaitLayout()
        main { it.gameView.beginRun();it.gameView.background();assertFalse(awake());it.gameView.foreground();assertFalse(awake())
            it.gameView.screen=GameView.Screen.RESULT;assertFalse(awake()) }
    }
    @Test fun scaledTextWrapsAndEveryTitleActionRemainsReachable() {
        val scale=activity.resources.configuration.fontScale
        val metrics=JSONObject().put("fontScale",scale)
        main {
            val views=textViews(it.gameView)
            for(t in views) {
                if(t.text==s(R.string.title_tagline)) { metrics.put("taglineTextPx",t.textSize);metrics.put("taglineLines",t.lineCount) }
                if(t.text==s(R.string.start_game)) { metrics.put("buttonTextPx",t.textSize);metrics.put("buttonHeightPx",t.height) }
                assertTrue("Clipped text: ${t.text}", t.layout==null || t.layout.height<=t.height-t.paddingTop-t.paddingBottom)
            }
        }
        val folder=File(context.getExternalFilesDir(null),"accessibility").apply { mkdirs() }
        File(folder,"font-$scale.json").writeText(metrics.toString(2))
        shot("title-top-$scale")
        click(s(R.string.settings));shot("settings-$scale");device.pressBack();awaitLayout()
        shot("title-bottom-$scale")
        main { it.gameView.beginRun();it.gameView.pauseGame() };awaitLayout();shot("pause-$scale")
        click(s(R.string.save_and_home));assertNotNull(GameStore(context).loadRun())
    }
    @Test fun liveStatusEventsAreCoalescedWhileVisibleCountersChange() {
        fixture()
        val events=java.util.concurrent.CopyOnWriteArrayList<Long>()
        ins.uiAutomation.setOnAccessibilityEventListener { event ->
            if(event.eventType==android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                event.source?.viewIdResourceName==context.packageName+":id/game_live_status") events.add(event.eventTime)
        }
        try {
            repeat(26) { step ->
                main { it.gameView.engine.blocks[9]=step%2==0 }
                SystemClock.sleep(250)
            }
            assertTrue("A changed status should publish",events.isNotEmpty())
            assertTrue("Live updates flooded: $events",events.size<=2)
            if(events.size==2) assertTrue(events[1]-events[0]>=4800)
        } finally { ins.uiAutomation.setOnAccessibilityEventListener(null) }
    }
    @Test fun narrowArenaScrollKeepsLastColumnReachableWithoutDragRestores() {
        fixture()
        var horizontal: android.widget.HorizontalScrollView?=null
        var narrow=false
        var rowY=0
        main {
            val v=it.gameView
            horizontal=v.blockCell(0).parent.parent as android.widget.HorizontalScrollView
            narrow=(v.blockCell(0).parent as View).width>horizontal!!.width
            v.engine.blocks[7]=false
            v.blockCell(0).requestRectangleOnScreen(Rect(0,0,v.blockCell(0).width,v.blockCell(0).height),true)
        }
        awaitLayout()
        if(narrow) {
            main { rowY=it.gameView.blockCenterOnScreen(0).y.toInt() }
            // Stay inside the arena; starting at the system edge invokes Android's Back gesture.
            device.swipe(device.displayWidth*4/5,rowY,device.displayWidth/5,rowY,30)
            SystemClock.sleep(500);awaitLayout()
            main { assertTrue(horizontal!!.scrollX>0);assertEquals(0,it.gameView.engine.restoredCount) }
        }
        var point=android.graphics.PointF()
        main { point=it.gameView.blockCenterOnScreen(7) }
        if(narrow) shot("narrow-before-tap")
        device.click(point.x.toInt(),point.y.toInt());awaitLayout()
        if(narrow) shot("narrow-after-tap")
        main { assertTrue("Last column tap at $point; scroll=${horizontal!!.scrollX}; state=${it.gameView.screen}; locked=${it.gameView.engine.locked}",it.gameView.engine.blocks[7]);assertEquals(1,it.gameView.engine.restoredCount) }
    }

    @Test fun effectAndPenaltyBannersNeverMoveBlockTargets() {
        fixture()
        var before=android.graphics.PointF()
        main { before=it.gameView.blockCenterOnScreen(9);it.gameView.engine.applyEffect(SlotEffect.TRIPLE_SPLIT) }
        SystemClock.sleep(200);awaitLayout()
        main { assertEquals(before,it.gameView.blockCenterOnScreen(9));it.gameView.engine.penalize() }
        SystemClock.sleep(200);awaitLayout()
        main { assertEquals(before,it.gameView.blockCenterOnScreen(9)) }
    }

}
