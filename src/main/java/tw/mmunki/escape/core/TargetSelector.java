package tw.mmunki.escape.core;

import java.util.List;
import java.util.Optional;

/**
 * Pure nearest-player selection so the targeting rule can be tested without a
 * running Minecraft server.
 */
public final class TargetSelector {
    private TargetSelector() {
    }

    public record Candidate<T>(T id, double x, double y, double z, boolean eligible) {
    }

    public static <T> Optional<T> nearest(List<Candidate<T>> candidates, double x, double y, double z) {
        T best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Candidate<T> candidate : candidates) {
            if (!candidate.eligible()) {
                continue;
            }
            double dx = candidate.x() - x;
            double dy = candidate.y() - y;
            double dz = candidate.z() - z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate.id();
            }
        }
        return Optional.ofNullable(best);
    }
}
