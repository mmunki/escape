package tw.mmunki.escape.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscapeLifetimeTest {
    @Test
    void aShortConfiguredWindowIsRaisedToTwoMinutes() {
        EscapeLifetime lifetime = new EscapeLifetime(200);
        assertEquals(2400, lifetime.lifetimeTicks());
        assertFalse(lifetime.expired(2399));
        assertTrue(lifetime.expired(2400));
    }

    @Test
    void theHunterCannotVanishDuringTheFirstTwoMinutes() {
        EscapeLifetime lifetime = new EscapeLifetime(2400);
        for (int tick = 0; tick < 2400; tick++) {
            assertFalse(lifetime.mayVanish(tick, false), "vanished at tick " + tick);
        }
        assertTrue(lifetime.mayVanish(2400, false));
    }

    @Test
    void onlyDeathAllowsAnEarlierExit() {
        EscapeLifetime lifetime = new EscapeLifetime(6000);
        assertTrue(lifetime.mayVanish(0, true));
        assertFalse(lifetime.mayVanish(5999, false));
        assertTrue(lifetime.mayVanish(6000, false));
    }

    @Test
    void remainingTimeIsReportedForTheStatusCommand() {
        EscapeLifetime lifetime = new EscapeLifetime(2400);
        assertEquals(2400, lifetime.remainingTicks(0));
        assertEquals(1, lifetime.remainingTicks(2399));
        assertEquals(0, lifetime.remainingTicks(5000));
    }
}
