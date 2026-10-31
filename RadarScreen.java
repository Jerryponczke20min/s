package pl.radar;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class RadarScreen extends Screen {
    private static final int W = 400, ROW_H = 22;
    private final RadarConfig cfg = RadarConfig.I;
    private List<PlayerData> rows = new ArrayList<>();
    private int scroll = 0, left, top, listTop;

    private static final String[] MATERIAL = {"Dowolny", "Diament", "Netherite"};
    private static final String[] MODE = {"Dowolny", "Anarchiczny I", "Anarchiczny II"};

    public RadarScreen() { super(Text.literal("Radar Graczy")); }

    @Override
    protected void init() {
        left = width / 2 - W / 2;
        top = 16;

        addDrawableChild(ButtonWidget.builder(Text.literal(cfg.tab == 0 ? "§l[MineStar]" : "MineStar"),
            b -> { cfg.tab = 0; cfg.save(); clearAndInit(); }).dimensions(left, top, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(cfg.tab == 1 ? "§l[Anarchia]" : "Anarchia"),
            b -> { cfg.tab = 1; cfg.save(); clearAndInit(); }).dimensions(left + 104, top, 100, 20).build());

        int ya = top + 28;
        if (cfg.tab == 0) {
            field(left + 22, ya, 44, cfg.swMin, s -> cfg.swMin = s, "od");
            field(left + 70, ya, 44, cfg.swMax, s -> cfg.swMax = s, "do");
            field(left + 150, ya, 44, cfg.arMin, s -> cfg.arMin = s, "od");
            field(left + 198, ya, 44, cfg.arMax, s -> cfg.arMax = s, "do");
        } else {
            addDrawableChild(ButtonWidget.builder(Text.literal("Set: " + MATERIAL[cfg.material]), b -> {
                cfg.material = (cfg.material + 1) % 3; cfg.save(); clearAndInit();
            }).dimensions(left, ya - 1, 100, 20).build());
            field(left + 150, ya, 36, cfg.minEnch, s -> cfg.minEnch = s, "ench");
            field(left + 232, ya, 36, cfg.minProt, s -> cfg.minProt = s, "prot");
            addDrawableChild(ButtonWidget.builder(Text.literal(MODE[cfg.anMode]), b -> {
                cfg.anMode = (cfg.anMode + 1) % 3; cfg.save(); clearAndInit();
            }).dimensions(left + 280, ya - 1, 120, 20).build());
        }

        int yb = top + 54;
        field(left + 62, yb, 40, cfg.radarRange, s -> cfg.radarRange = s, "m");
        field(left + 152, yb, 40, cfg.tracerRange, s -> cfg.tracerRange = s, "m");
        field(left + 232, yb, 56, cfg.color, s -> cfg.color = s, "RRGGBB");

        boolean auto = cfg.tab == 0 ? cfg.msAuto : cfg.anAuto;
        addDrawableChild(ButtonWidget.builder(Text.literal("Auto-tracery wg filtra: " + (auto ? "§aON" : "§cOFF")), b -> {
            if (cfg.tab == 0) cfg.msAuto = !cfg.msAuto; else cfg.anAuto = !cfg.anAuto;
            cfg.save(); clearAndInit();
        }).dimensions(left, top + 78, 200, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("ESP: " + (cfg.esp ? "§aON" : "§cOFF")), b -> {
            cfg.esp = !cfg.esp; cfg.save(); clearAndInit();
        }).dimensions(left + 204, top + 78, 96, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Wyczyść ręczne"), b -> Radar.TRACED.clear())
            .dimensions(left + 304, top + 78, 96, 20).build());

        listTop = top + 106;
        tick();
    }

    private void field(int x, int y, int w, String val, Consumer<String> setter, String hint) {
        TextFieldWidget tf = new TextFieldWidget(textRenderer, x, y, w, 18, Text.empty());
        tf.setMaxLength(12);
        tf.setText(val);
        tf.setPlaceholder(Text.literal(hint));
        tf.setChangedListener(s -> { setter.accept(s); cfg.save(); });
        addDrawableChild(tf);
    }

    @Override
    public void tick() {
        rows = Radar.scan(cfg.radar());
        int max = Math.max(0, rows.size() - visibleRows());
        scroll = Math.min(scroll, max);
    }

    private int visibleRows() { return Math.max(1, (height - listTop - 12) / ROW_H); }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext c, int mx, int my, float d) {
        super.renderBackground(c, mx, my, d);
        c.fill(left - 8, top - 8, left + W + 8, height - 6, 0xC0101010);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        int ya = top + 32, yb = top + 58;

        if (cfg.tab == 0) {
            c.drawItem(new ItemStack(Items.DIAMOND_SWORD), left, ya - 3);
            c.drawItem(new ItemStack(Items.SHIELD), left + 128, ya - 3);
        } else {
            c.drawText(textRenderer, "Min ench:", left + 106, ya, 0xAAAAAA, false);
            c.drawText(textRenderer, "Min prot:", left + 190, ya, 0xAAAAAA, false);
        }
        c.drawText(textRenderer, "Radar (m)", left, yb, 0xAAAAAA, false);
        c.drawText(textRenderer, "Tracer (m)", left + 106, yb, 0xAAAAAA, false);
        c.drawText(textRenderer, "Kolor #", left + 194, yb, 0xAAAAAA, false);
        c.fill(left + 292, yb - 2, left + 308, yb + 12, 0xFF000000 | cfg.colorRgb());

        c.drawText(textRenderer, "Graczy: " + rows.size(), left + 330, top + 6, 0xFFFFFF, false);

        int vis = visibleRows();
        for (int i = 0; i < vis && i + scroll < rows.size(); i++) {
            PlayerData d = rows.get(i + scroll);
            int y = listTop + i * ROW_H;
            boolean on = Radar.TRACED.contains(d.name) || Radar.targets.contains(d.player.getUuid());
            boolean match = cfg.tab == 0 ? Radar.matchesMineStar(d, cfg) : Radar.matchesAnarchy(d, cfg);
            c.fill(left, y, left + W, y + ROW_H - 2, match ? 0x6030A030 : 0x40FFFFFF);

            PlayerListEntry e = MinecraftClient.getInstance().getNetworkHandler() == null ? null
                : MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(d.player.getUuid());
            if (e != null) PlayerSkinDrawer.draw(c, e.getSkinTextures(), left + 2, y + 1, 16);

            c.drawText(textRenderer, d.name, left + 22, y + 2, 0xFFFFFF, true);
            c.drawText(textRenderer, String.format("%.0fm", d.dist), left + 22, y + 11, 0x999999, false);

            if (cfg.tab == 0) {
                int x = left + 130;
                c.drawItem(new ItemStack(Items.DIAMOND_SWORD), x, y);
                c.drawText(textRenderer, d.sword < 0 ? "?" : String.valueOf(d.sword), x + 18, y + 5, 0xFF8844, true);
                x += 60;
                c.drawItem(new ItemStack(Items.SHIELD), x, y);
                c.drawText(textRenderer, d.set < 0 ? "?" : String.valueOf(d.set), x + 18, y + 5, 0x55AAFF, true);
                x += 60;
                c.drawItem(new ItemStack(Items.SKELETON_SKULL), x, y);
                c.drawText(textRenderer, d.skulls < 0 ? "?" : String.valueOf(d.skulls), x + 18, y + 5, 0x55FFFF, true);
            } else {
                int x = left + 110;
                c.drawItem(d.player.getMainHandStack(), x, y);
                c.drawItem(d.player.getOffHandStack(), x + 18, y);
                x += 44;
                for (EquipmentSlot s : AnarchyAnalyzer.ARMOR) {
                    c.drawItem(d.player.getEquippedStack(s), x, y);
                    x += 18;
                }
                String info = d.tier == 2 ? "§dAnar. II" : d.tier == 1 ? "§5Anar. I"
                    : d.netheritePieces == 4 ? "§8Netherite" : d.diamondPieces == 4 ? "§bDiament" : "§7-";
                c.drawText(textRenderer, info + " §7e" + d.minEnch + " p" + d.minProt, x + 4, y + 5, 0xFFFFFF, false);
            }

            c.drawText(textRenderer, on ? "§a[TRACER]" : "§7[tracer]", left + W - 52, y + 6, 0xFFFFFF, false);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0 || mx < left + W - 56 || mx > left + W || my < listTop) return false;
        int i = (int) ((my - listTop) / ROW_H) + scroll;
        if (i >= 0 && i < rows.size() && (my - listTop) / ROW_H < visibleRows()) {
            String n = rows.get(i).name;
            if (!Radar.TRACED.remove(n)) Radar.TRACED.add(n);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        int max = Math.max(0, rows.size() - visibleRows());
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(v)));
        return true;
    }
}
