package io.github.hatake716.taero

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

class GameEngineTest {
    private fun game() = GameEngine(Random(716))
    private fun GameEngine.noSlots() { nextSlotNanos = Long.MAX_VALUE }

    @Test fun boardHasExactlyEightColumnsAndFiveRows() {
        val e = game()
        assertEquals(40,e.remaining); assertEquals(1,e.balls.size)
        for (i in 0..39) {
            val b = GameEngine.blockBox(i)
            assertEquals(i,GameEngine.blockAt((b.left+b.right)/2,(b.top+b.bottom)/2))
        }
        assertNull(GameEngine.blockAt(900.0,100.0)); assertNull(GameEngine.blockAt(50.0,600.0))
    }

    @Test fun onlyDestroyedBlocksCanRegenerate() {
        val e=game()
        assertFalse(e.restore(0)); assertFalse(e.restore(-1)); assertFalse(e.restore(40))
        e.blocks[9]=false
        assertTrue(e.restore(9)); assertEquals(40,e.remaining); assertEquals(1,e.restoredCount)
        assertFalse(e.restore(9))
    }

    @Test fun tapIsCommittedOnlyAfterSinglePointerRelease() {
        val e=game(); val g=TapGuard(); e.blocks[3]=false
        g.down(3,true); assertFalse(e.blocks[3])
        assertTrue(g.up(e,3)); assertTrue(e.blocks[3])
        e.blocks[4]=false; g.down(4,true); g.cancel(); assertFalse(g.up(e,4))
    }

    @Test fun twoFingersCancelEvenTheFirstPendingRestore() {
        val e=game(); val g=TapGuard(); e.blocks[3]=false; e.blocks[4]=false
        g.down(3,true); g.multiple(e)
        assertFalse(g.up(e,3)); assertFalse(e.blocks[3]); assertFalse(e.restore(4))
        assertEquals(GameEngine.PENALTY_NANOS,e.lockedUntil)
    }

    @Test fun lockLastsExactlyThreeSecondsAndSimulationKeepsMoving() {
        val e=game(); e.noSlots(); e.blocks[0]=false
        e.penalize(); val startY=e.balls[0].y
        e.advance(2_999_900_000)
        assertTrue(e.locked); assertFalse(e.restore(0)); assertNotEquals(startY,e.balls[0].y,0.01)
        e.advance(100_000)
        assertFalse(e.locked); assertTrue(e.restore(0)); assertEquals(30000,e.ticks)
    }

    @Test fun furtherPointersDoNotExtendCurrentAlert() {
        val e=game(); e.penalize(); e.advance(500_000_000); e.penalize()
        assertEquals(3_000_000_000,e.lockedUntil)
    }

    @Test fun aGestureStartedDuringLockCannotRestoreAfterItExpires() {
        val e=game(); e.blocks[0]=false; e.penalize(); val g=TapGuard()
        g.down(0,!e.locked); e.advance(3_000_000_000)
        assertFalse(g.up(e,0))
        g.down(0,true); assertTrue(g.up(e,0))
    }

    @Test fun slotFiresAtSevenSecondsNotFive() {
        val e=game()
        e.advance(5_000_000_000); assertEquals(0,e.slotCount)
        e.advance(1_999_900_000); assertEquals(0,e.slotCount)
        e.advance(100_000); assertEquals(1,e.slotCount); assertEquals(14_000_000_000,e.nextSlotNanos)
    }

    @Test fun longFrameProcessesEverySlotBoundary() {
        val e=game(); e.blocks.fill(true)
        // An upward ball below the board remains away from blocks for this timing fixture.
        e.balls.clear(); e.serveAt=100_000_000_000
        e.advance(14_100_000_000)
        assertEquals(2,e.slotCount); assertEquals(14_100_000_000,e.elapsedNanos)
    }

    @Test fun speedStacksMultiplicativelyAndResetsAbsolutely() {
        val e=game()
        e.applyEffect(SlotEffect.SPEED_DOUBLE); e.applyEffect(SlotEffect.SPEED_TRIPLE)
        assertEquals(6.0,e.speedMultiplier,0.0)
        e.applyEffect(SlotEffect.SPEED_TRIPLE); assertEquals(18.0,e.speedMultiplier,0.0)
        e.applyEffect(SlotEffect.RESET_SPEED); assertEquals(1.0,e.speedMultiplier,0.0)
        e.applyEffect(SlotEffect.SPEED_DOUBLE); assertEquals(2.0,e.speedMultiplier,0.0)
    }

