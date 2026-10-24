package tw.mmunki.escape.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import tw.mmunki.escape.EscapeConfig;
import tw.mmunki.escape.EscapeSettings;
import tw.mmunki.escape.core.DeathLootTable;
import tw.mmunki.escape.core.EscapeLifetime;
import tw.mmunki.escape.core.PvpStateMachine;
import tw.mmunki.escape.core.TargetSelector;
import tw.mmunki.escape.entity.goal.EscapeHazardGoal;
import tw.mmunki.escape.entity.goal.EscapeHuntGoal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class EscapeEntity extends Monster {
    private static final EntityDataAccessor<String> DATA_TARGET =
            SynchedEntityData.defineId(EscapeEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_LIFETIME =
            SynchedEntityData.defineId(EscapeEntity.class, EntityDataSerializers.INT);

    private static final int CRIT_SOUND_INTERVAL = 4;
    private static final int RETARGET_INTERVAL = 20;
    private static final double LEASH_DISTANCE = 64.0D;

    private EscapeLifetime lifetime = new EscapeLifetime(EscapeLifetime.MINIMUM_TICKS);
    private PvpStateMachine pvp = new PvpStateMachine(0.35D);
    private int lifeTicks;
    private int trapTicks;
    private int critSoundCooldown;
    private boolean pendingCritical;

    public EscapeEntity(EntityType<? extends EscapeEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        setPersistenceRequired();
        setCanPickUpLoot(false);
        equip();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FOLLOW_RANGE, 128.0D)
                .add(Attributes.ATTACK_DAMAGE, 9.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.25D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0D);
    }

    private void equip() {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
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
        builder.define(DATA_LIFETIME, EscapeLifetime.MINIMUM_TICKS);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new EscapeHazardGoal(this));
        goalSelector.addGoal(2, new EscapeHuntGoal(this));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 48.0F));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    public void bindTo(Player player, int lifetimeTicks) {
        EscapeSettings settings = EscapeConfig.get();
        lifetime = new EscapeLifetime(lifetimeTicks);
        pvp = new PvpStateMachine(settings.healThreshold);
        entityData.set(DATA_LIFETIME, lifetime.lifetimeTicks());
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(settings.maxHealth);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(settings.movementSpeed);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(settings.attackDamage);
        setHealth((float) settings.maxHealth);
        retargetNow();
        if (getTarget() == null) {
            setTarget(player);
            entityData.set(DATA_TARGET, player.getUUID().toString());
        }
    }

    public PvpStateMachine pvp() {
        return pvp;
    }

    public EscapeSettings settings() {
        return EscapeConfig.get();
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
        return lifetime.remainingTicks(lifeTicks);
    }

    public EscapeLifetime lifetime() {
        return lifetime;
    }

    public void retargetNow() {
        if (level() instanceof ServerLevel serverLevel) {
            retarget(serverLevel);
        }
    }

    public void catchUp(Player target) {
        if (level() instanceof ServerLevel serverLevel) {
            teleportNear(serverLevel, target);
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (!(target instanceof Player player)) {
            return false;
        }
        return player == getTarget() && isEligible(player);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void checkDespawn() {
        // A spawned hunter must survive for at least 120 seconds; only death
        // or the end of that window may remove it.
    }

    @Override
    public boolean isAlwaysTicking() {
        return true;
    }

    private boolean isEligible(Player player) {
        if (player == null || player.isRemoved() || !player.isAlive() || player.isSpectator()) {
            return false;
        }
        EscapeSettings settings = EscapeConfig.get();
        if (settings.disableInCreative && player.isCreative()) {
            return false;
        }
        return player.level() == level();
    }

    private Player findNearestPlayer(ServerLevel serverLevel) {
        List<TargetSelector.Candidate<Player>> candidates = new ArrayList<>();
        for (ServerPlayer player : serverLevel.players()) {
            candidates.add(new TargetSelector.Candidate<>(
                    player, player.getX(), player.getY(), player.getZ(), isEligible(player)));
        }
        return TargetSelector.nearest(candidates, getX(), getY(), getZ()).orElse(null);
    }

    private void retarget(ServerLevel serverLevel) {
        Player nearest = findNearestPlayer(serverLevel);
        if (nearest == null) {
            setTarget(null);
            entityData.set(DATA_TARGET, "");
            return;
        }
        if (getTarget() != nearest) {
            setTarget(nearest);
        }
        entityData.set(DATA_TARGET, nearest.getUUID().toString());
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
        if (lifeTicks == 1) {
            retarget(serverLevel);
            spawnEffects(serverLevel, getTarget() instanceof Player player ? player : null);
        }
        if (lifeTicks % RETARGET_INTERVAL == 0 || !(getTarget() instanceof Player player) || !isEligible(player)) {
            retarget(serverLevel);
        }
        if (lifetime.expired(lifeTicks)) {
            vanish(serverLevel, getTarget() instanceof Player player ? player : null);
            return;
        }
        Player target = getTarget() instanceof Player player ? player : null;
        if (target != null && lifeTicks % 20 == 0) {
            leash(serverLevel, target);
        }
        if (lifeTicks % 10 == 0) {
            serverLevel.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.2D, getZ(),
                    3, 0.35D, 0.25D, 0.35D, 0.01D);
        }
        if (target != null && lifeTicks % 40 == 0 && distanceToSqr(target) < 24.0D * 24.0D) {
            serverLevel.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 0.7F, 0.6F);
        }
        handleTraps(serverLevel, target);
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
        if (target != null) {
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false, false));
            if (EscapeConfig.get().announce) {
                int seconds = Math.max(1, getLifetimeTicks() / 20);
                target.sendOverlayMessage(Component.literal("ESCAPE is hunting you. Survive " + seconds + " seconds.")
                        .withStyle(ChatFormatting.DARK_RED));
            }
        }
    }

    public void setPendingCritical(boolean pendingCritical) {
        this.pendingCritical = pendingCritical;
    }

    public void startBlocking() {
        if (!isUsingItem()) {
            startUsingItem(InteractionHand.OFF_HAND);
        }
    }

    public void stopBlocking() {
        if (isUsingItem()) {
            stopUsingItem();
        }
    }

    public void healInCombat() {
        heal(Math.max(5.0F, getMaxHealth() * 0.35F));
        addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 1, false, true, true));
        addEffect(new MobEffectInstance(MobEffects.SPEED, 100, 0, false, true, true));
        addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 120, 1, false, true, true));
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HEART, getX(), getY() + 1.4D, getZ(),
                    8, 0.5D, 0.5D, 0.5D, 0.02D);
            serverLevel.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.GENERIC_DRINK, SoundSource.HOSTILE, 1.0F, 1.1F);
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean critical = pendingCritical || fallDistance > 0.0F
                || getRandom().nextFloat() < EscapeConfig.get().critChance;
        pendingCritical = false;
        boolean hit = super.doHurtTarget(level, target);
        if (!hit || !(target instanceof LivingEntity living)) {
            return hit;
        }
        EscapeSettings settings = EscapeConfig.get();
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
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        EscapeSettings settings = EscapeConfig.get();
        String raw = DeathLootTable.choose(settings.deathLoot, getRandom().nextInt(1_000_000));
        ItemStack stack = resolveLoot(raw);
        ItemEntity item = new ItemEntity(level, getX(), getY() + 0.4D, getZ(), stack);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
        if (settings.announceLoot) {
            MutableComponent message = Component.literal("Escape dropped ").append(stack.getHoverName()).append("!");
            level.getServer().getPlayerList().broadcastSystemMessage(message.withStyle(ChatFormatting.GOLD), false);
        }
    }

    private static ItemStack resolveLoot(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) {
            try {
                id = Identifier.withDefaultNamespace(raw);
            } catch (RuntimeException exception) {
                return new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
            }
        }
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == null || item == Items.AIR) {
            return new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
        }
        return new ItemStack(item);
    }

    @Override
    public void die(DamageSource damageSource) {
        if (!level().isClientSide()) {
            Player target = getTarget() instanceof Player player ? player : null;
            if (target != null && EscapeConfig.get().announce) {
                target.sendOverlayMessage(Component.literal("You killed Escape.")
                        .withStyle(ChatFormatting.GREEN));
            }
        }
        super.die(damageSource);
    }

    private void vanish(ServerLevel serverLevel, Player target) {
        serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.0D, getZ(),
                60, 0.6D, 1.1D, 0.6D, 0.12D);
        serverLevel.playSound(null, getX(), getY(), getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.1F, 0.5F);
        if (target != null && EscapeConfig.get().announce) {
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
        output.putInt("EscapeLifetime", lifetime.lifetimeTicks());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        Optional<String> uuid = input.getString("EscapeTarget");
        uuid.ifPresent(value -> entityData.set(DATA_TARGET, value));
        lifetime = new EscapeLifetime(Math.max(EscapeLifetime.MINIMUM_TICKS,
                input.getIntOr("EscapeLifetime", EscapeLifetime.MINIMUM_TICKS)));
        entityData.set(DATA_LIFETIME, lifetime.lifetimeTicks());
        lifeTicks = input.getIntOr("EscapeLife", 0);
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENDERMAN_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.ENDERMAN_DEATH;
    }
}
