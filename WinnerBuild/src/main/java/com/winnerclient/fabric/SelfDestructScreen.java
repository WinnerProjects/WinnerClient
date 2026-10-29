package com.winnerclient.fabric;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.ModuleManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.Click;
import net.minecraft.text.Text;

public class SelfDestructScreen extends Screen {
    public SelfDestructScreen() {
        super(Text.translatable("gui.winnerclient.selfdestruct"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xC0000000);

        int cx = width / 2;
        int cy = height / 2;

        String title = "SELF DESTRUCT";
        String msg = "Disable all WinnerClient modules?";
        String msg2 = "Are you sure?";
        String confirm = "YES - DELETE";
        String cancel = "Cancel";

        int titleW = client.textRenderer.getWidth(title);
        context.drawTextWithShadow(client.textRenderer, title, cx - titleW / 2, cy - 50, 0xFFFF4444);

        int msgW = client.textRenderer.getWidth(msg);
        context.drawTextWithShadow(client.textRenderer, msg, cx - msgW / 2, cy - 30, 0xFFFFFFFF);

        int msg2W = client.textRenderer.getWidth(msg2);
        context.drawTextWithShadow(client.textRenderer, msg2, cx - msg2W / 2, cy - 16, 0xFFFFAAAA);

        int btnY = cy + 4;
        int btnH = 22;
        int btnW = 120;

        int yesX = cx - btnW - 8;
        int noX = cx + 8;

        int yesColor = (mouseX >= yesX && mouseX < yesX + btnW && mouseY >= btnY && mouseY < btnY + btnH)
                ? 0xFFCC2222 : 0xFF881111;
        int noColor = (mouseX >= noX && mouseX < noX + btnW && mouseY >= btnY && mouseY < btnY + btnH)
                ? 0xFF333333 : 0xFF1A1A1A;

        context.fill(yesX, btnY, yesX + btnW, btnY + btnH, yesColor);
        int cw = client.textRenderer.getWidth(confirm);
        context.drawTextWithShadow(client.textRenderer, confirm, yesX + (btnW - cw) / 2, btnY + 7, 0xFFFFFFFF);

        context.fill(noX, btnY, noX + btnW, btnY + btnH, noColor);
        int nw = client.textRenderer.getWidth(cancel);
        context.drawTextWithShadow(client.textRenderer, cancel, noX + (btnW - nw) / 2, btnY + 7, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int cx = width / 2;
        int cy = height / 2;
        double mx = click.x();
        double my = click.y();

        int btnY = cy + 4;
        int btnH = 22;
        int btnW = 120;

        int yesX = cx - btnW - 8;
        int noX = cx + 8;

        if (mx >= yesX && mx < yesX + btnW && my >= btnY && my < btnY + btnH) {
            execute();
            return true;
        }

        if (mx >= noX && mx < noX + btnW && my >= btnY && my < btnY + btnH) {
            client.setScreen(null);
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    private void execute() {
        for (Module m : ModuleManager.getModules()) {
            if (m.isEnabled()) {
                m.setEnabled(false);
            }
        }
        client.setScreen(null);
    }
}
