package com.winnerclient.fabric;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.ModuleManager;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * WinnerClient's three-column ClickGUI.
 * Module rows only contain the module name, keybind and enable toggle. The
 * settings of the one selected module are rendered in the right-hand panel.
 */
public class ClickGuiScreen extends Screen {
    private static final int GUI_WIDTH = 540;
    private static final int GUI_HEIGHT = 340;
    private static final int SIDEBAR_WIDTH = 100;
    private static final int MODULES_WIDTH = 176;
    private static final int SETTINGS_WIDTH = GUI_WIDTH - SIDEBAR_WIDTH - MODULES_WIDTH;

    private static final int BG_COLOR       = 0xFF050711;
    private static final int SIDEBAR_COLOR  = 0xFF070A16;
    private static final int PANEL_COLOR    = 0xFF080D1C;
    private static final int CARD_COLOR     = 0xFF0B1124;
    private static final int HEADER_COLOR   = 0xFF0D1429;
    private static final int ACCENT_COLOR   = 0xFF922BFF;
    private static final int ACCENT_BRIGHT  = 0xFFB86CFF;
    private static final int HOVER_COLOR    = 0xFF15152F;
    private static final int ACTIVE_COLOR   = 0xFF21134D;
    private static final int TEXT_COLOR     = 0xFFF0EDFF;
    private static final int TEXT_SECONDARY = 0xFFA7B0D3;
    private static final int TRACK_COLOR    = 0xFF1A2342;
    private static final int MODULE_OFF     = 0xFF273252;
    private static final int BORDER_COLOR   = 0xFF252044;

    private enum Page {
        COMBAT("Combat", "⚔"),
        MOVEMENT("Movement", "➤"),
        PLAYER("Player", "♟"),
        WORLD("World", "◉"),
        RENDER("Render", "▣"),
        MISC("Misc", "⚙"),
        CONFIGS("Configs", "▤");

        final String title;
        final String icon;
        Page(String title, String icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    private static Page selectedPage = Page.COMBAT;
    private static Module selectedModule;
    private static int settingsScrollOffset;
    private static final Map<Page, Integer> categoryScrolls = new EnumMap<>(Page.class);

    private int guiX;
    private int guiY;
    private int guiWidth = GUI_WIDTH;
    private int guiHeight = GUI_HEIGHT;
    private double mouseX;
    private double mouseY;
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
        updateBounds();
        renderBackground(context, mouseX, mouseY, delta);

        // Subtle outer shadow and the thin purple frame from the reference.
        context.fill(guiX - 3, guiY - 3, guiX + guiWidth + 3, guiY + guiHeight + 3, 0x55000000);
        context.fill(guiX - 1, guiY - 1, guiX + guiWidth + 1, guiY + guiHeight + 1, ACCENT_COLOR);
        context.fill(guiX, guiY, guiX + guiWidth, guiY + guiHeight, BG_COLOR);

        renderSidebar(context);
        renderModulePanel(context);
        renderSettingsPanel(context);
    }

    private void updateBounds() {
        guiWidth = Math.min(GUI_WIDTH, Math.max(360, width - 24));
        guiHeight = Math.min(GUI_HEIGHT, Math.max(250, height - 24));
        guiX = (width - guiWidth) / 2;
        guiY = (height - guiHeight) / 2;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xA900020A);
    }

