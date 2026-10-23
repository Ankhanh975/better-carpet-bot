package matejstastny.bettercarpetbots;

import carpet.CarpetServer;
import net.fabricmc.api.ModInitializer;

public class CarpetBotsMod implements ModInitializer {
    public static final String MOD_ID = "better-carpet-bots";

    @Override
    public void onInitialize() {
        CarpetServer.manageExtension(new CarpetBotsExtension());
    }
}
