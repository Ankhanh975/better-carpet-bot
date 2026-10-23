package matejstastny.bettercarpetbots;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public class BotConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("better-carpet-bots.json");

    private static BotConfig INSTANCE = new BotConfig();

    public String skinUrl = null;

    public static BotConfig get() {
        return INSTANCE;
    }

    public static void load() {
        if (!Files.exists(CONFIG_FILE)) return;
        try {
            BotConfig loaded = GSON.fromJson(Files.readString(CONFIG_FILE), BotConfig.class);
            if (loaded != null) INSTANCE = loaded;
        } catch (IOException e) {
            // leave defaults
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            Files.writeString(CONFIG_FILE, GSON.toJson(INSTANCE));
        } catch (IOException e) {
            // best-effort
        }
    }
}
