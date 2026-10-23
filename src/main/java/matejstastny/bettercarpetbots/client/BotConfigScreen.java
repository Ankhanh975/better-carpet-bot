package matejstastny.bettercarpetbots.client;

import matejstastny.bettercarpetbots.BotConfig;
import matejstastny.bettercarpetbots.BotManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public class BotConfigScreen extends Screen {
    private final Screen parent;
    private EditBox skinUrlField;

    public BotConfigScreen(Screen parent) {
        super(Component.literal("Better Carpet Bots"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        this.skinUrlField = new EditBox(this.font, cx - 150, cy - 10, 300, 20, Component.empty());
        this.skinUrlField.setMaxLength(512);
        String current = BotConfig.get().skinUrl;
        this.skinUrlField.setValue(current != null ? current : "");
        this.addRenderableWidget(this.skinUrlField);
        this.setInitialFocus(this.skinUrlField);

        this.addRenderableWidget(Button.builder(Component.literal("Save & Apply"), btn -> save())
                .bounds(cx - 155, cy + 20, 150, 20)
                .build());

        this.addRenderableWidget(
                Button.builder(Component.literal("Cancel"), btn -> this.minecraft.setScreenAndShow(parent))
                        .bounds(cx + 5, cy + 20, 150, 20)
                        .build());
    }

    private void save() {
        String url = this.skinUrlField.getValue().trim();
        BotConfig.get().skinUrl = url.isEmpty() ? null : url;
        BotConfig.save();

        if (!url.isEmpty()) {
            MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
            if (server != null) {
                server.executeIfPossible(() -> {
                    BotManager.setGlobalBotSkinUrl(url);
                    BotManager.applyGlobalSkinToAllBots(server);
                });
            }
        }

        this.minecraft.setScreenAndShow(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, delta);
        guiGraphics.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFFFFF);
        guiGraphics.text(
                this.font,
                Component.literal("Bot Skin URL"),
                this.width / 2 - 150,
                this.height / 2 - 25,
                0xFFA0A0A0,
                true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
