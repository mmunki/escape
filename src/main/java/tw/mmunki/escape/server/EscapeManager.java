package tw.mmunki.escape.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import tw.mmunki.escape.Escape;
import tw.mmunki.escape.EscapeConfig;
import tw.mmunki.escape.EscapeSettings;
import tw.mmunki.escape.core.EscapeCycle;
import tw.mmunki.escape.core.TargetSelector;
import tw.mmunki.escape.entity.EscapeEntities;
import tw.mmunki.escape.entity.EscapeEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class EscapeManager {
    private static final TicketType ESCAPE_TICKET = new TicketType(
            TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION);

    private MinecraftServer server;
    private EscapeCycle cycle;
    private EscapeEntity active;
    private UUID activeTarget;
    private ServerLevel ticketLevel;
    private ChunkPos ticketChunk;

    public void start(MinecraftServer server) {
        this.server = server;
        EscapeConfig.load();
        resetCycle();
    }

    public void stop() {
        despawn();
        server = null;
        cycle = null;
    }

    public void reload() {
        EscapeConfig.reload();
        resetCycle();
    }

    private void resetCycle() {
        EscapeSettings settings = EscapeConfig.get();
        cycle = new EscapeCycle(settings.cycleTicks(), settings.activeTicks(), settings.firstDelayTicks());
    }

    public void tick(MinecraftServer server) {
        if (this.server != server) {
            start(server);
        }
        EscapeSettings settings = EscapeConfig.get();
        if (!settings.enabled) {
            despawn();
            return;
        }
        if (cycle == null) {
            resetCycle();
        }
        if (active != null && (active.isRemoved() || !active.isAlive())) {
            active = null;
            activeTarget = null;
            releaseTicket();
        }
        boolean scheduled = cycle.tick();
        if (active != null) {
            activeTarget = active.getTargetUuid();
            keepLoaded(active);
            if (scheduled) {
                cycle.deferFullCycle();
            }
            return;
        }
        if (!scheduled) {
            return;
        }
        if (!startRandom(settings)) {
            cycle.retry(settings.retryTicks());
        }
    }

    public void onDisconnect(ServerPlayer player) {
        // The hunter is never allowed to leave because its current target
        // disconnected; it re-targets the nearest remaining survival player.
        if (active != null) {
            active.retargetNow();
        }
    }

    public boolean startFor(ServerPlayer player, EscapeSettings settings) {
        despawn();
        EscapeEntity entity = spawnFor(player, settings);
        if (entity != null) {
            cycle.deferFullCycle();
            return true;
        }
        return false;
    }

    public boolean startRandom(EscapeSettings settings) {
        if (server == null) {
            return false;
        }
        List<ServerPlayer> candidates = eligiblePlayers(settings);
        if (candidates.isEmpty()) {
            return false;
        }
        BlockPos anchor = server.overworld().getRespawnData().pos();
        List<TargetSelector.Candidate<ServerPlayer>> positioned = new ArrayList<>();
        for (ServerPlayer player : candidates) {
            positioned.add(new TargetSelector.Candidate<>(
                    player, player.getX(), player.getY(), player.getZ(), true));
        }
        ServerPlayer nearest = TargetSelector.nearest(
                positioned, anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D).orElse(candidates.get(0));
        return spawnFor(nearest, settings) != null;
    }

    private List<ServerPlayer> eligiblePlayers(EscapeSettings settings) {
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            if (settings.disableInCreative && player.isCreative()) {
                continue;
            }
            if (!(player.level() instanceof ServerLevel)) {
                continue;
            }
            candidates.add(player);
        }
        return candidates;
    }

    private EscapeEntity spawnFor(ServerPlayer player, EscapeSettings settings) {
        ServerLevel level = player.level();
        BlockPos pos = findSpawnPos(level, player);
        EscapeEntity entity = EscapeEntities.ESCAPE.create(level, EntitySpawnReason.EVENT);
        if (entity == null) {
            Escape.LOGGER.warn("Could not create the Escape entity");
            return null;
        }
        entity.snapTo(pos.getX() + 0.5D, (double) pos.getY(), pos.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        entity.bindTo(player, settings.activeTicks());
        if (!level.addFreshEntity(entity)) {
            entity.discard();
            Escape.LOGGER.warn("Could not add the Escape entity to {}", level.dimension());
            return null;
        }
        active = entity;
        activeTarget = entity.getTargetUuid() == null ? player.getUUID() : entity.getTargetUuid();
        keepLoaded(entity);
        Escape.LOGGER.info("Escape {}+{} spawned for {} at {} {} {}",
                Escape.VERSION, "26.2", player.getScoreboardName(),
                pos.getX(), pos.getY(), pos.getZ());
        return entity;
    }

    private BlockPos findSpawnPos(ServerLevel level, ServerPlayer player) {
        for (int attempt = 0; attempt < 64; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = 10.0D + level.getRandom().nextDouble() * 14.0D;
            double x = player.getX() + Math.cos(angle) * distance;
            double z = player.getZ() + Math.sin(angle) * distance;
            BlockPos base = BlockPos.containing(x, player.getY(), z);
            int[] offsets = {0, 1, 2, -1, 3, -2, 4, -3};
            for (int offset : offsets) {
                BlockPos candidate = base.above(offset);
                if (isSpawnable(level, candidate)) {
                    return candidate;
                }
            }
        }
        BlockPos fallback = player.blockPosition().above();
        if (isSpawnable(level, fallback)) {
            return fallback;
        }
        return player.blockPosition();
    }

    private boolean isSpawnable(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState floor = level.getBlockState(pos.below());
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        return feet.isAir()
                && head.isAir()
                && floor.isSolid()
                && !EscapeEntity.isHazard(floor)
                && feet.getFluidState().isEmpty()
                && head.getFluidState().isEmpty();
    }

    private void keepLoaded(EscapeEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        ChunkPos pos = entity.chunkPosition();
        if (level == ticketLevel && pos.equals(ticketChunk)) {
            return;
        }
        releaseTicket();
        level.getChunkSource().addTicketWithRadius(ESCAPE_TICKET, pos, 1);
        ticketLevel = level;
        ticketChunk = pos;
    }

    private void releaseTicket() {
        if (ticketLevel != null && ticketChunk != null) {
            ticketLevel.getChunkSource().removeTicketWithRadius(ESCAPE_TICKET, ticketChunk, 1);
        }
        ticketLevel = null;
        ticketChunk = null;
    }

    public void despawn() {
        EscapeEntity entity = active;
        releaseTicket();
        if (entity == null) {
            activeTarget = null;
            return;
        }
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, entity.getX(), entity.getY() + 1.0D, entity.getZ(),
                    50, 0.6D, 1.0D, 0.6D, 0.1D);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.6F);
        }
        entity.discard();
        active = null;
        activeTarget = null;
    }

    public String status() {
        EscapeSettings settings = EscapeConfig.get();
        if (!settings.enabled) {
            return "Escape is disabled.";
        }
        if (active != null && active.isAlive() && !active.isRemoved()) {
            return "Escape is hunting " + targetName() + " (" + (active.getRemainingTicks() / 20) + "s left).";
        }
        if (cycle == null) {
            return "Escape is not active.";
        }
        return "No Escape active. Next encounter in " + cycle.secondsUntilNext() + "s.";
    }

    public String targetName() {
        if (activeTarget == null || server == null) {
            return "nobody";
        }
        ServerPlayer player = server.getPlayerList().getPlayer(activeTarget);
        return player == null ? "nearest player" : player.getScoreboardName();
    }

    public boolean hasActive() {
        return active != null && active.isAlive() && !active.isRemoved();
    }
}
