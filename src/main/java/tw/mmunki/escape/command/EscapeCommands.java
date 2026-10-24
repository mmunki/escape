package tw.mmunki.escape.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import tw.mmunki.escape.EscapeConfig;
import tw.mmunki.escape.EscapeSettings;
import tw.mmunki.escape.server.EscapeManager;

public final class EscapeCommands {
    private EscapeCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, EscapeManager manager) {
        dispatcher.register(Commands.literal("escape")
                .executes(context -> status(context.getSource(), manager))
                .then(Commands.literal("help")
                        .executes(context -> help(context.getSource())))
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource(), manager)))
                .then(Commands.literal("start")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(context -> start(context.getSource(), manager))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> startFor(context.getSource(), manager,
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("stop")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(context -> stop(context.getSource(), manager)))
                .then(Commands.literal("reload")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(context -> reload(context.getSource(), manager))));
    }

    private static int start(CommandSourceStack source, EscapeManager manager) {
        ServerPlayer player = source.getPlayer();
        EscapeSettings settings = EscapeConfig.get();
        if (player != null) {
            if (!manager.startFor(player, settings)) {
                source.sendFailure(Component.literal("Could not spawn Escape for you. Is the area safe?"));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("Escape is now hunting you.")
                    .withStyle(ChatFormatting.DARK_RED), false);
            return 1;
        }
        if (!manager.startRandom(settings)) {
            source.sendFailure(Component.literal("Could not spawn Escape: no eligible player."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Escape is now hunting " + manager.targetName() + ".")
                .withStyle(ChatFormatting.DARK_RED), true);
        return 1;
    }

    private static int startFor(CommandSourceStack source, EscapeManager manager, ServerPlayer target) {
        if (!manager.startFor(target, EscapeConfig.get())) {
            source.sendFailure(Component.literal("Could not spawn Escape for " + target.getScoreboardName() + "."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Escape is now hunting " + target.getScoreboardName() + ".")
                .withStyle(ChatFormatting.DARK_RED), true);
        return 1;
    }

    private static int stop(CommandSourceStack source, EscapeManager manager) {
        manager.despawn();
        source.sendSuccess(() -> Component.literal("Escape was removed.")
                .withStyle(ChatFormatting.GRAY), true);
        return 1;
    }

    private static int status(CommandSourceStack source, EscapeManager manager) {
        source.sendSuccess(() -> Component.literal(manager.status())
                .withStyle(ChatFormatting.DARK_RED), false);
        return 1;
    }

    private static int reload(CommandSourceStack source, EscapeManager manager) {
        manager.reload();
        EscapeSettings settings = EscapeConfig.get();
        source.sendSuccess(() -> Component.literal("Escape config reloaded: enabled=" + settings.enabled
                        + " cycle=" + settings.cycleSeconds + "s active=" + settings.activeSeconds + "s")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Escape commands:").withStyle(ChatFormatting.DARK_RED), false);
        source.sendSuccess(() -> Component.literal("/escape status - current encounter and countdown")
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/escape start [player] - spawn Escape now")
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/escape stop - remove the current Escape")
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/escape reload - reload config/escape.json")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }
}
