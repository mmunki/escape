package tw.mmunki.escape.core;

import tw.mmunki.escape.EscapeSettings;

import java.util.List;

/**
 * Pure selection and normalisation of the configurable rare-item loot table.
 */
public final class DeathLootTable {
    public static final String FALLBACK = "minecraft:enchanted_golden_apple";

    private DeathLootTable() {
    }

    public static List<String> normalize(List<String> entries) {
        return EscapeSettings.normalizeLoot(entries);
    }

    public static String choose(List<String> entries, int randomIndex) {
        List<String> normalized = normalize(entries);
        int index = Math.floorMod(randomIndex, normalized.size());
        return normalized.get(index);
    }

    public static String choose(List<String> entries, java.util.random.RandomGenerator random) {
        List<String> normalized = normalize(entries);
        return normalized.get(random.nextInt(normalized.size()));
    }
}
