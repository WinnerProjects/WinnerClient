package com.winnerclient.fabric.module.render;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;

public class Nametags extends Module {
    private final Setting.BoolSetting self = addBool("Self", "Show own nametag", false);
    private final Setting.ColorSetting color = addColor("Color", "Nametag color", 0xFFFFFFFF, true);

    public Nametags() {
        super("Nametags", "Enhanced nametags", Category.RENDER);
    }

    public boolean showSelf() { return isEnabled() && self.getValue(); }
}
