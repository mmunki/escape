package tw.mmunki.escape.core;

/**
 * Enforces the hard minimum lifetime of a spawned hunter. The entity may only
 * leave early when it is killed; a configured window shorter than two minutes
 * is raised to two minutes and natural despawn/unload is never allowed.
 */
public final class EscapeLifetime {
    public static final int MINIMUM_SECONDS = 120;
    public static final int MINIMUM_TICKS = MINIMUM_SECONDS * 20;

    private final int configuredTicks;

    public EscapeLifetime(int configuredTicks) {
        this.configuredTicks = configuredTicks;
    }

    public int lifetimeTicks() {
        return Math.max(MINIMUM_TICKS, configuredTicks);
    }

    public boolean expired(int ageTicks) {
        return ageTicks >= lifetimeTicks();
    }

    /**
     * A hunter may only vanish when the minimum lifetime elapsed. Being killed
     * is handled by the normal death path and is not a "vanish".
     */
    public boolean mayVanish(int ageTicks, boolean killed) {
        return killed || expired(ageTicks);
    }

    public int remainingTicks(int ageTicks) {
        return Math.max(0, lifetimeTicks() - Math.max(0, ageTicks));
    }

    public int configuredTicks() {
        return configuredTicks;
    }
}
