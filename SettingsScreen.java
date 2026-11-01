package com.example.fakebonemeal;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Wlasne, kolorowe GUI. Wspolrzedne sa lokalne dla panelu (W x H). Kolory biora sie z motywu. */
public class SettingsScreen extends Screen {

    private static final int W = 290;
    private static final int H = 216;

    // kolory stale
    private static final int TEXT = 0xFFF0F5F0;
    private static final int MUTED = 0xFF9AA39A;
    private static final int DISABLED = 0xFF5A645A;
    private static final int YELLOW = 0xFFFFE066;
    private static final int DANGER = 0xFFC9453F;
    private static final int DANGER_HOVER = 0xFFE0605B;

    @FunctionalInterface
    private interface Painter {
        void paint(DrawContext c, Btn b, boolean hover);
    }

    private static final class Btn {
        int x, y, w, h;
        boolean enabled = true;
        Runnable action;      // null = element tylko do rysowania
        Painter painter;

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Btn> btns = new ArrayList<>();
    private Theme th = Theme.current();
    private int tab = 0;      // 0 itemy, 1 klikniecia, 2 teren, 3 wyglad
    private int page = 0;
    private int pages = 1;
    private int itemCount = 0;

    public SettingsScreen() {
        super(Text.literal("Fake Bone Meal"));
    }

    // ---------------------------------------------------------------- uklad

    private float scale() {
        float fit = Math.min((this.width - 8f) / W, (this.height - 8f) / H);
        return Math.min(fit, ClientSettings.guiScale / 100f);
    }

    private int originX() {
        return Math.round((this.width - W * scale()) / 2f);
    }

    private int originY() {
        return Math.round((this.height - H * scale()) / 2f);
    }

    @Override
    protected void init() {
        rebuild();
    }

    private Btn add(int x, int y, int w, int h, Painter painter, Runnable action) {
        Btn b = new Btn();
        b.x = x;
        b.y = y;
        b.w = w;
        b.h = h;
        b.painter = painter;
        b.action = action;
        btns.add(b);
        return b;
    }

    private Btn button(int x, int y, int w, int h, String label, int style, boolean enabled, Runnable action) {
        Btn b = add(x, y, w, h, (c, bt, hv) -> paintButton(c, bt, hv, label, style), action);
        b.enabled = enabled;
        return b;
    }

    private void rebuild() {
        btns.clear();
        th = Theme.current();

        add(W - 100, 4, 92, 16, this::paintMaster, () -> ClientSettings.enabled = !ClientSettings.enabled);

        String[] names = {"Itemy", "Kliknięcia", "Teren", "Wygląd"};
        for (int i = 0; i < names.length; i++) {
            final int t = i;
            add(10 + i * 68, 28, 66, 18,
                    (c, b, hv) -> paintTab(c, b, hv, names[t], tab == t),
                    () -> {
                        tab = t;
                        rebuild();
                    });
        }

        if (tab == 0) {
            buildItemsTab();
        } else if (tab == 1) {
            buildClicksTab();
        } else if (tab == 2) {
            buildTerrainTab();
        } else {
            buildAppearanceTab();
        }

        button(10, H - 30, W - 20, 22, "Gotowe", 1, true, this::close);
    }

    // ---------------------------------------------------------------- zakladka: itemy

