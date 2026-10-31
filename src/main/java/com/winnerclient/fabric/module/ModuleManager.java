package com.winnerclient.fabric.module;

import com.winnerclient.fabric.module.combat.*;
import com.winnerclient.fabric.module.movement.*;
import com.winnerclient.fabric.module.player.*;
import com.winnerclient.fabric.module.render.*;
import com.winnerclient.fabric.module.world.*;

import com.winnerclient.fabric.module.client.*;
import com.winnerclient.fabric.module.misc.*;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

public class ModuleManager {
    private static final List<Module> modules = new ArrayList<>();
    private static final Map<String, Module> moduleMap = new LinkedHashMap<>();

    public static void init() {
        // Combat
        register(new KillAura());
        register(new Criticals());
        register(new TriggerBot());
        register(new SwingSpeed());
        register(new CrystalAura());
        register(new AutoTrap());
        register(new AutoMineCage());
        register(new AutoTotem());
        register(new SpinBot());

        // Movement
        register(new Flight());
        register(new Speed());
        register(new Step());
        register(new Sprint());
        register(new Jesus());
        register(new NoFall());
        register(new SafeWalk());
        register(new Scaffold());
        register(new Swim());
        register(new com.winnerclient.fabric.module.movement.Timer());
        register(new Phase());

        // Player
        register(new AutoMine());
        register(new Freecam());
        register(new Sneak());
        register(new NameHider());
        register(new XCarry());

        // Render
        register(new ESP());
        register(new BlockESP());
        register(new StorageESP());
        register(new HoleESP());
        register(new Fullbright());
        register(new XRay());
        register(new Nametags());
        register(new BlockOverlay());
        register(new Freelook());
        register(new SpotifyHUD());

        // World
        register(new Nuker());

        // Client
        register(new Notifications());
        register(new HUD());
        register(new ClickGUIModule());
        register(new AntiAFK());
        register(new SelfDestruct());
        register(new DiscordRPC());

        // Misc
        register(new PacketESP());
        register(new DebrisESP());
        register(new LightESP());
        register(new SpoofDebug());
        register(new RenderDistanceBypass());
        register(new PacketStorageFinder());
        register(new SeedChunkFinder());

        for (Module m : modules) {
            if (m.isEnabled()) {
                m.onEnable();
            }
        }
    }

    public static void register(Module module) {
        modules.add(module);
        moduleMap.put(module.getName().toLowerCase(), module);
    }

    public static List<Module> getModules() { return modules; }

    public static List<Module> getEnabledModules() {
        return modules.stream().filter(Module::isEnabled).collect(Collectors.toList());
    }

    public static List<Module> getModulesByCategory(Module.Category category) {
        return modules.stream().filter(m -> m.getCategory() == category).collect(Collectors.toList());
    }

    public static Module getModule(String name) {
        return moduleMap.get(name.toLowerCase());
    }

    private static final java.util.BitSet prevKeys = new java.util.BitSet(512);

    public static void checkKeybinds() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) return;
        long window = mc.getWindow().getHandle();
        if (window == 0) return;
        for (Module m : modules) {
            int kb = m.getKeyBind();
            if (kb <= 0) continue;
            boolean pressed = GLFW.glfwGetKey(window, kb) == GLFW.GLFW_PRESS;
            boolean wasPressed = prevKeys.get(kb);
            prevKeys.set(kb, pressed);
            if (m.isHoldToUse()) {
                if (pressed && !m.isEnabled()) {
                    m.setEnabled(true);
                } else if (!pressed && m.isEnabled()) {
                    m.setEnabled(false);
                }
            } else if (pressed && !wasPressed) {
                m.toggle();
            }
        }
    }

    public static void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        for (Module m : modules) {
            if (m.isEnabled()) {
                try { m.onTick(); } catch (Exception ignored) {}
            }
        }
    }

    public static void onRender(DrawContext context, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        for (Module m : modules) {
            if (m.isEnabled()) {
                try { m.onRender(context, delta); } catch (Exception ignored) {}
            }
        }
    }

    public static void onWorldRender(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        for (Module m : modules) {
            if (m.isEnabled()) {
                try { m.onWorldRender(context); } catch (Exception ignored) {}
            }
        }
    }

    public static void onDisconnect() {
        for (Module m : modules) {
            try { m.onDisconnect(); } catch (Exception ignored) {}
        }
    }

    public static void onJoin() {}

    public static void onEnable(Module module) {
        module.setEnabled(true);
    }

    public static void onDisable(Module module) {
        module.setEnabled(false);
    }
}
