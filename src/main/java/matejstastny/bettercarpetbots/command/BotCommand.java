package matejstastny.bettercarpetbots.command;

import static net.minecraft.commands.Commands.*;

import carpet.fakes.ServerPlayerInterface;
import carpet.helpers.EntityPlayerActionPack;
import carpet.helpers.EntityPlayerActionPack.Action;
import carpet.helpers.EntityPlayerActionPack.ActionType;
import carpet.patches.EntityPlayerMPFake;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.function.Consumer;
import matejstastny.bettercarpetbots.BotManager;
import matejstastny.bettercarpetbots.screen.BotInventoryHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class BotCommand {

    private static final SimpleCommandExceptionType BOT_NOT_FOUND =
            new SimpleCommandExceptionType(Component.literal("Bot not found or not online."));
    private static final SimpleCommandExceptionType NOT_A_BOT =
            new SimpleCommandExceptionType(Component.literal("That player is not a bot."));
    private static final SimpleCommandExceptionType INVALID_SPAWN_FLAGS =
            new SimpleCommandExceptionType(Component.literal("Spawn flags must be one or two digits, each 0 or 1."));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("bot")
                .requires(Commands.hasPermission(BotManager.getBotPermissionCheck()))

                // /bot spawn [<name> [<flags>]]
                .then(literal("spawn")
                        .executes(ctx -> spawnBot(ctx, null, "00"))
                        .then(argument("name", StringArgumentType.word())
                                .executes(ctx -> spawnBot(ctx, StringArgumentType.getString(ctx, "name"), "00"))
                                .then(argument("flags", StringArgumentType.word())
                                        .executes(ctx -> spawnBot(
                                                ctx,
                                                StringArgumentType.getString(ctx, "name"),
                                                StringArgumentType.getString(ctx, "flags"))))))

                // /bot <name> <action>
                .then(argument("botname", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (ServerPlayer p :
                                    BotManager.getActiveBots(ctx.getSource().getServer())) {
                                builder.suggest(p.getScoreboardName());
                            }
                            ServerPlayer caller = ctx.getSource().getPlayer();
                            if (caller != null) builder.suggest(caller.getScoreboardName());
                            return builder.buildFuture();
                        })
                        .then(literal("attack")
                                .executes(ctx -> action(
                                        ctx,
                                        ap -> ap.start(ActionType.ATTACK, Action.continuous()),
                                        bot -> BotManager.setBotAttack(bot, 0)))
                                .then(literal("stop")
                                        .executes(ctx -> action(
                                                ctx,
                                                ap -> ap.start(ActionType.ATTACK, null),
                                                bot -> BotManager.setBotAttack(bot, -1))))
                                .then(literal("continuous")
                                        .executes(ctx -> action(
                                                ctx,
                                                ap -> ap.start(ActionType.ATTACK, Action.continuous()),
                                                bot -> BotManager.setBotAttack(bot, 0))))
                                .then(literal("interval")
                                        .then(argument("ticks", IntegerArgumentType.integer(1))
                                                .executes(ctx -> action(
                                                        ctx,
                                                        ap -> ap.start(
                                                                ActionType.ATTACK,
                                                                Action.interval(
                                                                        IntegerArgumentType.getInteger(ctx, "ticks"))),
                                                        bot -> BotManager.setBotAttack(
                                                                bot, IntegerArgumentType.getInteger(ctx, "ticks")))))))
                        .then(literal("use")
                                .executes(ctx -> action(
                                        ctx,
                                        ap -> ap.start(ActionType.USE, Action.continuous()),
                                        bot -> BotManager.setBotUse(bot, 0)))
                                .then(literal("stop")
                                        .executes(ctx -> action(
                                                ctx,
                                                ap -> ap.start(ActionType.USE, null),
                                                bot -> BotManager.setBotUse(bot, -1))))
                                .then(literal("continuous")
                                        .executes(ctx -> action(
                                                ctx,
                                                ap -> ap.start(ActionType.USE, Action.continuous()),
                                                bot -> BotManager.setBotUse(bot, 0))))
                                .then(literal("interval")
                                        .then(argument("ticks", IntegerArgumentType.integer(1))
                                                .executes(ctx -> action(
                                                        ctx,
                                                        ap -> ap.start(
                                                                ActionType.USE,
                                                                Action.interval(
                                                                        IntegerArgumentType.getInteger(ctx, "ticks"))),
                                                        bot -> BotManager.setBotUse(
                                                                bot, IntegerArgumentType.getInteger(ctx, "ticks")))))))
                        .then(literal("jump")
                                .executes(ctx -> action(
                                        ctx,
                                        ap -> ap.start(ActionType.JUMP, Action.continuous()),
                                        bot -> BotManager.setBotJumping(bot, true)))
                                .then(literal("stop")
                                        .executes(ctx -> action(
                                                ctx,
                                                ap -> ap.start(ActionType.JUMP, null),
                                                bot -> BotManager.setBotJumping(bot, false)))))
                        .then(literal("forward")
                                .executes(ctx ->
                                        action(ctx, ap -> ap.setForward(1), bot -> BotManager.setBotMovement(bot, 1)))
                                .then(literal("stop")
                                        .executes(ctx -> action(
                                                ctx,
                                                ap -> ap.setForward(0),
                                                bot -> BotManager.setBotMovement(bot, 0)))))
                        .then(literal("backward")
                                .executes(ctx ->
                                        action(ctx, ap -> ap.setForward(-1), bot -> BotManager.setBotMovement(bot, -1)))
                                .then(literal("stop")
                                        .executes(ctx -> action(
                                                ctx,
                                                ap -> ap.setForward(0),
                                                bot -> BotManager.setBotMovement(bot, 0)))))
                        .then(literal("drop").executes(ctx -> drop(ctx, false)))
                        .then(literal("dropstack").executes(ctx -> drop(ctx, true)))
                        .then(literal("swaphands")
                                .executes(ctx -> action(ctx, ap -> ap.start(ActionType.SWAP_HANDS, Action.once()))))
                        .then(literal("sneak")
                                .executes(ctx -> action(
                                        ctx, ap -> ap.setSneaking(true), bot -> BotManager.setBotSneaking(bot, true)))
                                .then(literal("stop")
                                        .executes(ctx -> action(
                                                ctx,
                                                ap -> ap.setSneaking(false),
                                                bot -> BotManager.setBotSneaking(bot, false)))))
                        .then(literal("sprint")
                                .executes(ctx -> action(
                                        ctx, ap -> ap.setSprinting(true), bot -> BotManager.setBotSprinting(bot, true)))
                                .then(literal("stop")
                                        .executes(ctx -> action(
                                                ctx,
                                                ap -> ap.setSprinting(false),
                                                bot -> BotManager.setBotSprinting(bot, false)))))
                        .then(literal("stop")
                                .executes(ctx ->
                                        action(ctx, EntityPlayerActionPack::stopAll, BotManager::stopBotActions)))
                        .then(literal("kill").executes(BotCommand::killBot))
                        .then(literal("disconnect").executes(BotCommand::disconnectBot))
                        .then(literal("look")
                                .then(literal("at").executes(BotCommand::lookAtCaller))
                                .then(literal("north").executes(ctx -> action(ctx, ap -> ap.look(Direction.NORTH))))
                                .then(literal("south").executes(ctx -> action(ctx, ap -> ap.look(Direction.SOUTH))))
                                .then(literal("east").executes(ctx -> action(ctx, ap -> ap.look(Direction.EAST))))
                                .then(literal("west").executes(ctx -> action(ctx, ap -> ap.look(Direction.WEST))))
                                .then(literal("up").executes(ctx -> action(ctx, ap -> ap.look(Direction.UP))))
                                .then(literal("down").executes(ctx -> action(ctx, ap -> ap.look(Direction.DOWN))))
                                .then(argument("yaw", FloatArgumentType.floatArg(-180, 180))
                                        .then(argument("pitch", FloatArgumentType.floatArg(-90, 90))
                                                .executes(ctx -> action(
                                                        ctx,
                                                        ap -> ap.look(
                                                                FloatArgumentType.getFloat(ctx, "yaw"),
                                                                FloatArgumentType.getFloat(ctx, "pitch")))))))
                        .then(literal("inventory").executes(BotCommand::openInventory))));
    }

    // --- Spawn ---

    private static int spawnBot(CommandContext<CommandSourceStack> ctx, String rawName, String flags)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        MinecraftServer server = src.getServer();
        if (!flags.matches("[01]{1,2}")) throw INVALID_SPAWN_FLAGS.create();
        boolean attack = flags.charAt(0) == '1';
        boolean moveForward = flags.length() > 1 && flags.charAt(1) == '1';
        String name = (rawName == null || rawName.isBlank()) ? BotManager.nextAutoName(server) : rawName;

        if (!isValidName(name)) {
            src.sendFailure(Component.literal(
                    "Invalid name '" + name + "': must be 1-16 chars, letters/digits/underscores only."));
            return 0;
        }
        if (BotManager.isRealPlayer(name)) {
            src.sendFailure(Component.literal("'" + name + "' is a real player and cannot be spawned as a bot."));
            return 0;
        }
        if (server.getPlayerList().getPlayer(name) != null) {
            src.sendFailure(Component.literal("A player named '" + name + "' is already online."));
            return 0;
        }

        ServerPlayer executor = src.getPlayerOrException();
        BotManager.BotState savedState = BotManager.getSavedBotState(name);
        ServerLevel savedLevel = BotManager.getSavedBotLevel(server, savedState);
        boolean restoring = savedState != null && savedLevel != null;
        Vec3 pos = restoring
                ? new Vec3(savedState.x, savedState.y, savedState.z)
                : new Vec3(executor.getX(), executor.getY(), executor.getZ());
        ServerLevel level = restoring ? savedLevel : src.getLevel();
        ResourceKey<Level> levelKey = level.dimension();
        float yaw = restoring ? savedState.yaw : executor.getYRot();
        float pitch = restoring ? savedState.pitch : executor.getXRot();

        boolean started =
                EntityPlayerMPFake.createFake(name, server, pos, yaw, pitch, levelKey, GameType.SURVIVAL, false);
        if (!started) {
            src.sendFailure(Component.literal("Failed to spawn bot '" + name + "'."));
            return 0;
        }

        if (!restoring) BotManager.setBotSpawnActions(server, name, attack, moveForward);
        return 1;
    }

    // --- Actions ---

    private static int lookAtCaller(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer caller = ctx.getSource().getPlayerOrException();
        return action(ctx, ap -> ap.look(caller.getYRot(), caller.getXRot()));
    }

    @FunctionalInterface
    private interface ActionConsumer {
        void accept(EntityPlayerActionPack ap) throws Exception;
    }

    private static int action(CommandContext<CommandSourceStack> ctx, ActionConsumer consumer)
            throws CommandSyntaxException {
        return action(ctx, consumer, bot -> {});
    }

    private static int action(
            CommandContext<CommandSourceStack> ctx, ActionConsumer consumer, Consumer<ServerPlayer> stateUpdater)
            throws CommandSyntaxException {
        ServerPlayer bot = getBot(ctx);
        EntityPlayerActionPack ap = ((ServerPlayerInterface) bot).getActionPack();
        try {
            consumer.accept(ap);
        } catch (CommandSyntaxException e) {
            throw e;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Action failed: " + e.getMessage()));
            return 0;
        }
        stateUpdater.accept(bot);
        return 1;
    }

    private static int drop(CommandContext<CommandSourceStack> ctx, boolean fullStack) throws CommandSyntaxException {
        ServerPlayer bot = getBot(ctx);
        ((ServerPlayerInterface) bot).getActionPack().drop(bot.getInventory().getSelectedSlot(), fullStack);
        return 1;
    }

    // --- Kill ---

    private static int killBot(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer bot = getBot(ctx);
        if (bot instanceof EntityPlayerMPFake fake) {
            BotManager.forgetBot(ctx.getSource().getServer(), bot);
            fake.kill(Component.literal("Removed by /bot kill"));
        } else {
            bot.connection.disconnect(Component.literal("Removed by /bot kill"));
        }
        return 1;
    }

    private static int disconnectBot(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer bot = getBot(ctx);
        if (!(bot instanceof EntityPlayerMPFake fake)) throw NOT_A_BOT.create();
        BotManager.disconnectBot(ctx.getSource().getServer(), fake);
        ctx.getSource()
                .sendSuccess(
                        () -> Component.literal("Disconnected bot '" + bot.getScoreboardName()
                                + "' while keeping its state. Use /bot spawn " + bot.getScoreboardName()
                                + " to summon it."),
                        false);
        return 1;
    }

    // --- Inventory ---

    private static int openInventory(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer opener = src.getPlayerOrException();
        ServerPlayer bot = getBot(ctx);

        if (opener.distanceToSqr(bot) > 100.0) {
            src.sendFailure(Component.literal("Too far from bot (max 10 blocks)."));
            return 0;
        }

        opener.openMenu(new SimpleMenuProvider(
                (syncId, openerInv, player) -> new BotInventoryHandler(syncId, openerInv, bot),
                Component.literal("Bot: " + bot.getScoreboardName())));
        return 1;
    }

    // --- Helpers ---

    private static ServerPlayer getBot(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "botname");
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayer player = server.getPlayerList().getPlayer(name);
        if (player == null) throw BOT_NOT_FOUND.create();
        if (!(player instanceof EntityPlayerMPFake)) {
            ServerPlayer caller = ctx.getSource().getPlayer();
            if (caller == null || caller != player) throw NOT_A_BOT.create();
        }
        return player;
    }

    private static boolean isValidName(String name) {
        return !name.isEmpty() && name.length() <= 16 && name.matches("[a-zA-Z0-9_]+");
    }
}
