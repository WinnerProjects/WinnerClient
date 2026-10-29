package com.winnerclient.fabric;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.ModuleManager;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class ClickGuiScreen extends Screen {
    private static final int SIDEBAR_WIDTH = 74;
    private static final int HEADER_HEIGHT = 30;
    private static final int PADDING = 4;

    private static final int BG_COLOR       = 0xFF151515;
    private static final int SIDEBAR_COLOR  = 0xFF0F0F0F;
    private static final int HEADER_COLOR   = 0xFF1A1A1A;
    private static final int PANEL_COLOR    = 0xFF121212;
    private static final int ACCENT_COLOR   = 0xFF6B5CE7;
    private static final int HOVER_COLOR    = 0xFF1E1E1E;
    private static final int ACTIVE_COLOR   = 0xFF252525;
    private static final int TEXT_COLOR     = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY = 0xFF888888;
    private static final int MODULE_ON      = 0xFF6B5CE7;
    private static final int MODULE_OFF     = 0xFF444444;

    private static Module.Category selectedCategory = Module.Category.COMBAT;
    private static Module selectedModule;
    private static int settingsScrollOffset;
    private static final Map<Module.Category, Integer> categoryScrolls = new HashMap<>();

    private double mouseX, mouseY;
    private boolean draggingSlider;
    private String draggingSettingName;
    private Module bindingModule;
    private Setting.StringSetting focusedString;

    protected ClickGuiScreen() {
        super(Text.translatable("gui.winnerclient.title"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        renderBackground(context, mouseX, mouseY, delta);

        int guiX = (width - 500) / 2;
        int guiY = (height - 340) / 2;

        renderSidebar(context, guiX, guiY);
        renderModulePanel(context, guiX + SIDEBAR_WIDTH, guiY);
        renderSettingsPanel(context, guiX + SIDEBAR_WIDTH + 210, guiY);
    }

    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x80000000);
    }

    private void renderSidebar(DrawContext context, int x, int y) {
        context.fill(x, y, x + SIDEBAR_WIDTH, y + 340, SIDEBAR_COLOR);

        TextRenderer tr = client.textRenderer;
        context.drawTextWithShadow(tr, "Winner", x + 8, y + 8, ACCENT_COLOR);
        context.drawTextWithShadow(tr, "Client", x + 8, y + 20, TEXT_COLOR);
        context.drawTextWithShadow(tr, "v1.0.0", x + 8, y + 32, TEXT_SECONDARY);

        int catY = y + HEADER_HEIGHT + 22;
        Module.Category[] cats = Module.Category.values();

        for (Module.Category cat : cats) {
            boolean isSelected = cat == selectedCategory;
            int bgColor = isSelected ? ACCENT_COLOR : (isHovered(x + 2, catY, SIDEBAR_WIDTH - 4) ? HOVER_COLOR : SIDEBAR_COLOR);
            context.fill(x + 2, catY, x + SIDEBAR_WIDTH - 2, catY + 26, bgColor);

            int textColor = isSelected ? 0xFFFFFFFF : TEXT_SECONDARY;
            int textWidth = tr.getWidth(cat.displayName);
            int textX = x + (SIDEBAR_WIDTH - textWidth) / 2;
            int count = ModuleManager.getModulesByCategory(cat).size();
            String label = cat.displayName + " (" + count + ")";

            context.drawTextWithShadow(tr, label, x + 6, catY + 9, textColor);
            catY += 28;
        }
    }

    private void renderModulePanel(DrawContext context, int x, int y) {
        context.fill(x, y, x + 210, y + 340, PANEL_COLOR);

        TextRenderer tr = client.textRenderer;
        context.fill(x, y, x + 210, y + HEADER_HEIGHT, HEADER_COLOR);
        String catTitle = selectedCategory.displayName;
        context.drawTextWithShadow(tr, catTitle, x + 8, y + 8, TEXT_COLOR);

        String sep = " | ";
        String subtitle = ModuleManager.getModulesByCategory(selectedCategory).size() + sep
            + ModuleManager.getModulesByCategory(selectedCategory).stream().filter(Module::isEnabled).count();
        context.drawTextWithShadow(tr, "Modules" + sep + subtitle + " enabled",
            x + (210 - tr.getWidth("Modules | 0  | 0 enabled")) / 2, y + 20, TEXT_SECONDARY);

        context.enableScissor(x + 1, y + HEADER_HEIGHT + 1, x + 209, y + 339);
        int modY = y + HEADER_HEIGHT + 3 - categoryScrolls.getOrDefault(selectedCategory, 0);
        List<Module> mods = ModuleManager.getModulesByCategory(selectedCategory);

        for (Module mod : mods) {
            boolean enabled = mod.isEnabled();
            boolean hasSettings = !mod.getSettings().isEmpty();
            int bg = enabled ? ACTIVE_COLOR : (mouseX > x && mouseX < x + 210 && mouseY > modY && mouseY < modY + 20 ? HOVER_COLOR : PANEL_COLOR);
            context.fill(x + 2, modY, x + 208, modY + 20, bg);

            context.drawTextWithShadow(tr, mod.getName(), x + 6, modY + 6, enabled ? MODULE_ON : MODULE_OFF);

            int kb = mod.getKeyBind();
            if (kb > 0) {
                String keyName = GLFW.glfwGetKeyName(kb, 0);
                if (keyName != null) {
                    int kw = tr.getWidth(keyName);
                    context.drawTextWithShadow(tr, keyName, x + 208 - 8 - kw - (hasSettings ? 14 : 0), modY + 6, TEXT_SECONDARY);
                }
            }
            if (hasSettings) {
                String gear = mod == selectedModule ? ">" : "+";
                context.drawTextWithShadow(tr, gear, x + 196, modY + 6, TEXT_SECONDARY);
            }
            modY += 22;
        }
        context.disableScissor();

        String modDesc = "";
        for (Module mod : mods) {
            int mdY = y + HEADER_HEIGHT + 3 + mods.indexOf(mod) * 22 - categoryScrolls.getOrDefault(selectedCategory, 0);
            if (mouseX > x && mouseX < x + 210 && mouseY > mdY && mouseY < mdY + 20) {
                modDesc = mod.getName() + ": " + mod.getDescription();
                break;
            }
        }
        if (!modDesc.isEmpty()) {
            context.drawTextWithShadow(tr, modDesc, x + 4, y + 340 + 2, 0xFFAAAAAA);
        }
    }

    private void renderSettingsPanel(DrawContext context, int x, int y) {
        context.fill(x, y, x + 216, y + 340, PANEL_COLOR);
        TextRenderer tr = client.textRenderer;

        context.fill(x, y, x + 216, y + HEADER_HEIGHT, HEADER_COLOR);
        if (selectedModule == null) {
            context.drawTextWithShadow(tr, "Settings", x + 8, y + 10, TEXT_SECONDARY);
            return;
        }

        context.drawTextWithShadow(tr, selectedModule.getName(), x + 8, y + 10, MODULE_ON);

        int bindX = x + 216 - 60;
        int bindY = y + 6;
        int bindW = 52;
        int bindH = 18;
        boolean isBinding = bindingModule == selectedModule;
        int bindColor = isBinding ? 0xFFCC4444 : 0xFF333333;
        context.fill(bindX, bindY, bindX + bindW, bindY + bindH, bindColor);
        String bindText = isBinding ? "..." : (selectedModule.getKeyBind() > 0
            ? GLFW.glfwGetKeyName(selectedModule.getKeyBind(), 0) : "Bind");
        context.drawCenteredTextWithShadow(tr, bindText != null ? bindText : "Key",
            bindX + bindW / 2, bindY + 5, 0xFFFFFFFF);

        List<Setting<?>> sets = selectedModule.getSettings();

        context.enableScissor(x + 1, y + HEADER_HEIGHT + 1, x + 215, y + 339);
        int setY = y + HEADER_HEIGHT + 8 - settingsScrollOffset;

        for (Setting<?> s : sets) {
            context.drawTextWithShadow(tr, s.getName(), x + 8, setY, TEXT_COLOR);
            setY += 12;

            if (s instanceof Setting.BoolSetting bs) {
                renderBoolSetting(context, x, setY, bs);
            } else if (s instanceof Setting.NumberSetting ns) {
                renderNumberSetting(context, x, setY, ns);
            } else if (s instanceof Setting.IntSetting is) {
                renderIntSetting(context, x, setY, is);
            } else if (s instanceof Setting.ModeSetting ms) {
                renderModeSetting(context, x, setY, ms);
            } else if (s instanceof Setting.ColorSetting cs) {
                renderColorSetting(context, x, setY, cs);
            } else if (s instanceof Setting.StringSetting ss) {
                renderStringSetting(context, x, setY, ss);
            }
            setY += 26;
        }
        context.disableScissor();
    }

    private void renderBoolSetting(DrawContext context, int x, int y, Setting.BoolSetting bs) {
        boolean val = bs.getValue();
        int color = val ? MODULE_ON : MODULE_OFF;
        context.fill(x + 8, y, x + 8 + 40, y + 14, color);
        int textX = x + 28 - client.textRenderer.getWidth(val ? "ON" : "OFF") / 2;
        context.drawTextWithShadow(client.textRenderer, val ? "ON" : "OFF", textX, y + 3, 0xFFFFFFFF);
    }

    private void renderNumberSetting(DrawContext context, int x, int y, Setting.NumberSetting ns) {
        double val = ns.getValue();
        double range = ns.max - ns.min;
        double frac = (val - ns.min) / range;
        int sliderX = x + 8;
        int sliderWidth = 180;

        context.fill(sliderX, y + 4, sliderX + sliderWidth, y + 8, 0xFF333333);
        int fillW = (int)(sliderWidth * frac);
        context.fill(sliderX, y + 4, sliderX + fillW, y + 8, ACCENT_COLOR);

        String valStr = String.format("%.1f", val);
        int txtW = client.textRenderer.getWidth(valStr);
        context.drawTextWithShadow(client.textRenderer, valStr, sliderX + sliderWidth + 4, y + 1, TEXT_SECONDARY);
    }

    private void renderIntSetting(DrawContext context, int x, int y, Setting.IntSetting is) {
        int val = is.getValue();
        int range = is.max - is.min;
        if (range == 0) range = 1;
        double frac = (double)(val - is.min) / range;
        int sliderX = x + 8;
        int sliderWidth = 180;

        context.fill(sliderX, y + 4, sliderX + sliderWidth, y + 8, 0xFF333333);
        int fillW = (int)(sliderWidth * frac);
        context.fill(sliderX, y + 4, sliderX + fillW, y + 8, ACCENT_COLOR);

        String valStr = String.valueOf(val);
        int txtW = client.textRenderer.getWidth(valStr);
        context.drawTextWithShadow(client.textRenderer, valStr, sliderX + sliderWidth + 4, y + 1, TEXT_SECONDARY);
    }

    private void renderModeSetting(DrawContext context, int x, int y, Setting.ModeSetting ms) {
        String val = ms.getValue();
        int txtW = client.textRenderer.getWidth(val);
        context.fill(x + 8, y, x + 8 + txtW + 12, y + 14, ACCENT_COLOR);
        context.drawTextWithShadow(client.textRenderer, val, x + 14, y + 3, 0xFFFFFFFF);
    }

    private void renderColorSetting(DrawContext context, int x, int y, Setting.ColorSetting cs) {
        int c = cs.getValue();
        int sliderX = x + 8;
        int sliderWidth = 180;

        for (int i = 0; i < sliderWidth; i++) {
            int col = java.awt.Color.HSBtoRGB((float) i / sliderWidth, 1.0f, 1.0f);
            context.fill(sliderX + i, y, sliderX + i + 1, y + 10, col);
        }

        float[] hsv = java.awt.Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, null);
        int markerX = sliderX + (int)(hsv[0] * sliderWidth);
        context.fill(markerX - 1, y - 1, markerX + 2, y + 11, 0xFFFFFFFF);

        context.fill(sliderX + sliderWidth + 4, y, sliderX + sliderWidth + 24, y + 10, c);
        String hex = String.format("#%06X", c & 0xFFFFFF);
        context.drawTextWithShadow(client.textRenderer, hex, sliderX + sliderWidth + 28, y + 1, TEXT_SECONDARY);
    }

    private void renderStringSetting(DrawContext context, int x, int y, Setting.StringSetting ss) {
        boolean focused = focusedString == ss;
        int boxX = x + 8;
        int boxW = 180;
        context.fill(boxX, y, boxX + boxW, y + 14, focused ? 0xFF23233F : 0xFF1A1A1A);
        context.drawTextWithShadow(client.textRenderer, ss.getValue().isEmpty() ? (focused ? "|" : "...") : ss.getValue(),
            boxX + 3, y + 3, focused ? 0xFFFFFFFF : TEXT_SECONDARY);
        if (focused) {
            context.fill(boxX, y, boxX + boxW, y + 1, ACCENT_COLOR);
        }
    }

    private boolean isHovered(int x, int y, int w) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 26;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mX = click.x();
        double mY = click.y();
        int button = click.getKeycode();
        this.mouseX = mX;
        this.mouseY = mY;
        int guiX = (width - 500) / 2;
        int guiY = (height - 340) / 2;

        if (mX >= guiX && mX <= guiX + SIDEBAR_WIDTH && mY >= guiY + HEADER_HEIGHT) {
            Module.Category[] cats = Module.Category.values();
            int catY = guiY + HEADER_HEIGHT + 22;
            for (Module.Category cat : cats) {
                if (isHovered(guiX + 2, catY, SIDEBAR_WIDTH - 4)) {
                    selectedCategory = cat;
                    selectedModule = null;
                    settingsScrollOffset = 0;
                    focusedString = null;
                    return true;
                }
                catY += 28;
            }
        }

        if (mX >= guiX + SIDEBAR_WIDTH && mX <= guiX + SIDEBAR_WIDTH + 210 && mY >= guiY + HEADER_HEIGHT && mY <= guiY + 339) {
            int modY = guiY + HEADER_HEIGHT + 3 - categoryScrolls.getOrDefault(selectedCategory, 0);
            List<Module> mods = ModuleManager.getModulesByCategory(selectedCategory);
            for (Module mod : mods) {
                if (mX >= guiX + SIDEBAR_WIDTH && mX <= guiX + SIDEBAR_WIDTH + 210 && mY >= modY && mY <= modY + 20) {
                    boolean hasSets = !mod.getSettings().isEmpty();
                    boolean onKeybind = false;
                    if (mod.getKeyBind() > 0) {
                        String kn = GLFW.glfwGetKeyName(mod.getKeyBind(), 0);
                        if (kn != null) {
                            int kw = client.textRenderer.getWidth(kn);
                            int kbTextX = guiX + SIDEBAR_WIDTH + 208 - 8 - kw - (hasSets ? 14 : 0);
                            onKeybind = mX >= kbTextX - 4 && mX <= kbTextX + kw + 4;
                        }
                    }
                    if (button == 0 && onKeybind) {
                        bindingModule = (bindingModule == mod) ? null : mod;
                        selectedModule = mod;
                        focusedString = null;
                        return true;
                    }
                    if (button == 0) {
                        mod.toggle();
                        selectedModule = hasSets ? mod : null;
                        focusedString = null;
                        return true;
                    }
                    if (button == 1) {
                        selectedModule = mod;
                        focusedString = null;
                        return true;
                    }
                }
                modY += 22;
            }
        }

        if (mX >= guiX + SIDEBAR_WIDTH + 210 && mX <= guiX + SIDEBAR_WIDTH + 210 + 216) {
            int rx = (int)mX;
            int ry = (int)mY;
            int sx = guiX + SIDEBAR_WIDTH + 210;

            if (selectedModule != null && button == 0) {
                int bindX = sx + 216 - 60;
                int bindY = guiY + 6;
                if (rx >= bindX && rx <= bindX + 52 && ry >= bindY && ry <= bindY + 18) {
                    bindingModule = (bindingModule == selectedModule) ? null : selectedModule;
                    return true;
                }
            }

            checkSettingsClick(sx, guiY, rx, ry, button);
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    private void checkSettingsClick(int x, int y, int mx, int my, int button) {
        if (selectedModule == null) return;
        int setY = y + HEADER_HEIGHT + 8 - settingsScrollOffset;
        TextRenderer tr = client.textRenderer;

        for (Setting<?> s : selectedModule.getSettings()) {
            if (my >= setY + 12 && my <= setY + 12 + 14) {
                if (s instanceof Setting.BoolSetting bs) {
                    if (mx >= x + 8 && mx <= x + 48) {
                        bs.toggle();
                        com.winnerclient.fabric.ConfigManager.save();
                    }
                } else if (s instanceof Setting.ModeSetting ms) {
                    int txtW = tr.getWidth(ms.getValue());
                    if (mx >= x + 8 && mx <= x + 8 + txtW + 12) {
                        ms.cycle();
                        com.winnerclient.fabric.ConfigManager.save();
                    }
                } else if (s instanceof Setting.NumberSetting ns) {
                    if (button == 1 && mx >= x + 8 && mx <= x + 8 + 180) {
                        draggingSlider = true;
                        draggingSettingName = ns.getName();
                        updateSlider(x, ns, mx);
                    }
                } else if (s instanceof Setting.IntSetting is) {
                    if (button == 1 && mx >= x + 8 && mx <= x + 8 + 180) {
                        draggingSlider = true;
                        draggingSettingName = is.getName();
                        updateIntSlider(x, is, mx);
                    }
                } else if (s instanceof Setting.ColorSetting cs) {
                    if (mx >= x + 8 && mx <= x + 8 + 180) {
                        draggingSlider = true;
                        draggingSettingName = cs.getName();
                        updateColorSlider(x, cs, mx);
                    }
                } else if (s instanceof Setting.StringSetting ss) {
                    if (mx >= x + 8 && mx <= x + 8 + 180) {
                        focusedString = (focusedString == ss) ? null : ss;
                    }
                }
            }
            setY += 38;
        }
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        this.mouseX = click.x();
        this.mouseY = click.y();
        if (draggingSlider && selectedModule != null) {
            int guiX = (width - 500) / 2;
            int guiY = (height - 340) / 2;
            Setting<?> s = selectedModule.getSetting(draggingSettingName);
            if (s instanceof Setting.NumberSetting ns) {
                updateSlider(guiX + SIDEBAR_WIDTH + 210, ns, (int)click.x());
            } else if (s instanceof Setting.IntSetting is) {
                updateIntSlider(guiX + SIDEBAR_WIDTH + 210, is, (int)click.x());
            } else if (s instanceof Setting.ColorSetting cs) {
                updateColorSlider(guiX + SIDEBAR_WIDTH + 210, cs, (int)click.x());
            }
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (draggingSlider) {
            com.winnerclient.fabric.ConfigManager.save();
        }
        draggingSlider = false;
        draggingSettingName = null;
        return super.mouseReleased(click);
    }

    private void updateSlider(int x, Setting.NumberSetting ns, int mx) {
        int sliderX = x + 8;
        int sliderWidth = 180;
        double frac = (double)(mx - sliderX) / sliderWidth;
        frac = Math.max(0, Math.min(1, frac));
        double val = ns.min + frac * (ns.max - ns.min);
        val = ns.min + Math.round((val - ns.min) / ns.increment) * ns.increment;
        val = Math.max(ns.min, Math.min(ns.max, val));
        ns.setValue(val);
    }

    private void updateColorSlider(int x, Setting.ColorSetting cs, int mx) {
        int sliderX = x + 8;
        int sliderWidth = 180;
        double frac = (double)(mx - sliderX) / sliderWidth;
        frac = Math.max(0, Math.min(1, frac));
        int current = cs.getValue();
        float[] hsv = java.awt.Color.RGBtoHSB((current >> 16) & 0xFF, (current >> 8) & 0xFF, current & 0xFF, null);
        int rgb = java.awt.Color.HSBtoRGB((float)frac, hsv[1], hsv[2]);
        cs.setValue((rgb & 0xFFFFFF) | (current & 0xFF000000));
    }

    private void updateIntSlider(int x, Setting.IntSetting is, int mx) {
        int sliderX = x + 8;
        int sliderWidth = 180;
        double frac = (double)(mx - sliderX) / sliderWidth;
        frac = Math.max(0, Math.min(1, frac));
        int val = (int)(is.min + frac * (is.max - is.min));
        val = Math.max(is.min, Math.min(is.max, val));
        is.setValue(val);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int guiX = (width - 500) / 2;
        int guiY = (height - 340) / 2;

        if (mouseX >= guiX + SIDEBAR_WIDTH && mouseX <= guiX + SIDEBAR_WIDTH + 210) {
            int mods = ModuleManager.getModulesByCategory(selectedCategory).size();
            int totalH = mods * 22 + 20;
            int panelH = 339 - guiY - HEADER_HEIGHT;
            int maxScroll = Math.max(0, totalH - panelH);
            int scroll = categoryScrolls.getOrDefault(selectedCategory, 0);
            scroll -= (int)(verticalAmount * 20);
            scroll = Math.max(0, Math.min(scroll, maxScroll));
            categoryScrolls.put(selectedCategory, scroll);
            return true;
        }

        if (mouseX >= guiX + SIDEBAR_WIDTH + 210 && selectedModule != null) {
            int totalH = selectedModule.getSettings().size() * 38 + 20;
            int panelH = 339 - guiY - HEADER_HEIGHT;
            int maxScroll = Math.max(0, totalH - panelH);
            settingsScrollOffset -= (int)(verticalAmount * 20);
            settingsScrollOffset = Math.max(0, Math.min(settingsScrollOffset, maxScroll));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.getKeycode() == GLFW.GLFW_KEY_ESCAPE) {
            if (bindingModule != null) {
                bindingModule.setKeyBind(-2);
                bindingModule = null;
                com.winnerclient.fabric.ConfigManager.save();
                return true;
            }
            if (focusedString != null) {
                focusedString = null;
                com.winnerclient.fabric.ConfigManager.save();
                return true;
            }
            client.setScreen(null);
            return true;
        }
        if (bindingModule != null) {
            bindingModule.setKeyBind(input.getKeycode());
            bindingModule = null;
            com.winnerclient.fabric.ConfigManager.save();
            return true;
        }
        if (focusedString != null) {
            if (input.getKeycode() == GLFW.GLFW_KEY_ENTER) {
                focusedString = null;
                com.winnerclient.fabric.ConfigManager.save();
            } else if (input.getKeycode() == GLFW.GLFW_KEY_BACKSPACE) {
                String v = focusedString.getValue();
                if (!v.isEmpty()) {
                    int last = v.codePointBefore(v.length());
                    focusedString.setValue(v.substring(0, v.length() - Character.charCount(last)));
                    com.winnerclient.fabric.ConfigManager.save();
                }
            } else if (input.isPaste()) {
                focusedString.setValue(focusedString.getValue() + client.keyboard.getClipboard());
                com.winnerclient.fabric.ConfigManager.save();
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (focusedString != null) {
            if (input.isValidChar()) {
                focusedString.setValue(focusedString.getValue() + input.asString());
                com.winnerclient.fabric.ConfigManager.save();
            }
            return true;
        }
        return false;
    }
}
