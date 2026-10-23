package matejstastny.bettercarpetbots.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;

public class BotInventoryHandler extends AbstractContainerMenu {

    // 45 container slots (5 rows × 9):
    //   Rows 0-3 (slots  0-35): bot main inv [9..35] then hotbar [0..8]
    //   Row  4   (slots 36-40): helmet, chestplate, leggings, boots, offhand
    //   Row  4   (slots 41-44): filler (gray glass panes, locked)
    // Slots 45-80: opener's own inventory (standard chest layout)

    // Screen slot indices for armor + offhand (used for visual glass pane injection)
    private static final int[] ARMOR_OFFHAND_SLOTS = {36, 37, 38, 39, 40};

    private final ServerPlayer bot;
    private final ServerPlayer opener;
    private final SimpleContainer fillerInv;
    private final ItemStack[] armorOffhandPlaceholders;

    public BotInventoryHandler(int syncId, Inventory openerInv, ServerPlayer bot) {
        super(MenuType.GENERIC_9x5, syncId);
        this.bot = bot;
        this.opener = (ServerPlayer) openerInv.player;
        this.fillerInv = new SimpleContainer(4);

        this.armorOffhandPlaceholders = new ItemStack[] {
            makePlaceholder(Items.STAINED_GLASS_PANE.pick(DyeColor.RED), "Helmet"),
            makePlaceholder(Items.STAINED_GLASS_PANE.pick(DyeColor.ORANGE), "Chestplate"),
            makePlaceholder(Items.STAINED_GLASS_PANE.pick(DyeColor.YELLOW), "Leggings"),
            makePlaceholder(Items.STAINED_GLASS_PANE.pick(DyeColor.GREEN), "Boots"),
            makePlaceholder(Items.STAINED_GLASS_PANE.pick(DyeColor.CYAN), "Offhand"),
        };

        // Point slots directly at the bot's live inventory - no proxy.
        // This ensures the screen handler's own tracking picks up any changes
        // (items picked up by the bot, etc.) automatically every tick.
        Inventory botInv = bot.getInventory();

        // Rows 0-2: non-hotbar (botInv slots 9-35)
        for (int i = 0; i < 27; i++) {
            addSlot(new Slot(botInv, 9 + i, 8 + (i % 9) * 18, 18 + (i / 9) * 18));
        }
        // Row 3: hotbar (botInv slots 0-8)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(botInv, col, 8 + col * 18, 72));
        }

        // Row 4: armor (type-filtered) + offhand + filler
        addSlot(new ArmorSlot(botInv, 39, 8, 90, EquipmentSlot.HEAD));
        addSlot(new ArmorSlot(botInv, 38, 26, 90, EquipmentSlot.CHEST));
        addSlot(new ArmorSlot(botInv, 37, 44, 90, EquipmentSlot.LEGS));
        addSlot(new ArmorSlot(botInv, 36, 62, 90, EquipmentSlot.FEET));
        addSlot(new Slot(botInv, 40, 80, 90)); // offhand: any item
        for (int i = 0; i < 4; i++) {
            addSlot(new FillerSlot(fillerInv, i, 98 + i * 18, 90));
        }

        // Opener's own inventory (3 rows + hotbar, matching GENERIC_9X5 layout)
        int yShift = 18;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(openerInv, 9 + col + row * 9, 8 + col * 18, 103 + row * 18 + yShift));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(openerInv, col, 8 + col * 18, 161 + yShift));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return !bot.isRemoved() && player.distanceToSqr(bot) <= 100.0;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        fillerInv.stopOpen(player);
    }

    /**
     * After the vanilla sync sends real slot contents, visually replace empty
     * armor/offhand slots with glass pane placeholders on the client.
     *
     * Keeping getItem() empty means vanilla insertion logic works normally -
     * the client sees the placeholder only as a visual hint.
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        for (int i = 0; i < ARMOR_OFFHAND_SLOTS.length; i++) {
            if (this.slots.get(ARMOR_OFFHAND_SLOTS[i]).getItem().isEmpty()) {
                opener.connection.send(new ClientboundContainerSetSlotPacket(
                        this.containerId,
                        this.incrementStateId(),
                        ARMOR_OFFHAND_SLOTS[i],
                        armorOffhandPlaceholders[i]));
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();

        if (index < 41) {
            // Bot slot → opener inventory
            if (!moveItemStackTo(stack, 45, 81, true)) return ItemStack.EMPTY;
        } else if (index >= 45) {
            // Opener inventory → bot slots
            if (!moveItemStackTo(stack, 0, 41, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY; // filler slots 41-44
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    private static ItemStack makePlaceholder(Item item, String label) {
        ItemStack pane = new ItemStack(item);
        pane.set(DataComponents.CUSTOM_NAME, Component.literal(label).withStyle(s -> s.withColor(ChatFormatting.WHITE)
                .withItalic(false)));
        return pane;
    }

    /** Armor slot that restricts insertion to the correct armor type via EQUIPPABLE component. */
    private static class ArmorSlot extends Slot {
        private final EquipmentSlot armorSlot;

        ArmorSlot(Container inv, int index, int x, int y, EquipmentSlot armorSlot) {
            super(inv, index, x, y);
            this.armorSlot = armorSlot;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
            return equippable != null && equippable.slot() == armorSlot;
        }
    }

    /** Locked slot that always shows a gray glass pane; blocks all interaction. */
    private static class FillerSlot extends Slot {
        private final ItemStack pane;

        FillerSlot(Container inv, int index, int x, int y) {
            super(inv, index, x, y);
            this.pane = new ItemStack(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY));
            this.pane.set(DataComponents.CUSTOM_NAME, Component.literal(" ").withStyle(s -> s.withItalic(false)));
        }

        @Override
        public ItemStack getItem() {
            return pane;
        }

        @Override
        public boolean hasItem() {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
