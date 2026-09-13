package io.github.hatake716.taero

import android.graphics.PointF
import android.os.Build
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
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.min

/** Opt-in media capture: only reads game state and sends ordinary touchscreen input. */
@RunWith(AndroidJUnit4::class)
class StoreCaptureTest {
    @Test fun captureNaturalGame() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("storeCapture")=="true")
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk")) { "Capture is emulator-only" }
        val ins=InstrumentationRegistry.getInstrumentation()
        val context=ins.targetContext
        val device=UiDevice.getInstance(ins)
        Configurator.getInstance().waitForIdleTimeout=0
        context.getSharedPreferences("taero.v1",0).edit().clear().commit()
        val folder=File(context.getExternalFilesDir(null),"store-capture").apply { mkdirs() }
        val shots=JSONArray(); val observations=JSONArray()
        var epoch=0L
        lateinit var activity: MainActivity
        fun main(action: (MainActivity)->Unit) { ins.runOnMainSync { action(activity) } }
        fun shot(name: String) {
            assertTrue(device.takeScreenshot(File(folder,"$name.png")))
            main { a -> shots.put(JSONObject().put("file","$name.png").put("videoSeconds",(SystemClock.elapsedRealtime()-epoch)/1000.0)
                .put("gameSeconds",a.gameView.engine.elapsedNanos/1e9).put("score",ScoreFormat.score(a.gameView.engine.ticks))) }
        }
        fun point(index: Int): PointF {
            var p=PointF()
            main {
                val v=it.gameView; val loc=IntArray(2);v.getLocationOnScreen(loc)
                val scale=min(v.width/900f,v.height/1800f); val b=GameEngine.blockBox(index)
                p=PointF(loc[0]+(v.width-900*scale)/2+((b.left+b.right).toFloat()/2+50)*scale,
                    loc[1]+(v.height-1800*scale)/2+((b.top+b.bottom).toFloat()/2+413)*scale)
            }
            return p
        }
        fun multiTouch() {
            val a=point(9);val b=point(10);val down=SystemClock.uptimeMillis()
            fun inject(action: Int, pts: List<PointF>) {
                val props=pts.indices.map { i -> MotionEvent.PointerProperties().apply { id=i;toolType=MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
                val coords=pts.map { p -> MotionEvent.PointerCoords().apply { x=p.x;y=p.y;pressure=1f;size=.1f } }.toTypedArray()
                val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,pts.size,props,coords,0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0)
                ins.uiAutomation.injectInputEvent(event,true);event.recycle()
            }
            inject(MotionEvent.ACTION_DOWN,listOf(a))
            inject(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(a,b))
            inject(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(a,b))
            inject(MotionEvent.ACTION_UP,listOf(a))
        }
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity=it }
        val record=ins.uiAutomation.executeShellCommand("screenrecord --bit-rate 14000000 --time-limit 180 /sdcard/taero-store.mp4")
        epoch=SystemClock.elapsedRealtime()
        try {
            SystemClock.sleep(1500)
            shot("08-title")
            device.findObject(By.text(activity.getString(R.string.start_game))).click()
            val deadline=SystemClock.elapsedRealtime()+155_000
            var shotIndex=0;var cheat=false;var finished=false
            var lastRestore=0L;var lastObservation=0L;var restoreCursor=0
            while(SystemClock.elapsedRealtime()<deadline) {
                var sec=0.0;var empty=emptyList<Int>();var remaining=40;var locked=false;var slots=0
                main { a ->
                    val e=a.gameView.engine;sec=e.elapsedNanos/1e9;empty=e.blocks.indices.filter { !e.blocks[it] }
                    remaining=e.remaining;locked=e.locked;slots=e.slotCount;finished=a.gameView.screen==GameView.Screen.RESULT
                    if(SystemClock.elapsedRealtime()-lastObservation>=45) {
                        observations.put(JSONObject().put("videoSeconds",(SystemClock.elapsedRealtime()-epoch)/1000.0)
                            .put("seconds",sec).put("destroyed",e.destroyedCount).put("restored",e.restoredCount)
                            .put("slots",slots).put("effect",e.lastEffect?.name).put("balls",e.balls.size)
                            .put("remaining",remaining).put("speed",e.speedMultiplier).put("locked",locked))
                        lastObservation=SystemClock.elapsedRealtime()
                    }
                }
                if(finished) break
                if(shotIndex<4 && sec>=3.5+shotIndex*3.5) { shotIndex++;shot("0$shotIndex-gameplay") }
                if(!cheat && sec>=16.0) { multiTouch();SystemClock.sleep(300);shot("05-one-finger-rule");cheat=true }
                if(sec<39 && !locked && empty.isNotEmpty() && remaining<35 && SystemClock.elapsedRealtime()-lastRestore>=105) {
                    val p=point(empty[restoreCursor++ % empty.size]);device.click(p.x.toInt(),p.y.toInt());lastRestore=SystemClock.elapsedRealtime()
                }
                SystemClock.sleep(35)
            }
            assertTrue("Natural run should reach its result",finished)
            SystemClock.sleep(700);shot("06-result")
            device.findObject(By.text(activity.getString(R.string.view_rankings))).click()
            assertTrue(device.wait(Until.hasObject(By.text(activity.getString(R.string.rankings_title,1))),2000))
            SystemClock.sleep(500);shot("07-ranking")
            File(folder,"capture.json").writeText(JSONObject().put("language",activity.resources.configuration.locales[0].language)
                .put("epochElapsedRealtimeMs",epoch).put("screenshots",shots).put("observations",observations)
                .put("source","Unmodified game state; single-finger taps and one two-finger gesture injected by instrumentation")
                .toString(2))
        } finally {
            ins.uiAutomation.executeShellCommand("pkill -2 screenrecord").close()
            SystemClock.sleep(700);record.close()
            main { it.gameView.pauseGame() };scenario.close()
        }
    }
}
