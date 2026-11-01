package com.example.fakebonemeal;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.IdentityHashMap;
import java.util.Map;

/** Logika "falszywego wygladu": zamienione itemy wygladaja jak bone meal, tylko u Ciebie. */
public final class VisualState {

    /** Gdy true, rysujemy prawdziwe itemy (np. ikony w naszym GUI). */
    public static boolean bypass = false;

    /** Ile razy "zuzyto" dany stack (tylko wizualnie). Klucz = konkretny obiekt stacka. */
    private static final Map<ItemStack, Integer> USED = new IdentityHashMap<>();

    private VisualState() {
    }

    public static boolean isFake(ItemStack stack) {
        return !bypass
                && ClientSettings.enabled
                && stack != null
                && !stack.isEmpty()
                && stack.getItem() != Items.BONE_MEAL
                && ClientSettings.items.contains(stack.getItem());
    }

    /** Model/ikona: bone meal zamiast prawdziwego itemu. */
    public static ItemStack modelStack(ItemStack stack) {
        return isFake(stack) ? new ItemStack(Items.BONE_MEAL) : stack;
    }

    /** Licznik pod ikona: prawdziwa ilosc minus "zuzyte" wizualnie. */
    public static ItemStack overlayStack(ItemStack stack) {
        if (!isFake(stack)) {
            return stack;
        }
        int shown = stack.getCount() - USED.getOrDefault(stack, 0);
        return new ItemStack(Items.BONE_MEAL, Math.max(1, shown));
    }

    /** Wywolywane przy kliknieciu: wizualnie zmniejsza liczbe o 1 (prawdziwa zostaje bez zmian). */
    public static void fakeUse(ItemStack stack) {
        if (USED.size() > 256) {
            USED.clear();
        }
        int used = USED.getOrDefault(stack, 0) + 1;
        if (used >= stack.getCount()) {
            used = 0; // "skonczyla sie" - licznik wraca do pelnej ilosci
        }
        USED.put(stack, used);
    }
}
