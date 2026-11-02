package com.winnerclient.fabric.module.player;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.Optional;

public class NameHider extends Module {
    public static NameHider instance;

    private final Setting.StringSetting originalName = addString(
        "OriginalName",
        "Name to hide. Leave empty to hide your own name.",
        ""
    );

    private final Setting.StringSetting replacedName = addString(
        "ReplacedName",
        "Name to show instead.",
        "Hidden"
    );

    public NameHider() {
        super("NameHider", "Replace a player's name", Category.PLAYER);
        instance = this;
    }

    public static String getOriginal() {
        if (instance == null || !instance.isEnabled()) return "";

        String configured = instance.originalName.getValue();
        if (configured != null && !configured.isEmpty()) return configured;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return "";

        return mc.player.getName().getString();
    }

    public static String getReplaced() {
        if (instance == null || !instance.isEnabled()) return "";

        String replacement = instance.replacedName.getValue();
        return replacement == null ? "" : replacement;
    }

    public static Text replace(Text text) {
        if (text == null) return null;

        String original = getOriginal();
        String replacement = getReplaced();

        if (original.isEmpty() || original.equals(replacement)) {
            return text;
        }

        MutableText result = Text.empty();
        boolean[] changed = {false};

        // Visit the resolved text, including translation arguments,
        // while preserving the style of each text segment.
        text.visit((style, segment) -> {
            String updated = segment.replace(original, replacement);

            if (!updated.equals(segment)) {
                changed[0] = true;
            }

            result.append(Text.literal(updated).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);

        return changed[0] ? result : text;
    }
}
