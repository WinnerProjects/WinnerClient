package com.winnerclient.fabric.module.client;

import club.minnced.discord.rpc.DiscordEventHandlers;
import club.minnced.discord.rpc.DiscordRichPresence;
import com.winnerclient.fabric.WinnerClientMod;
import com.winnerclient.fabric.module.Module;
import net.minecraft.client.MinecraftClient;

public class DiscordRPC extends Module {
    private static final String APPLICATION_ID = "1531334961636839617";

    private club.minnced.discord.rpc.DiscordRPC lib;
    private DiscordRichPresence presence;
    private Thread rpcThread;
    private volatile boolean running;
    private final Object lock = new Object();

    public DiscordRPC() {
        // Keep native Discord RPC disabled by default: its native library may be unavailable on Android.
        super("DiscordRPC", "Show rich presence on Discord", Category.CLIENT, false);
    }

    @Override
    public void onEnable() {
        synchronized (lock) {
            try {
                lib = club.minnced.discord.rpc.DiscordRPC.INSTANCE;

                DiscordEventHandlers handlers = new DiscordEventHandlers();
                handlers.ready = (user) -> WinnerClientMod.LOGGER.info(
                    "Discord RPC ready as {}#{}", user.username, user.discriminator);
                handlers.errored = (errorCode, message) -> WinnerClientMod.LOGGER.error(
                    "Discord RPC error ({}): {}", errorCode, message);
                handlers.disconnected = (errorCode, message) -> WinnerClientMod.LOGGER.warn(
                    "Discord RPC disconnected ({}): {}", errorCode, message);

                lib.Discord_Initialize(APPLICATION_ID, handlers, false, null);

                presence = new DiscordRichPresence();
                presence.startTimestamp = System.currentTimeMillis() / 1000;
                presence.state = "Playing Minecraft 1.21.11";
                presence.details = "WinnerClient";
                presence.largeImageKey = "winnerclient";
                presence.largeImageText = "WinnerClient";
                presence.smallImageKey = "winnerclient";
                presence.smallImageText = "WinnerClient";

                lib.Discord_UpdatePresence(presence);
                lib.Discord_RunCallbacks();
                running = true;
            } catch (LinkageError | RuntimeException e) {
                running = false;
                lib = null;
                presence = null;
                WinnerClientMod.LOGGER.warn(
                    "Discord RPC could not start; continuing without Discord presence.", e);
                return;
            }
        }

        rpcThread = new Thread(this::rpcLoop, "DiscordRPC");
        rpcThread.setDaemon(true);
        rpcThread.start();
    }

    @Override
    public void onDisable() {
        running = false;
        if (rpcThread != null) {
            rpcThread.interrupt();
            rpcThread = null;
        }
        synchronized (lock) {
            if (lib != null) {
                try {
                    lib.Discord_ClearPresence();
                    lib.Discord_Shutdown();
                } catch (LinkageError | RuntimeException e) {
                    WinnerClientMod.LOGGER.debug("Discord RPC shutdown failed.", e);
                } finally {
                    lib = null;
                }
            }
            presence = null;
        }
    }

    private void rpcLoop() {
        while (running) {
            synchronized (lock) {
                if (lib != null) {
                    try {
                        lib.Discord_RunCallbacks();
                    } catch (LinkageError | RuntimeException e) {
                        WinnerClientMod.LOGGER.warn("Discord RPC callback failed; stopping Discord RPC.", e);
                        running = false;
                        return;
                    }
                }
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}
