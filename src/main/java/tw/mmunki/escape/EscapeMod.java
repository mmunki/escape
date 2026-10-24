package tw.mmunki.escape;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import tw.mmunki.escape.command.EscapeCommands;
import tw.mmunki.escape.entity.EscapeEntities;
import tw.mmunki.escape.server.EscapeManager;

public final class EscapeMod implements ModInitializer {
    private final EscapeManager manager = new EscapeManager();

    @Override
    public void onInitialize() {
        EscapeConfig.load();
        EscapeEntities.register();

        ServerLifecycleEvents.SERVER_STARTING.register(manager::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> manager.stop());
        ServerTickEvents.END_SERVER_TICK.register(manager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                manager.onDisconnect(handler.player));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                EscapeCommands.register(dispatcher, manager));

        Escape.LOGGER.info("Escape {} loaded for Minecraft 26.2", Escape.VERSION);
    }
}
