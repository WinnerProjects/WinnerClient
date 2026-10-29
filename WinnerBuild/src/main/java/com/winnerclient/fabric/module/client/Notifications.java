package com.winnerclient.fabric.module.client;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;

import java.util.LinkedList;
import java.util.Queue;

public class Notifications extends Module {
    private final Setting.IntSetting duration = addInt("Duration", "Notification display time (ticks)", 40, 10, 200);
    private final Setting.ModeSetting position = addMode("Position", "Notification position", "TopLeft", "TopLeft", "TopRight", "BottomLeft", "BottomRight");
    private final Queue<Notification> notifications = new LinkedList<>();

    public Notifications() {
        super("Notifications", "Module toggle notifications", Category.CLIENT);
    }

    public String getPosition() { return position.getValue(); }

    public void notify(String text, int color) {
        notifications.add(new Notification(text, color, duration.getValue()));
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        notifications.removeIf(n -> {
            n.remaining--;
            return n.remaining <= 0;
        });
    }

    public Queue<Notification> getNotifications() { return notifications; }

    public static class Notification {
        public final String text;
        public final int color;
        public int remaining;
        public Notification(String text, int color, int remaining) {
            this.text = text;
            this.color = color;
            this.remaining = remaining;
        }
    }
}
