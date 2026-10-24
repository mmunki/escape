package tw.mmunki.escape.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HuntPlannerTest {
    @Test
    void swimsTowardsPlayersInWater() {
        assertEquals(HuntPlanner.Action.SWIM, HuntPlanner.decide(
                new HuntPlanner.Observation(true, false, true, 0, false, false, 30)));
    }

    @Test
    void opensDoorsThatBlockThePath() {
        assertEquals(HuntPlanner.Action.OPEN_DOOR, HuntPlanner.decide(
                new HuntPlanner.Observation(false, true, true, 0, false, true, 4)));
    }

    @Test
    void breaksBlocksThatBlockThePath() {
        assertEquals(HuntPlanner.Action.BREAK_BLOCK, HuntPlanner.decide(
                new HuntPlanner.Observation(false, false, true, 0, false, false, 6)));
    }

    @Test
    void pillarsUpToAHighTarget() {
        assertEquals(HuntPlanner.Action.PILLAR_UP, HuntPlanner.decide(
                new HuntPlanner.Observation(false, false, false, 3.0, false, true, 3)));
    }

    @Test
    void bridgesOverAGap() {
        assertEquals(HuntPlanner.Action.BRIDGE, HuntPlanner.decide(
                new HuntPlanner.Observation(false, false, false, 0, true, true, 5)));
    }

    @Test
    void catchesUpWhenAGoalIsFarAndHidden() {
        assertEquals(HuntPlanner.Action.CATCH_UP, HuntPlanner.decide(
                new HuntPlanner.Observation(false, false, false, 0, false, false, 20)));
    }

    @Test
    void usesNormalPathfindingOnAClearRoute() {
        assertEquals(HuntPlanner.Action.PATH, HuntPlanner.decide(
                new HuntPlanner.Observation(false, false, false, 0, false, true, 5)));
    }
}
