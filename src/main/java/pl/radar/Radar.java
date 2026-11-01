package pl.radar;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Radar {
    /** Gracze zaznaczeni recznie (po nicku). */
    public static final Set<String> TRACED = ConcurrentHashMap.newKeySet();
    /** Moby zaznaczone recznie (po UUID) i bloki (tabliczki) zaznaczone recznie. */
    public static final Set<UUID> TRACED_UUID = ConcurrentHashMap.newKeySet();
    public static final Set<BlockPos> TRACED_BLOCKS = ConcurrentHashMap.newKeySet();
    /** Wszystkie byty i bloki, na ktore rysujemy tracer (liczone co tick). */
    public static volatile Set<UUID> targets = Set.of();
    public static volatile Set<BlockPos> blockTargets = Set.of();

    /** Wynik wyszukiwania w zakladce Event: byt albo blok. */
    public static class Hit {
        public final String label;
        public final double dist;
        public final Entity entity;  // albo null
        public final BlockPos pos;   // albo null
        Hit(String label, double dist, Entity entity, BlockPos pos) {
            this.label = label; this.dist = dist; this.entity = entity; this.pos = pos;
        }
    }

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

    // ---------- EVENT: wyszukiwanie po napisie (moby, hologramy, tabliczki) ----------

    public static boolean eventActive(RadarConfig c) {
        return !c.eventType.isBlank() || !c.eventText.isBlank();
    }

    private static String strip(Text t) {
        if (t == null) return "";
        String s = Formatting.strip(t.getString());
        return s == null ? "" : s;
    }

    /** Napis nad bytem: custom name, a dla Text Display tresc tekstu. */
    public static String entityText(Entity e) {
        String s = strip(e.getCustomName());
        if (e instanceof DisplayEntity.TextDisplayEntity td) {
            s = (s + " " + strip(td.getText())).trim();
        }
        return s;
    }

    public static String entityLabel(Entity e) {
        String type = e.getType().getName().getString();
        String n = entityText(e).replace('\n', ' ');
        if (n.length() > 40) n = n.substring(0, 40);
        return n.isEmpty() ? type : n + " §7(" + type + ")";
    }

    public static boolean matchesEvent(Entity e, RadarConfig c) {
        if (e instanceof PlayerEntity || !eventActive(c)) return false;
        if (!c.eventType.isBlank() && !Registries.ENTITY_TYPE.getId(e.getType()).toString().equals(c.eventType)) {
            return false;
        }
        if (!c.eventText.isBlank()) {
            if (!entityText(e).toLowerCase().contains(c.eventText.trim().toLowerCase())) return false;
        }
        return true;
    }

    private static String signText(SignBlockEntity s) {
        StringBuilder sb = new StringBuilder();
        for (Text t : s.getFrontText().getMessages(false)) sb.append(strip(t)).append(' ');
        for (Text t : s.getBackText().getMessages(false)) sb.append(strip(t)).append(' ');
        return sb.toString().trim();
    }

    public static List<Hit> scanEvents(double range) {
        MinecraftClient mc = MinecraftClient.getInstance();
        RadarConfig c = RadarConfig.I;
        List<Hit> out = new ArrayList<>();
        if (mc.player == null || mc.world == null || !eventActive(c)) return out;

        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || !matchesEvent(e, c)) continue;
            double d = mc.player.distanceTo(e);
            if (d > range) continue;
            out.add(new Hit(entityLabel(e), d, e, null));
        }

        // bloki (tabliczki) - tylko gdy podano napis i nie wybrano typu moba
        if (c.eventBlocks && c.eventType.isBlank() && !c.eventText.isBlank()) {
            String q = c.eventText.trim().toLowerCase();
            ChunkPos cp = mc.player.getChunkPos();
            int r = (int) Math.ceil(range / 16.0) + 1;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    WorldChunk ch = mc.world.getChunkManager().getWorldChunk(cp.x + dx, cp.z + dz);
                    if (ch == null) continue;
                    for (BlockEntity be : ch.getBlockEntities().values()) {
                        if (!(be instanceof SignBlockEntity sign)) continue;
                        double d = be.getPos().toCenterPos().distanceTo(mc.player.getPos());
                        if (d > range) continue;
                        String txt = signText(sign);
                        if (txt.toLowerCase().contains(q)) {
                            out.add(new Hit(txt.length() > 40 ? txt.substring(0, 40) : txt + " §7(tabliczka)",
                                d, null, be.getPos().toImmutable()));
                        }
                    }
                }
            }
        }
        out.sort(Comparator.comparingDouble(h -> h.dist));
        return out;
    }

    // ---------- tick ----------

    private static int tickCount = 0;
    private static List<Hit> cacheHits = List.of();

    public static void tick() {
        RadarConfig c = RadarConfig.I;
        MinecraftClient mc = MinecraftClient.getInstance();
        Set<UUID> t = new HashSet<>();
        Set<BlockPos> bt = new HashSet<>();
        boolean fresh = false;
        Set<UUID> old = targets;
        Set<BlockPos> oldB = blockTargets;

        // gracze
        for (PlayerData d : scan(c.tracer())) {
            if (!shouldTrace(d, c)) continue;
            UUID u = d.player.getUuid();
            t.add(u);
            if (!TRACED.contains(d.name) && !old.contains(u)) fresh = true;
        }

        if (mc.world != null && mc.player != null) {
            double r = c.tracer();

            // reczne zaznaczenia mobow
            if (!TRACED_UUID.isEmpty()) {
                for (Entity e : mc.world.getEntities()) {
                    if (e == mc.player || e instanceof PlayerEntity) continue;
                    if (TRACED_UUID.contains(e.getUuid()) && mc.player.distanceTo(e) <= r) t.add(e.getUuid());
                }
            }
            // reczne zaznaczenia blokow
            for (BlockPos p : TRACED_BLOCKS) {
                if (p.toCenterPos().distanceTo(mc.player.getPos()) <= r) bt.add(p);
            }

            // auto: wyniki Event (odswiezane co 5 tickow)
            if (c.eventAuto && eventActive(c)) {
                if (tickCount++ % 5 == 0) cacheHits = scanEvents(r);
                for (Hit h : cacheHits) {
                    if (h.entity != null) {
                        if (h.entity.isRemoved()) continue;
                        UUID u = h.entity.getUuid();
                        t.add(u);
                        if (!old.contains(u)) fresh = true;
                    } else if (h.pos != null) {
                        bt.add(h.pos);
                        if (!oldB.contains(h.pos)) fresh = true;
                    }
                }
            } else {
                cacheHits = List.of();
            }
        }

        if (fresh) Ui.ping();
        targets = t;
        blockTargets = bt;
    }
}