    private void buildItemsTab() {
        ItemStack held = this.client != null && this.client.player != null
                ? this.client.player.getMainHandStack() : ItemStack.EMPTY;

        String label;
        boolean canAdd = false;
        if (held.isEmpty()) {
            label = "Weź item do ręki, żeby go dodać";
        } else if (held.getItem() == Items.BONE_MEAL) {
            label = "Prawdziwego bone meala nie trzeba";
        } else if (ClientSettings.items.contains(held.getItem())) {
            label = "Ten item jest już na liście";
        } else {
            label = "+ Dodaj z ręki: " + held.getName().getString();
            canAdd = true;
        }
        final Item heldItem = held.getItem();
        button(10, 52, W - 20, 20, this.textRenderer.trimToWidth(label, W - 34), 1, canAdd, () -> {
            ClientSettings.items.add(heldItem);
            rebuild();
        });

        List<Item> all = new ArrayList<>(ClientSettings.items);
        itemCount = all.size();
        pages = Math.max(1, (all.size() + 2) / 3);
        if (page >= pages) {
            page = pages - 1;
        }

        for (int i = 0; i < 3; i++) {
            int idx = page * 3 + i;
            if (idx >= all.size()) {
                break;
            }
            final Item it = all.get(idx);
            int y = 76 + i * 26;
            add(10, y, W - 20, 24, (c, b, hv) -> {
                roundRect(c, b.x, b.y, b.w, b.h, th.card);
                roundRect(c, b.x + 4, b.y + 3, 18, 18, th.slot);
                drawRealItem(c, it, b.x + 5, b.y + 4);
                text(c, this.textRenderer.trimToWidth(it.getName().getString(), 150), b.x + 28, b.y + 8, TEXT);
                text(c, "→ Bone Meal", b.x + b.w - 92, b.y + 8, MUTED);
            }, null);
            button(W - 32, y + 3, 18, 18, "×", 2, true, () -> {
                ClientSettings.items.remove(it);
                rebuild();
            });
        }

        button(10, 158, 24, 18, "<", 0, page > 0, () -> {
            page--;
            rebuild();
        });
        button(W - 34, 158, 24, 18, ">", 0, page < pages - 1, () -> {
            page++;
            rebuild();
        });
        add(40, 158, W - 80, 18, (c, b, hv) -> {
            String s = (page + 1) + " / " + pages;
            c.drawCenteredTextWithShadow(this.textRenderer, Text.literal(s), b.x + b.w / 2, b.y + 5, MUTED);
        }, null);
    }

    // ---------------------------------------------------------------- zakladka: klikniecia

    private void buildClicksTab() {
        ClientSettings.fixMap();
        button(10, 52, 56, 14, "+ krok", 1, ClientSettings.pattern.size() < ClientSettings.MAX_STEPS, () -> {
            ClientSettings.pattern.add(2);
            ClientSettings.fixMap();
            rebuild();
        });
        button(70, 52, 56, 14, "- krok", 0, ClientSettings.pattern.size() > 1, () -> {
            ClientSettings.pattern.remove(ClientSettings.pattern.size() - 1);
            ClientSettings.fixMap();
            rebuild();
        });
        button(130, 52, W - 140, 14, "Zastosuj [" + FakeBoneMealClient.shuffleKeyName() + "]", 0, true,
                FakeBoneMealClient::shufflePattern);

        for (int i = 0; i < ClientSettings.pattern.size(); i++) {
            final int idx = i;
            int y = 72 + i * 20;
            add(10, y, W - 20, 18, (c, b, hv) -> {
                roundRect(c, b.x, b.y, b.w, b.h, th.card);
                text(c, "Kliknięcie " + (idx + 1), b.x + 6, b.y + 5, TEXT);
                drawRealItem(c, FakeBoneMeal.FLOWERS[idx % FakeBoneMeal.FLOWERS.length].asItem(), b.x + 84, b.y + 1);
                text(c, "× " + ClientSettings.pattern.get(idx), b.x + 106, b.y + 5, YELLOW);
            }, null);
            button(W - 58, y + 1, 20, 16, "-", 0, true, () ->
                    ClientSettings.pattern.set(idx, Math.max(0, ClientSettings.pattern.get(idx) - 1)));
            button(W - 34, y + 1, 20, 16, "+", 0, true, () ->
                    ClientSettings.pattern.set(idx, Math.min(16, ClientSettings.pattern.get(idx) + 1)));
            // ustawione tasowanie: po H to kliknięcie dostaje liczbe z wybranego kliknięcia
            button(150, y + 1, 66, 16, "H ← #" + (ClientSettings.shuffleMap.get(idx) + 1), 0, true, () -> {
                ClientSettings.shuffleMap.set(idx,
                        (ClientSettings.shuffleMap.get(idx) + 1) % ClientSettings.pattern.size());
                rebuild();
            });
        }

        add(10, 174, W - 20, 10, (c, b, hv) -> {
            String before = joinNumbers(ClientSettings.pattern);
            String after = joinNumbers(ClientSettings.shuffled());
            c.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Po " + FakeBoneMealClient.shuffleKeyName() + ":  " + before + "  →  " + after),
                    b.x + b.w / 2, b.y, YELLOW);
        }, null);
    }

