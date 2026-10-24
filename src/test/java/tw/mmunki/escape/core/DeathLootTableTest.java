package tw.mmunki.escape.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathLootTableTest {
    @Test
    void fallsBackToEnchantedGoldenAppleForAnEmptyTable() {
        assertEquals(DeathLootTable.FALLBACK, DeathLootTable.choose(List.of(), 0));
        assertEquals(DeathLootTable.FALLBACK, DeathLootTable.choose(List.of(" ", ""), 0));
    }

    @Test
    void selectsOneEntryFromTheConfiguredTable() {
        List<String> loot = List.of("minecraft:netherite_ingot", "minecraft:elytra",
                "minecraft:totem_of_undying");
        assertEquals("minecraft:netherite_ingot", DeathLootTable.choose(loot, 0));
        assertEquals("minecraft:elytra", DeathLootTable.choose(loot, 1));
        assertEquals("minecraft:totem_of_undying", DeathLootTable.choose(loot, 2));
        assertEquals("minecraft:netherite_ingot", DeathLootTable.choose(loot, 3));
    }

    @Test
    void randomSelectionStaysInsideTheTable() {
        List<String> loot = List.of("minecraft:netherite_ingot", "minecraft:elytra",
                "minecraft:totem_of_undying", "minecraft:diamond_block");
        Random random = new Random(42L);
        for (int i = 0; i < 100; i++) {
            assertTrue(loot.contains(DeathLootTable.choose(loot, random)));
        }
        assertEquals("minecraft:elytra", DeathLootTable.choose(List.of("minecraft:elytra"), random));
    }

    @Test
    void normalisesWhitespaceAndDuplicates() {
        assertEquals(List.of("minecraft:elytra", "minecraft:netherite_ingot"),
                DeathLootTable.normalize(List.of(" minecraft:elytra ", "minecraft:elytra",
                        "minecraft:netherite_ingot")));
    }
}
