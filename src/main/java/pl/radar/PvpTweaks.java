package pl.radar;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;

/**
 * Stabilizacja kamery przez ustawienia gry: bez kolysania podczas chodzenia (view bobbing),
 * bez trzesienia po obrazeniach (damage tilt) i bez zmian FOV przy sprincie (FOV effects).
 * Oryginalne wartosci sa zapisywane w configu i przywracane po wylaczeniu.
 */
public class PvpTweaks {
    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null) return;
        GameOptions o = mc.options;
        RadarConfig c = RadarConfig.I;

        if (c.noShake) {
            if (!c.origSaved) {
                c.origBob = o.getBobView().getValue();
                c.origTilt = o.getDamageTiltStrength().getValue();
                c.origFov = o.getFovEffectScale().getValue();
                c.origSaved = true;
                c.save();
            }
            if (o.getBobView().getValue()) o.getBobView().setValue(false);
            if (o.getDamageTiltStrength().getValue() != 0.0) o.getDamageTiltStrength().setValue(0.0);
            if (o.getFovEffectScale().getValue() != 0.0) o.getFovEffectScale().setValue(0.0);
        } else if (c.origSaved) {
            o.getBobView().setValue(c.origBob);
            o.getDamageTiltStrength().setValue(c.origTilt);
            o.getFovEffectScale().setValue(c.origFov);
            c.origSaved = false;
            c.save();
        }
    }
}
