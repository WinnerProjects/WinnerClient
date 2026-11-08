package com.winnerclient.fabric.module.combat;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.PlayerInput;

import java.util.ArrayList;
import java.util.List;

public class AutoTotem extends Module {

    private final Setting.BoolSetting legit = addBool(
            "Legit",
            "Use configurable delay and movement restrictions",
            false
    );

    private final Setting.IntSetting legitDelay = addInt(
            "LegitDelayTicks",
            "Ticks to wait after a totem is needed",
            3, 0, 40
    );

    private final Setting.BoolSetting legitStationary = addBool(
            "LegitStationary",
            "Wait until movement, sprinting and sneaking stop",
            true
    );

    private final Setting.IntSetting legitStillTicks = addInt(
            "LegitStillTicks",
            "Ticks to wait while stationary",
            2, 0, 20
    );

    private final Setting.BoolSetting legitGroundOnly = addBool(
            "LegitGroundOnly",
            "Only swap while on the ground",
            true
    );

    private final Setting.NumberSetting healthThreshold = addNum(
            "HealthThreshold",
            "Health to swap at",
            20.0, 1.0, 20.0, 1.0
    );

    private final Setting.BoolSetting always = addBool(
            "Always",
            "Always keep totem in offhand",
            true
    );

    private final Setting.BoolSetting absorption = addBool(
            "IncludeAbsorption",
            "Count absorption hearts in the health threshold",
            false
    );

    private final Setting.BoolSetting inventoryOnly = addBool(
            "InventoryOnly",
            "Only equip while your inventory screen is open",
            false
    );

    private final Setting.BoolSetting fast = addBool(
            "Fast",
            "Refill on the next eligible tick without the retry delay",
            true
    );

    private final Setting.IntSetting retryDelay = addInt(
            "RetryDelayTicks",
            "Retry delay when Fast is off",
            1, 1, 40
    );

    private final Setting.IntSetting confirmationTicks = addInt(
            "ConfirmTicks",
            "Wait before retrying an unconfirmed inventory swap",
            6, 2, 20
    );

    private final Setting.BoolSetting hotbarFirst = addBool(
            "HotbarFirst",
            "Use hotbar totems before main inventory totems",
            false
    );

    private final Setting.BoolSetting preferSpare = addBool(
            "PreferSpareTotems",
            "Use another totem before moving a totem held in the main hand",
            true
    );

    private static final int OFFHAND_SWAP_BUTTON = 40;

    private final RefillGate refill = new RefillGate();

    private ClientPlayerEntity owner;
    private int totemNeededSince = -1;
    private int stationarySince = -1;

    public AutoTotem() {
        super(
                "AutoTotem",
                "Auto-equip Totem of Undying",
                Category.COMBAT
        );
    }

    /*
     * ConfigManager tüm ayarları kaydedip yükler.
     * GUI, getSettings() üzerinden filtrelenmiş listeyi görür.
     */
    public List<Setting<?>> getAllSettings() {
        return super.getSettings();
    }

    @Override
    public List<Setting<?>> getSettings() {
        List<Setting<?>> all = super.getSettings();

        if (legit.getValue()) {
            return all;
        }

        List<Setting<?>> visible = new ArrayList<>();

        for (Setting<?> setting : all) {
            if (setting == legitDelay
                    || setting == legitStationary
                    || setting == legitStillTicks
                    || setting == legitGroundOnly) {
                continue;
            }

            visible.add(setting);
        }

        return visible;
    }

    private void resetLegitState() {
        totemNeededSince = -1;
        stationarySince = -1;
    }

    @Override
    public void onEnable() {
        refill.reset();
        resetLegitState();
        owner = null;
    }

    @Override
    public void onDisable() {
        refill.reset();
        resetLegitState();
        owner = null;
    }

    @Override
    public void onDisconnect() {
        refill.reset();
        resetLegitState();
        owner = null;
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null
                || mc.world == null
                || mc.interactionManager == null) {
            resetLegitState();
            return;
        }

        if (owner != mc.player) {
            refill.reset();
            resetLegitState();
            owner = mc.player;
        }