    private void renderSidebar(DrawContext context) {
        TextRenderer tr = client.textRenderer;
        int x = guiX;
        int y = guiY;
        context.fill(x, y, x + SIDEBAR_WIDTH, y + guiHeight, SIDEBAR_COLOR);
        context.fill(x + SIDEBAR_WIDTH - 1, y, x + SIDEBAR_WIDTH, y + guiHeight, BORDER_COLOR);

        // Brand block.
        context.drawTextWithShadow(tr, "Winner", x + 10, y + 12, ACCENT_BRIGHT);
        context.drawTextWithShadow(tr, "Client", x + 43, y + 12, TEXT_COLOR);
        context.fill(x + 10, y + 29, x + 88, y + 30, 0xFF34205D);

        int itemY = y + 43;
        for (Page page : Page.values()) {
            boolean active = page == selectedPage;
            boolean hovered = contains(x + 5, itemY, SIDEBAR_WIDTH - 10, 24);
            int itemColor = active ? ACTIVE_COLOR : (hovered ? HOVER_COLOR : SIDEBAR_COLOR);
            if (active) {
                card(context, x + 4, itemY, x + SIDEBAR_WIDTH - 5, itemY + 24, itemColor, ACCENT_COLOR);
                context.fill(x + 4, itemY + 5, x + 6, itemY + 19, ACCENT_BRIGHT);
            } else {
                context.fill(x + 5, itemY, x + SIDEBAR_WIDTH - 5, itemY + 24, itemColor);
            }
            int color = active ? 0xFFFFFFFF : TEXT_SECONDARY;
            context.drawTextWithShadow(tr, page.icon, x + 11, itemY + 7, active ? ACCENT_BRIGHT : color);
            context.drawTextWithShadow(tr, page.title, x + 29, itemY + 7, color);
            itemY += 29;
        }

        int footerY = y + guiHeight - 34;
        context.fill(x + 8, footerY - 8, x + SIDEBAR_WIDTH - 8, footerY - 7, BORDER_COLOR);
        context.drawTextWithShadow(tr, "WinnerClient", x + 10, footerY, TEXT_COLOR);
        context.drawTextWithShadow(tr, "v1.0.0", x + 10, footerY + 11, TEXT_SECONDARY);
        context.drawTextWithShadow(tr, "⚙", x + 79, footerY + 4, ACCENT_BRIGHT);
    }

    private void renderModulePanel(DrawContext context) {
        int x = guiX + SIDEBAR_WIDTH;
        int y = guiY;
        int w = MODULES_WIDTH;
        context.fill(x, y, x + w, y + guiHeight, PANEL_COLOR);
        context.fill(x, y + 36, x + w, y + 37, BORDER_COLOR);

        TextRenderer tr = client.textRenderer;
        String heading = selectedPage == Page.CONFIGS ? "Configs" : selectedPage.title;
        context.drawTextWithShadow(tr, heading, x + 10, y + 10, TEXT_COLOR);

        if (selectedPage == Page.CONFIGS) {
            renderConfigList(context, x, y);
            return;
        }

        List<Module> modules = getVisibleModules();
        long enabledCount = modules.stream().filter(Module::isEnabled).count();
        String summary = modules.size() + " modules  •  " + enabledCount + " enabled";
        context.drawTextWithShadow(tr, summary, x + 10, y + 23, TEXT_SECONDARY);

        int listTop = y + 43;
        int scroll = categoryScrolls.getOrDefault(selectedPage, 0);
        context.enableScissor(x + 3, listTop, x + w - 3, y + guiHeight - 8);

        int rowY = listTop - scroll;
        for (Module module : modules) {
            boolean selected = module == selectedModule;
            boolean hovered = contains(x + 6, rowY, w - 12, 22);
            int bg = selected ? ACTIVE_COLOR : (hovered ? HOVER_COLOR : CARD_COLOR);
            card(context, x + 6, rowY, x + w - 6, rowY + 22, bg,
                selected ? ACCENT_COLOR : BORDER_COLOR);

            context.drawTextWithShadow(tr, module.getName(), x + 12, rowY + 7, TEXT_COLOR);

            // A compact key label preserves keybind editing without crowding the row.
            String keyName = bindingModule == module ? "..." : keyName(module.getKeyBind());
            if (!keyName.isEmpty()) {
                int keyX = x + w - 67;
                context.drawTextWithShadow(tr, keyName, keyX, rowY + 7, bindingModule == module ? ACCENT_BRIGHT : TEXT_SECONDARY);
            } else if (contains(x + w - 68, rowY, 30, 22)) {
                context.drawTextWithShadow(tr, "Bind", x + w - 66, rowY + 7, TEXT_SECONDARY);
            }

            drawToggle(context, x + w - 31, rowY + 5, module.isEnabled());
            rowY += 25;
        }
        context.disableScissor();

        if (modules.isEmpty()) {
            context.drawCenteredTextWithShadow(tr, "No modules in this category",
                x + w / 2, listTop + 12, TEXT_SECONDARY);
        }
    }