    // ---------------------------------------------------------------- zakladka: teren

    private void buildTerrainTab() {
        stepper(54, "Promień sadzenia", () -> ClientSettings.radius, v -> ClientSettings.radius = v, 1, 5, 1, "");
        stepper(72, "Trawa na kliknięcie", () -> ClientSettings.grass, v -> ClientSettings.grass = v, 0, 16, 1, "");
        toggle(90, "Wysoka trawa", () -> ClientSettings.tall, () -> ClientSettings.tall = !ClientSettings.tall);
        toggle(108, "Dźwięk i cząsteczki", () -> ClientSettings.effects,
                () -> ClientSettings.effects = !ClientSettings.effects);

        add(10, 128, W - 20, 10, (c, b, hv) -> text(c, "Rodzaje kwiatów", b.x, b.y, MUTED), null);

        for (int i = 0; i < FakeBoneMeal.FLOWERS.length; i++) {
            final int bit = i;
            int x = 10 + (i % 6) * 46;
            int y = 140 + (i / 6) * 20;
            add(x, y, 40, 18, (c, b, hv) -> {
                boolean on = (ClientSettings.flowerMask & (1 << bit)) != 0;
                roundRect(c, b.x, b.y, b.w, b.h, on ? th.btnHover : (hv ? th.cardHover : th.card));
                if (on) {
                    c.drawBorder(b.x, b.y, b.w, b.h, th.accent);
                }
                drawRealItem(c, FakeBoneMeal.FLOWERS[bit].asItem(), b.x + 12, b.y + 1);
                if (!on) {
                    c.fill(b.x + 1, b.y + 1, b.x + b.w - 1, b.y + b.h - 1, 0xAA101010);
                }
            }, () -> {
                int next = ClientSettings.flowerMask ^ (1 << bit);
                if (next != 0) {
                    ClientSettings.flowerMask = next;
                }
            });
        }
    }

    // ---------------------------------------------------------------- zakladka: wyglad

    private void buildAppearanceTab() {
        add(10, 54, W - 20, 10, (c, b, hv) -> text(c, "Kolor motywu", b.x, b.y, MUTED), null);

        for (int i = 0; i < Theme.NAMES.length; i++) {
            final int idx = i;
            add(10 + i * 30, 66, 26, 18, (c, b, hv) -> {
                int rgb = idx == Theme.CUSTOM ? ClientSettings.customRgb : Theme.COLORS[idx];
                roundRect(c, b.x, b.y, b.w, b.h, 0xFF000000 | rgb);
                if (idx == Theme.CUSTOM) {
                    c.drawCenteredTextWithShadow(this.textRenderer, Text.literal("RGB"),
                            b.x + b.w / 2, b.y + 5, 0xFFFFFFFF);
                }
                if (ClientSettings.themeIndex == idx) {
                    c.drawBorder(b.x - 1, b.y - 1, b.w + 2, b.h + 2, 0xFFFFFFFF);
                } else if (hv) {
                    c.drawBorder(b.x, b.y, b.w, b.h, 0xAAFFFFFF);
                }
            }, () -> {
                ClientSettings.themeIndex = idx;
                rebuild();
            });
        }

        add(10, 88, W - 20, 10, (c, b, hv) ->
                c.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("Motyw: " + Theme.NAMES[Math.max(0, Math.min(Theme.NAMES.length - 1,
                                ClientSettings.themeIndex))]),
                        b.x + b.w / 2, b.y, th.accent), null);