    @Test fun ballResetPreservesSpeedAndOtherEffects() {
        val e=game()
        e.applyEffect(SlotEffect.ADD_BALLS); e.applyEffect(SlotEffect.ADD_BALLS)
        assertEquals(7,e.balls.size)
        e.applyEffect(SlotEffect.SPEED_TRIPLE); e.applyEffect(SlotEffect.PIERCE)
        e.applyEffect(SlotEffect.RESET_BALLS)
        assertEquals(1,e.balls.size); assertEquals(3.0,e.speedMultiplier,0.0); assertTrue(e.piercing)
    }

    @Test fun speedResetPreservesBalls() {
        val e=game(); e.applyEffect(SlotEffect.ADD_BALLS); e.applyEffect(SlotEffect.SPEED_DOUBLE)
        e.applyEffect(SlotEffect.RESET_SPEED)
        assertEquals(4,e.balls.size); assertEquals(1.0,e.speedMultiplier,0.0)
    }

    @Test fun temporaryEffectsExtendRemainingDurationAndExpireExactly() {
        val e=game(); e.noSlots()
        e.applyEffect(SlotEffect.PIERCE); e.applyEffect(SlotEffect.SPLIT)
        e.advance(2_000_000_000)
        e.applyEffect(SlotEffect.PIERCE); e.applyEffect(SlotEffect.SPLIT)
        assertEquals(10_000_000_000,e.pierceUntil); assertEquals(10_000_000_000,e.splitUntil)
        e.balls.clear(); e.serveAt=20_000_000_000
        e.advance(7_999_900_000); assertTrue(e.piercing); assertTrue(e.splitting)
        e.advance(100_000); assertFalse(e.piercing); assertFalse(e.splitting)
    }

    @Test fun piercingDestroysSuccessiveRowsWithoutReflecting() {
        val e=game(); e.noSlots(); e.balls.clear()
        e.balls += Ball(55.0,450.0,0.0,-1.0,0)
        e.applyEffect(SlotEffect.PIERCE); e.advance(700_000_000)
        assertTrue(e.destroyedCount>=4); assertTrue(e.balls[0].dy<0)
    }

    @Test fun splitTurnsOneBallIntoTwoNotThree() {
        val e=game(); e.noSlots(); e.balls.clear()
        e.balls += Ball(55.0,416.0,0.0,-1.0,0)
        e.applyEffect(SlotEffect.SPLIT); e.advance(10_000_000)
        assertEquals(1,e.destroyedCount); assertEquals(2,e.balls.size)
        assertNotEquals(e.balls[0].dx,e.balls[1].dx,1e-6)
    }

    @Test fun expiredSplitDoesNotCreateChildren() {
        val e=game(); e.noSlots(); e.balls.clear(); e.splitUntil=0
        e.balls += Ball(55.0,416.0,0.0,-1.0,0)
        e.advance(10_000_000)
        assertEquals(1,e.destroyedCount); assertEquals(1,e.balls.size)
    }

    @Test fun highSpeedCannotTunnelThroughThinBlock() {
        val e=game(); e.noSlots(); e.balls.clear(); e.speedMultiplier=729.0
        e.balls += Ball(55.0,800.0,0.0,-1.0,0)
        e.advance(2_000_000)
        assertFalse(e.blocks[32]); assertEquals(1,e.destroyedCount)
    }

    @Test fun gameEndsAtLastCollisionAndCannotBeResurrected() {
        val e=game(); e.noSlots(); e.blocks.fill(false); e.blocks[32]=true; e.balls.clear()
        // Bottom face y=406, expanded by radius 7 => collision at y=413.
        e.balls += Ball(55.0,417.4,0.0,-1.0,0)
        e.advance(500_000_000)
        assertTrue(e.finished); assertEquals(0,e.remaining)
        assertEquals(10_000_000,e.elapsedNanos)
        assertFalse(e.restore(0)); val t=e.elapsedNanos; e.advance(1_000_000_000); assertEquals(t,e.elapsedNanos)
    }

    @Test fun simultaneousBallEventsResolveByTimeNotListOrder() {
        val e=game(); e.noSlots(); e.blocks.fill(false); e.blocks[32]=true; e.balls.clear()
        e.balls += Ball(55.0,457.0,0.0,-1.0,0) // second arrival is listed first
        e.balls += Ball(55.0,417.4,0.0,-1.0,1)
        e.advance(500_000_000)
        assertEquals(10_000_000,e.elapsedNanos); assertEquals(1,e.destroyedCount)
    }

