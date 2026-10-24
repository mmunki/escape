package tw.mmunki.escape.core;

/**
 * Chooses the physical way the hunter continues the chase. The entity feeds it
 * an observation of the world and performs the returned action.
 */
public final class HuntPlanner {
    public enum Action {
        PATH,
        SWIM,
        OPEN_DOOR,
        BREAK_BLOCK,
        PILLAR_UP,
        BRIDGE,
        CATCH_UP
    }

    public record Observation(
            boolean inWater,
            boolean doorAhead,
            boolean pathBlocked,
            double verticalDelta,
            boolean gapAhead,
            boolean hasLineOfSight,
            double distance) {
    }

    private HuntPlanner() {
    }

    public static Action decide(Observation observation) {
        if (observation.inWater()) {
            return Action.SWIM;
        }
        if (observation.pathBlocked() && observation.doorAhead()) {
            return Action.OPEN_DOOR;
        }
        if (observation.gapAhead()
                && observation.distance() <= 8.0D
                && observation.verticalDelta() > -2.0D
                && observation.verticalDelta() < 2.0D) {
            return Action.BRIDGE;
        }
        if (observation.verticalDelta() > 1.2D && observation.distance() <= 4.0D) {
            return Action.PILLAR_UP;
        }
        if (observation.pathBlocked()) {
            return Action.BREAK_BLOCK;
        }
        if (!observation.hasLineOfSight() && observation.distance() > 12.0D) {
            return Action.CATCH_UP;
        }
        return Action.PATH;
    }
}
