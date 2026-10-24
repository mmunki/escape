package tw.mmunki.escape;

import org.junit.jupiter.api.Test;

import java.util.List;

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
        assertEquals(160, settings.healCooldownTicks());
        assertTrue(settings.blockBreaking);
        assertTrue(settings.blockPlacing);
        assertTrue(settings.announceLoot);
        assertTrue(settings.deathLoot.contains("minecraft:enchanted_golden_apple"));
        assertTrue(settings.deathLoot.contains("minecraft:netherite_ingot"));
        assertTrue(settings.deathLoot.contains("minecraft:elytra"));
        assertTrue(settings.deathLoot.contains("minecraft:totem_of_undying"));
    }

    @Test
    void activeWindowIsNeverShorterThanTwoMinutes() {
        EscapeSettings settings = new EscapeSettings();
        settings.cycleSeconds = 60;
        settings.activeSeconds = 600;
        EscapeSettings normalized = settings.normalized();
        assertEquals(140, normalized.cycleSeconds);
        assertEquals(130, normalized.activeSeconds);
        assertTrue(normalized.activeSeconds < normalized.cycleSeconds);

        settings = new EscapeSettings();
        settings.cycleSeconds = 300;
        settings.activeSeconds = 10;
        normalized = settings.normalized();
        assertEquals(120, normalized.activeSeconds);
        assertEquals(2400, normalized.activeTicks());
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
        settings.healThreshold = 5.0D;
        settings.healCooldownSeconds = 0;

        EscapeSettings normalized = settings.normalized();
        assertEquals(140, normalized.cycleSeconds);
        assertEquals(120, normalized.activeSeconds);
        assertEquals(0, normalized.firstDelaySeconds);
        assertEquals(1.0D, normalized.maxHealth);
        assertEquals(0.60D, normalized.movementSpeed);
        assertEquals(1.0D, normalized.attackDamage);
        assertEquals(1.0D, normalized.critChance);
        assertEquals(1.0D, normalized.critMultiplier);
        assertEquals(5, normalized.retrySeconds);
        assertEquals(0.90D, normalized.healThreshold);
        assertEquals(3, normalized.healCooldownSeconds);
    }

    @Test
    void lootTableIsNormalizedAndFallsBackToADefault() {
        EscapeSettings settings = new EscapeSettings();
        settings.deathLoot = List.of(" minecraft:netherite_ingot ", "", "minecraft:elytra",
                "minecraft:netherite_ingot");
        assertEquals(List.of("minecraft:netherite_ingot", "minecraft:elytra"),
                settings.normalized().deathLoot);

        settings.deathLoot = List.of("  ", "");
        assertEquals(EscapeSettings.DEFAULT_DEATH_LOOT, settings.normalized().deathLoot);
    }

    @Test
    void disabledConfigStaysDisabled() {
        EscapeSettings settings = new EscapeSettings();
        settings.enabled = false;
        assertFalse(settings.normalized().enabled);
    }
}
