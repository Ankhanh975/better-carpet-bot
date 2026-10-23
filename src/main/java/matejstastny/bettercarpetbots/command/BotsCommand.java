package matejstastny.bettercarpetbots.command;

import static net.minecraft.commands.Commands.*;

import carpet.fakes.ServerPlayerInterface;
import carpet.patches.EntityPlayerMPFake;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import matejstastny.bettercarpetbots.BotConfig;
import matejstastny.bettercarpetbots.BotManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class BotsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("bots")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(literal("list").executes(BotsCommand::listBots))
                .then(literal("stop").executes(BotsCommand::stopAll))
                .then(literal("kill").executes(BotsCommand::killAll))
                .then(literal("permissionLevel")
                        .then(argument("level", IntegerArgumentType.integer(0, 4))
                                .executes(ctx -> {
                                    int level = IntegerArgumentType.getInteger(ctx, "level");
                                    BotManager.setBotPermissionLevel(level);
                                    ctx.getSource()
                                            .sendSuccess(
                                                    () -> Component.literal(
                                                            "/bot permission level set to " + level + "."),
                                                    true);
                                    return level;
                                })))
                .then(literal("skin")
                        .then(literal("all")
                                .then(argument("url", StringArgumentType.greedyString())
                                        .executes(ctx -> skinAll(ctx, StringArgumentType.getString(ctx, "url")))))
                        .then(argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (ServerPlayer p : BotManager.getActiveBots(
                                            ctx.getSource().getServer())) {
                                        builder.suggest(p.getScoreboardName());
                                    }
                                    return builder.buildFuture();
                                })
                                .then(argument("url", StringArgumentType.greedyString())
                                        .executes(ctx -> skinOne(
                                                ctx,
                                                StringArgumentType.getString(ctx, "name"),
                                                StringArgumentType.getString(ctx, "url")))))));
    }

    private static int listBots(CommandContext<CommandSourceStack> ctx) {
        List<ServerPlayer> bots = BotManager.getActiveBots(ctx.getSource().getServer());
        if (bots.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No bots are currently online."), false);
        } else {
            StringBuilder sb = new StringBuilder("Online bots (" + bots.size() + "): ");
            for (int i = 0; i < bots.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(bots.get(i).getScoreboardName());
            }
            String msg = sb.toString();
            ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        }
        return bots.size();
    }

    private static int stopAll(CommandContext<CommandSourceStack> ctx) {
        List<ServerPlayer> bots = BotManager.getActiveBots(ctx.getSource().getServer());
        for (ServerPlayer bot : bots) {
            ((ServerPlayerInterface) bot).getActionPack().stopAll();
            BotManager.stopBotActions(bot);
        }
        ctx.getSource()
                .sendSuccess(() -> Component.literal("Stopped all actions for " + bots.size() + " bot(s)."), false);
        return bots.size();
    }

    private static int killAll(CommandContext<CommandSourceStack> ctx) {
        List<ServerPlayer> bots = BotManager.getActiveBots(ctx.getSource().getServer());
        for (ServerPlayer bot : bots) {
            if (bot instanceof EntityPlayerMPFake fake) {
                BotManager.forgetBot(ctx.getSource().getServer(), bot);
                fake.kill(Component.literal("Removed by /bots kill"));
            } else {
                bot.connection.disconnect(Component.literal("Removed by /bots kill"));
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Killed " + bots.size() + " bot(s)."), false);
        return bots.size();
    }

    private static int skinOne(CommandContext<CommandSourceStack> ctx, String name, String url) {
        ServerPlayer bot = ctx.getSource().getServer().getPlayerList().getPlayer(name);
        if (bot == null || !(bot instanceof EntityPlayerMPFake)) {
            ctx.getSource().sendFailure(Component.literal("Bot '" + name + "' is not online."));
            return 0;
        }
        return applySkin(ctx, bot, url) ? 1 : 0;
    }

    private static int skinAll(CommandContext<CommandSourceStack> ctx, String url) {
        BotManager.setGlobalBotSkinUrl(url);
        BotConfig.get().skinUrl = url;
        BotConfig.save();
        List<ServerPlayer> bots = BotManager.getActiveBots(ctx.getSource().getServer());
        int applied = 0;
        for (ServerPlayer bot : bots) {
            if (BotManager.applySkin(ctx.getSource().getServer(), bot, url)) applied++;
        }
        int finalApplied = applied;
        ctx.getSource()
                .sendSuccess(
                        () -> Component.literal(
                                "Skin saved. Applied to " + finalApplied + "/" + bots.size() + " online bot(s)."),
                        false);
        return applied;
    }

    private static boolean applySkin(CommandContext<CommandSourceStack> ctx, ServerPlayer bot, String url) {
        boolean ok = BotManager.applySkin(ctx.getSource().getServer(), bot, url);
        if (!ok) {
            String botName = bot.getScoreboardName();
            ctx.getSource().sendFailure(Component.literal("Skin command failed for '" + botName + "'."));
        }
        return ok;
    }
}