    private void renderConfigList(DrawContext context, int x, int y) {
        TextRenderer tr = client.textRenderer;
        int rowY = y + 46;
        card(context, x + 6, rowY, x + MODULES_WIDTH - 6, rowY + 34, ACTIVE_COLOR, ACCENT_COLOR);
        context.drawTextWithShadow(tr, "Default Config", x + 13, rowY + 7, TEXT_COLOR);
        context.drawTextWithShadow(tr, "WinnerClient", x + 13, rowY + 20, TEXT_SECONDARY);
        context.drawTextWithShadow(tr, "1", x + MODULES_WIDTH - 22, rowY + 13, ACCENT_BRIGHT);
    }

    private void renderSettingsPanel(DrawContext context) {
        int x = guiX + SIDEBAR_WIDTH + MODULES_WIDTH;
        int y = guiY;
        int w = guiWidth - SIDEBAR_WIDTH - MODULES_WIDTH;
        context.fill(x, y, x + w, y + guiHeight, BG_COLOR);
        card(context, x + 6, y + 7, x + w - 7, y + guiHeight - 7, PANEL_COLOR, BORDER_COLOR);

        if (selectedPage == Page.CONFIGS) {
            renderConfigSettings(context, x, y, w);
            return;
        }

        TextRenderer tr = client.textRenderer;
        if (selectedModule == null) {
            context.drawTextWithShadow(tr, "Module Settings", x + 15, y + 16, TEXT_COLOR);
            context.drawTextWithShadow(tr, "Select a module to view its settings.", x + 15, y + 34, TEXT_SECONDARY);
            context.drawTextWithShadow(tr, "Right-click a module, or click its name.", x + 15, y + 48, TEXT_SECONDARY);
            return;
        }

        int headerX = x + 14;
        int headerY = y + 14;
        context.drawTextWithShadow(tr, "✣", headerX, headerY + 1, ACCENT_BRIGHT);
        context.drawTextWithShadow(tr, selectedModule.getName(), headerX + 19, headerY, TEXT_COLOR);

        String bindText = bindingModule == selectedModule ? "Press key..." : "Bind";
        int bindW = tr.getWidth(bindText) + 10;
        int bindX = x + w - 14 - 25 - bindW - 7;
        card(context, bindX, y + 10, bindX + bindW, y + 27,
            bindingModule == selectedModule ? ACTIVE_COLOR : CARD_COLOR,
            bindingModule == selectedModule ? ACCENT_COLOR : BORDER_COLOR);
        context.drawCenteredTextWithShadow(tr, bindText, bindX + bindW / 2, y + 15,
            bindingModule == selectedModule ? ACCENT_BRIGHT : TEXT_SECONDARY);
        drawToggle(context, x + w - 34, y + 12, selectedModule.isEnabled());

        context.fill(x + 8, y + 36, x + w - 8, y + 37, BORDER_COLOR);
        context.drawTextWithShadow(tr, "SETTINGS", x + 14, y + 43, ACCENT_BRIGHT);

        int contentTop = y + 60;
        int contentBottom = y + guiHeight - 14;
        context.enableScissor(x + 8, contentTop, x + w - 8, contentBottom);

        int rowY = contentTop + 2 - settingsScrollOffset;
        for (Setting<?> setting : selectedModule.getSettings()) {
            int rowHeight = settingHeight(setting);
            renderSetting(context, x, rowY, w, setting, rowHeight);
            rowY += rowHeight + 5;
        }
        context.disableScissor();

        if (selectedModule.getSettings().isEmpty()) {
            context.drawTextWithShadow(tr, "This module has no settings.", x + 14, contentTop + 8, TEXT_SECONDARY);
        }
    }

