package com.winnerclient.fabric;

import com.winnerclient.fabric.module.render.BlockESP;
import net.minecraft.block.Block;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class BlockPickerScreen extends Screen {
    private static final int BACKGROUND = 0xFF050A1F;
    private static final int PANEL = 0xFF080F2B;
    private static final int ROW = 0xFF10173A;
    private static final int SELECTED = 0xFF23135A;
    private static final int BORDER = 0xFF28346B;
    private static final int PURPLE = 0xFFA632FF;
    private static final int TEXT_COLOR = 0xFFECE9FF;
    private static final int MUTED = 0xFFACB8E0;

    private static final int ROW_HEIGHT = 25;

    private final Screen parent;
    private final BlockESP esp;

    private final List<Block> allBlocks = new ArrayList<>();
    private final List<Block> filtered = new ArrayList<>();

    private String query = "";
    private int scroll = 0;

    public BlockPickerScreen(Screen parent, BlockESP esp) {
        super(Text.literal("Select blocks"));

        this.parent = parent;
        this.esp = esp;

        Registries.BLOCK.forEach(allBlocks::add);

        allBlocks.sort(
            Comparator.comparing(
                block -> Registries.BLOCK.getId(block).toString()
            )
        );

        updateFilter();
    }

    private int panelWidth() {
        return Math.min(380, Math.max(160, width - 20));
    }

    private int panelHeight() {
        return Math.min(300, Math.max(120, height - 20));
    }

    private int left() {
        return (width - panelWidth()) / 2;
    }

    private int top() {
        return (height - panelHeight()) / 2;
    }

    private int listTop() {
        return top() + 70;
    }

    private int listBottom() {
        return top() + panelHeight() - 28;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW_HEIGHT);
    }

    private void updateFilter() {
        filtered.clear();

        String needle = query.toLowerCase(Locale.ROOT).trim();

        for (Block block : allBlocks) {
            String id = Registries.BLOCK.getId(block).toString();
            String name = block.getName().getString();

            if (
                needle.isEmpty() ||
                id.toLowerCase(Locale.ROOT).contains(needle) ||
                name.toLowerCase(Locale.ROOT).contains(needle)
            ) {
                filtered.add(block);
            }
        }

        scroll = 0;
    }

    private void clampScroll() {
        int max = Math.max(0, filtered.size() - visibleRows());
        scroll = Math.max(0, Math.min(scroll, max));
    }

    private void box(
        DrawContext context,
        int x1,
        int y1,
        int x2,
        int y2,
        int color,
        int border
    ) {
        context.fill(x1, y1, x2, y2, border);
        context.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, color);
    }

    @Override
    public void render(
        DrawContext context,
        int mouseX,
        int mouseY,
        float delta
    ) {
        context.fill(0, 0, width, height, 0xA0020719);

        int x = left();
        int y = top();
        int w = panelWidth();
        int h = panelHeight();

        box(context, x, y, x + w, y + h, BACKGROUND, PURPLE);

        context.drawTextWithShadow(
            client.textRenderer,
            "BLOCK ESP",
            x + 12,
            y + 10,
            TEXT_COLOR
        );

        String selectedCount = esp.getSelectedIds().size() + " selected";

        context.drawTextWithShadow(
            client.textRenderer,
            selectedCount,
            x + w - 12 - client.textRenderer.getWidth(selectedCount),
            y + 10,
            MUTED
        );

        box(
            context,
            x + 10,
            y + 28,
            x + w - 10,
            y + 50,
            PANEL,
            BORDER
        );

        String searchText = query.isEmpty()
            ? "Search block name or ID..."
            : query + "_";

        context.enableScissor(x + 15, y + 30, x + w - 15, y + 48);

        context.drawTextWithShadow(
            client.textRenderer,
            searchText,
            x + 16,
            y + 35,
            query.isEmpty() ? MUTED : TEXT_COLOR
        );

        context.disableScissor();

        context.drawTextWithShadow(
            client.textRenderer,
            filtered.size() + " blocks",
            x + 12,
            y + 56,
            MUTED
        );

        clampScroll();

        int rowStart = listTop();
        int rowEnd = listBottom();

        context.enableScissor(x + 10, rowStart, x + w - 10, rowEnd);

        for (int row = 0; row < visibleRows(); row++) {
            int index = scroll + row;

            if (index >= filtered.size()) {
                break;
            }

            Block block = filtered.get(index);
            int rowY = rowStart + row * ROW_HEIGHT;

            boolean selected = esp.isSelected(block);

            boolean hovered =
                mouseX >= x + 10 &&
                mouseX < x + w - 10 &&
                mouseY >= rowY &&
                mouseY < rowY + ROW_HEIGHT - 2;

            int rowColor = selected
                ? SELECTED
                : (hovered ? ROW : PANEL);

            box(
                context,
                x + 10,
                rowY,
                x + w - 10,
                rowY + ROW_HEIGHT - 2,
                rowColor,
                selected ? PURPLE : BORDER
            );

            // Blocks without an item form remain selectable.
            context.drawItem(
                new ItemStack(block.asItem()),
                x + 15,
                rowY + 3
            );

            String name = block.getName().getString();
            String id = Registries.BLOCK.getId(block).toString();

            context.enableScissor(
                x + 35,
                rowY + 1,
                x + w - 42,
                rowY + ROW_HEIGHT - 3
            );

            context.drawTextWithShadow(
                client.textRenderer,
                name,
                x + 36,
                rowY + 3,
                TEXT_COLOR
            );

            context.drawTextWithShadow(
                client.textRenderer,
                id,
                x + 36,
                rowY + 13,
                MUTED
            );

            context.disableScissor();

            context.drawTextWithShadow(
                client.textRenderer,
                selected ? "[X]" : "[ ]",
                x + w - 35,
                rowY + 8,
                selected ? PURPLE : MUTED
            );
        }

        context.disableScissor();

        context.drawTextWithShadow(
            client.textRenderer,
            "Click to select  |  Scroll  |  Esc to return",
            x + 12,
            y + h - 17,
            MUTED
        );
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.getKeycode() != 0) {
            return super.mouseClicked(click, doubled);
        }

        double mx = click.x();
        double my = click.y();

        int x = left();
        int w = panelWidth();

        if (
            mx >= x + 10 &&
            mx < x + w - 10 &&
            my >= listTop() &&
            my < listBottom()
        ) {
            int row = (int) ((my - listTop()) / ROW_HEIGHT);
            int index = scroll + row;

            if (index >= 0 && index < filtered.size()) {
                esp.toggleBlock(filtered.get(index));
                return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(
        double mouseX,
        double mouseY,
        double horizontalAmount,
        double verticalAmount
    ) {
        if (
            mouseX >= left() + 10 &&
            mouseX < left() + panelWidth() - 10 &&
            mouseY >= listTop() &&
            mouseY < listBottom()
        ) {
            if (verticalAmount > 0) {
                scroll -= 3;
            } else if (verticalAmount < 0) {
                scroll += 3;
            }

            clampScroll();
            return true;
        }

        return super.mouseScrolled(
            mouseX,
            mouseY,
            horizontalAmount,
            verticalAmount
        );
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.getKeycode();

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        if (key == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
            int codePoint = query.codePointBefore(query.length());

            query = query.substring(
                0,
                query.length() - Character.charCount(codePoint)
            );

            updateFilter();
            return true;
        }

        if (input.isPaste()) {
            query += client.keyboard.getClipboard();
            updateFilter();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (input.isValidChar()) {
            query += input.asString();
            updateFilter();
            return true;
        }

        return false;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
              }
