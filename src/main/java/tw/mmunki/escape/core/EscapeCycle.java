package tw.mmunki.escape.core;

/**
 * Pure timing model for the encounter. Every {@code cycleTicks} the manager is
 * asked to spawn one entity; the entity removes itself after
 * {@code activeTicks}. No Minecraft classes are used here so the timing can be
 * unit tested.
 */
public final class EscapeCycle {
    private final int cycleTicks;
    private final int activeTicks;
    private final int firstDelayTicks;

    private long now;
    private long nextSpawnAt;

    public EscapeCycle(int cycleTicks, int activeTicks, int firstDelayTicks) {
        this.cycleTicks = Math.max(20, cycleTicks);
        this.activeTicks = Math.max(10, Math.min(activeTicks, this.cycleTicks));
        this.firstDelayTicks = Math.max(0, firstDelayTicks);
        this.nextSpawnAt = this.firstDelayTicks;
    }

    /**
     * Advances one tick.
     *
     * @return true when the manager should try to spawn the entity now
     */
    public boolean tick() {
        now++;
        if (now < nextSpawnAt) {
            return false;
        }
        nextSpawnAt = now + cycleTicks;
        return true;
    }

    /**
     * Called when a spawn could not happen (for example, no eligible player).
     * The next attempt is moved forward without consuming a whole cycle.
     */
    public void retry(int ticks) {
        nextSpawnAt = now + Math.max(20, ticks);
    }

    /** Force the next automatic attempt to happen one full cycle from now. */
    public void deferFullCycle() {
        nextSpawnAt = now + cycleTicks;
    }

    public long ticksUntilNext() {
        return Math.max(0L, nextSpawnAt - now);
    }

    public int secondsUntilNext() {
        return (int) Math.ceil(ticksUntilNext() / 20.0D);
    }

    public int cycleTicks() {
        return cycleTicks;
    }

    public int activeTicks() {
        return activeTicks;
    }

    public int firstDelayTicks() {
        return firstDelayTicks;
    }

    public long now() {
        return now;
    }
}
