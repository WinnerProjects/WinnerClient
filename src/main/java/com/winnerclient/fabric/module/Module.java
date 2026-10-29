package com.winnerclient.fabric.module;

import com.winnerclient.fabric.module.client.Notifications;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    private final String name;
    private final String description;
    private final Category category;
    private boolean enabled;
    private int keyBind;
    private int defaultKeyBind = -1;
    private boolean holdToUse;
    private final List<Setting<?>> settings = new ArrayList<>();

    protected Module(String name, String description, Category category) {
        this(name, description, category, false);
    }

    protected Module(String name, String description, Category category, boolean startEnabled) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.enabled = startEnabled;
        this.keyBind = -1;
    }

    public int getKeyBind() {
        if (keyBind == -2) return -1;
        if (keyBind > 0) return keyBind;
        if (defaultKeyBind > 0) return defaultKeyBind;
        return -1;
    }

    public int getRawKeyBind() {
        return keyBind;
    }

    public void setKeyBind(int keyBind) {
        this.keyBind = keyBind;
    }

    protected void setDefaultKeyBind(int key) {
        this.defaultKeyBind = key;
    }

    protected void setHoldToUse(boolean holdToUse) {
        this.holdToUse = holdToUse;
    }

    public boolean isHoldToUse() {
        return holdToUse;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public boolean isEnabled() { return enabled; }
    public List<Setting<?>> getSettings() { return settings; }

    public void toggle() {
        enabled = !enabled;
        if (enabled) onEnable();
        else onDisable();
        com.winnerclient.fabric.ConfigManager.save();
        sendToggleNotification();
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            if (enabled) onEnable();
            else onDisable();
            if (!com.winnerclient.fabric.ConfigManager.isLoading()) {
                com.winnerclient.fabric.ConfigManager.save();
                sendToggleNotification();
            }
        }
    }

    protected void onEnable() {}
    protected void onDisable() {}

    private void sendToggleNotification() {
        Notifications notifs = (Notifications) ModuleManager.getModule("Notifications");
        if (notifs != null && notifs.isEnabled()) {
            String text = (enabled ? "+ " : "- ") + name;
            notifs.notify(text, enabled ? 0xFF00FF00 : 0xFFFF0000);
        }
    }
    public void onTick() {}
    public void onRender(DrawContext context, float delta) {}
    public void onWorldRender(WorldRenderContext context) {}
    public void onDisconnect() {}

    protected <T extends Setting<?>> T register(T setting) {
        settings.add(setting);
        return setting;
    }

    protected Setting.BoolSetting addBool(String name, String description, boolean def) {
        Setting.BoolSetting s = new Setting.BoolSetting(name, description, def);
        settings.add(s);
        return s;
    }

    protected Setting.NumberSetting addNum(String name, String description, double def, double min, double max, double inc) {
        Setting.NumberSetting s = new Setting.NumberSetting(name, description, def, min, max, inc);
        settings.add(s);
        return s;
    }

    protected Setting.IntSetting addInt(String name, String description, int def, int min, int max) {
        Setting.IntSetting s = new Setting.IntSetting(name, description, def, min, max);
        settings.add(s);
        return s;
    }

    protected Setting.ModeSetting addMode(String name, String description, String def, String... modes) {
        Setting.ModeSetting s = new Setting.ModeSetting(name, description, def, modes);
        settings.add(s);
        return s;
    }

    protected Setting.ColorSetting addColor(String name, String description, int def, boolean alpha) {
        Setting.ColorSetting s = new Setting.ColorSetting(name, description, def, alpha);
        settings.add(s);
        return s;
    }

    protected Setting.StringSetting addString(String name, String description, String def) {
        Setting.StringSetting s = new Setting.StringSetting(name, description, def);
        settings.add(s);
        return s;
    }

    public Setting<?> getSetting(String name) {
        return settings.stream().filter(s -> s.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public boolean getBool(String name) {
        Setting<?> s = getSetting(name);
        return s instanceof Setting.BoolSetting && ((Setting.BoolSetting) s).getValue();
    }

    public double getNum(String name) {
        Setting<?> s = getSetting(name);
        return s instanceof Setting.NumberSetting ? ((Setting.NumberSetting) s).getValue() : 0;
    }

    public int getInt(String name) {
        Setting<?> s = getSetting(name);
        return s instanceof Setting.IntSetting ? ((Setting.IntSetting) s).getValue() : 0;
    }

    public String getMode(String name) {
        Setting<?> s = getSetting(name);
        return s instanceof Setting.ModeSetting ? ((Setting.ModeSetting) s).getValue() : "";
    }

    public int getColor(String name) {
        Setting<?> s = getSetting(name);
        return s instanceof Setting.ColorSetting ? ((Setting.ColorSetting) s).getValue() : 0xFFFFFFFF;
    }

    public enum Category {
        COMBAT("Combat"),
        MOVEMENT("Movement"),
        PLAYER("Player"),
        RENDER("Render"),
        WORLD("World"),
        MISC("Misc"),
        CLIENT("Client");

        public final String displayName;
        Category(String displayName) { this.displayName = displayName; }
    }
}