    @Test fun paddleNeverWarps() {
        val e=game(); e.noSlots(); e.balls.clear(); e.paddleX=100.0
        e.balls += Ball(720.0,700.0,0.0,1.0,0)
        var previous=e.paddleX
        repeat(80) {
            e.advance(4_000_000)
            assertTrue(abs(e.paddleX-previous) <= GameEngine.PADDLE_SPEED*.004+1e-6)
            previous=e.paddleX
        }
    }

    @Test fun cpuCatchesAnIsolatedReachableBallIncludingWallReflection() {
        val e=game(); e.noSlots(); e.balls.clear()
        e.balls += Ball(750.0,730.0,.6,.8,0)
        assertEquals(GameEngine.reflectX(750+(.6/.8)*(967-730)),e.targetX(),1e-6)
        e.advance(900_000_000)
        assertEquals(1,e.balls.size); assertTrue(e.balls[0].dy<0)
    }

    @Test fun cpuCannotCatchBothEndsAtTheSameInstant() {
        val e=game(); e.noSlots(); e.balls.clear(); e.paddleX=63.0
        e.balls += Ball(45.0,960.0,0.0,1.0,0)
        e.balls += Ball(755.0,960.0,0.0,1.0,1)
        e.advance(230_000_000)
        assertEquals(1,e.balls.size); assertEquals(0L,e.balls[0].id)
    }

    @Test fun allLostBallsCauseAServeWithoutEndingRunOrResettingSpeed() {
        val e=game(); e.noSlots(); e.balls.clear(); e.speedMultiplier=6.0
        e.advance(654_000_000)
        assertEquals(1,e.balls.size); assertEquals(6.0,e.speedMultiplier,0.0); assertFalse(e.finished)
    }

    @Test fun ballAndSpeedEffectsHaveNoArtificialGameplayCap() {
        val e=game()
        repeat(200) { e.applyEffect(SlotEffect.ADD_BALLS) }
        repeat(8) { e.applyEffect(SlotEffect.SPEED_TRIPLE) }
        assertEquals(601,e.balls.size); assertEquals(6561.0,e.speedMultiplier,0.0)
    }

    @Test fun timeKeepsFourDecimalPlacesAndScoreUsesSecondsSquared() {
        assertEquals("12.3456",ScoreFormat.seconds(123456))
        assertEquals(BigDecimal("152.41383936"),ScoreFormat.exact(123456))
        assertEquals("152.4138",ScoreFormat.score(123456))
        assertEquals("0.0001",ScoreFormat.seconds(1))
        assertEquals(BigDecimal("0.00000001"),ScoreFormat.exact(1))
        assertEquals(BigDecimal("1000000000000.00000000"),ScoreFormat.exact(10_000_000_000))
    }

    @Test fun nanosecondRemainderIsNotDiscardedBetweenFrames() {
        val e=game(); e.noSlots(); repeat(1234) { e.advance(123_457) }
        assertEquals(152_345_938,e.elapsedNanos); assertEquals(1523,e.ticks)
    }

    @Test fun rankingRetainsBest100DeduplicatesAndBreaksTiesStably() {
        var list = emptyList<ScoreEntry>()
        for(i in 0..149) list=Ranking.insert(list,ScoreEntry("$i",i.toLong(),i.toLong(),0,0))
        assertEquals(100,list.size); assertEquals(149L,list.first().ticks); assertEquals(50L,list.last().ticks)
        list=Ranking.insert(list,list.first()); assertEquals(100,list.size)
        list=Ranking.insert(list,ScoreEntry("tie",149L,999L,0,0))
        assertEquals("149",list.first().id); assertEquals("tie",list[1].id)
    }

    @Test(timeout=15000) fun seededGamesReachGameOverWithFiniteState() {
        for(seed in 0..11) {
            val e=GameEngine(Random(seed))
            repeat(2400) {
                if(!e.finished) e.advance(100_000_000)
                e.events.clear()
                assertTrue(e.balls.all { b -> b.x.isFinite() && b.y.isFinite() && abs(sqrt(b.dx*b.dx+b.dy*b.dy)-1)<1e-8 })
            }
            assertTrue("seed $seed did not finish",e.finished)
        }
    }
}
