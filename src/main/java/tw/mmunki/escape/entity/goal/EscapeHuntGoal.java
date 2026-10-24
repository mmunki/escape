package tw.mmunki.escape.entity.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import tw.mmunki.escape.EscapeSettings;
import tw.mmunki.escape.core.HuntPlanner;
import tw.mmunki.escape.core.PvpStateMachine;
import tw.mmunki.escape.entity.EscapeEntity;

import java.util.EnumSet;

/**
 * The relentless part of the hunter: PvP spacing, strafing, shield use,
 * critical jumps, healing retreats and physical tunneling/bridging when the
 * normal pathfinder cannot reach the target.
 */
public final class EscapeHuntGoal extends Goal {
    private static final double MELEE_REACH = 3.2D;
    private static final double CRIT_REACH = 3.4D;

    private final EscapeEntity mob;
    private int attackCooldown;
    private int breakCooldown;
    private int placeCooldown;
    private int critWindup;
    private int healCooldown;
    private int catchUpCooldown;
    private int pillarTicks;
    private int swimTicks;

    public EscapeHuntGoal(EscapeEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() instanceof Player player && player.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return mob.getTarget() instanceof Player player && player.isAlive() && !player.isSpectator();
    }

    @Override
    public void start() {
        attackCooldown = 0;
        critWindup = 0;
        pillarTicks = 0;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        mob.stopBlocking();
        critWindup = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (!(mob.getTarget() instanceof Player target) || !target.isAlive()) {
            return;
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (breakCooldown > 0) {
            breakCooldown--;
        }
        if (placeCooldown > 0) {
            placeCooldown--;
        }
        if (healCooldown > 0) {
            healCooldown--;
        }
        if (catchUpCooldown > 0) {
            catchUpCooldown--;
        }
        if (pillarTicks > 0) {
            pillarTicks--;
        }

        EscapeSettings settings = mob.settings();
        double distance = mob.distanceTo(target);
        double verticalDelta = target.getY() - mob.getY();
        boolean lineOfSight = mob.hasLineOfSight(target);
        boolean attackReady = attackCooldown <= 0 && critWindup <= 0;
        boolean canCrit = mob.onGround() && distance <= CRIT_REACH && attackReady;

        PvpStateMachine.Action action = mob.pvp().decide(new PvpStateMachine.Input(
                distance,
                mob.getHealth() / Math.max(1.0F, mob.getMaxHealth()),
                lineOfSight,
                isTargetAttacking(target),
                target.isBlocking(),
                attackReady,
                canCrit,
                mob.getLastHurtByMobTimestamp() == 0 ? 100 : (int) (mob.level().getGameTime() - mob.getLastHurtByMobTimestamp()),
                target.getHealth() / Math.max(1.0F, target.getMaxHealth())));

        if (critWindup > 0) {
            critWindup--;
            if (mob.distanceToSqr(target) <= CRIT_REACH * CRIT_REACH
                    && (mob.fallDistance > 0.08F || critWindup <= 0)) {
                attack(target, true);
                critWindup = 0;
            } else {
                approach(target, distance, settings.movementSpeed);
                return;
            }
        }

        switch (action) {
            case RETREAT_HEAL -> {
                retreat(target, settings);
                if (healCooldown <= 0) {
                    mob.healInCombat();
                    healCooldown = settings.healCooldownTicks();
                    mob.pvp().resetCombo();
                }
                return;
            }
            case BLOCK -> {
                mob.startBlocking();
                mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
                if (distance < 2.6D) {
                    retreatStep(target);
                } else if (distance > 3.6D) {
                    approach(target, distance, settings.movementSpeed);
                }
                return;
            }
            case CRIT_ATTACK -> {
                mob.stopBlocking();
                if (mob.onGround()) {
                    mob.jumpFromGround();
                    mob.setJumping(true);
                    critWindup = 9;
                    approach(target, distance, settings.movementSpeed);
                    return;
                }
            }
            case STRAFE_LEFT -> strafe(target, true, settings.movementSpeed);
            case STRAFE_RIGHT -> strafe(target, false, settings.movementSpeed);
            case APPROACH, ATTACK -> approach(target, distance, settings.movementSpeed);
        }

        if (action == PvpStateMachine.Action.ATTACK
                && mob.distanceToSqr(target) <= MELEE_REACH * MELEE_REACH
                && lineOfSight) {
            attack(target, false);
        }

        HuntPlanner.Action physical = HuntPlanner.decide(new HuntPlanner.Observation(
                mob.isInWater(),
                doorAhead(target),
                pathBlocked(target, distance, lineOfSight),
                verticalDelta,
                gapAhead(target),
                lineOfSight,
                distance));

        switch (physical) {
            case SWIM -> swim(target);
            case OPEN_DOOR, BREAK_BLOCK -> {
                if (!tryClearPath(target, distance, lineOfSight)) {
                    approach(target, distance, settings.movementSpeed);
                }
            }
            case PILLAR_UP, BRIDGE -> {
                tryPlace(target, distance, verticalDelta, settings);
                approach(target, distance, settings.movementSpeed);
            }
            case CATCH_UP -> {
                if (catchUpCooldown <= 0 && distance > 12.0D) {
                    mob.catchUp(target);
                    catchUpCooldown = 100;
                } else {
                    approach(target, distance, settings.movementSpeed);
                }
            }
            case PATH -> {
                if (!lineOfSight && distance > 4.0D) {
                    tryClearPath(target, distance, false);
                }
            }
        }
    }

    private boolean isTargetAttacking(Player target) {
        return target.swinging || target.isBlocking();
    }

    private void attack(Player target, boolean critical) {
        mob.stopBlocking();
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (mob.distanceToSqr(target) > 4.0D * 4.0D || !mob.hasLineOfSight(target)) {
            return;
        }
        mob.setPendingCritical(critical);
        mob.doHurtTarget((ServerLevel) mob.level(), target);
        attackCooldown = 9;
    }

    private void approach(Player target, double distance, double speed) {
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (distance > 2.2D) {
            mob.getNavigation().moveTo(target, speed);
            if (mob.getNavigation().isDone()) {
                mob.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), speed);
            }
        } else {
            mob.getNavigation().stop();
        }
    }

    private void strafe(Player target, boolean left, double speed) {
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4D) {
            dx = 1.0D;
            dz = 0.0D;
            length = 1.0D;
        }
        double perpX = -dz / length;
        double perpZ = dx / length;
        if (!left) {
            perpX = -perpX;
            perpZ = -perpZ;
        }
        double sideX = target.getX() + perpX * 2.8D;
        double sideZ = target.getZ() + perpZ * 2.8D;
        mob.getNavigation().moveTo(sideX, target.getY(), sideZ, speed);
        if (mob.getNavigation().isDone()) {
            mob.getMoveControl().setWantedPosition(sideX, target.getY(), sideZ, speed);
        }
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    private void retreat(Player target, EscapeSettings settings) {
        mob.stopBlocking();
        mob.getNavigation().moveTo(
                mob.getX() + (mob.getX() - target.getX()) * 2.0D,
                mob.getY(),
                mob.getZ() + (mob.getZ() - target.getZ()) * 2.0D,
                1.35D);
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    private void retreatStep(Player target) {
        Vec3 away = mob.position().subtract(target.position()).horizontal();
        if (away.lengthSqr() > 1.0E-4D) {
            away = away.normalize();
            mob.getNavigation().moveTo(mob.getX() + away.x * 2.5D, mob.getY(), mob.getZ() + away.z * 2.5D, 1.1D);
        }
    }

    private void swim(Player target) {
        mob.getNavigation().moveTo(target, 1.15D);
        mob.getMoveControl().setWantedPosition(target.getX(), target.getY() + 0.2D, target.getZ(), 1.2D);
        swimTicks++;
        if (swimTicks % 5 == 0 && target.getY() > mob.getY() + 0.4D) {
            mob.setDeltaMovement(mob.getDeltaMovement().add(0.0D, 0.035D, 0.0D));
        }
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    private boolean pathBlocked(Player target, double distance, boolean lineOfSight) {
        if (mob.getNavigation().isStuck()) {
            return true;
        }
        if (!lineOfSight && distance > 4.0D) {
            return true;
        }
        return mob.getNavigation().isDone() && distance > 3.0D;
    }

    private boolean doorAhead(Player target) {
        Direction direction = horizontalDirectionTo(target);
        BlockPos front = mob.blockPosition().relative(direction);
        return mob.level().getBlockState(front).getBlock() instanceof DoorBlock
                || mob.level().getBlockState(front.above()).getBlock() instanceof DoorBlock;
    }

    private boolean gapAhead(Player target) {
        Direction direction = horizontalDirectionTo(target);
        BlockPos front = mob.blockPosition().relative(direction);
        BlockState frontState = mob.level().getBlockState(front);
        BlockState support = mob.level().getBlockState(front.below());
        return frontState.isAir() && (support.isAir() || !support.getFluidState().isEmpty());
    }

    private boolean tryClearPath(Player target, double distance, boolean lineOfSight) {
        EscapeSettings settings = mob.settings();
        if (!settings.blockBreaking || breakCooldown > 0) {
            return false;
        }
        boolean stuck = mob.getNavigation().isStuck()
                || mob.getNavigation().isDone() && distance > 3.0D
                || !lineOfSight;
        if (!stuck) {
            return false;
        }
        Direction direction = horizontalDirectionTo(target);
        BlockPos front = mob.blockPosition().relative(direction);
        BlockPos[] candidates = {
                front,
                front.above(),
                mob.blockPosition().above(2),
                front.below()
        };
        for (BlockPos pos : candidates) {
            if (openDoor(pos)) {
                breakCooldown = 4;
                return true;
            }
        }
        int broken = 0;
        for (BlockPos pos : candidates) {
            if (breakBlock(pos)) {
                broken++;
                if (broken >= 2) {
                    break;
                }
            }
        }
        if (broken > 0) {
            breakCooldown = 5;
            return true;
        }
        return false;
    }

    private boolean openDoor(BlockPos pos) {
        BlockState state = mob.level().getBlockState(pos);
        if (!(state.getBlock() instanceof DoorBlock door) || door.isOpen(state)) {
            return false;
        }
        door.setOpen(mob, mob.level(), state, pos, true);
        mob.level().playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.HOSTILE, 0.8F, 0.9F);
        return true;
    }

    private boolean breakBlock(BlockPos pos) {
        BlockState state = mob.level().getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || EscapeEntity.isHazard(state)) {
            return false;
        }
        if (state.getBlock() instanceof DoorBlock) {
            return false;
        }
        float hardness = state.getDestroySpeed(mob.level(), pos);
        if (hardness < 0.0F || hardness > 50.0F) {
            return false;
        }
        mob.level().destroyBlock(pos, false, mob, 512);
        mob.level().playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.HOSTILE, 0.8F, 0.9F);
        return true;
    }

    private void tryPlace(Player target, double distance, double verticalDelta, EscapeSettings settings) {
        if (!settings.blockPlacing || placeCooldown > 0) {
            return;
        }
        if (verticalDelta > 1.2D && distance <= 3.5D) {
            if (mob.onGround()) {
                mob.jumpFromGround();
                mob.setJumping(true);
                pillarTicks = 12;
                placeCooldown = 4;
                return;
            }
            if (pillarTicks > 0) {
                BlockPos under = mob.blockPosition().below();
                if (mob.getY() - under.getY() > 1.05D && placeBlock(under)) {
                    placeCooldown = 3;
                }
            }
            return;
        }
        if (verticalDelta > -2.0D && verticalDelta < 2.0D && distance >= 1.5D && distance <= 8.0D) {
            Direction direction = horizontalDirectionTo(target);
            BlockPos support = mob.blockPosition().relative(direction).below();
            if (isGap(support) && placeBlock(support)) {
                placeCooldown = 3;
            }
        }
    }

    private boolean isGap(BlockPos pos) {
        BlockState state = mob.level().getBlockState(pos);
        if (EscapeEntity.isHazard(state)) {
            return false;
        }
        return state.isAir() || !state.getFluidState().isEmpty();
    }

    private boolean placeBlock(BlockPos pos) {
        BlockState state = mob.level().getBlockState(pos);
        if (EscapeEntity.isHazard(state)) {
            return false;
        }
        if (!state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced()) {
            return false;
        }
        if (!mob.level().setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), 3)) {
            return false;
        }
        mob.level().playSound(null, pos, Blocks.COBBLESTONE.defaultBlockState().getSoundType().getPlaceSound(),
                SoundSource.HOSTILE, 0.7F, 1.0F);
        return true;
    }

    private Direction horizontalDirectionTo(Player target) {
        return Direction.getApproximateNearest(target.getX() - mob.getX(), 0.0D, target.getZ() - mob.getZ());
    }
}