        stepper(102, "Przezroczystość panelu", () -> ClientSettings.panelOpacity,
                v -> ClientSettings.panelOpacity = v, 40, 100, 10, "%");
        stepper(120, "Rozmiar menu", () -> ClientSettings.guiScale,
                v -> ClientSettings.guiScale = v, 70, 130, 10, "%");

        if (ClientSettings.themeIndex == Theme.CUSTOM) {
            rgbGroup(10, 140, "R", 16);
            rgbGroup(100, 140, "G", 8);
            rgbGroup(190, 140, "B", 0);
            add(10, 160, W - 20, 10, (c, b, hv) ->
                    roundRect(c, b.x, b.y, b.w, b.h, 0xFF000000 | (ClientSettings.customRgb & 0xFFFFFF)), null);
        }
    }

    private void rgbGroup(int x, int y, String label, int shift) {
        add(x, y, 78, 16, (c, b, hv) -> {
            roundRect(c, b.x, b.y, b.w, b.h, th.card);
            text(c, label, b.x + 4, b.y + 4, TEXT);
            int v = (ClientSettings.customRgb >> shift) & 255;
            c.drawCenteredTextWithShadow(this.textRenderer, Text.literal(String.valueOf(v)),
                    b.x + 43, b.y + 4, YELLOW);
        }, null);
        button(x + 14, y + 1, 16, 14, "-", 0, true, () -> changeChannel(shift, -17));
        button(x + 56, y + 1, 16, 14, "+", 0, true, () -> changeChannel(shift, 17));
    }

    private void changeChannel(int shift, int delta) {
        int v = Math.max(0, Math.min(255, ((ClientSettings.customRgb >> shift) & 255) + delta));
        ClientSettings.customRgb = (ClientSettings.customRgb & ~(255 << shift)) | (v << shift);
        th = Theme.current();
    }

    // ---------------------------------------------------------------- wspolne elementy

    private void stepper(int y, String label, IntSupplier get, IntConsumer set, int min, int max, int step,
                         String suffix) {
        add(10, y, W - 20, 16, (c, b, hv) -> {
            roundRect(c, b.x, b.y, b.w, b.h, th.card);
            text(c, label, b.x + 6, b.y + 4, TEXT);
            c.drawCenteredTextWithShadow(this.textRenderer, Text.literal(get.getAsInt() + suffix),
                    W - 48, b.y + 4, YELLOW);
        }, null);
        button(W - 82, y + 1, 20, 14, "-", 0, true, () -> set.accept(Math.max(min, get.getAsInt() - step)));
        button(W - 34, y + 1, 20, 14, "+", 0, true, () -> set.accept(Math.min(max, get.getAsInt() + step)));
    }

    private void toggle(int y, String label, BooleanSupplier get, Runnable flip) {
        add(10, y, W - 20, 16, (c, b, hv) -> {
            roundRect(c, b.x, b.y, b.w, b.h, hv ? th.cardHover : th.card);
            text(c, label, b.x + 6, b.y + 4, TEXT);
            paintSwitch(c, b.x + b.w - 34, b.y + 2, get.getAsBoolean());
        }, flip);
    }

    // ---------------------------------------------------------------- rysowanie

    private void paintButton(DrawContext c, Btn b, boolean hover, String label, int style) {
        int bg;
        int fg;
        if (!b.enabled) {
            bg = th.btnOff;
            fg = DISABLED;
        } else if (style == 1) {
            bg = hover ? th.accentHover : th.accent;
            fg = th.onAccent;
        } else if (style == 2) {
            bg = hover ? DANGER_HOVER : DANGER;
            fg = 0xFFFFFFFF;
        } else {
            bg = hover ? th.btnHover : th.btn;
            fg = TEXT;
        }
        roundRect(c, b.x, b.y, b.w, b.h, bg);
        c.drawCenteredTextWithShadow(this.textRenderer, Text.literal(label),
                b.x + b.w / 2, b.y + (b.h - 8) / 2, fg);
    }

    private void paintTab(DrawContext c, Btn b, boolean hover, String name, boolean selected) {
        roundRect(c, b.x, b.y, b.w, b.h, selected ? th.card : (hover ? th.cardHover : th.btnOff));
        if (selected) {
            c.fill(b.x + 2, b.y + b.h - 2, b.x + b.w - 2, b.y + b.h, th.accent);
        }
        c.drawCenteredTextWithShadow(this.textRenderer, Text.literal(name),
                b.x + b.w / 2, b.y + 5, selected ? th.accent : (hover ? TEXT : MUTED));
    }

    private void paintMaster(DrawContext c, Btn b, boolean hover) {
        boolean on = ClientSettings.enabled;
        String s = on ? "WŁĄCZONY" : "WYŁĄCZONY";
        int tx = b.x + b.w - 34 - this.textRenderer.getWidth(s);
        text(c, s, tx, b.y + 4, on ? 0xFFFFFFFF : 0xFFFFB3B0);
        paintSwitch(c, b.x + b.w - 30, b.y + 2, on);
    }

    private void paintSwitch(DrawContext c, int x, int y, boolean on) {
        roundRect(c, x, y, 28, 12, on ? th.accent : 0xFF4A524A);
        int kx = on ? x + 16 : x + 2;
        c.fill(kx, y + 2, kx + 10, y + 10, 0xFFFFFFFF);
    }

    private static void roundRect(DrawContext c, int x, int y, int w, int h, int color) {
        c.fill(x + 1, y, x + w - 1, y + h, color);
        c.fill(x, y + 1, x + w, y + h - 1, color);
    }

    private static String joinNumbers(List<Integer> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private void text(DrawContext c, String s, int x, int y, int color) {
        c.drawTextWithShadow(this.textRenderer, s, x, y, color);
    }

    private void drawRealItem(DrawContext c, Item item, int x, int y) {
        VisualState.bypass = true;
        try {
            c.drawItem(new ItemStack(item), x, y);
        } finally {
            VisualState.bypass = false;
        }
    }

    // ---------------------------------------------------------------- render + wejscie

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        th = Theme.current();

        float s = scale();
        int ox = originX();
        int oy = originY();
        double lx = (mouseX - ox) / s;
        double ly = (mouseY - oy) / s;

        MatrixStack m = context.getMatrices();
        m.push();
        m.translate((float) ox, (float) oy, 0f);
        m.scale(s, s, 1f);

        // cien, tlo, ramka
        context.fill(3, 3, W + 3, H + 3, 0x66000000);
        roundRect(context, 0, 0, W, H, th.panel);
        context.drawBorder(0, 0, W, H, th.border);

        // naglowek
        context.fillGradient(1, 1, W - 1, 25, th.headerTop, th.headerBottom);
        context.fill(1, 25, W - 1, 26, th.border);
        drawRealItem(context, Items.BONE_MEAL, 6, 5);
        text(context, "Fake Bone Meal", 26, 9, 0xFFFFFFFF);

        // pasek na dole
        context.fillGradient(1, H - 5, W - 1, H - 1, th.strip1, th.strip2);

        for (Btn b : btns) {
            b.painter.paint(context, b, b.enabled && b.action != null && b.contains(lx, ly));
        }

        if (tab == 0 && itemCount == 0) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Brak itemów na liście"), W / 2, 106, MUTED);
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Weź item do ręki i kliknij „Dodaj”"), W / 2, 120, DISABLED);
        }

        m.pop();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            float s = scale();
            double lx = (mouseX - originX()) / s;
            double ly = (mouseY - originY()) / s;
            for (int i = btns.size() - 1; i >= 0; i--) {
                Btn b = btns.get(i);
                if (b.action != null && b.enabled && b.contains(lx, ly)) {
                    if (this.client != null) {
                        this.client.getSoundManager().play(
                                PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    }
                    b.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // przewijanie kolkiem myszy listy itemow
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (tab == 0) {
            if (verticalAmount < 0 && page < pages - 1) {
                page++;
                rebuild();
                return true;
            } else if (verticalAmount > 0 && page > 0) {
                page--;
                rebuild();
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() {
        ClientSettings.save();
        FakeBloom.resetIndex();
        super.close();
    }
}
