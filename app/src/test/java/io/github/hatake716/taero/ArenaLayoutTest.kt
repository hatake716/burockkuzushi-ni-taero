package io.github.hatake716.taero

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.roundToInt

class ArenaLayoutTest {
    @Test fun allFortyTargetsAreAtLeast48DpAndNeverOverlap() {
        for (density in listOf(1f,1.75f,2.625f,3f)) for (screenDp in listOf(320,360,411,600)) {
            val target=ceil(48*density).toInt()
            val width=maxOf((screenDp*density).toInt(),ArenaLayout.minimumWidth(target))
            val g=ArenaLayout(width,(480*density).toInt(),target)
            val cells=(0..39).map(g::cell)
            for((index,c) in cells.withIndex()) {
                assertTrue(c.right.roundToInt()-c.left.roundToInt() >= target)
                assertTrue(c.bottom.roundToInt()-c.top.roundToInt() >= target)
                assertEquals(index,g.hit(((c.left+c.right)/2).toFloat(),((c.top+c.bottom)/2).toFloat()))
                val block=g.block(index)
                assertTrue(block.left>=c.left && block.right<=c.right && block.top>=c.top && block.bottom<=c.bottom)
                for((j,other) in cells.withIndex()) if(j!=index)
                    assertFalse(c.left<other.right && c.right>other.left && c.top<other.bottom && c.bottom>other.top)
            }
            assertNull(g.hit(-1f,0f));assertNull(g.hit(width.toFloat(),0f))
        }
    }
    @Test fun spokenSummaryIsNeverScheduledAtFrameRate() {
        val cadence=StatusCadence()
        cadence.reset(100)
        for(t in 101L until 5100L step 16) assertFalse(cadence.due(t))
        assertTrue(cadence.due(5100));assertFalse(cadence.due(9999));assertTrue(cadence.due(10100))
        cadence.reset(12000) // Slot/penalty speech postpones periodic speech.
        assertFalse(cadence.due(16999));assertTrue(cadence.due(17000))
    }
}
