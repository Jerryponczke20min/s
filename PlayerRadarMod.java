package pl.radar;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class PlayerRadarMod implements ClientModInitializer {
    public static KeyBinding OPEN;

    @Override
    public void onInitializeClient() {
        OPEN = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.playerradar.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.playerradar"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN.wasPressed()) mc.setScreen(new RadarScreen());
            if (mc.world == null) { Radar.targets = java.util.Set.of(); return; }
            Radar.tick();
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("radar")
                .executes(ctx -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    mc.send(() -> mc.setScreen(new RadarScreen()));
                    return 1;
                })
                .then(ClientCommandManager.literal("debug").executes(ctx -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    List<PlayerData> l = Radar.scan(RadarConfig.I.radar());
                    if (l.isEmpty()) say("Brak graczy w zasiegu.");
                    for (int i = 0; i < Math.min(5, l.size()); i++) {
                        PlayerData d = l.get(i);
                        say(d.name + " | raw: '" + MineStarParser.raw(d) + "' | miecz=" + d.sword
                            + " set=" + d.set + " duszki=" + d.skulls);
                    }
                    return 1;
                }))
                .then(ClientCommandManager.literal("trace")
                    .then(ClientCommandManager.literal("off").executes(ctx -> {
                        Radar.TRACED.clear(); say("Tracery reczne wylaczone."); return 1;
                    }))
                    .then(ClientCommandManager.argument("nick", StringArgumentType.word()).executes(ctx -> {
                        String n = StringArgumentType.getString(ctx, "nick");
                        if (Radar.TRACED.remove(n)) say("Tracer OFF: " + n);
                        else { Radar.TRACED.add(n); say("Tracer ON: " + n); }
                        return 1;
                    })))));
        TracerRenderer.register();
    }

    public static void say(String s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal("§6[Radar] §f" + s), false);
    }
}
