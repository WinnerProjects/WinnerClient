package com.winnerclient.fabric.module.client;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.ModuleManager;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.Comparator;
import java.util.List;

public class HUD extends Module {
    private final Setting.BoolSetting rainbow = addBool("Rainbow", "Rainbow colors", false);
    private final Setting.ColorSetting color = addColor("Color", "Text color", 0xFF6B5CE7, true);
    private final Setting.ModeSetting position = addMode("Position", "Module list position", "TopRight", "TopLeft", "TopRight", "BottomLeft", "BottomRight");

    public HUD() {
        super("HUD", "On-screen display of enabled modules", Category.CLIENT);
    }

    @Override
    public void onRender(DrawContext context, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        List<Module> enabled = ModuleManager.getEnabledModules();
        if (!enabled.isEmpty()) {
            enabled.sort(Comparator.comparingInt(m -> -mc.textRenderer.getWidth(((Module)m).getName())));

            int maxWidth = 0;
            for (Module m : enabled) {
                int w = mc.textRenderer.getWidth(m.getName());
                if (w > maxWidth) maxWidth = w;
            }

            int padding = 4;
            int lineHeight = 12;
            int totalHeight = enabled.size() * lineHeight;
            int sWidth = mc.getWindow().getScaledWidth();
            int sHeight = mc.getWindow().getScaledHeight();

            int startX, startY;
            switch (position.getValue()) {
                case "TopRight" -> {
                    startX = sWidth - maxWidth - padding;
                    startY = padding;
                }
                case "BottomLeft" -> {
                    startX = padding;
                    startY = sHeight - totalHeight - padding;
                }
                case "BottomRight" -> {
                    startX = sWidth - maxWidth - padding;
                    startY = sHeight - totalHeight - padding;
                }
                default -> {
                    startX = padding;
                    startY = padding;
                }
            }

            int index = 0;
            for (Module m : enabled) {
                int c;
                if (rainbow.getValue()) {
                    float hue = (System.currentTimeMillis() % 4000) / 4000f + index * 0.05f;
                    c = java.awt.Color.HSBtoRGB(hue, 0.8f, 1.0f);
                } else {
                    c = color.getValue();
                }
                context.drawText(mc.textRenderer, m.getName(), startX, startY + index * lineHeight, c, true);
                index++;
            }
        }

        Notifications notifs = (Notifications) ModuleManager.getModule("Notifications");
        if (notifs != null && notifs.isEnabled()) {
            String pos = notifs.getPosition();
            boolean rightAlign = pos.contains("Right");
            boolean bottom = pos.contains("Bottom");
            int padding = 4;
            int lineHeight = 12;
            var queue = notifs.getNotifications();

            int notifX = rightAlign ? mc.getWindow().getScaledWidth() - padding : padding;
            int notifY = bottom
                ? mc.getWindow().getScaledHeight() - padding - queue.size() * lineHeight
                : padding + 30;

            for (var n : queue) {
                int alpha = Math.min(255, n.remaining * 8);
                int c = (n.color & 0x00FFFFFF) | (alpha << 24);
                int drawX = rightAlign ? notifX - mc.textRenderer.getWidth(n.text) : notifX;
                context.drawText(mc.textRenderer, n.text, drawX, notifY, c, true);
                notifY += lineHeight;
            }
        }
    }
}
