package tw.mmunki.escape;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscapeSettingsTest {
    @Test
    void defaultsMatchTheRequestedEncounter() {
        EscapeSettings settings = new EscapeSettings().normalized();
        assertTrue(settings.enabled);
        assertEquals(300, settings.cycleSeconds);
        assertEquals(120, settings.activeSeconds);
        assertEquals(20.0D, settings.maxHealth);
        assertEquals(9.0D, settings.attackDamage);
        assertEquals(6000, settings.cycleTicks());
        assertEquals(2400, settings.activeTicks());
    }

    @Test
    void activeWindowNeverOverlapsTheNextCycle() {
        EscapeSettings settings = new EscapeSettings();
        settings.cycleSeconds = 60;
        settings.activeSeconds = 600;
        EscapeSettings normalized = settings.normalized();
        assertEquals(50, normalized.activeSeconds);
        assertTrue(normalized.activeSeconds < normalized.cycleSeconds);
    }

    @Test
    void invalidValuesAreClamped() {
        EscapeSettings settings = new EscapeSettings();
        settings.cycleSeconds = 1;
        settings.activeSeconds = 1;
        settings.firstDelaySeconds = -20;
        settings.maxHealth = 0.0D;
        settings.movementSpeed = 5.0D;
        settings.attackDamage = -1.0D;
        settings.critChance = 4.0D;
        settings.critMultiplier = 0.1D;
        settings.retrySeconds = 0;

        EscapeSettings normalized = settings.normalized();
        assertEquals(30, normalized.cycleSeconds);
        assertEquals(10, normalized.activeSeconds);
        assertEquals(0, normalized.firstDelaySeconds);
        assertEquals(1.0D, normalized.maxHealth);
        assertEquals(0.60D, normalized.movementSpeed);
        assertEquals(1.0D, normalized.attackDamage);
        assertEquals(1.0D, normalized.critChance);
        assertEquals(1.0D, normalized.critMultiplier);
        assertEquals(5, normalized.retrySeconds);
    }

    @Test
    void disabledConfigStaysDisabled() {
        EscapeSettings settings = new EscapeSettings();
        settings.enabled = false;
        assertFalse(settings.normalized().enabled);
    }
}
