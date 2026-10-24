package tw.mmunki.escape.entity.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import tw.mmunki.escape.entity.EscapeEntity;

import java.util.EnumSet;

/**
 * Makes Escape leave lava, fire, powder snow, cobwebs, cactus and other trap
 * blocks instead of walking into them. Primed TNT nearby is treated as a trap
 * as well. If no path exists, {@link EscapeEntity#tick()} teleports it out after
 * a short delay, so a trap can slow Escape down but cannot hold it forever.
 */
public final class EscapeHazardGoal extends Goal {
    private final EscapeEntity mob;
    private int cooldown;
    private int runTicks;

    public EscapeHazardGoal(EscapeEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        cooldown = 5;
        if (!mob.onGround() && !mob.isInLava() && !mob.isOnFire()) {
            return false;
        }
        return mob.isInLava()
                || mob.isOnFire()
                || mob.isInPowderSnow
                || mob.isFullyFrozen()
                || mob.isTntNearby()
                || mob.nearHazard(mob.blockPosition(), 3);
    }

    @Override
    public boolean canContinueToUse() {
        return runTicks > 0 && !mob.getNavigation().isDone();
    }

    @Override
    public void start() {
        runTicks = 40 + mob.getRandom().nextInt(30);
        BlockPos safe = mob.findSafeSpot(mob.blockPosition(), 8);
        if (safe == null) {
            safe = mob.findSafeSpot(mob.blockPosition(), 16);
        }
        if (safe != null) {
            mob.getNavigation().moveTo(safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D, 1.25D);
        }
    }

    @Override
    public void tick() {
        runTicks--;
        if (mob.getNavigation().isDone() && runTicks > 0) {
            BlockPos safe = mob.findSafeSpot(mob.blockPosition(), 12);
            if (safe != null) {
                mob.getNavigation().moveTo(safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D, 1.25D);
                runTicks = Math.max(runTicks, 20);
            }
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        cooldown = 10;
    }
}
