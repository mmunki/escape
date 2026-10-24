package tw.mmunki.escape;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class EscapeSettings {
    public static final int MIN_ACTIVE_SECONDS = 120;
    public static final int MIN_CYCLE_SECONDS = MIN_ACTIVE_SECONDS + 20;
    public static final List<String> DEFAULT_DEATH_LOOT = List.of(
            "minecraft:enchanted_golden_apple",
            "minecraft:netherite_ingot",
            "minecraft:elytra",
            "minecraft:totem_of_undying",
            "minecraft:netherite_scrap",
            "minecraft:diamond_block");

    public boolean enabled = true;
    public int cycleSeconds = 300;
    public int activeSeconds = 120;
    public int firstDelaySeconds = 60;
    public boolean disableInCreative = true;
    public boolean announce = true;
    public double maxHealth = 20.0D;
    public double movementSpeed = 0.32D;
    public double attackDamage = 9.0D;
    public double critChance = 0.45D;
    public double critMultiplier = 1.5D;
    public int retrySeconds = 30;
    public double healThreshold = 0.35D;
    public int healCooldownSeconds = 8;
    public boolean blockBreaking = true;
    public boolean blockPlacing = true;
    public boolean announceLoot = true;
    public List<String> deathLoot = new ArrayList<>(DEFAULT_DEATH_LOOT);

    public EscapeSettings normalized() {
        EscapeSettings copy = new EscapeSettings();
        copy.enabled = enabled;
        copy.cycleSeconds = clamp(cycleSeconds, MIN_CYCLE_SECONDS, 3600);
        copy.activeSeconds = clamp(activeSeconds, MIN_ACTIVE_SECONDS, copy.cycleSeconds - 10);
        copy.firstDelaySeconds = clamp(firstDelaySeconds, 0, copy.cycleSeconds);
        copy.disableInCreative = disableInCreative;
        copy.announce = announce;
        copy.maxHealth = clamp(maxHealth, 1.0D, 100.0D);
        copy.movementSpeed = clamp(movementSpeed, 0.10D, 0.60D);
        copy.attackDamage = clamp(attackDamage, 1.0D, 40.0D);
        copy.critChance = clamp(critChance, 0.0D, 1.0D);
        copy.critMultiplier = clamp(critMultiplier, 1.0D, 4.0D);
        copy.retrySeconds = clamp(retrySeconds, 5, 600);
        copy.healThreshold = clamp(healThreshold, 0.10D, 0.90D);
        copy.healCooldownSeconds = clamp(healCooldownSeconds, 3, 60);
        copy.blockBreaking = blockBreaking;
        copy.blockPlacing = blockPlacing;
        copy.announceLoot = announceLoot;
        copy.deathLoot = normalizeLoot(deathLoot);
        return copy;
    }

    public int cycleTicks() {
        return clamp(cycleSeconds, MIN_CYCLE_SECONDS, 3600) * 20;
    }

    public int activeTicks() {
        return clamp(activeSeconds, MIN_ACTIVE_SECONDS, 3599) * 20;
    }

    public int firstDelayTicks() {
        return clamp(firstDelaySeconds, 0, 3600) * 20;
    }

    public int retryTicks() {
        return clamp(retrySeconds, 5, 600) * 20;
    }

    public int healCooldownTicks() {
        return clamp(healCooldownSeconds, 3, 60) * 20;
    }

    public static List<String> normalizeLoot(List<String> entries) {
        if (entries == null) {
            return List.copyOf(DEFAULT_DEATH_LOOT);
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                unique.add(trimmed);
            }
        }
        if (unique.isEmpty()) {
            return List.copyOf(DEFAULT_DEATH_LOOT);
        }
        return List.copyOf(unique);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
