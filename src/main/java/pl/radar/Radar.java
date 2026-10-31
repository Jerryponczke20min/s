package pl.radar;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Radar {
    /** Gracze zaznaczeni recznie (po nicku). */
    public static final Set<String> TRACED = ConcurrentHashMap.newKeySet();
    /** Gracze, na ktorych w tej chwili rysujemy tracer (liczone co tick). */
    public static volatile Set<UUID> targets = Set.of();

    public static List<PlayerData> scan(double range) {
        MinecraftClient mc = MinecraftClient.getInstance();
        List<PlayerData> out = new ArrayList<>();
        if (mc.player == null || mc.world == null) return out;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            double dist = mc.player.distanceTo(p);
            if (dist > range) continue;
            PlayerData d = new PlayerData(p, dist);
            MineStarParser.parse(d);
            AnarchyAnalyzer.analyze(d);
            out.add(d);
        }
        out.sort(Comparator.comparingDouble(x -> x.dist));
        return out;
    }

    private static int num(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    public static boolean matchesMineStar(PlayerData d, RadarConfig c) {
        boolean active = !(c.swMin.isBlank() && c.swMax.isBlank() && c.arMin.isBlank() && c.arMax.isBlank());
        if (!active || d.sword < 0 || d.set < 0) return false;
        return d.sword >= num(c.swMin, 0) && d.sword <= num(c.swMax, Integer.MAX_VALUE)
            && d.set >= num(c.arMin, 0) && d.set <= num(c.arMax, Integer.MAX_VALUE);
    }

    public static boolean matchesAnarchy(PlayerData d, RadarConfig c) {
        boolean active = c.material != 0 || c.anMode != 0 || !c.minEnch.isBlank() || !c.minProt.isBlank();
        if (!active) return false;
        if (c.material == 1 && d.diamondPieces < 4) return false;
        if (c.material == 2 && d.netheritePieces < 4) return false;
        if (d.minEnch < num(c.minEnch, 0)) return false;
        if (d.minProt < num(c.minProt, 0)) return false;
        return d.tier >= c.anMode;
    }

    public static boolean shouldTrace(PlayerData d, RadarConfig c) {
        if (d.dist > c.tracer()) return false;
        if (TRACED.contains(d.name)) return true;
        if (c.msAuto && matchesMineStar(d, c)) return true;
        return c.anAuto && matchesAnarchy(d, c);
    }

    public static void tick() {
        RadarConfig c = RadarConfig.I;
        Set<UUID> t = new HashSet<>();
        for (PlayerData d : scan(c.tracer())) {
            if (shouldTrace(d, c)) t.add(d.player.getUuid());
        }
        targets = t;
    }
}
