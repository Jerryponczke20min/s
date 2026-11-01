package com.example.fakebonemeal;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class FakeBoneMealClient implements ClientModInitializer {

    private static KeyBinding openKey;
    private static KeyBinding shuffleKey;

    @Override
    public void onInitializeClient() {
        ClientSettings.load();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.fakebonemeal.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "key.categories.fakebonemeal"));

        shuffleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.fakebonemeal.shuffle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "key.categories.fakebonemeal"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    client.setScreen(new SettingsScreen());
                }
            }
            while (shuffleKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    shufflePattern();
                }
            }
        });
    }

    /** Przestawia liczby miedzy kliknieciami wedlug ustawionej w GUI mapy (np. 2,1,0,2 -> 1,2,2,0). */
    public static void shufflePattern() {
        List<Integer> p = ClientSettings.pattern;
        List<Integer> next = ClientSettings.shuffled();
        p.clear();
        p.addAll(next);

        ClientSettings.save();
        FakeBloom.resetIndex();

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < p.size(); i++) {
                if (i > 0) {
                    sb.append("   ");
                }
                sb.append(i + 1).append('.').append(p.get(i));
            }
            client.player.sendMessage(Text.literal("Fake Bone Meal  " + sb), true);
        }
    }

    public static String shuffleKeyName() {
        return shuffleKey == null ? "H" : shuffleKey.getBoundKeyLocalizedText().getString();
    }
}
