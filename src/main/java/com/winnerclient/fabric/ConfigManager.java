package com.winnerclient.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.ModuleManager;
import com.winnerclient.fabric.module.Setting;
import com.winnerclient.fabric.module.combat.AutoTotem;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ConfigManager {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static Path configPath;
    private static boolean loading = false;

    public static void init() {
        configPath = MinecraftClient.getInstance()
                .runDirectory
                .toPath()
                .resolve("config")
                .resolve(WinnerClientMod.MOD_ID + ".json");

        try {
            Files.createDirectories(configPath.getParent());
        } catch (IOException e) {
            WinnerClientMod.LOGGER.error(
                    "Failed to create config directory",
                    e
            );
        }
    }

    public static boolean isLoading() {
        return loading;
    }

    public static java.io.File getConfigFile() {
        return configPath != null ? configPath.toFile() : null;
    }

    private static List<Setting<?>> getSettingsForConfig(Module module) {
        if (module instanceof AutoTotem autoTotem) {
            return autoTotem.getAllSettings();
        }

        return module.getSettings();
    }

    public static void load() {
        if (configPath == null) {
            init();
        }

        if (!Files.exists(configPath)) {
            return;
        }

        loading = true;

        try {
            String content = Files.readString(configPath);
            JsonObject root = GSON.fromJson(content, JsonObject.class);

            if (root == null || !root.has("modules")) {
                return;
            }

            JsonObject modulesObj = root.getAsJsonObject("modules");

            for (Module module : ModuleManager.getModules()) {
                if (!modulesObj.has(module.getName())) {
                    continue;
                }

                JsonObject modObj = modulesObj.getAsJsonObject(
                        module.getName()
                );

                if (modObj.has("enabled")) {
                    module.setEnabled(
                            modObj.get("enabled").getAsBoolean()
                    );
                }

                if (modObj.has("keyBind")) {
                    module.setKeyBind(
                            modObj.get("keyBind").getAsInt()
                    );
                }

                if (!modObj.has("settings")) {
                    continue;
                }

                JsonObject settingsObj = modObj.getAsJsonObject("settings");

                for (Setting<?> setting : getSettingsForConfig(module)) {
                    if (!settingsObj.has(setting.getName())) {
                        continue;
                    }

                    JsonElement value = settingsObj.get(setting.getName());

                    if (setting instanceof Setting.BoolSetting boolSetting) {
                        boolSetting.setValue(value.getAsBoolean());
                    } else if (setting instanceof Setting.NumberSetting numberSetting) {
                        numberSetting.setValue(value.getAsDouble());
                    } else if (setting instanceof Setting.IntSetting intSetting) {
                        intSetting.setValue(value.getAsInt());
                    } else if (setting instanceof Setting.ModeSetting modeSetting) {
                        modeSetting.setValue(value.getAsString());
                    } else if (setting instanceof Setting.ColorSetting colorSetting) {
                        colorSetting.setValue(value.getAsInt());
                    } else if (setting instanceof Setting.StringSetting stringSetting) {
                        stringSetting.setValue(value.getAsString());
                    }
                }
            }

            WinnerClientMod.LOGGER.info(
                    "Config loaded from {}",
                    configPath
            );
        } catch (Exception e) {
            WinnerClientMod.LOGGER.error(
                    "Failed to load config",
                    e
            );
        } finally {
            loading = false;
        }
    }

    public static void save() {
        if (configPath == null) {
            init();
        }

        JsonObject root = new JsonObject();
        JsonObject modulesObj = new JsonObject();

        for (Module module : ModuleManager.getModules()) {
            JsonObject modObj = new JsonObject();

            modObj.addProperty("enabled", module.isEnabled());
            modObj.addProperty("keyBind", module.getRawKeyBind());

            JsonObject settingsObj = new JsonObject();

            for (Setting<?> setting : getSettingsForConfig(module)) {
                if (setting instanceof Setting.BoolSetting boolSetting) {
                    settingsObj.addProperty(
                            setting.getName(),
                            boolSetting.getValue()
                    );
                } else if (setting instanceof Setting.NumberSetting numberSetting) {
                    settingsObj.addProperty(
                            setting.getName(),
                            numberSetting.getValue()
                    );
                } else if (setting instanceof Setting.IntSetting intSetting) {
                    settingsObj.addProperty(
                            setting.getName(),
                            intSetting.getValue()
                    );
                } else if (setting instanceof Setting.ModeSetting modeSetting) {
                    settingsObj.addProperty(
                            setting.getName(),
                            modeSetting.getValue()
                    );
                } else if (setting instanceof Setting.ColorSetting colorSetting) {
                    settingsObj.addProperty(
                            setting.getName(),
                            colorSetting.getValue()
                    );
                } else if (setting instanceof Setting.StringSetting stringSetting) {
                    settingsObj.addProperty(
                            setting.getName(),
                            stringSetting.getValue()
                    );
                }
            }

            modObj.add("settings", settingsObj);
            modulesObj.add(module.getName(), modObj);
        }

        root.add("modules", modulesObj);

        try {
            Files.writeString(configPath, GSON.toJson(root));
        } catch (IOException e) {
            WinnerClientMod.LOGGER.error(
                    "Failed to save config",
                    e
            );
        }
    }
}
