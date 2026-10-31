package pl.radar;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

/** Male narzedzia do ladnego GUI: zaokraglone prostokaty, kolory, dzwieki. */
public final class Ui {
    public static final int BG = 0xF0181A20, CARD = 0xFF1F222B, CARD2 = 0xFF2A2E39, CARD3 = 0xFF353A48;
    public static final int TEXT = 0xFFE8EAF0, MUTED = 0xFF8A90A0;
    private static long lastPing = 0;

    private Ui() {}

    /** Prostokat z zaokraglonymi rogami (promien r). */
    public static void rrect(DrawContext c, int x, int y, int w, int h, int r, int argb) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, argb);
            c.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, argb);
        }
        c.fill(x, y + r, x + w, y + h - r, argb);
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = (int) (((a >>> 24) & 255) * (1 - t) + ((b >>> 24) & 255) * t);
        int ar = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int ag = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int ab = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    public static int alpha(int rgb, int a) { return (a << 24) | (rgb & 0xFFFFFF); }
    public static int solid(int rgb) { return 0xFF000000 | (rgb & 0xFFFFFF); }
    public static float easeOut(float t) { float u = 1 - t; return 1 - u * u * u; }

    public static void click(float pitch) {
        if (!RadarConfig.I.sounds) return;
        MinecraftClient.getInstance().getSoundManager()
            .play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    /** Dzwiek powiadomienia o nowym celu (z cooldownem). */
    public static void ping() {
        if (!RadarConfig.I.sounds || !RadarConfig.I.notify) return;
        long now = System.currentTimeMillis();
        if (now - lastPing < 800) return;
        lastPing = now;
        click(1.9f);
    }
}
