package com.winnerclient.fabric.module;

public abstract class Setting<T> {
    protected final String name;
    protected final String description;
    protected T value;
    protected final T defaultValue;

    protected Setting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description;
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public T getValue() { return value; }
    public T getDefaultValue() { return defaultValue; }

    public void setValue(T value) {
        this.value = value;
    }

    public void reset() {
        this.value = defaultValue;
    }

    public static class BoolSetting extends Setting<Boolean> {
        public BoolSetting(String name, String description, boolean defaultValue) {
            super(name, description, defaultValue);
        }
        public void toggle() {
            setValue(!getValue());
        }
    }

    public static class ModeSetting extends Setting<String> {
        public final String[] modes;
        public ModeSetting(String name, String description, String defaultValue, String... modes) {
            super(name, description, defaultValue);
            this.modes = modes;
        }
        public void cycle() {
            int idx = -1;
            for (int i = 0; i < modes.length; i++) {
                if (modes[i].equals(value)) { idx = i; break; }
            }
            int next = (idx + 1) % modes.length;
            setValue(modes[next]);
        }
    }

    public static class NumberSetting extends Setting<Double> {
        public final double min;
        public final double max;
        public final double increment;

        public NumberSetting(String name, String description, double defaultValue, double min, double max, double increment) {
            super(name, description, defaultValue);
            this.min = min;
            this.max = max;
            this.increment = increment;
        }
    }

    public static class IntSetting extends Setting<Integer> {
        public final int min;
        public final int max;

        public IntSetting(String name, String description, int defaultValue, int min, int max) {
            super(name, description, defaultValue);
            this.min = min;
            this.max = max;
        }
    }

    public static class StringSetting extends Setting<String> {
        public StringSetting(String name, String description, String defaultValue) {
            super(name, description, defaultValue);
        }
    }

    public static class ColorSetting extends Setting<Integer> {
        public boolean allowAlpha;

        public ColorSetting(String name, String description, int defaultColor, boolean allowAlpha) {
            super(name, description, defaultColor);
            this.allowAlpha = allowAlpha;
        }

        public int getRed()   { return (value >> 16) & 0xFF; }
        public int getGreen() { return (value >> 8)  & 0xFF; }
        public int getBlue()  { return value         & 0xFF; }
        public int getAlpha() { return (value >> 24) & 0xFF; }

        public void cycle() {
            int r = getRed(), g = getGreen(), b = getBlue();
            float[] hsv = java.awt.Color.RGBtoHSB(r, g, b, null);
            hsv[0] = (hsv[0] + 1.0f / 12.0f) % 1.0f;
            int rgb = java.awt.Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]);
            setValue((rgb & 0xFFFFFF) | (value & 0xFF000000));
        }
    }
}