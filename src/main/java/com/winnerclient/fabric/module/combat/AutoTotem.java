package com.winnerclient.fabric.module.combat;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public class AutoTotem extends Module {
    private final Setting.NumberSetting healthThreshold = addNum("HealthThreshold", "Health to swap at", 20.0, 1.0, 20.0, 1.0);
    private final Setting.BoolSetting always = addBool("Always", "Always keep totem in offhand", true);
    private final Setting.BoolSetting absorption = addBool("IncludeAbsorption", "Count absorption hearts in the health threshold", false);
    private final Setting.BoolSetting inventoryOnly = addBool("InventoryOnly", "Only equip while your inventory screen is open", false);
    private final Setting.BoolSetting fast = addBool("Fast", "Refill on the next eligible tick without the retry delay", true);
    private final Setting.IntSetting retryDelay = addInt("RetryDelayTicks", "Retry delay when Fast is off", 1, 1, 40);
    private final Setting.IntSetting confirmationTicks = addInt("ConfirmTicks", "Wait before retrying an unconfirmed inventory swap", 6, 2, 20);
    private final Setting.BoolSetting hotbarFirst = addBool("HotbarFirst", "Use hotbar totems before main inventory totems", false);
    private final Setting.BoolSetting preferSpare = addBool("PreferSpareTotems", "Use another totem before moving a totem held in the main hand", true);
    private final RefillGate refill = new RefillGate();
    private net.minecraft.client.network.ClientPlayerEntity owner;

    private static final int OFFHAND_SWAP_BUTTON = 40;

    @Override
    public void onEnable() {
        refill.reset();
    }

    @Override
    public void onDisable() {
        refill.reset();
    }

    @Override
    public void onDisconnect() {
        refill.reset();
        owner = null;
    }

    public AutoTotem() {
        super("AutoTotem", "Auto-equip Totem of Undying", Category.COMBAT);
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (owner != mc.player) { refill.reset(); owner = mc.player; }
        if (!mc.player.isAlive() || mc.player.isSpectator()) { refill.reset(); return; }
        if (!refill.ready(mc.player.age)) return;

        // Container screens use different slot layouts. Never apply player slot
        // numbers to chests, merchants, crafting tables, or other containers.
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return;
        if (mc.currentScreen != null
                && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen)) return;
        if (inventoryOnly.getValue()
                && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen)) return;

        var handler = mc.player.playerScreenHandler;
        if (!handler.getCursorStack().isEmpty()) return;

        float health = mc.player.getHealth();
        if (absorption.getValue()) health += mc.player.getAbsorptionAmount();
        if (!always.getValue() && health > healthThreshold.getValue().floatValue()) return;

        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;

        int screenSlot = -1;
        int selectedTotem = -1;
        // Player handler: 9..35 = main inventory, 36..44 = hotbar.
        // Exclude crafting slots, armor slots, and the offhand itself.
        for (int n = 0; n < 36; n++) {
            int i = hotbarFirst.getValue() ? (n < 9 ? 36 + n : n) : 9 + n;
            if (handler.getSlot(i).getStack().isOf(Items.TOTEM_OF_UNDYING)) {
                if (preferSpare.getValue() && i == 36 + mc.player.getInventory().selectedSlot) {
                    selectedTotem = i;
                    continue;
                }
                screenSlot = i;
                break;
            }
        }
        if (screenSlot == -1) screenSlot = selectedTotem;
        if (screenSlot == -1) return;

        // One swap preserves the cursor and returns the old offhand item to
        // the source slot, without a partially completed pickup sequence.
        refill.attempted(mc.player.age, Math.max(confirmationTicks.getValue(), fast.getValue() ? 1 : retryDelay.getValue()));
        mc.interactionManager.clickSlot(handler.syncId, screenSlot,
                OFFHAND_SWAP_BUTTON, SlotActionType.SWAP, mc.player);
    }

    public void onInventoryUpdate() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (refill.pending()) {
            if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) refill.confirmed();
            else refill.rejected(mc.player.age, Math.max(2, retryDelay.getValue()));
        }
        if (fast.getValue()) onTick();
    }

    public void onTotemPop() {
        refill.popped();
        if (fast.getValue()) onTick();
    }
}
