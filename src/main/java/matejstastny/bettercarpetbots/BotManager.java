package matejstastny.bettercarpetbots;

import carpet.CarpetServer;
import carpet.fakes.ServerPlayerInterface;
import carpet.helpers.EntityPlayerActionPack;
import carpet.patches.EntityPlayerMPFake;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BotManager {
    public static final String TEAM_NAME = "bots";

    private static final Logger LOGGER = LoggerFactory.getLogger(BotManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type BOT_STATES_TYPE = new TypeToken<Map<String, BotState>>() {}.getType();
    private static final Map<String, BotState> botStates = new HashMap<>();
    private static final Set<String> removedBotNames = new HashSet<>();
    private static final Set<String> restoreAttempts = new HashSet<>();
    private static MinecraftServer pendingRestoreServer;
    private static ServerPlayer pendingRestorePlayer;
    private static int restoreDelayTicks;
    private static int stateSaveTicks;

    private static int botPermissionLevel = 0;
    private static String globalBotSkinUrl = null;
    private static final Set<String> realPlayerNames = new HashSet<>();

    private static final PermissionCheck[] LEVEL_CHECKS = {
        Commands.LEVEL_ALL,
        Commands.LEVEL_MODERATORS,
        Commands.LEVEL_GAMEMASTERS,
        Commands.LEVEL_ADMINS,
        Commands.LEVEL_OWNERS,
    };

    public static int getBotPermissionLevel() {
        return botPermissionLevel;
    }

    public static void setBotPermissionLevel(int level) {
        botPermissionLevel = level;
    }

    public static PermissionCheck getBotPermissionCheck() {
        return LEVEL_CHECKS[Math.max(0, Math.min(4, botPermissionLevel))];
    }

    private BotManager() {}

    public static void ensureBotTeam(MinecraftServer server) {
        Scoreboard sb = server.getScoreboard();
        PlayerTeam team = sb.getPlayerTeam(TEAM_NAME);
        if (team == null) {
            team = sb.addPlayerTeam(TEAM_NAME);
        }
        team.setColor(Optional.of(TeamColor.GREEN));
        team.setPlayerPrefix(Component.literal("[Bot] ").withStyle(ChatFormatting.GREEN));
    }

    public static void addToTeam(MinecraftServer server, String playerName) {
        ensureBotTeam(server);
        Scoreboard sb = server.getScoreboard();
        PlayerTeam team = sb.getPlayerTeam(TEAM_NAME);
        sb.addPlayerToTeam(playerName, team);
    }

    public static String getGlobalBotSkinUrl() {
        return globalBotSkinUrl;
    }

    public static void setGlobalBotSkinUrl(String url) {
        if (url.startsWith("\"") && url.endsWith("\"")) {
            url = url.substring(1, url.length() - 1);
        }
        globalBotSkinUrl = url;
    }

    public static void loadBotStates(MinecraftServer server) {
        botStates.clear();
        removedBotNames.clear();
        restoreAttempts.clear();
        pendingRestoreServer = null;
        pendingRestorePlayer = null;
        restoreDelayTicks = 0;
        stateSaveTicks = 0;
        Path file = getBotStatesFile(server);
        if (!Files.exists(file)) return;
        try {
            Map<String, BotState> loaded = GSON.fromJson(Files.readString(file), BOT_STATES_TYPE);
            if (loaded != null) botStates.putAll(loaded);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to load Better Carpet Bots state from {}", file, e);
        }
    }

    public static void restoreBots(MinecraftServer server, ServerPlayer joiningPlayer) {
        boolean changed = false;
        List<Map.Entry<String, BotState>> entries = new ArrayList<>(botStates.entrySet());
        entries.sort(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER));
        Map<String, Integer> positionCounts = new HashMap<>();
        int missingPositionCount = 0;
        for (Map.Entry<String, BotState> entry : entries) {
            BotState state = entry.getValue();
            if (state.x != 0 || state.y != 0 || state.z != 0) {
                positionCounts.merge(positionKey(state), 1, Integer::sum);
            } else {
                missingPositionCount++;
            }
        }
        Map<String, Integer> positionIndexes = new HashMap<>();
        int missingPositionIndex = 0;

        for (Map.Entry<String, BotState> entry : entries) {
            String name = entry.getKey();
            BotState state = entry.getValue();
            if (server.getPlayerList().getPlayer(name) != null || !restoreAttempts.add(name)) continue;

            boolean missingPosition = state.x == 0 && state.y == 0 && state.z == 0;
            ResourceKey<Level> dimension;
            Vec3 position;
            float yaw = state.yaw;
            float pitch = state.pitch;
            int groupSize;
            int groupIndex;
            if (missingPosition) {
                groupSize = missingPositionCount;
                groupIndex = missingPositionIndex++;
                dimension = joiningPlayer.level().dimension();
            } else {
                String key = positionKey(state);
                groupSize = positionCounts.getOrDefault(key, 1);
                groupIndex = positionIndexes.merge(key, 1, Integer::sum) - 1;
                Identifier dimensionId;
                try {
                    dimensionId = Identifier.parse(state.dimension);
                } catch (RuntimeException e) {
                    LOGGER.error("Cannot restore bot '{}' with invalid dimension '{}'", name, state.dimension, e);
                    continue;
                }
                dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
            }
            boolean duplicatePosition = groupSize > 1;
            if (missingPosition || duplicatePosition) {
                Vec3 center = missingPosition ? joiningPlayer.position() : new Vec3(state.x, state.y, state.z);
                double angle = 2.0 * Math.PI * groupIndex / Math.max(1, groupSize);
                double radius = groupSize == 1 ? 2.5 : Math.max(2.5, groupSize * 0.75);
                position = center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                yaw = (float) Math.toDegrees(Math.atan2(-Math.cos(angle), -Math.sin(angle)));
                pitch = 0;
                state.x = position.x;
                state.y = position.y;
                state.z = position.z;
                state.yaw = yaw;
                state.pitch = pitch;
                state.dimension = dimension.identifier().toString();
                changed = true;
                LOGGER.warn(
                        "Restoring bot '{}' at distinct recovery position {} {} {}",
                        name,
                        position.x,
                        position.y,
                        position.z);
            } else {
                position = new Vec3(state.x, state.y, state.z);
            }

            ServerLevel level = server.getLevel(dimension);
            if (level == null) {
                LOGGER.warn("Cannot restore bot '{}' because dimension '{}' is unavailable", name, dimension);
                continue;
            }
            boolean started = EntityPlayerMPFake.createFake(
                    name, server, position, yaw, pitch, level.dimension(), GameType.SURVIVAL, false);
            if (!started) {
                restoreAttempts.remove(name);
                LOGGER.warn("Failed to restore bot '{}'", name);
            } else {
                LOGGER.info(
                        "Queued bot '{}' for restoration at {} {} {} in {}",
                        name,
                        state.x,
                        state.y,
                        state.z,
                        state.dimension);
            }
        }
        if (changed) saveBotStates(server);
    }

    private static String positionKey(BotState state) {
        return state.dimension + ":" + state.x + "," + state.y + "," + state.z;
    }

    public static void scheduleBotRestore(MinecraftServer server) {
        scheduleBotRestore(server, null);
    }

    public static void scheduleBotRestore(MinecraftServer server, ServerPlayer joiningPlayer) {
        if (botStates.isEmpty() || pendingRestoreServer == server || !restoreAttempts.isEmpty()) return;
        pendingRestoreServer = server;
        pendingRestorePlayer = joiningPlayer;
        restoreDelayTicks = 20;
    }

    public static void onServerTick(MinecraftServer server) {
        if (pendingRestoreServer == server && --restoreDelayTicks <= 0) {
            ServerPlayer joiningPlayer = pendingRestorePlayer;
            pendingRestoreServer = null;
            pendingRestorePlayer = null;
            if (joiningPlayer != null && joiningPlayer.isAlive()) {
                restoreBots(server, joiningPlayer);
            } else {
                LOGGER.warn("Skipping bot restoration because the joining player is no longer available");
            }
        }

        if (++stateSaveTicks >= 100) {
            stateSaveTicks = 0;
            if (!getActiveBots(server).isEmpty()) saveBotStates(server);
        }
    }

    public static void cancelPendingBotRestore(MinecraftServer server) {
        if (pendingRestoreServer != server) return;
        pendingRestoreServer = null;
        pendingRestorePlayer = null;
        restoreDelayTicks = 0;
    }

    public static void onBotLoggedOut(MinecraftServer server, ServerPlayer player) {
        if (!(player instanceof EntityPlayerMPFake) || removedBotNames.contains(player.getScoreboardName())) return;
        snapshotBot(player);
        saveBotStates(server);
    }

    public static void saveBotStates(MinecraftServer server) {
        for (ServerPlayer bot : getActiveBots(server)) {
            if (!removedBotNames.contains(bot.getScoreboardName())) snapshotBot(bot);
        }
        try {
            Path file = getBotStatesFile(server);
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(botStates));
        } catch (IOException e) {
            LOGGER.error("Failed to save Better Carpet Bots state", e);
        }
    }

    public static void forgetBot(MinecraftServer server, ServerPlayer bot) {
        if (!(bot instanceof EntityPlayerMPFake)) return;
        String name = bot.getScoreboardName();
        removedBotNames.add(name);
        restoreAttempts.remove(name);
        botStates.remove(name);
        saveBotStates(server);
    }

    public static void updateBotState(ServerPlayer bot, Consumer<BotState> update) {
        if (!(bot instanceof EntityPlayerMPFake)) return;
        BotState state = botStates.computeIfAbsent(bot.getScoreboardName(), ignored -> new BotState());
        update.accept(state);
    }

    private static void snapshotBot(ServerPlayer bot) {
        BotState state = botStates.computeIfAbsent(bot.getScoreboardName(), ignored -> new BotState());
        state.x = bot.getX();
        state.y = bot.getY();
        state.z = bot.getZ();
        state.yaw = bot.getYRot();
        state.pitch = bot.getXRot();
        state.dimension = bot.level().dimension().identifier().toString();
    }

    private static void restoreBotActions(ServerPlayer bot) {
        BotState state = botStates.get(bot.getScoreboardName());
        if (state == null) return;
        EntityPlayerActionPack actions = ((ServerPlayerInterface) bot).getActionPack();
        if (state.attacking) {
            actions.start(
                    EntityPlayerActionPack.ActionType.ATTACK,
                    state.attackInterval > 0
                            ? EntityPlayerActionPack.Action.interval(state.attackInterval)
                            : EntityPlayerActionPack.Action.continuous());
        }
        if (state.using) {
            actions.start(
                    EntityPlayerActionPack.ActionType.USE,
                    state.useInterval > 0
                            ? EntityPlayerActionPack.Action.interval(state.useInterval)
                            : EntityPlayerActionPack.Action.continuous());
        }
        if (state.jumping)
            actions.start(EntityPlayerActionPack.ActionType.JUMP, EntityPlayerActionPack.Action.continuous());
        if (state.movement != 0) actions.setForward(state.movement);
        actions.setSneaking(state.sneaking);
        actions.setSprinting(state.sprinting);
    }

    public static void setBotAttack(ServerPlayer bot, int interval) {
        updateBotState(bot, state -> {
            state.attacking = interval >= 0;
            state.attackInterval = Math.max(0, interval);
        });
    }

    public static void setBotUse(ServerPlayer bot, int interval) {
        updateBotState(bot, state -> {
            state.using = interval >= 0;
            state.useInterval = Math.max(0, interval);
        });
    }

    public static void setBotJumping(ServerPlayer bot, boolean jumping) {
        updateBotState(bot, state -> state.jumping = jumping);
    }

    public static void setBotMovement(ServerPlayer bot, float movement) {
        updateBotState(bot, state -> state.movement = movement);
    }

    public static void setBotSneaking(ServerPlayer bot, boolean sneaking) {
        updateBotState(bot, state -> state.sneaking = sneaking);
    }

    public static void setBotSprinting(ServerPlayer bot, boolean sprinting) {
        updateBotState(bot, state -> state.sprinting = sprinting);
    }

    public static void stopBotActions(ServerPlayer bot) {
        updateBotState(bot, state -> {
            state.attacking = false;
            state.using = false;
            state.jumping = false;
            state.movement = 0;
            state.sneaking = false;
            state.sprinting = false;
        });
    }

    public static class BotState {
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
        public String dimension = Level.OVERWORLD.identifier().toString();
        public boolean attacking;
        public int attackInterval;
        public boolean using;
        public int useInterval;
        public boolean jumping;
        public float movement;
        public boolean sneaking;
        public boolean sprinting;
    }

    public static void applyGlobalSkinToAllBots(MinecraftServer server) {
        if (globalBotSkinUrl == null) return;
        for (ServerPlayer bot : getActiveBots(server)) {
            applySkin(server, bot, globalBotSkinUrl);
        }
    }

    public static boolean applySkin(MinecraftServer server, ServerPlayer bot, String url) {
        try {
            server.getCommands()
                    .performPrefixedCommand(
                            bot.createCommandSourceStack()
                                    .withPermission(LevelBasedPermissionSet.OWNER)
                                    .withSuppressedOutput(),
                            "skin set web slim \"" + url + "\"");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Called from CarpetExtension.onPlayerLoggedIn for every joining player. */
    public static void onPlayerJoin(ServerPlayer player) {
        MinecraftServer server = CarpetServer.minecraft_server;
        if (player instanceof EntityPlayerMPFake) {
            addToTeam(server, player.getScoreboardName());
            restoreBotActions(player);
            if (globalBotSkinUrl != null) {
                applySkin(server, player, globalBotSkinUrl);
            }
        } else {
            markAsRealPlayer(server, player.getScoreboardName());
            scheduleBotRestore(server, player);
        }
    }

    // --- Real player protection ---

    public static boolean isRealPlayer(String name) {
        return realPlayerNames.contains(name);
    }

    /**
     * Records a name as belonging to a real (non-fake) player. On the first call for a given
     * name, deletes any offline-UUID player data that may have been written by a bot with that
     * name, then persists the updated list to disk.
     */
    public static void markAsRealPlayer(MinecraftServer server, String name) {
        if (realPlayerNames.add(name)) {
            deleteOfflinePlayerData(server, name);
            saveRealPlayers(server);
        }
    }

    public static void loadRealPlayers(MinecraftServer server) {
        realPlayerNames.clear();
        Path file = getRealPlayersFile(server);
        if (!Files.exists(file)) return;
        try {
            Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(realPlayerNames::add);
        } catch (IOException e) {
            // leave set empty on read error
        }
    }

    private static void saveRealPlayers(MinecraftServer server) {
        try {
            Path file = getRealPlayersFile(server);
            Files.createDirectories(file.getParent());
            Files.write(file, realPlayerNames, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // data remains in memory for this session
        }
    }

    private static void deleteOfflinePlayerData(MinecraftServer server, String name) {
        try {
            UUID offlineUUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            Path playerDataDir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
            Path backupDir = server.getWorldPath(LevelResource.ROOT).resolve("bot-backup");
            Files.createDirectories(backupDir);

            Path dat = playerDataDir.resolve(offlineUUID + ".dat");
            if (Files.exists(dat)) {
                Files.copy(dat, backupDir.resolve(name + ".bak"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Files.delete(dat);
            }
            Files.deleteIfExists(playerDataDir.resolve(offlineUUID + ".dat_old"));
        } catch (Exception e) {
            // ignore - best-effort cleanup
        }
    }

    private static Path getRealPlayersFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("better-carpet-bots")
                .resolve("real-players.txt");
    }

    private static Path getBotStatesFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("better-carpet-bots")
                .resolve("bots.json");
    }

    // ---

    public static boolean isBotMember(MinecraftServer server, String name) {
        PlayerTeam team = server.getScoreboard().getPlayerTeam(TEAM_NAME);
        return team != null && team.getPlayers().contains(name);
    }

    public static List<ServerPlayer> getActiveBots(MinecraftServer server) {
        List<ServerPlayer> bots = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player instanceof EntityPlayerMPFake) {
                bots.add(player);
            }
        }
        return bots;
    }

    /** Returns the next available auto-name: Bot1, Bot2, ... */
    public static String nextAutoName(MinecraftServer server) {
        int i = 1;
        while (true) {
            String name = "Bot" + i;
            if (server.getPlayerList().getPlayer(name) == null) {
                return name;
            }
            i++;
        }
    }
}
