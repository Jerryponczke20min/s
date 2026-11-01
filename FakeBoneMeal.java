package com.example.fakebonemeal;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/** Stale i proste funkcje pomocnicze. Mod dziala w calosci po stronie klienta. */
public final class FakeBoneMeal {

    public static final String MOD_ID = "fakebonemeal";

    /** 12 rodzajow kwiatow (kolejnosc = bity w masce). */
    public static final Block[] FLOWERS = {
            Blocks.DANDELION, Blocks.POPPY, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY,
            Blocks.CORNFLOWER, Blocks.ALLIUM, Blocks.RED_TULIP, Blocks.ORANGE_TULIP,
            Blocks.PINK_TULIP, Blocks.WHITE_TULIP, Blocks.LILY_OF_THE_VALLEY, Blocks.BLUE_ORCHID
    };
    public static final int ALL_FLOWERS = (1 << FLOWERS.length) - 1;

    private FakeBoneMeal() {
    }

    /** Zamienia tekst typu "3,2" na tablice {3, 2}. */
    public static int[] parsePattern(String text) {
        List<Integer> list = new ArrayList<>();
        for (String part : text.split("[^0-9]+")) {
            if (part.isEmpty() || list.size() >= 8) {
                continue;
            }
            try {
                list.add(Math.min(16, Integer.parseInt(part)));
            } catch (NumberFormatException ignored) {
            }
        }
        if (list.isEmpty()) {
            return new int[]{3, 2};
        }
        return list.stream().mapToInt(Integer::intValue).toArray();
    }
}
