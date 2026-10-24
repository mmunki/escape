package tw.mmunki.escape.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscapeCycleTest {
    @Test
    void firstEncounterHappensAfterTheFirstDelay() {
        EscapeCycle cycle = new EscapeCycle(6000, 2400, 1200);
        for (int i = 0; i < 1199; i++) {
            assertFalse(cycle.tick(), "spawned too early at tick " + i);
        }
        assertTrue(cycle.tick());
    }

    @Test
    void encountersRepeatEveryCycle() {
        EscapeCycle cycle = new EscapeCycle(6000, 2400, 0);
        assertTrue(cycle.tick());
        for (int i = 0; i < 5999; i++) {
            assertFalse(cycle.tick(), "unexpected spawn at offset " + i);
        }
        assertTrue(cycle.tick());
    }

    @Test
    void retryMovesTheNextAttemptWithoutConsumingTheCycle() {
        EscapeCycle cycle = new EscapeCycle(6000, 2400, 0);
        assertTrue(cycle.tick());
        cycle.retry(600);
        for (int i = 0; i < 599; i++) {
            assertFalse(cycle.tick());
        }
        assertTrue(cycle.tick());
    }

    @Test
    void deferFullCyclePushesTheNextAttemptAWholeCycleOut() {
        EscapeCycle cycle = new EscapeCycle(6000, 2400, 0);
        assertTrue(cycle.tick());
        cycle.deferFullCycle();
        for (int i = 0; i < 5999; i++) {
            assertFalse(cycle.tick());
        }
        assertTrue(cycle.tick());
    }

    @Test
    void countdownIsReportedInSeconds() {
        EscapeCycle cycle = new EscapeCycle(6000, 2400, 1200);
        assertEquals(60, cycle.secondsUntilNext());
        for (int i = 0; i < 1199; i++) {
            cycle.tick();
        }
        assertEquals(1, cycle.secondsUntilNext());
    }

    @Test
    void valuesAreClampedToSaneBounds() {
        EscapeCycle cycle = new EscapeCycle(1, 1, -5);
        assertEquals(20, cycle.cycleTicks());
        assertEquals(10, cycle.activeTicks());
        assertEquals(0, cycle.firstDelayTicks());
        assertTrue(cycle.tick());
    }
}
