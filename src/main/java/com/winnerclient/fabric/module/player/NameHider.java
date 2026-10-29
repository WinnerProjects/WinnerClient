package com.winnerclient.fabric.module.player;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.text.MutableText;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;

public class NameHider extends Module {
    public static NameHider instance;

    private final Setting.StringSetting originalName = addString("OriginalName", "Name to hide", "");
    private final Setting.StringSetting replacedName = addString("ReplacedName", "Name to show instead", "Hidden");

    public NameHider() {
        super("NameHider", "Replace a player's name everywhere", Category.PLAYER);
        instance = this;
    }

    public static String getOriginal() {
        if (instance == null || !instance.isEnabled()) return "";
        return instance.originalName.getValue();
    }

    public static String getReplaced() {
        if (instance == null || !instance.isEnabled()) return "";
        return instance.replacedName.getValue();
    }

    public static Text replace(Text text) {
        String orig = getOriginal();
        String repl = getReplaced();
        if (orig.isEmpty() || orig.equals(repl)) return text;
        return replaceRecursive(text, orig, repl);
    }

    private static Text replaceRecursive(Text text, String orig, String repl) {
        MutableText result;
        if (text.getContent() instanceof PlainTextContent ptc) {
            String s = ptc.string();
            result = s.contains(orig) ? Text.literal(s.replace(orig, repl)) : text.copy();
        } else {
            result = text.copy();
            result.getSiblings().clear();
        }
        result.setStyle(text.getStyle());
        for (Text sibling : text.getSiblings()) {
            result.append(replaceRecursive(sibling, orig, repl));
        }
        return result;
    }
}
