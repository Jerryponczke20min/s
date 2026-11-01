package com.example.fakebonemeal;

/** Paleta kolorow GUI wyliczana z jednego koloru glownego (akcentu). */
public final class Theme {

    public static final String[] NAMES = {
            "Trawa", "Ocean", "Sakura", "Złoto", "Fiolet", "Ogień", "Turkus", "Mono", "Własny"
    };
    public static final int[] COLORS = {
            0x5BD05B, 0x4DA6FF, 0xFF7FB5, 0xFFC23D, 0xA77BFF, 0xFF6B3D, 0x3DDBC6, 0xBFC5CC, 0x5BD05B
    };
    public static final int CUSTOM = 8;

    public final int accent, accentHover, border, headerTop, headerBottom, strip1, strip2;
    public final int panel, card, cardHover, btn, btnHover, btnOff, slot, onAccent;

    private Theme(int rgb, int opacityPct) {
        accent = opaque(rgb);
        accentHover = opaque(lerp(rgb, 0xFFFFFF, 0.3));
        border = opaque(mul(rgb, 0.85));
        headerTop = opaque(mul(rgb, 0.75));
        headerBottom = opaque(mul(rgb, 0.4));
        strip1 = opaque(lerp(rgb, 0xFFFFFF, 0.1));
        strip2 = opaque(mul(rgb, 0.55));

        int a = Math.max(0, Math.min(255, opacityPct * 255 / 100));
        panel = (a << 24) | mul(rgb, 0.10);
        card = opaque(mul(rgb, 0.20));
        cardHover = opaque(mul(rgb, 0.30));
        btn = opaque(mul(rgb, 0.28));
        btnHover = opaque(mul(rgb, 0.45));
        btnOff = opaque(mul(rgb, 0.10));
        slot = opaque(mul(rgb, 0.07));

        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        double lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        onAccent = lum > 0.5 ? 0xFF0B1A0B : 0xFFFFFFFF;
    }

    public static int currentRgb() {
        int idx = Math.max(0, Math.min(NAMES.length - 1, ClientSettings.themeIndex));
        return idx == CUSTOM ? (ClientSettings.customRgb & 0xFFFFFF) : COLORS[idx];
    }

    public static Theme current() {
        return new Theme(currentRgb(), ClientSettings.panelOpacity);
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private static int mul(int rgb, double f) {
        int r = (int) (((rgb >> 16) & 255) * f);
        int g = (int) (((rgb >> 8) & 255) * f);
        int b = (int) ((rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    private static int lerp(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
