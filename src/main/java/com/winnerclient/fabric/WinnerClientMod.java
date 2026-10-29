package com.winnerclient.fabric;

import com.winnerclient.fabric.event.EventBus;
import com.winnerclient.fabric.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WinnerClientMod implements ClientModInitializer {
    public static final String MOD_ID = "winnerclient";
    public static final String MOD_NAME = "WinnerClient";
    public static final String MOD_VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static KeyBinding openGuiKey;
    public static MinecraftClient client;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing {} v{} for Minecraft 1.21.11...", MOD_NAME, MOD_VERSION);

        client = MinecraftClient.getInstance();

        ModuleManager.init();
        LOGGER.info("Registered {} modules.", ModuleManager.getModules().size());

        ConfigManager.init();
        ConfigManager.load();

        openGuiKey = new KeyBinding(
            "key.winnerclient.open_gui",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            KeyBinding.Category.MISC
        );
        EventBus.registerKeyBinding(openGuiKey);

        EventBus.init();
        LOGGER.info("EventBus initialized.");

        LOGGER.info("{} v{} loaded successfully.", MOD_NAME, MOD_VERSION);
    }

    public static void openClickGui() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.setScreen(new ClickGuiScreen());
    }
}