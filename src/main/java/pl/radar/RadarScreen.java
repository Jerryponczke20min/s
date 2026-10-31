package pl.radar;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
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
    private static final String[] TABS = {"MineStar", "Anarchia", "Event", "Ustawienia"};
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
    private RField tracerHex, guiHex;

    private List<PlayerData> rows = new ArrayList<>();
    private List<Entity> found = new ArrayList<>();
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

        btn(x0 + W - 30, y0 + 8, 20, 16, () -> "×", null, this::close);

        switch (cfg.tab) {
            case 0 -> initMineStar();
            case 1 -> initAnarchy();
            case 2 -> initEvent();
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

        btn(x0 + 394, cy + 22, 62, 18, () -> "Dowolny", null, () -> { cfg.eventType = ""; cfg.save(); });
        field(x0 + 244, cy + 60, 212, cfg.eventText, "np. Halloween", s -> cfg.eventText = s);
        btn(x0 + 244, cy + 84, 212, 20, () -> "Auto-tracery: " + (cfg.eventAuto ? "§aON" : "§cOFF"),
            () -> cfg.eventAuto, () -> { cfg.eventAuto = !cfg.eventAuto; cfg.save(); });
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
        f.setMaxLength(32);
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
        if (cfg.tab <= 1) {
            rows = Radar.scan(cfg.radar());
            scrollP = Math.max(0, Math.min(scrollP, rows.size() - Math.max(1, rectP[3] / ROW)));
        } else if (cfg.tab == 2) {
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

        // zakladki
        float target = x0 + 14 + cfg.tab * 104 + 30;
        tabX = tabX < 0 ? target : tabX + (target - tabX) * 0.25f;
        for (int i = 0; i < TABS.length; i++) {
            int tx = x0 + 14 + i * 104;
            boolean hov = inside(mx, my, tx, y0 + 26, 100, 20);
            if (hov && i != cfg.tab) Ui.rrect(c, tx, y0 + 26, 100, 18, 5, 0x20FFFFFF);
            int col = i == cfg.tab ? 0xFFFFFFFF : hov ? 0xFFC8CCD8 : Ui.MUTED;
            c.drawText(tr, TABS[i], tx + (100 - tr.getWidth(TABS[i])) / 2, y0 + 31, col, false);
        }
        Ui.rrect(c, Math.round(tabX), y0 + 45, 40, 2, 1, Ui.solid(accent));

        switch (cfg.tab) {
            case 0, 1 -> renderPlayers(c, mx, my, tr, accent);
            case 2 -> renderEvent(c, mx, my, tr, accent);
            default -> renderSettings(c, tr, accent);
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
        if (cfg.tab == 0) {
            c.drawItem(new ItemStack(Items.DIAMOND_SWORD), x0 + 24, cy + 9);
            c.drawItem(new ItemStack(Items.SHIELD), x0 + 170, cy + 9);
        } else {
            c.drawText(tr, "Ench >=", x0 + 144, cy + 14, Ui.MUTED, false);
            c.drawText(tr, "Prot >=", x0 + 226, cy + 14, Ui.MUTED, false);
        }
        c.drawText(tr, "Puste pola = brak filtra. Pasujący gracze są podświetleni.", x0 + 164, cy + 40, Ui.MUTED, false);

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
            boolean match = cfg.tab == 0 ? Radar.matchesMineStar(d, cfg) : Radar.matchesAnarchy(d, cfg);
            boolean on = Radar.TRACED.contains(d.name) || Radar.targets.contains(d.player.getUuid());
            boolean hov = inside(mx, my, lx, ry, lw - 8, ROW - 2);
            int bg = match ? Ui.alpha(accent, hov ? 0x70 : 0x50) : (hov ? Ui.CARD3 : Ui.CARD2);
            Ui.rrect(c, lx, ry, lw - 8, ROW - 2, 6, bg);

            PlayerListEntry pe = MinecraftClient.getInstance().getNetworkHandler() == null ? null
                : MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(d.player.getUuid());
            if (pe != null) PlayerSkinDrawer.draw(c, pe.getSkinTextures(), lx + 6, ry + 4, 16);

            c.drawText(tr, d.name, lx + 28, ry + 4, 0xFFFFFFFF, true);
            c.drawText(tr, String.format("%.0f m", d.dist), lx + 28, ry + 14, Ui.MUTED, false);

            if (cfg.tab == 0) {
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

        // lewa: lista mobow
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

        // prawa: wybrany mob + napis + znalezione
        c.drawText(tr, "Wybrany mob", x0 + 244, cy + 10, Ui.MUTED, false);
        EntityType<?> sel = null;
        for (EntityType<?> t : ALL_MOBS) {
            if (Registries.ENTITY_TYPE.getId(t).toString().equals(cfg.eventType)) { sel = t; break; }
        }
        if (sel != null) {
            c.drawItem(icon(sel), x0 + 244, cy + 22);
            c.drawText(tr, sel.getName().getString(), x0 + 264, cy + 27, 0xFFFFFFFF, true);
        } else {
            c.drawText(tr, "Dowolny (kliknij moba z listy)", x0 + 244, cy + 27, Ui.MUTED, false);
        }
        c.drawText(tr, "Napis nad głową (zawiera)", x0 + 244, cy + 48, Ui.MUTED, false);

        c.drawText(tr, "Znalezione: " + found.size() + (Radar.eventActive(cfg) ? "" : " §8(ustaw moba lub napis)"),
            x0 + 244, cy + 112, Ui.MUTED, false);
        int fx = x0 + 244, fy = cy + 124, fw = 212, fh = bottom - 6 - fy;
        rectF[0] = fx; rectF[1] = fy; rectF[2] = fw; rectF[3] = fh;
        int fvis = Math.max(1, fh / MROW);
        for (int i = 0; i < fvis && i + scrollF < found.size(); i++) {
            Entity en = found.get(i + scrollF);
            int ry = fy + i * MROW;
            boolean on = Radar.TRACED_UUID.contains(en.getUuid()) || Radar.targets.contains(en.getUuid());
            boolean hov = inside(mx, my, fx, ry, fw - 6, MROW - 2);
            Ui.rrect(c, fx, ry, fw - 6, MROW - 2, 5, hov ? Ui.CARD3 : Ui.CARD2);
            c.drawItem(icon(en.getType()), fx + 2, ry);
            String label = Radar.entityLabel(en);
            c.drawText(tr, tr.trimToWidth(label, 100), fx + 22, ry + 5, 0xFFFFFFFF, false);
            c.drawText(tr, String.format("%.0fm", mc().player.distanceTo(en)), fx + 124, ry + 5, Ui.MUTED, false);
            int px = fx + fw - 6 - 40;
            pill(c, tr, px, ry + 1, 36, 16, on, "TRACE", inside(mx, my, px, ry + 1, 36, 16), accent);
        }
        scrollbar(c, fx + fw - 3, fy, fh, found.size(), fvis, scrollF);
    }

    private static MinecraftClient mc() { return MinecraftClient.getInstance(); }

    // ---- ustawienia ----

    private void renderSettings(DrawContext c, TextRenderer tr, int accent) {
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

        // zakladki
        for (int i = 0; i < TABS.length; i++) {
            if (inside(mx, my, x0 + 14 + i * 104, y0 + 26, 100, 20)) {
                if (cfg.tab != i) {
                    cfg.tab = i; cfg.save();
                    Ui.click(1.2f);
                    clearAndInit();
                }
                return true;
            }
        }
        // przyciski
        for (Btn b : btns) {
            if (inside(mx, my, b.x, b.y, b.w, b.h)) {
                boolean before = b.on != null && b.on.getAsBoolean();
                b.run.run();
                boolean after = b.on != null && b.on.getAsBoolean();
                Ui.click(b.on == null ? 1.0f : (after ? 1.35f : 0.8f));
                return true;
            }
        }
        // gracze: pill TRACER
        if (cfg.tab <= 1 && inside(mx, my, rectP[0], rectP[1], rectP[2], rectP[3])) {
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
        // event: lista mobow
        if (cfg.tab == 2 && inside(mx, my, rectM[0], rectM[1], rectM[2] - 6, rectM[3])) {
            int i = (int) ((my - rectM[1]) / MROW) + scrollM;
            if (i >= 0 && i < mobs.size()) {
                String id = Registries.ENTITY_TYPE.getId(mobs.get(i)).toString();
                cfg.eventType = cfg.eventType.equals(id) ? "" : id;
                cfg.save();
                Ui.click(1.15f);
                return true;
            }
        }
        // event: znalezione
        if (cfg.tab == 2 && inside(mx, my, rectF[0], rectF[1], rectF[2], rectF[3])) {
            int i = (int) ((my - rectF[1]) / MROW) + scrollF;
            int px = rectF[0] + rectF[2] - 6 - 40;
            if (i >= 0 && i < found.size() && mx >= px && mx <= px + 36) {
                UUID u = found.get(i).getUuid();
                boolean added = !Radar.TRACED_UUID.remove(u);
                if (added) Radar.TRACED_UUID.add(u);
                Ui.click(added ? 1.35f : 0.8f);
                return true;
            }
        }
        // ustawienia: kolory
        if (cfg.tab == 3) {
            for (int i = 0; i < SWATCH.length; i++) {
                int sx = x0 + 256 + i * 20;
                String hex = String.format("%06X", SWATCH[i]);
                if (inside(mx, my, sx, cy + 58, 14, 14) && tracerHex != null) {
                    tracerHex.setText(hex); Ui.click(1.2f); return true;
                }
                if (inside(mx, my, sx, cy + 102, 14, 14) && guiHex != null) {
                    guiHex.setText(hex); Ui.click(1.2f); return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        int dir = -(int) Math.signum(v);
        if (cfg.tab <= 1 && inside(mx, my, rectP[0], rectP[1], rectP[2], rectP[3])) {
            int max = Math.max(0, rows.size() - Math.max(1, rectP[3] / ROW));
            scrollP = Math.max(0, Math.min(max, scrollP + dir));
            return true;
        }
        if (cfg.tab == 2) {
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
