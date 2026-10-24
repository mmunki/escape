package tw.mmunki.escape.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetSelectorTest {
    @Test
    void choosesTheNearestEligiblePlayer() {
        List<TargetSelector.Candidate<String>> candidates = List.of(
                new TargetSelector.Candidate<>("far", 20, 64, 0, true),
                new TargetSelector.Candidate<>("near", 2, 64, 0, true),
                new TargetSelector.Candidate<>("middle", 8, 64, 0, true));
        assertEquals(Optional.of("near"), TargetSelector.nearest(candidates, 0, 64, 0));
    }

    @Test
    void skipsIneligiblePlayersEvenWhenTheyAreCloser() {
        List<TargetSelector.Candidate<String>> candidates = List.of(
                new TargetSelector.Candidate<>("creative", 0, 64, 0, false),
                new TargetSelector.Candidate<>("survival", 12, 64, 0, true));
        assertEquals(Optional.of("survival"), TargetSelector.nearest(candidates, 0, 64, 0));
    }

    @Test
    void returnsEmptyWhenNobodyIsEligible() {
        List<TargetSelector.Candidate<String>> candidates = List.of(
                new TargetSelector.Candidate<>("spectator", 1, 64, 1, false));
        assertTrue(TargetSelector.nearest(candidates, 0, 64, 0).isEmpty());
    }
}
