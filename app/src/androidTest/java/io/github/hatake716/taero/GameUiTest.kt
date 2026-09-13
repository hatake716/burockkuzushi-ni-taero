package io.github.hatake716.taero

import android.app.LocaleManager
import android.graphics.PointF
import android.os.LocaleList
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
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
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class GameUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var activity: MainActivity
    private fun s(id: Int, vararg args: Any) = activity.getString(id, *args)
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
        val expectedLanguage=InstrumentationRegistry.getArguments().getString("expectedLanguage")
        if (expectedLanguage != null) assertEquals(expectedLanguage,activity.resources.configuration.locales[0].language)
        assertTrue(device.wait(Until.hasObject(By.text(s(R.string.start_game))),5000))
        // Accessible nodes may appear before the new window accepts touch input.
        val deadline=SystemClock.uptimeMillis()+2500
        var focused=false
        do {
            onActivity { focused=it.hasWindowFocus() && it.gameView.isLaidOut }
            if(!focused) SystemClock.sleep(30)
        } while(!focused && SystemClock.uptimeMillis()<deadline)
        assertTrue("The game window must accept input before tapping",focused)
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
        device.findObject(By.text(s(R.string.how_to_play))).click()
        assertTrue(device.wait(Until.hasObject(By.text(s(R.string.guide_title))),2000))
        shot("guide")
        device.findObject(By.res("android:id/button1")).click()
        assertTrue(device.wait(Until.hasObject(By.text(s(R.string.settings))),2000))
        device.findObject(By.text(s(R.string.settings))).click()
        assertTrue(device.wait(Until.hasObject(By.text(s(R.string.setting_music))),2000))
        shot("settings")
        device.findObject(By.text(s(R.string.setting_music))).click()
        val deadline=SystemClock.uptimeMillis()+1500
        while(GameStore(context).music && SystemClock.uptimeMillis()<deadline) SystemClock.sleep(30)
        assertFalse(GameStore(context).music)
        device.findObject(By.res("android:id/button1")).click()
    }

    @Test fun menuStartCountsDownThenRunsAndPauses() {
        device.findObject(By.text(s(R.string.start_game))).click()
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

    private fun combinedSlotFixture(effect: SlotEffect) {
        playingFixture()
        onActivity {
            it.gameView.engine=GameEngine(object: Random() {
                override fun nextBits(bitCount: Int)=0
                override fun nextInt(until: Int)=effect.ordinal
            }).apply {
                elapsedNanos=6_900_000_000; nextSlotNanos=7_000_000_000
                balls.clear(); balls += Ball(400.0,850.0,0.0,-1.0,0)
                serveAt=Long.MAX_VALUE
            }
        }
        val deadline=SystemClock.uptimeMillis()+2500
        var selected=false
        do {
            onActivity { selected=it.gameView.engine.lastEffect==effect }
            if(!selected) SystemClock.sleep(20)
        } while(!selected && SystemClock.uptimeMillis()<deadline)
        assertTrue(selected)
        onActivity { assertEquals(1,it.gameView.engine.slotCount) }
    }

    @Test fun combinedFiveBallsAndPierceDisplaysAndSurvivesSaving() {
        combinedSlotFixture(SlotEffect.ADD_FIVE_PIERCE)
        onActivity {
            assertEquals(6,it.gameView.engine.balls.size)
            assertTrue(it.gameView.engine.piercing)
        }
        shot("slot-five-pierce")
        onActivity { it.gameView.pauseGame() }
        val restored=GameStore(context).loadRun()!!.second
        assertEquals(SlotEffect.ADD_FIVE_PIERCE,restored.lastEffect)
        assertEquals(6,restored.balls.size); assertTrue(restored.piercing)
        assertEquals(12_000_000_000,restored.pierceUntil)
    }

    @Test fun combinedTripleSpeedAndSplitDisplaysAndSurvivesSaving() {
        combinedSlotFixture(SlotEffect.TRIPLE_SPLIT)
        onActivity {
            assertEquals(3.0,it.gameView.engine.speedMultiplier,0.0)
            assertTrue(it.gameView.engine.splitting)
        }
        shot("slot-triple-split")
        onActivity { it.gameView.pauseGame() }
        val restored=GameStore(context).loadRun()!!.second
        assertEquals(SlotEffect.TRIPLE_SPLIT,restored.lastEffect)
        assertEquals(3.0,restored.speedMultiplier,0.0); assertTrue(restored.splitting)
        assertEquals(12_000_000_000,restored.splitUntil)
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
        assertTrue(device.wait(Until.hasObject(By.text(s(R.string.continue_run))),3000))
        device.findObject(By.text(s(R.string.continue_run))).click()
        awaitScreen(GameView.Screen.COUNTDOWN)
        onActivity {
            assertEquals(saved.second.elapsedNanos,it.gameView.engine.elapsedNanos)
            assertEquals(saved.second.lockedUntil,it.gameView.engine.lockedUntil)
            assertFalse(it.gameView.engine.blocks[9]);assertEquals(GameView.Screen.COUNTDOWN,it.gameView.screen)
        }
    }

    @Test @SdkSuppress(minSdkVersion=33)
    fun languageChangeRecreatesUiAndKeepsRunAndRanking() {
        val manager=context.getSystemService(LocaleManager::class.java)
        val original=manager.applicationLocales
        val target=if(activity.resources.configuration.locales[0].language=="ja") "en" else "ja"
        val entry=ScoreEntry("locale-recreation",123456,1234,5,6)
        GameStore(context).record(entry)
        playingFixture()
        onActivity {
            it.gameView.engine.applyEffect(SlotEffect.ADD_FIVE_PIERCE)
            it.gameView.engine.applyEffect(SlotEffect.TRIPLE_SPLIT)
            it.gameView.pauseGame()
        }
        val saved=GameStore(context).loadRun()!!
        val previous=activity
        try {
            manager.applicationLocales=LocaleList.forLanguageTags(target)
            val deadline=SystemClock.uptimeMillis()+5000
            var replacement: MainActivity?=null
            do {
                instrumentation.runOnMainSync {
                    replacement=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<MainActivity>().firstOrNull { it!==previous }
                }
                if(replacement==null) SystemClock.sleep(30)
            } while(replacement==null && SystemClock.uptimeMillis()<deadline)
            assertNotNull("Language change should recreate the activity",replacement)
            activity=replacement!!
            assertEquals(target,activity.resources.configuration.locales[0].language)
            assertTrue(device.wait(Until.hasObject(By.text(s(R.string.continue_run))),2500))
            assertEquals(listOf(entry),GameStore(context).rankings())
            device.findObject(By.text(s(R.string.continue_run))).click()
            awaitScreen(GameView.Screen.COUNTDOWN)
            onActivity {
                assertEquals(saved.second.elapsedNanos,it.gameView.engine.elapsedNanos)
                assertEquals(saved.second.balls.size,it.gameView.engine.balls.size)
                assertEquals(saved.second.pierceUntil,it.gameView.engine.pierceUntil)
                assertEquals(saved.second.splitUntil,it.gameView.engine.splitUntil)
                assertEquals(3.0,it.gameView.engine.speedMultiplier,0.0)
            }
        } finally {
            onActivity { it.gameView.pauseGame() }
            scenario.close()
            manager.applicationLocales=original
            scenario=ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity { activity=it }
        }
    }

    @Test fun lastBlockTriggersResultAndDurableRankingExactlyOnce() {
        playingFixture()
        onActivity {
            val e=it.gameView.engine;e.elapsedNanos=12_345_600_000
            e.blocks.fill(false);e.blocks[32]=true
            e.balls += Ball(55.0,417.4,0.0,-1.0,1)
        }
        assertTrue(device.wait(Until.hasObject(By.text(s(R.string.play_again))),2500))
        onActivity { assertEquals(GameView.Screen.RESULT,it.gameView.screen);assertEquals(0,it.gameView.engine.remaining) }
        val list=GameStore(context).rankings()
        assertEquals(1,list.size);assertTrue(list[0].ticks>=123456);assertNull(GameStore(context).loadRun())
        shot("result")
        device.findObject(By.text(s(R.string.view_rankings))).click()
        assertTrue(device.wait(Until.hasObject(By.text(s(R.string.rankings_title, 1))),1500))
        shot("ranking")
        assertEquals(1,GameStore(context).rankings().size)
    }

    @Test fun top100PersistAcrossStoreInstances() {
        val store=GameStore(context)
        for (i in 0..104) store.record(ScoreEntry("test-$i",i*10001L,1000L+i,1,2))
        val read=GameStore(context).rankings()
        assertEquals(100,read.size);assertEquals(104*10001L,read.first().ticks);assertEquals(5*10001L,read.last().ticks)
        device.findObject(By.text(s(R.string.top_100))).click()
        assertTrue(device.wait(Until.hasObject(By.text(s(R.string.rankings_title, 100))),2000))
        val scroll=device.findObject(By.scrollable(true))
        assertNotNull(scroll)
    }
}