        if (!mc.player.isAlive() || mc.player.isSpectator()) {
            refill.reset();
            resetLegitState();
            return;
        }

        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            resetLegitState();
            return;
        }

        float health = mc.player.getHealth();

        if (absorption.getValue()) {
            health += mc.player.getAbsorptionAmount();
        }

        if (!always.getValue()
                && health > healthThreshold.getValue().floatValue()) {
            resetLegitState();
            return;
        }

        // Farklı container'larda oyuncu envanterinin slot düzenini kullanma.
        if (mc.player.currentScreenHandler
                != mc.player.playerScreenHandler) {
            resetLegitState();
            return;
        }

        if (mc.currentScreen != null
                && !(mc.currentScreen instanceof InventoryScreen)) {
            resetLegitState();
            return;
        }

        if (inventoryOnly.getValue()
                && !(mc.currentScreen instanceof InventoryScreen)) {
            resetLegitState();
            return;
        }

        var handler = mc.player.playerScreenHandler;

        if (!handler.getCursorStack().isEmpty()) {
            resetLegitState();
            return;
        }

        int screenSlot = -1;
        int selectedTotem = -1;

        // PlayerScreenHandler:
        // 9..35: ana envanter
        // 36..44: hotbar
        for (int n = 0; n < 36; n++) {
            int slot = hotbarFirst.getValue()
                    ? (n < 9 ? 36 + n : n)
                    : 9 + n;

            if (!handler.getSlot(slot)
                    .getStack()
                    .isOf(Items.TOTEM_OF_UNDYING)) {
                continue;
            }

            if (preferSpare.getValue()
                    && slot == 36 + mc.player.getInventory().selectedSlot) {
                selectedTotem = slot;
                continue;
            }

            screenSlot = slot;
            break;
        }

        if (screenSlot == -1) {
            screenSlot = selectedTotem;
        }

        if (screenSlot == -1) {
            resetLegitState();
            return;
        }

        if (legit.getValue()) {
            if (!legitReady(mc.player)) {
                return;
            }
        } else {
            resetLegitState();
        }

        if (!refill.ready(mc.player.age)) {
            return;
        }

        int waitTicks = Math.max(
                confirmationTicks.getValue(),
                fast.getValue() ? 1 : retryDelay.getValue()
        );

        refill.attempted(mc.player.age, waitTicks);

        mc.interactionManager.clickSlot(
                handler.syncId,
                screenSlot,
                OFFHAND_SWAP_BUTTON,
                SlotActionType.SWAP,
                mc.player
        );
    }

    private boolean legitReady(ClientPlayerEntity player) {
        int age = player.age;

        if (totemNeededSince == -1 || age < totemNeededSince) {
            totemNeededSince = age;
        }

        if (legitGroundOnly.getValue() && !player.isOnGround()) {
            stationarySince = -1;
            return false;
        }

        if (legitStationary.getValue()) {
            PlayerInput input = player.input.playerInput;

            boolean moving =
                    input.forward()
                    || input.backward()
                    || input.left()
                    || input.right()
                    || input.sneak()
                    || input.sprint()
                    || player.isSprinting()
                    || player.isSneaking();

            if (moving) {
                stationarySince = -1;
                return false;
            }

            if (stationarySince == -1 || age < stationarySince) {
                stationarySince = age;
            }

            if (age - stationarySince < legitStillTicks.getValue()) {
                return false;
            }
        } else {
            stationarySince = -1;
        }

        return age - totemNeededSince >= legitDelay.getValue();
    }

    public void onInventoryUpdate() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null) {
            return;
        }

        if (refill.pending()) {
            if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                refill.confirmed();
            } else {
                refill.rejected(
                        mc.player.age,
                        Math.max(2, retryDelay.getValue())
                );
            }
        }

        if (fast.getValue() && !legit.getValue()) {
            onTick();
        }
    }

    public void onTotemPop() {
        refill.popped();
        resetLegitState();

        if (fast.getValue() && !legit.getValue()) {
            onTick();
        }
    }
}