    private void renderConfigSettings(DrawContext context, int x, int y, int w) {
        TextRenderer tr = client.textRenderer;
        context.drawTextWithShadow(tr, "Config Manager", x + 14, y + 15, TEXT_COLOR);
        context.fill(x + 8, y + 36, x + w - 8, y + 37, BORDER_COLOR);

        File file = ConfigManager.getConfigFile();
        String fileName = file == null ? "winnerclient.json" : file.getName();
        String path = file == null ? "config/winnerclient.json" : file.getPath();
        context.drawTextWithShadow(tr, "Current profile", x + 14, y + 52, ACCENT_BRIGHT);
        card(context, x + 14, y + 68, x + w - 14, y + 94, CARD_COLOR, BORDER_COLOR);
        context.drawTextWithShadow(tr, fileName, x + 22, y + 77, TEXT_COLOR);

        context.drawTextWithShadow(tr, "Location", x + 14, y + 109, ACCENT_BRIGHT);
        context.enableScissor(x + 14, y + 122, x + w - 14, y + 146);
        context.drawTextWithShadow(tr, path, x + 14, y + 125, TEXT_SECONDARY);
        context.disableScissor();

        int buttonY = y + 161;
        card(context, x + 14, buttonY, x + w - 14, buttonY + 25, ACTIVE_COLOR, ACCENT_COLOR);
        context.drawCenteredTextWithShadow(tr, "Save Config", x + w / 2, buttonY + 8, TEXT_COLOR);
        card(context, x + 14, buttonY + 33, x + w - 14, buttonY + 58, CARD_COLOR, BORDER_COLOR);
        context.drawCenteredTextWithShadow(tr, "Load Config", x + w / 2, buttonY + 41, TEXT_COLOR);
        context.drawTextWithShadow(tr, "Changes are stored in the config file.", x + 14, buttonY + 70, TEXT_SECONDARY);
    }

