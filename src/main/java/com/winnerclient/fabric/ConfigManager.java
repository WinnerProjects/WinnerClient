package com.winnerclient.fabric;

import com.google.gson.*;
import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.ModuleManager;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path configPath;
    private static boolean loading = false;

    public static void init() {
        configPath = MinecraftClient.getInstance().runDirectory.toPath()
                .resolve("config").resolve(WinnerClientMod.MOD_ID + ".json");

        try {
            Files.createDirectories(configPath.getParent());
        } catch (IOException e) {
            WinnerClientMod.LOGGER.error("Failed to create config directory", e);
        }
    }

    public static boolean isLoading() {
        return loading;
    }

    public static java.io.File getConfigFile() {
        return configPath != null ? configPath.toFile() : null;
    }

    public static void load() {
        if (configPath == null) init();
        if (!Files.exists(configPath)) return;

        loading = true;

        try {
            String content = Files.readString(configPath);
            JsonObject root = GSON.fromJson(content, JsonObject.class);

            if (root == null || !root.has("modules")) return;

            JsonObject modulesObj = root.getAsJsonObject("modules");

            for (Module m : ModuleManager.getModules()) {
                if (!modulesObj.has(m.getName())) continue;

                JsonObject modObj = modulesObj.getAsJsonObject(m.getName());

                if (modObj.has("enabled")) {
                    m.setEnabled(modObj.get("enabled").getAsBoolean());
                }

                if (modObj.has("keyBind")) {
                    m.setKeyBind(modObj.get("keyBind").getAsInt());
                }

                if (modObj.has("settings")) {
                    JsonObject settingsObj = modObj.getAsJsonObject("settings");

                    for (Setting<?> s : m.getSettings()) {
                        if (!settingsObj.has(s.getName())) continue;

                        JsonElement val = settingsObj.get(s.getName());

                        if (s instanceof Setting.BoolSetting bs) {
                            bs.setValue(val.getAsBoolean());
                        } else if (s instanceof Setting.NumberSetting ns) {
                            ns.setValue(val.getAsDouble());
                        } else if (s instanceof Setting.IntSetting is) {
                            is.setValue(val.getAsInt());
                        } else if (s instanceof Setting.ModeSetting ms) {
                            ms.setValue(val.getAsString());
                        } else if (s instanceof Setting.ColorSetting cs) {
                            cs.setValue(val.getAsInt());
                        } else if (s instanceof Setting.StringSetting ss) {
                            ss.setValue(val.getAsString());
                        }
                    }
                }
            }

            WinnerClientMod.LOGGER.info("Config loaded from {}", configPath);
        } catch (Exception e) {
            WinnerClientMod.LOGGER.error("Failed to load config", e);
        } finally {
            loading = false;
        }
    }

    public static void save() {
        if (configPath == null) init();

        JsonObject root = new JsonObject();
        JsonObject modulesObj = new JsonObject();

        for (Module m : ModuleManager.getModules()) {
            JsonObject modObj = new JsonObject();

            modObj.addProperty("enabled", m.isEnabled());
            modObj.addProperty("keyBind", m.getRawKeyBind());

            JsonObject settingsObj = new JsonObject();

            for (Setting<?> s : m.getSettings()) {
                if (s instanceof Setting.BoolSetting bs) {
                    settingsObj.addProperty(s.getName(), bs.getValue());
                } else if (s instanceof Setting.NumberSetting ns) {
                    settingsObj.addProperty(s.getName(), ns.getValue());
                } else if (s instanceof Setting.IntSetting is) {
                    settingsObj.addProperty(s.getName(), is.getValue());
                } else if (s instanceof Setting.ModeSetting ms) {
                    settingsObj.addProperty(s.getName(), ms.getValue());
                } else if (s instanceof Setting.ColorSetting cs) {
                    settingsObj.addProperty(s.getName(), cs.getValue());
                } else if (s instanceof Setting.StringSetting ss) {
                    settingsObj.addProperty(s.getName(), ss.getValue());
                }
            }

            modObj.add("settings", settingsObj);
            modulesObj.add(m.getName(), modObj);
        }

        root.add("modules", modulesObj);

        try {
            Files.writeString(configPath, GSON.toJson(root));
        } catch (IOException e) {
            WinnerClientMod.LOGGER.error("Failed to save config", e);
        }
    }
}
