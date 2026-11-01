package pl.radar;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class RadarScreen extends Screen {
    private static final int W = 480, ROW = 26, MROW = 20;
    private static final int TAB_STEP = 92, TAB_W = 88, TAB_IND = 36;
    private static final String[] TABS = {"MineStar", "Anarchia", "Event", "PvP", "Ustawienia"};
    private static final int T_MS = 0, T_AN = 1, T_EV = 2, T_PVP = 3, T_SET = 4;
    private static final String[] MATERIAL = {"Set: dowolny", "Set: diament", "Set: netherite"};
    private static final String[] MODE = {"Tryb: dowolny", "Anarchiczny I", "Anarchiczny II"};
    private static final int[] SWATCH = {
        0x7C5CFF, 0x4F8CFF, 0x2ED3B7, 0x3DDC84, 0xFFB020, 0xFF6B35, 0xE5334F, 0x990F2D, 0xFF5CAA, 0xFFFFFF
    };

    private static List<EntityType<?>> ALL_MOBS;
    private static final Map<EntityType<?>, ItemStack> ICONS = new HashMap<>();

    private final RadarConfig cfg = RadarConfig.I;
    private int x0, y0, H, cy;
    private final long openAt = System.currentTimeMillis();
    private float tabX = -1;

    private final List<Btn> btns = new ArrayList<>();
    private final List<RField> fields = new ArrayList<>();
    private RField tracerHex, guiHex, hitHex;

    private List<PlayerData> rows = new ArrayList<>();
    private List<Radar.Hit> found = new ArrayList<>();
    private List<EntityType<?>> mobs = new ArrayList<>();
    private String mobSearch = "";
    private int scrollP, scrollM, scrollF;
    private final int[] rectP = new int[4], rectM = new int[4], rectF = new int[4];

    private static class Btn {
        int x, y, w, h;
        Supplier<String> label;
        BooleanSupplier on;
        Runnable run;
        float hover;
    }

    public RadarScreen() {
        super(Text.literal("Radar Graczy"));
        Ui.click(1.4f);
    }

    // ------------------------------------------------------------ init

    @Override
    protected void init() {
        x0 = width / 2 - W / 2;
        H = Math.min(height - 20, 350);
        y0 = (height - H) / 2;
        cy = y0 + 54;
        btns.clear();
        fields.clear();
        tracerHex = guiHex = hitHex = null;

        btn(x0 + W - 30, y0 + 8, 20, 16, () -> "×", null, this::close);

        switch (cfg.tab) {
            case T_MS -> initMineStar();
            case T_AN -> initAnarchy();
            case T_EV -> initEvent();
            case T_PVP -> initPvp();
            default -> initSettings();
        }
        tick();
    }

    private void autoButton(BooleanSupplier on, Runnable toggle) {
        btn(x0 + 24, cy + 34, 130, 20, () -> "Auto-tracery: " + (on.getAsBoolean() ? "§aON" : "§cOFF"), on, toggle);
    }

    private void initMineStar() {
        field(x0 + 46, cy + 9, 50, cfg.swMin, "od", s -> cfg.swMin = s);
        field(x0 + 100, cy + 9, 50, cfg.swMax, "do", s -> cfg.swMax = s);
        field(x0 + 192, cy + 9, 50, cfg.arMin, "od", s -> cfg.arMin = s);
        field(x0 + 246, cy + 9, 50, cfg.arMax, "do", s -> cfg.arMax = s);
        autoButton(() -> cfg.msAuto, () -> { cfg.msAuto = !cfg.msAuto; cfg.save(); });
    }

    private void initAnarchy() {
        btn(x0 + 24, cy + 9, 112, 18, () -> MATERIAL[cfg.material], null,
            () -> { cfg.material = (cfg.material + 1) % 3; cfg.save(); });
        field(x0 + 186, cy + 9, 34, cfg.minEnch, "0", s -> cfg.minEnch = s);
        field(x0 + 266, cy + 9, 34, cfg.minProt, "0", s -> cfg.minProt = s);
        btn(x0 + 312, cy + 9, 142, 18, () -> MODE[cfg.anMode], null,
            () -> { cfg.anMode = (cfg.anMode + 1) % 3; cfg.save(); });
        autoButton(() -> cfg.anAuto, () -> { cfg.anAuto = !cfg.anAuto; cfg.save(); });
    }

    private void initEvent() {
        if (ALL_MOBS == null) {
            ALL_MOBS = new ArrayList<>();
            for (EntityType<?> t : Registries.ENTITY_TYPE) {
                if (t.getSpawnGroup() != SpawnGroup.MISC || t == EntityType.ARMOR_STAND) ALL_MOBS.add(t);
            }
            ALL_MOBS.sort(Comparator.comparing(t -> t.getName().getString()));
        }
        field(x0 + 24, cy + 10, 190, mobSearch, "Szukaj moba...", s -> { mobSearch = s; scrollM = 0; refreshMobs(); });
        refreshMobs();

        field(x0 + 244, cy + 22, 212, cfg.eventText, "np. Halloween", s -> cfg.eventText = s);
        btn(x0 + 394, cy + 48, 62, 18, () -> "Dowolny", null, () -> { cfg.eventType = ""; cfg.save(); });
        btn(x0 + 244, cy + 72, 104, 20, () -> "Auto: " + (cfg.eventAuto ? "§aON" : "§cOFF"),
            () -> cfg.eventAuto, () -> { cfg.eventAuto = !cfg.eventAuto; cfg.save(); });
        btn(x0 + 352, cy + 72, 104, 20, () -> "Bloki: " + (cfg.eventBlocks ? "§aON" : "§cOFF"),
            () -> cfg.eventBlocks, () -> { cfg.eventBlocks = !cfg.eventBlocks; cfg.save(); });
    }

    private void initPvp() {
        btn(x0 + 24, cy + 10, 210, 20, () -> "Stabilizacja kamery: " + (cfg.noShake ? "§aON" : "§cOFF"),
            () -> cfg.noShake, () -> { cfg.noShake = !cfg.noShake; cfg.save(); });
        btn(x0 + 24, cy + 78, 138, 20, () -> "Hitboxy graczy: " + (cfg.hitPlayers ? "§aON" : "§cOFF"),
            () -> cfg.hitPlayers, () -> { cfg.hitPlayers = !cfg.hitPlayers; cfg.save(); });
        btn(x0 + 170, cy + 78, 138, 20, () -> "Hitboxy mobów: " + (cfg.hitMobs ? "§aON" : "§cOFF"),
            () -> cfg.hitMobs, () -> { cfg.hitMobs = !cfg.hitMobs; cfg.save(); });
        btn(x0 + 316, cy + 78, 138, 20, () -> "Linia wzroku: " + (cfg.hitLook ? "§aON" : "§cOFF"),
            () -> cfg.hitLook, () -> { cfg.hitLook = !cfg.hitLook; cfg.save(); });
        hitHex = field(x0 + 170, cy + 106, 70, cfg.hitColor, "RRGGBB", s -> cfg.hitColor = s);
    }

    private void initSettings() {
        field(x0 + 170, cy + 10, 60, cfg.radarRange, "100", s -> cfg.radarRange = s);
        field(x0 + 372, cy + 10, 70, cfg.tracerRange, "100", s -> cfg.tracerRange = s);
        tracerHex = field(x0 + 170, cy + 56, 70, cfg.color, "RRGGBB", s -> cfg.color = s);
        guiHex = field(x0 + 170, cy + 100, 70, cfg.guiColor, "RRGGBB", s -> cfg.guiColor = s);
        btn(x0 + 24, cy + 152, 138, 20, () -> "ESP: " + (cfg.esp ? "§aON" : "§cOFF"), () -> cfg.esp,
            () -> { cfg.esp = !cfg.esp; cfg.save(); });
        btn(x0 + 170, cy + 152, 138, 20, () -> "Dźwięki: " + (cfg.sounds ? "§aON" : "§cOFF"), () -> cfg.sounds,
            () -> { cfg.sounds = !cfg.sounds; cfg.save(); });
        btn(x0 + 316, cy + 152, 138, 20, () -> "Powiadomienie: " + (cfg.notify ? "§aON" : "§cOFF"), () -> cfg.notify,
            () -> { cfg.notify = !cfg.notify; cfg.save(); });
    }

    private void btn(int x, int y, int w, int h, Supplier<String> label, BooleanSupplier on, Runnable run) {
        Btn b = new Btn();
        b.x = x; b.y = y; b.w = w; b.h = h; b.label = label; b.on = on; b.run = run;
        btns.add(b);
    }

    private RField field(int x, int y, int w, String val, String hint, Consumer<String> setter) {
        RField f = new RField(textRenderer, x, y, w, 18);
        f.setMaxLength(40);
        f.setText(val);
        f.setPlaceholder(Text.literal(hint).formatted(Formatting.DARK_GRAY));
        f.setChangedListener(s -> { setter.accept(s); cfg.save(); });
        addSelectableChild(f);
        fields.add(f);
        return f;
    }

    private void refreshMobs() {
        String q = mobSearch.toLowerCase().trim();
        mobs = new ArrayList<>();
        for (EntityType<?> t : ALL_MOBS) {
            if (q.isEmpty() || t.getName().getString().toLowerCase().contains(q)
                || Registries.ENTITY_TYPE.getId(t).getPath().contains(q)) mobs.add(t);
        }
    }

    private static ItemStack icon(EntityType<?> t) {
        return ICONS.computeIfAbsent(t, k -> {
            SpawnEggItem egg = SpawnEggItem.forEntity(k);
            if (egg != null) return new ItemStack(egg);
            return new ItemStack(k == EntityType.ARMOR_STAND ? Items.ARMOR_STAND : Items.BARRIER);
        });
    }

    @Override
    public void tick() {
        if (cfg.tab <= T_AN) {
            rows = Radar.scan(cfg.radar());
            scrollP = Math.max(0, Math.min(scrollP, rows.size() - Math.max(1, rectP[3] / ROW)));
        } else if (cfg.tab == T_EV) {
            found = Radar.scanEvents(cfg.radar());
            scrollF = Math.max(0, Math.min(scrollF, found.size() - Math.max(1, rectF[3] / MROW)));
        }
    }

    @Override
    public boolean shouldPause() { return false; }

    // ------------------------------------------------------------ render

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        int accent = cfg.accent();

        float t = Math.min(1f, (System.currentTimeMillis() - openAt) / 220f);
        float e = Ui.easeOut(t);
        c.fill(0, 0, width, height, Ui.alpha(0x000000, (int) (140 * e)));

        c.getMatrices().push();
        c.getMatrices().translate(0f, (1f - e) * 14f, 0f);

        Ui.rrect(c, x0 - 2, y0 - 2, W + 4, H + 4, 11, 0x40000000);
        Ui.rrect(c, x0, y0, W, H, 9, Ui.BG);

        c.drawText(tr, "§lRADAR", x0 + 18, y0 + 10, Ui.solid(accent), false);
        c.drawText(tr, "GRACZY", x0 + 18 + tr.getWidth("§lRADAR") + 4, y0 + 10, Ui.MUTED, false);

        float target = x0 + 14 + cfg.tab * TAB_STEP + (TAB_W - TAB_IND) / 2f;
        tabX = tabX < 0 ? target : tabX + (target - tabX) * 0.25f;
        for (int i = 0; i < TABS.length; i++) {
            int tx = x0 + 14 + i * TAB_STEP;
            boolean hov = inside(mx, my, tx, y0 + 26, TAB_W, 20);
            if (hov && i != cfg.tab) Ui.rrect(c, tx, y0 + 26, TAB_W, 18, 5, 0x20FFFFFF);
            int col = i == cfg.tab ? 0xFFFFFFFF : hov ? 0xFFC8CCD8 : Ui.MUTED;
            c.drawText(tr, TABS[i], tx + (TAB_W - tr.getWidth(TABS[i])) / 2, y0 + 31, col, false);
        }
        Ui.rrect(c, Math.round(tabX), y0 + 45, TAB_IND, 2, 1, Ui.solid(accent));

        switch (cfg.tab) {
            case T_MS, T_AN -> renderPlayers(c, mx, my, tr, accent);
            case T_EV -> renderEvent(c, mx, my, tr, accent);
            case T_PVP -> renderPvp(c, tr);
            default -> renderSettings(c, tr);
        }

        for (Btn b : btns) renderBtn(c, b, mx, my, tr, accent);
        for (RField f : fields) f.render(c, mx, my, delta);

        c.getMatrices().pop();
    }

    private void renderBtn(DrawContext c, Btn b, int mx, int my, TextRenderer tr, int accent) {
        boolean hov = inside(mx, my, b.x, b.y, b.w, b.h);
        b.hover += ((hov ? 1f : 0f) - b.hover) * 0.3f;
        boolean on = b.on != null && b.on.getAsBoolean();
        int base = on ? Ui.lerp(Ui.alpha(accent, 0xB0), Ui.solid(accent), b.hover)
                      : Ui.lerp(Ui.CARD2, Ui.CARD3, b.hover);
        Ui.rrect(c, b.x, b.y, b.w, b.h, 5, base);
        String s = b.label.get();
        c.drawText(tr, s, b.x + (b.w - tr.getWidth(s)) / 2, b.y + (b.h - 8) / 2, 0xFFFFFFFF, false);
    }

    private void card(DrawContext c, int x, int y, int w, int h) { Ui.rrect(c, x, y, w, h, 7, Ui.CARD); }

    private void pill(DrawContext c, TextRenderer tr, int x, int y, int w, int h, boolean on, String text, boolean hov, int accent) {
        int col = on ? Ui.solid(accent) : (hov ? Ui.CARD3 : Ui.CARD2);
        Ui.rrect(c, x, y, w, h, h / 2, col);
        c.drawText(tr, text, x + (w - tr.getWidth(text)) / 2, y + (h - 8) / 2, on ? 0xFFFFFFFF : Ui.MUTED, false);
    }

    private void scrollbar(DrawContext c, int x, int y, int h, int total, int vis, int scroll) {
        if (total <= vis) return;
        int bh = Math.max(12, h * vis / total);
        int by = y + (h - bh) * scroll / Math.max(1, total - vis);
        Ui.rrect(c, x, y, 3, h, 1, 0x20FFFFFF);
        Ui.rrect(c, x, by, 3, bh, 1, 0x90FFFFFF);
    }

    // ---- zakladki graczy ----

    private void renderPlayers(DrawContext c, int mx, int my, TextRenderer tr, int accent) {
        card(c, x0 + 14, cy, W - 28, 60);
        if (cfg.tab == T_MS) {
            c.drawItem(new ItemStack(Items.DIAMOND_SWORD), x0 + 24, cy + 9);
            c.drawItem(new ItemStack(Items.SHIELD), x0 + 170, cy + 9);
        } else {
            c.drawText(tr, "Ench >=", x0 + 144, cy + 14, Ui.MUTED, false);
            c.drawText(tr, "Prot >=", x0 + 226, cy + 14, Ui.MUTED, false);
        }
        String hint = cfg.tab == T_MS ? "Puste pola = brak filtra. Pasujący gracze są podświetleni."
            : "I: netherite + enchanty • II: netherite + fiolet (amethyst) + lepsze ench.";
        c.drawText(tr, hint, x0 + 164, cy + 40, Ui.MUTED, false);

        int lx = x0 + 14, ly = cy + 68, lw = W - 28, lh = y0 + H - 14 - ly;
        rectP[0] = lx; rectP[1] = ly; rectP[2] = lw; rectP[3] = lh;
        int vis = Math.max(1, lh / ROW);

        if (rows.isEmpty()) {
            String s = "Brak graczy w zasięgu";
            c.drawText(tr, s, lx + (lw - tr.getWidth(s)) / 2, ly + 20, Ui.MUTED, false);
        }
        for (int i = 0; i < vis && i + scrollP < rows.size(); i++) {
            PlayerData d = rows.get(i + scrollP);
            int ry = ly + i * ROW;
            boolean match = cfg.tab == T_MS ? Radar.matchesMineStar(d, cfg) : Radar.matchesAnarchy(d, cfg);
            boolean on = Radar.TRACED.contains(d.name) || Radar.targets.contains(d.player.getUuid());
            boolean hov = inside(mx, my, lx, ry, lw - 8, ROW - 2);
            int bg = match ? Ui.alpha(accent, hov ? 0x70 : 0x50) : (hov ? Ui.CARD3 : Ui.CARD2);
            Ui.rrect(c, lx, ry, lw - 8, ROW - 2, 6, bg);

            PlayerListEntry pe = MinecraftClient.getInstance().getNetworkHandler() == null ? null
                : MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(d.player.getUuid());
            if (pe != null) PlayerSkinDrawer.draw(c, pe.getSkinTextures(), lx + 6, ry + 4, 16);

            c.drawText(tr, d.name, lx + 28, ry + 4, 0xFFFFFFFF, true);
            c.drawText(tr, String.format("%.0f m", d.dist), lx + 28, ry + 14, Ui.MUTED, false);

            if (cfg.tab == T_MS) {
                int x = lx + 150;
                c.drawItem(new ItemStack(Items.DIAMOND_SWORD), x, ry + 4);
                c.drawText(tr, d.sword < 0 ? "?" : String.valueOf(d.sword), x + 19, ry + 8, 0xFFFF9955, true);
                x += 62;
                c.drawItem(new ItemStack(Items.SHIELD), x, ry + 4);
                c.drawText(tr, d.set < 0 ? "?" : String.valueOf(d.set), x + 19, ry + 8, 0xFF66B8FF, true);
                x += 62;
                c.drawItem(new ItemStack(Items.SKELETON_SKULL), x, ry + 4);
                c.drawText(tr, d.skulls < 0 ? "?" : String.valueOf(d.skulls), x + 19, ry + 8, 0xFF66FFFF, true);
            } else {
                int x = lx + 120;
                c.drawItem(d.player.getMainHandStack(), x, ry + 4);
                c.drawItem(d.player.getOffHandStack(), x + 18, ry + 4);
                x += 44;
                for (EquipmentSlot s : AnarchyAnalyzer.ARMOR) {
                    c.drawItem(d.player.getEquippedStack(s), x, ry + 4);
                    x += 17;
                }
                String info = d.tier == 2 ? "§dAnar. II" : d.tier == 1 ? "§5Anar. I"
                    : d.netheritePieces == 4 ? "§8Netherite" : d.diamondPieces == 4 ? "§bDiament" : "§7-";
                c.drawText(tr, info + " §7e" + d.minEnch + " p" + d.minProt, x + 6, ry + 8, 0xFFFFFFFF, false);
            }

            int px = lx + lw - 8 - 62;
            pill(c, tr, px, ry + 4, 56, 16, on, "TRACER", inside(mx, my, px, ry + 4, 56, 16), accent);
        }
        scrollbar(c, lx + lw - 4, ly, lh, rows.size(), vis, scrollP);
    }

    // ---- event ----

    private void renderEvent(DrawContext c, int mx, int my, TextRenderer tr, int accent) {
        int bottom = y0 + H - 14;
        card(c, x0 + 14, cy, 210, bottom - cy);
        card(c, x0 + 234, cy, 232, bottom - cy);

        // lewa: opcjonalny filtr moba
        int lx = x0 + 24, ly = cy + 36, lw = 190, lh = bottom - 6 - ly;
        rectM[0] = lx; rectM[1] = ly; rectM[2] = lw; rectM[3] = lh;
        int vis = Math.max(1, lh / MROW);
        scrollM = Math.max(0, Math.min(scrollM, mobs.size() - vis));
        for (int i = 0; i < vis && i + scrollM < mobs.size(); i++) {
            EntityType<?> t = mobs.get(i + scrollM);
            int ry = ly + i * MROW;
            boolean sel = Registries.ENTITY_TYPE.getId(t).toString().equals(cfg.eventType);
            boolean hov = inside(mx, my, lx, ry, lw - 6, MROW - 2);
            if (sel) Ui.rrect(c, lx, ry, lw - 6, MROW - 2, 5, Ui.alpha(accent, 0x70));
            else if (hov) Ui.rrect(c, lx, ry, lw - 6, MROW - 2, 5, Ui.CARD2);
            c.drawItem(icon(t), lx + 2, ry);
            c.drawText(tr, t.getName().getString(), lx + 24, ry + 5, sel ? 0xFFFFFFFF : 0xFFC8CCD8, false);
        }
        if (mobs.isEmpty()) c.drawText(tr, "Brak wyników", lx + 4, ly + 6, Ui.MUTED, false);
        scrollbar(c, lx + lw - 3, ly, lh, mobs.size(), vis, scrollM);

        // prawa: napis (glowny filtr) + opcjonalny mob + wyniki
        c.drawText(tr, "Napis (moby, hologramy, tabliczki)", x0 + 244, cy + 10, Ui.MUTED, false);
        EntityType<?> sel = null;
        for (EntityType<?> t : ALL_MOBS) {
            if (Registries.ENTITY_TYPE.getId(t).toString().equals(cfg.eventType)) { sel = t; break; }
        }
        if (sel != null) {
            c.drawItem(icon(sel), x0 + 244, cy + 47);
            c.drawText(tr, sel.getName().getString(), x0 + 264, cy + 52, 0xFFFFFFFF, true);
        } else {
            c.drawText(tr, "Filtr moba: dowolny", x0 + 244, cy + 52, Ui.MUTED, false);
        }

        String active = Radar.eventActive(cfg) ? "" : " §8(wpisz napis)";
        c.drawText(tr, "Znalezione: " + found.size() + active, x0 + 244, cy + 100, Ui.MUTED, false);
        int fx = x0 + 244, fy = cy + 112, fw = 212, fh = bottom - 6 - fy;
        rectF[0] = fx; rectF[1] = fy; rectF[2] = fw; rectF[3] = fh;
        int fvis = Math.max(1, fh / MROW);
        for (int i = 0; i < fvis && i + scrollF < found.size(); i++) {
            Radar.Hit h = found.get(i + scrollF);
            int ry = fy + i * MROW;
            boolean on = h.entity != null ? (Radar.TRACED_UUID.contains(h.entity.getUuid()) || Radar.targets.contains(h.entity.getUuid()))
                                          : (Radar.TRACED_BLOCKS.contains(h.pos) || Radar.blockTargets.contains(h.pos));
            boolean hov = inside(mx, my, fx, ry, fw - 6, MROW - 2);
            Ui.rrect(c, fx, ry, fw - 6, MROW - 2, 5, hov ? Ui.CARD3 : Ui.CARD2);
            c.drawItem(h.entity != null ? icon(h.entity.getType()) : new ItemStack(Items.OAK_SIGN), fx + 2, ry);
            c.drawText(tr, tr.trimToWidth(h.label, 96), fx + 22, ry + 5, 0xFFFFFFFF, false);
            c.drawText(tr, String.format("%.0fm", h.dist), fx + 122, ry + 5, Ui.MUTED, false);
            int px = fx + fw - 6 - 40;
            pill(c, tr, px, ry + 1, 36, 16, on, "TRACE", inside(mx, my, px, ry + 1, 36, 16), accent);
        }
        scrollbar(c, fx + fw - 3, fy, fh, found.size(), fvis, scrollF);
    }

    // ---- PvP ----

    private void renderPvp(DrawContext c, TextRenderer tr) {
        card(c, x0 + 14, cy, W - 28, 62);
        c.drawText(tr, "Wyłącza kołysanie kamery podczas chodzenia, trzęsienie po obrażeniach", x0 + 24, cy + 38, Ui.MUTED, false);
        c.drawText(tr, "i zmiany FOV przy sprincie. Po wyłączeniu wraca Twoje ustawienie.", x0 + 24, cy + 48, Ui.MUTED, false);

        card(c, x0 + 14, cy + 70, W - 28, 74);
        c.drawText(tr, "Kolor hitboxów #", x0 + 24, cy + 111, Ui.MUTED, false);
        drawSwatches(c, cy + 108, cfg.hitRgb());
        c.drawText(tr, "Hitboxy w zasięgu radaru, widoczne też przez ściany.", x0 + 24, cy + 130, Ui.MUTED, false);
    }

    // ---- ustawienia ----

    private void renderSettings(DrawContext c, TextRenderer tr) {
        card(c, x0 + 14, cy, W - 28, 38);
        c.drawText(tr, "Zasięg radaru (m)", x0 + 24, cy + 15, Ui.MUTED, false);
        c.drawText(tr, "Zasięg tracerów (m)", x0 + 250, cy + 15, Ui.MUTED, false);

        card(c, x0 + 14, cy + 46, W - 28, 88);
        c.drawText(tr, "Kolor tracerów #", x0 + 24, cy + 61, Ui.MUTED, false);
        c.drawText(tr, "Kolor GUI #", x0 + 24, cy + 105, Ui.MUTED, false);
        drawSwatches(c, cy + 58, cfg.colorRgb());
        drawSwatches(c, cy + 102, cfg.accent());

        card(c, x0 + 14, cy + 142, W - 28, 40);
        c.drawText(tr, "R – otwórz GUI (klawisz zmienisz w Sterowaniu)  •  /radar debug – diagnostyka nametagów",
            x0 + 24, cy + 192, Ui.MUTED, false);
    }

    private void drawSwatches(DrawContext c, int y, int selectedRgb) {
        for (int i = 0; i < SWATCH.length; i++) {
            int sx = x0 + 256 + i * 20;
            if ((SWATCH[i] & 0xFFFFFF) == (selectedRgb & 0xFFFFFF)) Ui.rrect(c, sx - 2, y - 2, 18, 18, 5, 0xFFFFFFFF);
            Ui.rrect(c, sx, y, 14, 14, 4, Ui.solid(SWATCH[i]));
        }
    }

    // ------------------------------------------------------------ input

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;

        for (int i = 0; i < TABS.length; i++) {
            if (inside(mx, my, x0 + 14 + i * TAB_STEP, y0 + 26, TAB_W, 20)) {
                if (cfg.tab != i) {
                    cfg.tab = i; cfg.save();
                    Ui.click(1.2f);
                    clearAndInit();
                }
                return true;
            }
        }
        for (Btn b : btns) {
            if (inside(mx, my, b.x, b.y, b.w, b.h)) {
                b.run.run();
                boolean after = b.on != null && b.on.getAsBoolean();
                Ui.click(b.on == null ? 1.0f : (after ? 1.35f : 0.8f));
                return true;
            }
        }
        // gracze: pill TRACER
        if (cfg.tab <= T_AN && inside(mx, my, rectP[0], rectP[1], rectP[2], rectP[3])) {
            int i = (int) ((my - rectP[1]) / ROW) + scrollP;
            int px = rectP[0] + rectP[2] - 8 - 62;
            if (i >= 0 && i < rows.size() && mx >= px && mx <= px + 56) {
                String n = rows.get(i).name;
                boolean added = !Radar.TRACED.remove(n);
                if (added) Radar.TRACED.add(n);
                Ui.click(added ? 1.35f : 0.8f);
                return true;
            }
        }
        if (cfg.tab == T_EV) {
            // lista mobow (filtr)
            if (inside(mx, my, rectM[0], rectM[1], rectM[2] - 6, rectM[3])) {
                int i = (int) ((my - rectM[1]) / MROW) + scrollM;
                if (i >= 0 && i < mobs.size()) {
                    String id = Registries.ENTITY_TYPE.getId(mobs.get(i)).toString();
                    cfg.eventType = cfg.eventType.equals(id) ? "" : id;
                    cfg.save();
                    Ui.click(1.15f);
                    return true;
                }
            }
            // znalezione: TRACE
            if (inside(mx, my, rectF[0], rectF[1], rectF[2], rectF[3])) {
                int i = (int) ((my - rectF[1]) / MROW) + scrollF;
                int px = rectF[0] + rectF[2] - 6 - 40;
                if (i >= 0 && i < found.size() && mx >= px && mx <= px + 36) {
                    Radar.Hit h = found.get(i);
                    boolean added;
                    if (h.entity != null) {
                        added = !Radar.TRACED_UUID.remove(h.entity.getUuid());
                        if (added) Radar.TRACED_UUID.add(h.entity.getUuid());
                    } else {
                        added = !Radar.TRACED_BLOCKS.remove(h.pos);
                        if (added) Radar.TRACED_BLOCKS.add(h.pos);
                    }
                    Ui.click(added ? 1.35f : 0.8f);
                    return true;
                }
            }
        }
        // palety kolorow
        for (int i = 0; i < SWATCH.length; i++) {
            int sx = x0 + 256 + i * 20;
            String hex = String.format("%06X", SWATCH[i]);
            if (cfg.tab == T_SET) {
                if (inside(mx, my, sx, cy + 58, 14, 14) && tracerHex != null) { tracerHex.setText(hex); Ui.click(1.2f); return true; }
                if (inside(mx, my, sx, cy + 102, 14, 14) && guiHex != null) { guiHex.setText(hex); Ui.click(1.2f); return true; }
            } else if (cfg.tab == T_PVP) {
                if (inside(mx, my, sx, cy + 108, 14, 14) && hitHex != null) { hitHex.setText(hex); Ui.click(1.2f); return true; }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        int dir = -(int) Math.signum(v);
        if (cfg.tab <= T_AN && inside(mx, my, rectP[0], rectP[1], rectP[2], rectP[3])) {
            int max = Math.max(0, rows.size() - Math.max(1, rectP[3] / ROW));
            scrollP = Math.max(0, Math.min(max, scrollP + dir));
            return true;
        }
        if (cfg.tab == T_EV) {
            if (inside(mx, my, rectM[0], rectM[1], rectM[2], rectM[3])) {
                int max = Math.max(0, mobs.size() - Math.max(1, rectM[3] / MROW));
                scrollM = Math.max(0, Math.min(max, scrollM + dir));
                return true;
            }
            if (inside(mx, my, rectF[0], rectF[1], rectF[2], rectF[3])) {
                int max = Math.max(0, found.size() - Math.max(1, rectF[3] / MROW));
                scrollF = Math.max(0, Math.min(max, scrollF + dir));
                return true;
            }
        }
        return false;
    }
}