    private void renderSetting(DrawContext context, int panelX, int rowY, int panelWidth,
                               Setting<?> setting, int rowHeight) {
        int x = panelX + 10;
        int right = panelX + panelWidth - 10;
        TextRenderer tr = client.textRenderer;

        if (setting instanceof Setting.BoolSetting bool) {
            context.drawTextWithShadow(tr, setting.getName(), x + 3, rowY + 7, TEXT_COLOR);
            drawToggle(context, right - 24, rowY + 4, bool.getValue());
            return;
        }

        context.drawTextWithShadow(tr, setting.getName(), x + 3, rowY + 3, TEXT_COLOR);

        if (setting instanceof Setting.NumberSetting number) {
            String value = String.format(Locale.ROOT, "%.2f", number.getValue());
            drawSlider(context, x + 3, rowY + 22, right - x - 42, fraction(number.getValue(), number.min, number.max));
            context.drawTextWithShadow(tr, value, right - tr.getWidth(value), rowY + 3, TEXT_SECONDARY);
        } else if (setting instanceof Setting.IntSetting integer) {
            String value = String.valueOf(integer.getValue());
            drawSlider(context, x + 3, rowY + 22, right - x - 42, fraction(integer.getValue(), integer.min, integer.max));
            context.drawTextWithShadow(tr, value, right - tr.getWidth(value), rowY + 3, TEXT_SECONDARY);
        } else if (setting instanceof Setting.ModeSetting mode) {
            String value = mode.getValue();
            card(context, x + 3, rowY + 17, right, rowY + 35, CARD_COLOR, BORDER_COLOR);
            context.drawTextWithShadow(tr, value + "  ▾", x + 10, rowY + 22, TEXT_SECONDARY);
        } else if (setting instanceof Setting.ColorSetting color) {
            int sliderX = x + 3;
            int sliderWidth = Math.max(80, right - sliderX - 54);
            for (int i = 0; i < sliderWidth; i++) {
                int rgb = java.awt.Color.HSBtoRGB((float) i / sliderWidth, 1.0f, 1.0f);
                context.fill(sliderX + i, rowY + 20, sliderX + i + 1, rowY + 28, rgb);
            }
            context.fill(sliderX + sliderWidth + 5, rowY + 20, sliderX + sliderWidth + 25, rowY + 28, color.getValue());
            String hex = String.format(Locale.ROOT, "#%06X", color.getValue() & 0xFFFFFF);
            context.drawTextWithShadow(tr, hex, sliderX + sliderWidth + 29, rowY + 21, TEXT_SECONDARY);
        } else if (setting instanceof Setting.StringSetting string) {
            if (selectedModule instanceof com.winnerclient.fabric.module.render.BlockESP esp
                    && string.getName().equals("Blocks")) {
                String label = "Select blocks (" + esp.getSelectedIds().size() + ")";
                card(context, x + 3, rowY + 17, right, rowY + 35, ACTIVE_COLOR, ACCENT_COLOR);
                context.drawTextWithShadow(tr, label, x + 10, rowY + 22, TEXT_COLOR);
                return;
            }
            boolean focused = focusedString == string;
            String value = string.getValue().isEmpty() ? (focused ? "|" : "Enter text...") : string.getValue();
            card(context, x + 3, rowY + 17, right, rowY + 35, CARD_COLOR, focused ? ACCENT_COLOR : BORDER_COLOR);
            context.enableScissor(x + 7, rowY + 18, right - 4, rowY + 34);
            context.drawTextWithShadow(tr, value, x + 10, rowY + 22, focused ? TEXT_COLOR : TEXT_SECONDARY);
            context.disableScissor();
        }
    }

    private int settingHeight(Setting<?> setting) {
        return setting instanceof Setting.BoolSetting ? 25 : 39;
    }

    private double fraction(double value, double min, double max) {
        if (max <= min) return 0;
        return Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
    }

    private void drawSlider(DrawContext context, int x, int y, int width, double fraction) {
        int safeWidth = Math.max(1, width);
        int fillWidth = (int) (safeWidth * fraction);
        context.fill(x, y, x + safeWidth, y + 3, TRACK_COLOR);
        if (fillWidth > 0) context.fill(x, y, x + fillWidth, y + 3, ACCENT_COLOR);
        int knobX = x + fillWidth;
        context.fill(knobX - 2, y - 3, knobX + 3, y + 6, ACCENT_BRIGHT);
    }

    private List<Module> getVisibleModules() {
        if (selectedPage == Page.MISC) {
            List<Module> combined = new ArrayList<>();
            combined.addAll(ModuleManager.getModulesByCategory(Module.Category.MISC));
            combined.addAll(ModuleManager.getModulesByCategory(Module.Category.CLIENT));
            return combined;
        }

        Module.Category category = switch (selectedPage) {
            case COMBAT -> Module.Category.COMBAT;
            case MOVEMENT -> Module.Category.MOVEMENT;
            case PLAYER -> Module.Category.PLAYER;
            case WORLD -> Module.Category.WORLD;
            case RENDER -> Module.Category.RENDER;
            case MISC, CONFIGS -> Module.Category.MISC;
        };
        return ModuleManager.getModulesByCategory(category);
    }

    private int settingRowY(Setting<?> target) {
        int y = guiY + 60 + 2 - settingsScrollOffset;
        if (selectedModule == null) return y;
        for (Setting<?> setting : selectedModule.getSettings()) {
            if (setting == target) return y;
            y += settingHeight(setting) + 5;
        }
        return y;
    }

