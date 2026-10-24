package tw.mmunki.escape.core;

/**
 * Small, deterministic PvP brain. It decides what a skilled player would do
 * next: close the gap, strafe, block, crit, combo or disengage to heal.
 */
public final class PvpStateMachine {
    public enum Action {
        APPROACH,
        STRAFE_LEFT,
        STRAFE_RIGHT,
        ATTACK,
        CRIT_ATTACK,
        BLOCK,
        RETREAT_HEAL
    }

    public record Input(
            double distance,
            double healthFraction,
            boolean hasLineOfSight,
            boolean targetAttacking,
            boolean targetBlocking,
            boolean attackReady,
            boolean canCrit,
            int ticksSinceDamaged,
            double targetHealthFraction) {
    }

    private static final int STRAFE_SWITCH_TICKS = 8;

    private final double healThreshold;
    private int combo;
    private int strafeTicks;
    private boolean strafeLeft = true;

    public PvpStateMachine(double healThreshold) {
        this.healThreshold = healThreshold;
    }

    public Action decide(Input input) {
        if (input.distance() > 4.0D) {
            combo = 0;
        }
        if (input.healthFraction() <= healThreshold
                && input.ticksSinceDamaged() >= 20
                && input.distance() > 2.5D) {
            return Action.RETREAT_HEAL;
        }
        if (!input.hasLineOfSight()) {
            return Action.APPROACH;
        }
        if (input.distance() <= 3.2D) {
            if (input.attackReady()) {
                combo++;
                return input.canCrit() ? Action.CRIT_ATTACK : Action.ATTACK;
            }
            if (input.targetAttacking()) {
                return Action.BLOCK;
            }
            if (input.targetBlocking() && input.targetHealthFraction() > input.healthFraction()) {
                return Action.BLOCK;
            }
            return strafe();
        }
        if (input.distance() <= 5.0D) {
            if (input.targetAttacking() && !input.attackReady()) {
                return Action.BLOCK;
            }
            return strafe();
        }
        return Action.APPROACH;
    }

    private Action strafe() {
        strafeTicks++;
        if (strafeTicks >= STRAFE_SWITCH_TICKS) {
            strafeTicks = 0;
            strafeLeft = !strafeLeft;
        }
        return strafeLeft ? Action.STRAFE_LEFT : Action.STRAFE_RIGHT;
    }

    public int combo() {
        return combo;
    }

    public void resetCombo() {
        combo = 0;
    }
}
