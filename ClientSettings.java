package com.example.fakebonemeal;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/** Ustawienia zapamietane po stronie klienta (GUI) i zapisywane w config/fakebonemeal.properties. */
public final class ClientSettings {

    public static final int MAX_STEPS = 5;

    public static boolean enabled = false;
    public static final List<Integer> pattern = new ArrayList<>(List.of(3, 2));
    public static final Set<Item> items = new LinkedHashSet<>();

    /** Ustawione tasowanie: po nacisnieciu H kliknięcie i dostaje liczbe z kliknięcia shuffleMap[i]. */
    public static final List<Integer> shuffleMap = new ArrayList<>();

    public static int radius = 2;
    public static int grass = 6;
    public static boolean tall = true;
    public static boolean effects = true;
    public static int flowerMask = FakeBoneMeal.ALL_FLOWERS;

    // wyglad GUI
    public static int themeIndex = 0;
    public static int customRgb = 0x5BD05B;
    public static int panelOpacity = 100;   // 40..100 (%)
    public static int guiScale = 100;       // 70..130 (%)

    private ClientSettings() {
    }

    private static int partner(int i, int n) {
        if (i % 2 == 0) {
            return i + 1 < n ? i + 1 : i;   // domyslnie zamiana parami: 1<->2, 3<->4
        }
        return i - 1;
    }

    /** Dopasowuje mape tasowania do liczby krokow (zachowuje to, co uzytkownik ustawil). */
    public static void fixMap() {
        int n = pattern.size();
        while (shuffleMap.size() < n) {
            shuffleMap.add(partner(shuffleMap.size(), n));
        }
        while (shuffleMap.size() > n) {
            shuffleMap.remove(shuffleMap.size() - 1);
        }
        for (int i = 0; i < n; i++) {
            int v = shuffleMap.get(i);
            if (v < 0 || v >= n) {
                shuffleMap.set(i, i);
            }
        }
    }

    /** Wynik tasowania wedlug ustawionej mapy (bez zmieniania sekwencji). */
    public static List<Integer> shuffled() {
        fixMap();
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < pattern.size(); i++) {
            out.add(pattern.get(shuffleMap.get(i)));
        }
        return out;
    }

    public static String patternString() {
        return pattern.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public static String itemsString() {
        return items.stream().map(i -> Registries.ITEM.getId(i).toString()).collect(Collectors.joining(";"));
    }

    public static String optionsString() {
        return radius + "," + grass + "," + (tall ? 1 : 0) + "," + flowerMask + "," + (effects ? 1 : 0);
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("fakebonemeal.properties");
    }

    private static int intProp(Properties p, String key, int def, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(p.getProperty(key, String.valueOf(def)).trim())));
        } catch (Exception e) {
            return def;
        }
    }

    public static void load() {
        Path p = file();
        if (!Files.exists(p)) {
            return;
        }
        try (Reader r = Files.newBufferedReader(p)) {
            Properties props = new Properties();
            props.load(r);
            enabled = Boolean.parseBoolean(props.getProperty("enabled", "false"));
            radius = intProp(props, "radius", 2, 1, 5);
            grass = intProp(props, "grass", 6, 0, 16);
            tall = Boolean.parseBoolean(props.getProperty("tall", "true"));
            effects = Boolean.parseBoolean(props.getProperty("effects", "true"));
            flowerMask = intProp(props, "flowerMask", FakeBoneMeal.ALL_FLOWERS, 1, FakeBoneMeal.ALL_FLOWERS);
            themeIndex = intProp(props, "theme", 0, 0, Theme.NAMES.length - 1);
            customRgb = intProp(props, "customRgb", 0x5BD05B, 0, 0xFFFFFF);
            panelOpacity = intProp(props, "panelOpacity", 100, 40, 100);
            guiScale = intProp(props, "guiScale", 100, 70, 130);

            pattern.clear();
            for (int v : FakeBoneMeal.parsePattern(props.getProperty("pattern", "3,2"))) {
                if (pattern.size() < MAX_STEPS) {
                    pattern.add(v);
                }
            }

            shuffleMap.clear();
            for (String part : props.getProperty("shuffleMap", "").split(",")) {
                try {
                    shuffleMap.add(Integer.parseInt(part.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
            fixMap();

            items.clear();
            for (String s : props.getProperty("items", "").split(";")) {
                Identifier id = Identifier.tryParse(s.trim());
                if (id != null && Registries.ITEM.containsId(id)) {
                    Item item = Registries.ITEM.get(id);
                    if (item != Items.AIR && item != Items.BONE_MEAL) {
                        items.add(item);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(file())) {
            Properties props = new Properties();
            props.setProperty("enabled", String.valueOf(enabled));
            props.setProperty("pattern", patternString());
            props.setProperty("items", itemsString());
            props.setProperty("shuffleMap", shuffleMap.stream().map(String::valueOf)
                    .collect(Collectors.joining(",")));
            props.setProperty("radius", String.valueOf(radius));
            props.setProperty("grass", String.valueOf(grass));
            props.setProperty("tall", String.valueOf(tall));
            props.setProperty("effects", String.valueOf(effects));
            props.setProperty("flowerMask", String.valueOf(flowerMask));
            props.setProperty("theme", String.valueOf(themeIndex));
            props.setProperty("customRgb", String.valueOf(customRgb));
            props.setProperty("panelOpacity", String.valueOf(panelOpacity));
            props.setProperty("guiScale", String.valueOf(guiScale));
            props.store(w, "Fake Bone Meal");
        } catch (Exception ignored) {
        }
    }
}