    private void card(DrawContext context, int x1, int y1, int x2, int y2, int color, int border) {
        context.fill(x1 + 2, y1, x2 - 2, y2, border);
        context.fill(x1, y1 + 2, x2, y2 - 2, border);
        context.fill(x1 + 2, y1 + 1, x2 - 2, y2 - 1, color);
        context.fill(x1 + 1, y1 + 2, x2 - 1, y2 - 2, color);
    }

    private void drawToggle(DrawContext context, int x, int y, boolean enabled) {
        int color = enabled ? ACCENT_COLOR : MODULE_OFF;
        context.fill(x + 4, y, x + 20, y + 12, color);
        context.fill(x + 1, y + 2, x + 23, y + 10, color);
        int knobX = enabled ? x + 13 : x + 3;
        context.fill(knobX + 1, y + 2, knobX + 8, y + 10, enabled ? 0xFFFFFFFF : 0xFF9AA8D0);
        context.fill(knobX, y + 3, knobX + 9, y + 9, enabled ? 0xFFFFFFFF : 0xFF9AA8D0);
    }

    private String keyName(int key) {
        if (key <= 0) return "";
        String name = GLFW.glfwGetKeyName(key, 0);
        return name == null ? "Key" : name.toUpperCase(Locale.ROOT);
    }

