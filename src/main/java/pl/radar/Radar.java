package pl.radar;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Radar {
    /** Gracze zaznaczeni recznie (po nicku). */
    public static final Set<String> TRACED = ConcurrentHashMap.newKeySet();
    /** Moby zaznaczone recznie (po UUID). */
    public static final Set<UUID> TRACED_UUID = ConcurrentHashMap.newKeySet();
    /** Wszystkie byty (gracze i moby), na ktore rysujemy tracer (liczone co tick). */
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

    // ---------- EVENT (moby) ----------

    public static boolean eventActive(RadarConfig c) {
        return !c.eventType.isBlank() || !c.eventText.isBlank();
    }

    public static String stripName(Entity e) {
        if (e.getCustomName() == null) return "";
        String s = Formatting.strip(e.getCustomName().getString());
        return s == null ? "" : s;
    }

    public static String entityLabel(Entity e) {
        String type = e.getType().getName().getString();
        String n = stripName(e);
        return n.isEmpty() ? type : n + " §7(" + type + ")";
    }

    public static boolean matchesEvent(Entity e, RadarConfig c) {
        if (e instanceof PlayerEntity || !eventActive(c)) return false;
        if (!c.eventType.isBlank() && !Registries.ENTITY_TYPE.getId(e.getType()).toString().equals(c.eventType)) {
            return false;
        }
        if (!c.eventText.isBlank()) {
            String n = stripName(e).toLowerCase();
            if (!n.contains(c.eventText.trim().toLowerCase())) return false;
        }
        return true;
    }

    public static List<Entity> scanEvents(double range) {
        MinecraftClient mc = MinecraftClient.getInstance();
        List<Entity> out = new ArrayList<>();
        if (mc.player == null || mc.world == null || !eventActive(RadarConfig.I)) return out;
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || !matchesEvent(e, RadarConfig.I)) continue;
            if (mc.player.distanceTo(e) > range) continue;
            out.add(e);
        }
        out.sort(Comparator.comparingDouble(x -> mc.player.distanceTo(x)));
        return out;
    }

    // ---------- tick ----------

    public static void tick() {
        RadarConfig c = RadarConfig.I;
        MinecraftClient mc = MinecraftClient.getInstance();
        Set<UUID> t = new HashSet<>();
        boolean fresh = false;
        Set<UUID> old = targets;

        for (PlayerData d : scan(c.tracer())) {
            if (!shouldTrace(d, c)) continue;
            UUID u = d.player.getUuid();
            t.add(u);
            if (!TRACED.contains(d.name) && !old.contains(u)) fresh = true;
        }

        if (mc.world != null && mc.player != null
            && (!TRACED_UUID.isEmpty() || (c.eventAuto && eventActive(c)))) {
            double r = c.tracer();
            for (Entity e : mc.world.getEntities()) {
                if (e == mc.player || e instanceof PlayerEntity) continue;
                if (mc.player.distanceTo(e) > r) continue;
                UUID u = e.getUuid();
                boolean manual = TRACED_UUID.contains(u);
                if (manual || (c.eventAuto && matchesEvent(e, c))) {
                    t.add(u);
                    if (!manual && !old.contains(u)) fresh = true;
                }
            }
        }

        if (fresh) Ui.ping();
        targets = t;
    }
}
