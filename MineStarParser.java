package pl.radar;

import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Czyta liczby z nametaga: [miecz] [set] [duszki]. Nick jest usuwany, zeby cyfry w nicku nie psuly wyniku. */
public class MineStarParser {
    private static final Pattern NUM = Pattern.compile("[0-9]+");

    public static String raw(PlayerData d) {
        String s = Formatting.strip(d.player.getDisplayName().getString());
        return s == null ? "" : s;
    }

    public static void parse(PlayerData d) {
        String text = raw(d).replace(d.name, " ");
        List<Integer> n = new ArrayList<>();
        Matcher m = NUM.matcher(text);
        while (m.find()) {
            try { n.add(Integer.parseInt(m.group())); } catch (NumberFormatException ignored) {}
        }
        int s = n.size();
        if (s >= 3) {            // bierzemy ostatnie trzy liczby
            d.sword = n.get(s - 3);
            d.set = n.get(s - 2);
            d.skulls = n.get(s - 1);
        } else if (s == 2) {
            d.sword = n.get(0);
            d.set = n.get(1);
        }
    }
}
