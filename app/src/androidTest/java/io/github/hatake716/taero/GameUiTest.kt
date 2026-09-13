package io.github.hatake716.taero

import android.graphics.PointF
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.min

@RunWith(AndroidJUnit4::class)
class GameUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var activity: MainActivity
    private fun onActivity(action: (MainActivity) -> Unit) { instrumentation.runOnMainSync { action(activity) } }
    private fun awaitScreen(expected: GameView.Screen) {
        val deadline=SystemClock.uptimeMillis()+2500
        var actual=GameView.Screen.TITLE
        do {
            onActivity { actual=it.gameView.screen }
            if(actual==expected) return
            SystemClock.sleep(30)
        } while(SystemClock.uptimeMillis()<deadline)
        assertEquals(expected,actual)
    }
    @Before fun setup() {
        Configurator.getInstance().waitForIdleTimeout = 0
        context.getSharedPreferences("taero.v1",0).edit().clear().commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity = it }
        assertTrue(device.wait(Until.hasObject(By.text("神になって耐える  →")),5000))
    }
    @After fun cleanup() {
        if (::activity.isInitialized) onActivity { it.gameView.pauseGame() }
        if (::scenario.isInitialized) scenario.close()
    }

    private fun playingFixture() {
        onActivity {
            val v=it.gameView
            v.beginRun(); v.screen=GameView.Screen.PLAYING
            v.engine.balls.clear(); v.engine.serveAt=Long.MAX_VALUE
            v.engine.nextSlotNanos=Long.MAX_VALUE
        }
    }

    private fun point(index: Int): PointF {
        var p=PointF()
        onActivity {
            val v=it.gameView; val loc=IntArray(2); v.getLocationOnScreen(loc)
            val s=min(v.width/900f,v.height/1800f)
            val b=GameEngine.blockBox(index)
            p=PointF(loc[0]+(v.width-900*s)/2+((b.left+b.right).toFloat()/2+50)*s,
                loc[1]+(v.height-1800*s)/2+((b.top+b.bottom).toFloat()/2+413)*s)
        }
        return p
    }

    private fun inject(action: Int, points: List<PointF>, down: Long) {
        val props=points.indices.map { i -> MotionEvent.PointerProperties().apply { id=i; toolType=MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
        val coords=points.map { p -> MotionEvent.PointerCoords().apply { x=p.x;y=p.y;pressure=1f;size=.1f } }.toTypedArray()
        val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,points.size,props,coords,0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0)
        assertTrue(instrumentation.uiAutomation.injectInputEvent(event,true)); event.recycle()
    }

    private fun twoFingers(a: PointF,b: PointF) {
        val down=SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN,listOf(a),down)
        inject(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(a,b),down)
        inject(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(a,b),down)
        inject(MotionEvent.ACTION_UP,listOf(a),down)
    }

    private fun shot(name: String) {
        // Accessibility can observe a new display list before the compositor presents it.
        SystemClock.sleep(500)
        val folder=File(context.getExternalFilesDir(null),"screenshots").apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(folder,"$name.png")))
    }

    @Test fun titleGuideAndSettingsAreReachable() {
        shot("title")
        device.findObject(By.text("遊び方")).click()
        assertTrue(device.wait(Until.hasObject(By.text("神さまの遊び方")),2000))
        device.findObject(By.text("わかった！")).click()
        assertTrue(device.wait(Until.hasObject(By.text("音と演出")),2000))
        device.findObject(By.text("音と演出")).click()
        assertTrue(device.wait(Until.hasObject(By.text("BGM：電子音 × ピアノ × ドラム")),2000))
        device.findObject(By.text("BGM：電子音 × ピアノ × ドラム")).click()
        val deadline=SystemClock.uptimeMillis()+1500
        while(GameStore(context).music && SystemClock.uptimeMillis()<deadline) SystemClock.sleep(30)
        assertFalse(GameStore(context).music)
        device.findObject(By.text("閉じる")).click()
    }

    @Test fun menuStartCountsDownThenRunsAndPauses() {
        device.findObject(By.text("神になって耐える  →")).click()
        awaitScreen(GameView.Screen.COUNTDOWN)
        onActivity { assertEquals(GameView.Screen.COUNTDOWN,it.gameView.screen) }
        assertTrue(device.wait(Until.hasObject(By.text("Ⅱ")),5000))
        device.findObject(By.text("Ⅱ")).click()
        awaitScreen(GameView.Screen.PAUSED)
        var time=0L
        onActivity { assertEquals(GameView.Screen.PAUSED,it.gameView.screen); time=it.gameView.engine.elapsedNanos }
        SystemClock.sleep(300)
        onActivity { assertEquals(time,it.gameView.engine.elapsedNanos) }
        shot("pause")
    }

    @Test fun singleFingerTapRestoresOnlyItsCellAndNotOnDown() {
        playingFixture()
        onActivity { it.gameView.engine.blocks[9]=false; it.gameView.engine.blocks[10]=false }
        val p=point(9);val down=SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN,listOf(p),down)
        onActivity { assertFalse(it.gameView.engine.blocks[9]) }
        inject(MotionEvent.ACTION_UP,listOf(p),down)
        onActivity {
            assertTrue(it.gameView.engine.blocks[9]);assertFalse(it.gameView.engine.blocks[10])
            assertEquals(1,it.gameView.engine.restoredCount)
        }
    }

    @Test fun actualMultiTouchShowsPenaltyAndBlocksAllRestoresForThreeSeconds() {
        playingFixture()
        onActivity { it.gameView.engine.blocks[9]=false; it.gameView.engine.blocks[10]=false }
        val a=point(9);val b=point(10)
        twoFingers(a,b)
        onActivity {
            assertTrue(it.gameView.engine.locked);assertFalse(it.gameView.engine.blocks[9]);assertFalse(it.gameView.engine.blocks[10])
        }
        shot("cheat-alert")
        device.click(a.x.toInt(),a.y.toInt())
        onActivity { assertFalse(it.gameView.engine.blocks[9]) }
        SystemClock.sleep(3050)
        device.click(a.x.toInt(),a.y.toInt())
        onActivity { assertFalse(it.gameView.engine.locked);assertTrue(it.gameView.engine.blocks[9]) }
    }

    @Test fun draggingAcrossEmptyCellsDoesNotRegenerateThem() {
        playingFixture()
        onActivity { for (i in 8..15) it.gameView.engine.blocks[i]=false }
        val a=point(8);val b=point(15)
        device.swipe(a.x.toInt(),a.y.toInt(),b.x.toInt(),b.y.toInt(),12)
        onActivity { assertEquals(0,it.gameView.engine.restoredCount) }
    }

    @Test fun cancelEventCannotRegenerateOrLeakIntoNextGesture() {
        playingFixture();onActivity { it.gameView.engine.blocks[9]=false }
        val a=point(9);val down=SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN,listOf(a),down);inject(MotionEvent.ACTION_CANCEL,listOf(a),down)
        onActivity { assertFalse(it.gameView.engine.blocks[9]) }
        device.click(a.x.toInt(),a.y.toInt())
        onActivity { assertTrue(it.gameView.engine.blocks[9]) }
    }

    @Test fun slotActuallyFiresAtSevenSecondsAndDisplaysState() {
        playingFixture()
        onActivity {
            val e=it.gameView.engine
            e.elapsedNanos=6_900_000_000;e.nextSlotNanos=7_000_000_000
        }
        SystemClock.sleep(400)
        onActivity { assertEquals(1,it.gameView.engine.slotCount);assertNotNull(it.gameView.engine.lastEffect) }
        shot("slot-effect")
    }

    @Test fun restoreSavedGameAfterActivityRecreationIncludesPenaltyAndEffects() {
        playingFixture()
        onActivity {
            val e=it.gameView.engine;e.blocks[9]=false;e.applyEffect(SlotEffect.SPEED_TRIPLE)
            e.applyEffect(SlotEffect.PIERCE);e.penalize();it.gameView.pauseGame()
        }
        val saved=GameStore(context).loadRun()!!
        assertEquals(3.0,saved.second.speedMultiplier,0.0);assertTrue(saved.second.locked)
        scenario.recreate()
        scenario.onActivity { activity = it }
        assertTrue(device.wait(Until.hasObject(By.text("つづきから耐える  →")),3000))
        device.findObject(By.text("つづきから耐える  →")).click()
        awaitScreen(GameView.Screen.COUNTDOWN)
        onActivity {
            assertEquals(saved.second.elapsedNanos,it.gameView.engine.elapsedNanos)
            assertEquals(saved.second.lockedUntil,it.gameView.engine.lockedUntil)
            assertFalse(it.gameView.engine.blocks[9]);assertEquals(GameView.Screen.COUNTDOWN,it.gameView.screen)
        }
    }

    @Test fun lastBlockTriggersResultAndDurableRankingExactlyOnce() {
        playingFixture()
        onActivity {
            val e=it.gameView.engine;e.elapsedNanos=12_345_600_000
            e.blocks.fill(false);e.blocks[32]=true
            e.balls += Ball(55.0,417.4,0.0,-1.0,1)
        }
        assertTrue(device.wait(Until.hasObject(By.text("もう一度、耐える  →")),2500))
        onActivity { assertEquals(GameView.Screen.RESULT,it.gameView.screen);assertEquals(0,it.gameView.engine.remaining) }
        val list=GameStore(context).rankings()
        assertEquals(1,list.size);assertTrue(list[0].ticks>=123456);assertNull(GameStore(context).loadRun())
        shot("result")
        device.findObject(By.text("ランキングを見る")).click()
        assertTrue(device.wait(Until.hasObject(By.text("耐久ランキング  1 / 100")),1500))
        shot("ranking")
        assertEquals(1,GameStore(context).rankings().size)
    }

    @Test fun top100PersistAcrossStoreInstances() {
        val store=GameStore(context)
        for (i in 0..104) store.record(ScoreEntry("test-$i",i*10001L,1000L+i,1,2))
        val read=GameStore(context).rankings()
        assertEquals(100,read.size);assertEquals(104*10001L,read.first().ticks);assertEquals(5*10001L,read.last().ticks)
        device.findObject(By.text("耐久ランキング  TOP 100")).click()
        assertTrue(device.wait(Until.hasObject(By.text("耐久ランキング  100 / 100")),2000))
        val scroll=device.findObject(By.scrollable(true))
        assertNotNull(scroll)
    }
}