    private boolean contains(int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        int button = click.getKeycode();
        mouseX = mx;
        mouseY = my;
        updateBounds();

        // Sidebar navigation.
        if (mx >= guiX && mx <= guiX + SIDEBAR_WIDTH && my >= guiY + 43 && my < guiY + guiHeight - 35) {
            int itemY = guiY + 43;
            for (Page page : Page.values()) {
                if (mx >= guiX + 4 && mx <= guiX + SIDEBAR_WIDTH - 5 && my >= itemY && my <= itemY + 24) {
                    selectedPage = page;
                    selectedModule = null;
                    settingsScrollOffset = 0;
                    focusedString = null;
                    bindingModule = null;
                    return true;
                }
                itemY += 29;
            }
        }

        int moduleX = guiX + SIDEBAR_WIDTH;
        int moduleRight = moduleX + MODULES_WIDTH;
        if (mx >= moduleX && mx <= moduleRight && my >= guiY + 38 && my <= guiY + guiHeight - 8) {
            if (selectedPage == Page.CONFIGS) {
                int rowY = guiY + 46;
                if (my >= rowY && my <= rowY + 34) return true;
            } else {
                int rowY = guiY + 43 - categoryScrolls.getOrDefault(selectedPage, 0);
                for (Module module : getVisibleModules()) {
                    if (my >= rowY && my <= rowY + 22) {
                        int switchX = moduleRight - 31;
                        int keyX = moduleRight - 67;
                        if (button == 0 && mx >= switchX - 2 && mx <= switchX + 25) {
                            module.toggle();
                            return true;
                        }
                        if (button == 0 && mx >= keyX - 3 && mx < switchX - 3) {
                            bindingModule = bindingModule == module ? null : module;
                            focusedString = null;
                            return true;
                        }
                        if (button == 1 || button == 0) {
                            if (selectedModule == module) {
                                selectedModule = null;
                            } else {
                                selectedModule = module;
                            }
                            settingsScrollOffset = 0;
                            focusedString = null;
                            bindingModule = null;
                            return true;
                        }
                    }
                    rowY += 25;
                }
            }
        }

        int settingsX = guiX + SIDEBAR_WIDTH + MODULES_WIDTH;
        int settingsRight = guiX + guiWidth;
        if (mx >= settingsX && mx <= settingsRight && my >= guiY && my <= guiY + guiHeight) {
            if (selectedPage == Page.CONFIGS) {
                int buttonY = guiY + 161;
                if (button == 0 && mx >= settingsX + 14 && mx <= settingsRight - 14) {
                    if (my >= buttonY && my <= buttonY + 25) {
                        ConfigManager.save();
                        return true;
                    }
                    if (my >= buttonY + 33 && my <= buttonY + 58) {
                        ConfigManager.load();
                        return true;
                    }
                }
                return true;
            }

            if (selectedModule != null && button == 0) {
                int bindW = client.textRenderer.getWidth(bindingModule == selectedModule ? "Press key..." : "Bind") + 10;
                int bindX = settingsRight - 14 - 25 - bindW - 7;
                if (mx >= bindX && mx <= bindX + bindW && my >= guiY + 10 && my <= guiY + 27) {
                    bindingModule = bindingModule == selectedModule ? null : selectedModule;
                    return true;
                }
                if (mx >= settingsRight - 37 && mx <= settingsRight - 8 && my >= guiY + 10 && my <= guiY + 29) {
                    selectedModule.toggle();
                    return true;
                }
                clickSetting(mx, my, button, settingsX);
                return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    private void clickSetting(double mx, double my, int button, int panelX) {
        if (selectedModule == null || button != 0) return;

        for (Setting<?> setting : selectedModule.getSettings()) {
            int rowY = settingRowY(setting);
            int rowHeight = settingHeight(setting);
            if (my < rowY || my > rowY + rowHeight) continue;

            int left = panelX + 13;
            int right = guiX + guiWidth - 10;
            if (setting instanceof Setting.BoolSetting bool) {
                bool.toggle();
                ConfigManager.save();
            } else if (setting instanceof Setting.NumberSetting number) {
                if (my >= rowY + 15 && mx >= left && mx <= right - 42) {
                    draggingSlider = true;
                    draggingSettingName = setting.getName();
                    updateNumberSlider(left + 3, right - left - 42, number, (int) mx);
                }
            } else if (setting instanceof Setting.IntSetting integer) {
                if (my >= rowY + 15 && mx >= left && mx <= right - 42) {
                    draggingSlider = true;
                    draggingSettingName = setting.getName();
                    updateIntegerSlider(left + 3, right - left - 42, integer, (int) mx);
                }
            } else if (setting instanceof Setting.ModeSetting mode) {
                mode.cycle();
                ConfigManager.save();
            } else if (setting instanceof Setting.ColorSetting color) {
                if (my >= rowY + 15) {
                    draggingSlider = true;
                    draggingSettingName = setting.getName();
                    updateColorSlider(left + 3, Math.max(80, right - left - 57), color, (int) mx);
                }
            } else if (setting instanceof Setting.StringSetting string) {
                if (selectedModule instanceof com.winnerclient.fabric.module.render.BlockESP esp
                        && string.getName().equals("Blocks")) {
                    focusedString = null;
                    client.setScreen(new BlockPickerScreen(this, esp));
                } else {
                    focusedString = focusedString == string ? null : string;
                }
            }
            return;
        }
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        mouseX = click.x();
        mouseY = click.y();
        if (draggingSlider && selectedModule != null) {
            int panelX = guiX + SIDEBAR_WIDTH + MODULES_WIDTH;
            int left = panelX + 13;
            int right = guiX + guiWidth - 10;
            Setting<?> setting = selectedModule.getSetting(draggingSettingName);
            if (setting instanceof Setting.NumberSetting number) {
                updateNumberSlider(left + 3, right - left - 42, number, (int) click.x());
            } else if (setting instanceof Setting.IntSetting integer) {
                updateIntegerSlider(left + 3, right - left - 42, integer, (int) click.x());
            } else if (setting instanceof Setting.ColorSetting color) {
                updateColorSlider(left + 3, Math.max(80, right - left - 57), color, (int) click.x());
            }
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (draggingSlider) ConfigManager.save();
        draggingSlider = false;
        draggingSettingName = null;
        return super.mouseReleased(click);
    }

    private void updateNumberSlider(int x, int sliderWidth, Setting.NumberSetting setting, int mx) {
        double fraction = Math.max(0, Math.min(1, (double) (mx - x) / Math.max(1, sliderWidth)));
        double value = setting.min + fraction * (setting.max - setting.min);
        double step = setting.increment > 0 ? setting.increment : 0.1;
        value = setting.min + Math.round((value - setting.min) / step) * step;
        setting.setValue(Math.max(setting.min, Math.min(setting.max, value)));
    }

    private void updateIntegerSlider(int x, int sliderWidth, Setting.IntSetting setting, int mx) {
        double fraction = Math.max(0, Math.min(1, (double) (mx - x) / Math.max(1, sliderWidth)));
        int value = (int) Math.round(setting.min + fraction * (setting.max - setting.min));
        setting.setValue(Math.max(setting.min, Math.min(setting.max, value)));
    }

    private void updateColorSlider(int x, int sliderWidth, Setting.ColorSetting setting, int mx) {
        double fraction = Math.max(0, Math.min(1, (double) (mx - x) / Math.max(1, sliderWidth)));
        int current = setting.getValue();
        float[] hsv = java.awt.Color.RGBtoHSB((current >> 16) & 0xFF,
            (current >> 8) & 0xFF, current & 0xFF, null);
        int rgb = java.awt.Color.HSBtoRGB((float) fraction, hsv[1], hsv[2]);
        setting.setValue((rgb & 0xFFFFFF) | (current & 0xFF000000));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        updateBounds();
        int moduleX = guiX + SIDEBAR_WIDTH;
        int moduleRight = moduleX + MODULES_WIDTH;
        if (selectedPage != Page.CONFIGS && mouseX >= moduleX && mouseX <= moduleRight
                && mouseY >= guiY + 38 && mouseY < guiY + guiHeight - 8) {
            int totalHeight = getVisibleModules().size() * 25;
            int viewport = guiHeight - 51;
            int maxScroll = Math.max(0, totalHeight - viewport);
            int scroll = categoryScrolls.getOrDefault(selectedPage, 0);
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (verticalAmount * 22)));
            categoryScrolls.put(selectedPage, scroll);
            return true;
        }

        int settingsX = guiX + SIDEBAR_WIDTH + MODULES_WIDTH;
        if (selectedPage != Page.CONFIGS && selectedModule != null && mouseX >= settingsX
                && mouseX <= guiX + guiWidth && mouseY >= guiY + 58 && mouseY < guiY + guiHeight - 12) {
            int totalHeight = selectedModule.getSettings().stream().mapToInt(s -> settingHeight(s) + 5).sum();
            int viewport = guiHeight - 76;
            int maxScroll = Math.max(0, totalHeight - viewport);
            settingsScrollOffset = Math.max(0, Math.min(maxScroll,
                settingsScrollOffset - (int) (verticalAmount * 22)));
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
                ConfigManager.save();
                return true;
            }
            if (focusedString != null) {
                focusedString = null;
                ConfigManager.save();
                return true;
            }
            client.setScreen(null);
            return true;
        }

        if (bindingModule != null) {
            bindingModule.setKeyBind(input.getKeycode());
            bindingModule = null;
            ConfigManager.save();
            return true;
        }

        if (focusedString != null) {
            if (input.getKeycode() == GLFW.GLFW_KEY_ENTER) {
                focusedString = null;
                ConfigManager.save();
            } else if (input.getKeycode() == GLFW.GLFW_KEY_BACKSPACE) {
                String value = focusedString.getValue();
                if (!value.isEmpty()) {
                    int last = value.codePointBefore(value.length());
                    focusedString.setValue(value.substring(0, value.length() - Character.charCount(last)));
                    ConfigManager.save();
                }
            } else if (input.isPaste()) {
                focusedString.setValue(focusedString.getValue() + client.keyboard.getClipboard());
                ConfigManager.save();
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
                ConfigManager.save();
            }
            return true;
        }
        return false;
    }
}
