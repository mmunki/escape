package tw.mmunki.escape.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PvpStateMachineTest {
    private static PvpStateMachine.Input input(double distance, double health, boolean sight,
                                               boolean targetAttacking, boolean targetBlocking,
                                               boolean ready, boolean canCrit, int sinceDamaged) {
        return new PvpStateMachine.Input(distance, health, sight, targetAttacking, targetBlocking,
                ready, canCrit, sinceDamaged, 1.0D);
    }

    @Test
    void retreatsAndHealsAtLowHealth() {
        PvpStateMachine machine = new PvpStateMachine(0.35D);
        assertEquals(PvpStateMachine.Action.RETREAT_HEAL,
                machine.decide(input(3.0, 0.20, true, true, false, false, false, 30)));
    }

    @Test
    void attacksInMeleeRange() {
        PvpStateMachine machine = new PvpStateMachine(0.35D);
        assertEquals(PvpStateMachine.Action.ATTACK,
                machine.decide(input(2.0, 1.0, true, false, false, true, false, 100)));
    }

    @Test
    void jumpsForCriticalHitsWhenPossible() {
        PvpStateMachine machine = new PvpStateMachine(0.35D);
        assertEquals(PvpStateMachine.Action.CRIT_ATTACK,
                machine.decide(input(2.0, 1.0, true, false, false, true, true, 100)));
    }

    @Test
    void raisesTheShieldWhenTheTargetAttacksAndTheAxeIsCoolingDown() {
        PvpStateMachine machine = new PvpStateMachine(0.35D);
        assertEquals(PvpStateMachine.Action.BLOCK,
                machine.decide(input(2.0, 1.0, true, true, false, false, false, 100)));
    }

    @Test
    void strafesAtMediumRange() {
        PvpStateMachine machine = new PvpStateMachine(0.35D);
        PvpStateMachine.Action action = machine.decide(input(4.0, 1.0, true, false, false, false, false, 100));
        assertTrue(action == PvpStateMachine.Action.STRAFE_LEFT
                || action == PvpStateMachine.Action.STRAFE_RIGHT);
    }

    @Test
    void tracksComboHitsAndResetsWhenTheTargetEscapes() {
        PvpStateMachine machine = new PvpStateMachine(0.35D);
        assertEquals(PvpStateMachine.Action.ATTACK,
                machine.decide(input(2.0, 1.0, true, false, false, true, false, 100)));
        assertEquals(1, machine.combo());
        machine.decide(input(2.0, 1.0, true, false, false, true, false, 100));
        assertEquals(2, machine.combo());
        machine.decide(input(8.0, 1.0, true, false, false, false, false, 100));
        assertEquals(0, machine.combo());
    }

    @Test
    void approachesWhenLineOfSightIsLost() {
        PvpStateMachine machine = new PvpStateMachine(0.35D);
        assertEquals(PvpStateMachine.Action.APPROACH,
                machine.decide(input(10.0, 1.0, false, false, false, true, false, 100)));
    }
}
