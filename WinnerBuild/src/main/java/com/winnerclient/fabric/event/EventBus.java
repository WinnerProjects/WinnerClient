package com.winnerclient.fabric.event;

import com.winnerclient.fabric.ConfigManager;
import com.winnerclient.fabric.WinnerClientMod;
import com.winnerclient.fabric.module.ModuleManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class EventBus {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean initialized = false;
    private static long lastTickTime = System.currentTimeMillis();
    private static int ticks = 0;
    private static float tickRate = 20.0f;

    public static void init() {
        if (initialized) return;
        initialized = true;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            long now = System.currentTimeMillis();
            ticks++;
            if (now - lastTickTime >= 1000) {
                tickRate = ticks * 1000.0f / (now - lastTickTime);
                ticks = 0;
                lastTickTime = now;
            }

            if (WinnerClientMod.openGuiKey.wasPressed()) {
                WinnerClientMod.openClickGui();
            }

            ModuleManager.checkKeybinds();
            ModuleManager.onTick();
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (mc.player == null) return;
            ModuleManager.onWorldRender(context);
        });

        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (mc.player == null) return;
            ModuleManager.onRender(context, 0.0f);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ModuleManager.onDisconnect();
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ModuleManager.onJoin();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ConfigManager.save();
            WinnerClientMod.LOGGER.info("Config saved on shutdown.");
        });
    }

    public static float getTickRate() {
        float rate = tickRate;
        return rate > 22 ? 20 : rate;
    }

    public static void registerKeyBinding(KeyBinding key) {
        KeyBindingHelper.registerKeyBinding(key);
    }
}