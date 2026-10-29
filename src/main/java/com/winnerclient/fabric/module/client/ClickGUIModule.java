package com.winnerclient.fabric.module.client;

import com.winnerclient.fabric.WinnerClientMod;
import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;

public class ClickGUIModule extends Module {
    public ClickGUIModule() {
        super("ClickGUI", "Open the click GUI", Category.CLIENT);
    }

    @Override
    public void onEnable() {
        WinnerClientMod.openClickGui();
        toggle();
    }
}
