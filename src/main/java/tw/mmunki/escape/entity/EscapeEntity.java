package tw.mmunki.escape.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import tw.mmunki.escape.EscapeConfig;
import tw.mmunki.escape.EscapeSettings;
import tw.mmunki.escape.entity.goal.EscapeHazardGoal;

import java.util.Optional;
import java.util.UUID;

public final class EscapeEntity extends Monster {
    private static final EntityDataAccessor<String> DATA_TARGET =
            SynchedEntityData.defineId(EscapeEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_LIFETIME =
            SynchedEntityData.defineId(EscapeEntity.class, EntityDataSerializers.INT);

    private static final int CRIT_SOUND_INTERVAL = 4;
    private static final double LEASH_DISTANCE = 64.0D;

    private int lifeTicks;
    private int trapTicks;
    private int critSoundCooldown;

    public EscapeEntity(EntityType<? extends EscapeEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        setPersistenceRequired();
        equip();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.ATTACK_DAMAGE, 9.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.10D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0D);
    }

    private void equip() {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DRAGON_HEAD));
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            setDropChance(slot, 0.0F);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TARGET, "");
        builder.define(DATA_LIFETIME, 2400);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new EscapeHazardGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 32.0F));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
    }

    public void bindTo(Player player, int lifetimeTicks) {
        entityData.set(DATA_TARGET, player.getUUID().toString());
        entityData.set(DATA_LIFETIME, Math.max(20, lifetimeTicks));
        EscapeSettings settings = EscapeConfig.get();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(settings.maxHealth);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(settings.movementSpeed);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(settings.attackDamage);
        setHealth((float) settings.maxHealth);
        setTarget(player);
    }

    public UUID getTargetUuid() {
        String value = entityData.get(DATA_TARGET);
        if (value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public int getLifetimeTicks() {
        return entityData.get(DATA_LIFETIME);
    }

    public int getRemainingTicks() {
        return Math.max(0, getLifetimeTicks() - lifeTicks);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        UUID bound = getTargetUuid();
        if (bound != null && !bound.equals(target.getUUID())) {
            return false;
        }
        return super.canAttack(target);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        if (critSoundCooldown > 0) {
            critSoundCooldown--;
        }
        ServerLevel serverLevel = (ServerLevel) level();
        lifeTicks++;
        Player target = resolveTarget(serverLevel);
        if (target == null) {
            vanish(serverLevel, null, false);
            return;
        }
        if (getTarget() != target) {
            setTarget(target);
        }
        if (lifeTicks > getLifetimeTicks()) {
            vanish(serverLevel, target, true);
            return;
        }
        if (lifeTicks == 1) {
            spawnEffects(serverLevel, target);
        }
        if (lifeTicks % 20 == 0) {
            leash(serverLevel, target);
        }
        if (lifeTicks % 10 == 0) {
            serverLevel.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.2D, getZ(),
                    3, 0.35D, 0.25D, 0.35D, 0.01D);
        }
        if (lifeTicks % 40 == 0 && distanceToSqr(target) < 24.0D * 24.0D) {
            serverLevel.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 0.7F, 0.6F);
        }
        handleTraps(serverLevel, target);
    }

    private Player resolveTarget(ServerLevel serverLevel) {
        UUID uuid = getTargetUuid();
        if (uuid == null) {
            return null;
        }
        Player player = serverLevel.getPlayerByUUID(uuid);
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return null;
        }
        if (player.level() != serverLevel) {
            return null;
        }
        return player;
    }

    private void handleTraps(ServerLevel serverLevel, Player target) {
        boolean trapped = isInLava() || isOnFire() || isInPowderSnow || isFullyFrozen()
                || isInBlock(Blocks.COBWEB);
        if (trapped) {
            trapTicks++;
        } else {
            trapTicks = Math.max(0, trapTicks - 2);
        }
        if (trapTicks >= 20) {
            escapeFromTrap(serverLevel, target);
            trapTicks = 0;
        }
    }

    private boolean isInBlock(net.minecraft.world.level.block.Block block) {
        return level().getBlockState(blockPosition()).getBlock() == block;
    }

    private void leash(ServerLevel serverLevel, Player target) {
        if (distanceToSqr(target) > LEASH_DISTANCE * LEASH_DISTANCE) {
            teleportNear(serverLevel, target);
        } else if (lifeTicks % 100 == 0 && getNavigation().isDone()
                && distanceToSqr(target) > 32.0D * 32.0D) {
            teleportNear(serverLevel, target);
        }
    }

    private void teleportNear(ServerLevel serverLevel, Player target) {
        BlockPos safe = findSafeSpot(target.blockPosition(), 8);
        if (safe == null) {
            safe = target.blockPosition();
        }
        teleportTo(safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D);
        serverLevel.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.0D, getZ(),
                25, 0.4D, 0.8D, 0.4D, 0.2D);
        serverLevel.playSound(null, getX(), getY(), getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.9F, 0.7F);
    }

    public void escapeFromTrap(ServerLevel serverLevel, Player target) {
        BlockPos safe = findSafeSpot(blockPosition(), 8);
        if (safe == null) {
            safe = findSafeSpot(blockPosition(), 16);
        }
        if (safe != null) {
            teleportTo(safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D);
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.0D, getZ(),
                    30, 0.5D, 0.8D, 0.5D, 0.1D);
            serverLevel.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.9F);
        } else if (target != null) {
            teleportNear(serverLevel, target);
        }
    }

    public BlockPos findSafeSpot(BlockPos center, int radius) {
        for (int attempt = 0; attempt < 32; attempt++) {
            int dx = getRandom().nextInt(radius * 2 + 1) - radius;
            int dz = getRandom().nextInt(radius * 2 + 1) - radius;
            int dy = getRandom().nextInt(5) - 2;
            BlockPos candidate = center.offset(dx, dy, dz);
            if (isSafeSpot(candidate)) {
                return candidate;
            }
            for (int step = 1; step <= 3; step++) {
                if (isSafeSpot(candidate.above(step))) {
                    return candidate.above(step).immutable();
                }
                if (isSafeSpot(candidate.below(step))) {
                    return candidate.below(step).immutable();
                }
            }
        }
        return null;
    }

    public boolean isSafeSpot(BlockPos pos) {
        Level level = level();
        BlockState floor = level.getBlockState(pos.below());
        if (!floor.isSolid() || isHazard(floor)) {
            return false;
        }
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        if (!feet.isAir() || !head.isAir()) {
            return false;
        }
        if (!feet.getFluidState().isEmpty() || !head.getFluidState().isEmpty()) {
            return false;
        }
        return !nearHazard(pos, 1);
    }

    public boolean nearHazard(BlockPos center, int radius) {
        Level level = level();
        BlockPos min = center.offset(-radius, -2, -radius);
        BlockPos max = center.offset(radius, 2, radius);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (isHazard(level.getBlockState(pos))) {
                return true;
            }
        }
        return false;
    }

    public boolean isTntNearby() {
        return !level().getEntitiesOfClass(PrimedTnt.class, getBoundingBox().inflate(8.0D)).isEmpty();
    }

    public static boolean isHazard(BlockState state) {
        return state.getBlock() == Blocks.LAVA
                || state.getBlock() == Blocks.FIRE
                || state.getBlock() == Blocks.SOUL_FIRE
                || state.getBlock() == Blocks.CAMPFIRE
                || state.getBlock() == Blocks.SOUL_CAMPFIRE
                || state.getBlock() == Blocks.MAGMA_BLOCK
                || state.getBlock() == Blocks.CACTUS
                || state.getBlock() == Blocks.POWDER_SNOW
                || state.getBlock() == Blocks.SWEET_BERRY_BUSH
                || state.getBlock() == Blocks.WITHER_ROSE
                || state.getBlock() == Blocks.POINTED_DRIPSTONE
                || state.getBlock() == Blocks.COBWEB;
    }

    private void spawnEffects(ServerLevel serverLevel, Player target) {
        serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 1.0D, getZ(),
                45, 0.6D, 1.2D, 0.6D, 0.02D);
        serverLevel.playSound(null, getX(), getY(), getZ(),
                SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 1.4F, 0.55F);
        target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false, false));
        if (EscapeConfig.get().announce) {
            int seconds = Math.max(1, getLifetimeTicks() / 20);
            target.sendOverlayMessage(Component.literal("ESCAPE is hunting you. Survive " + seconds + " seconds.")
                    .withStyle(ChatFormatting.DARK_RED));
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        swing(InteractionHand.MAIN_HAND, true);
        boolean hit = super.doHurtTarget(level, target);
        if (!hit || !(target instanceof LivingEntity living)) {
            return hit;
        }
        EscapeSettings settings = EscapeConfig.get();
        boolean critical = fallDistance > 0.0F || getRandom().nextFloat() < settings.critChance;
        if (!critical) {
            return true;
        }
        double extra = getAttributeValue(Attributes.ATTACK_DAMAGE) * (settings.critMultiplier - 1.0D);
        if (extra > 0.0D) {
            living.hurtServer(level, level.damageSources().mobAttack(this), (float) extra);
        }
        level.sendParticles(ParticleTypes.CRIT, living.getX(), living.getY() + 1.0D, living.getZ(),
                14, 0.35D, 0.45D, 0.35D, 0.2D);
        if (critSoundCooldown <= 0) {
            level.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.0F, 0.75F);
            critSoundCooldown = CRIT_SOUND_INTERVAL;
        }
        return true;
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource damageSource) {
        if (!level().isClientSide()) {
            Player target = resolveTarget((ServerLevel) level());
            if (target != null && EscapeConfig.get().announce) {
                target.sendOverlayMessage(Component.literal("You killed Escape.")
                        .withStyle(ChatFormatting.GREEN));
            }
        }
        super.die(damageSource);
    }

    private void vanish(ServerLevel serverLevel, Player target, boolean survived) {
        serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.0D, getZ(),
                60, 0.6D, 1.1D, 0.6D, 0.12D);
        serverLevel.playSound(null, getX(), getY(), getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.1F, 0.5F);
        if (survived && target != null && EscapeConfig.get().announce) {
            target.sendOverlayMessage(Component.literal("You survived. Escape is gone.")
                    .withStyle(ChatFormatting.GREEN));
        }
        discard();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        UUID uuid = getTargetUuid();
        if (uuid != null) {
            output.putString("EscapeTarget", uuid.toString());
        }
        output.putInt("EscapeLife", lifeTicks);
        output.putInt("EscapeLifetime", getLifetimeTicks());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        Optional<String> uuid = input.getString("EscapeTarget");
        uuid.ifPresent(value -> entityData.set(DATA_TARGET, value));
        entityData.set(DATA_LIFETIME, Math.max(20, input.getIntOr("EscapeLifetime", 2400)));
        lifeTicks = input.getIntOr("EscapeLife", 0);
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return SoundEvents.ENDERMAN_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.ENDERMAN_DEATH;
    }
}
