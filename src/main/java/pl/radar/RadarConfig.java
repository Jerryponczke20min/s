package pl.radar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class RadarConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("playerradar.json");

    public int tab = 0; // 0 MineStar, 1 Anarchia, 2 Event, 3 Ustawienia
    public String radarRange = "100";
    public String tracerRange = "100";
    public String color = "990F2D";    // kolor tracerow / ESP (wisniowy)
    public String guiColor = "7C5CFF"; // kolor akcentu GUI
    public boolean esp = true;
    public boolean sounds = true;
    public boolean notify = true;

    // MineStar
    public String swMin = "", swMax = "", arMin = "", arMax = "";
    public boolean msAuto = false;

    // Anarchia
    public int material = 0; // 0 dowolny, 1 diament, 2 netherite
    public String minEnch = "", minProt = "";
    public int anMode = 0;   // 0 dowolny, 1 anarchiczny I, 2 anarchiczny II
    public boolean anAuto = false;

    // Event (moby)
    public String eventType = "";  // np. minecraft:cow, puste = dowolny
    public String eventText = "";  // napis nad glowa (zawiera)
    public boolean eventAuto = false;
    public boolean eventBlocks = true; // szukaj tez w tabliczkach

    // PvP
    public boolean noShake = true;     // stabilizacja kamery
    public boolean origSaved = false;  // zapamietane oryginalne ustawienia gry
    public boolean origBob = true;
    public double origTilt = 1.0, origFov = 1.0;
    public boolean hitPlayers = false, hitMobs = false, hitLook = false;
    public String hitColor = "FFFFFF";

    public static final RadarConfig I = load();

    private static RadarConfig load() {
        try {
            if (Files.exists(FILE)) {
                RadarConfig c = GSON.fromJson(Files.readString(FILE), RadarConfig.class);
                if (c != null) return c;
            }
        } catch (Exception ignored) {}
        return new RadarConfig();
    }

    public void save() {
        try { Files.writeString(FILE, GSON.toJson(this)); } catch (Exception ignored) {}
    }

    public double radar() { return parseD(radarRange, 100); }
    public double tracer() { return parseD(tracerRange, 100); }
    public int colorRgb() { return hex(color, 0x990F2D); }
    public int accent() { return hex(guiColor, 0x7C5CFF); }
    public int hitRgb() { return hex(hitColor, 0xFFFFFF); }

    private static int hex(String s, int def) {
        try { return Integer.parseInt(s.replace("#", "").trim(), 16) & 0xFFFFFF; }
        catch (Exception e) { return def; }
    }

    private static double parseD(String s, double def) {
        try { return Double.parseDouble(s.trim().replace(',', '.')); } catch (Exception e) { return def; }
    }
}
