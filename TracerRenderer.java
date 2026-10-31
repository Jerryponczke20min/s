package pl.radar;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Tracery + ESP rysowane wlasna macierza i projekcja (bez view bobbingu).
 * Poczatek tracera jest zawsze w srodku ekranu (celownik).
 */
public class TracerRenderer {
    private static final int[][] EDGES = {
        {0,1},{1,2},{2,3},{3,0}, {4,5},{5,6},{6,7},{7,4}, {0,4},{1,5},{2,6},{3,7}
    };

    public static void register() {
        WorldRenderEvents.LAST.register(ctx -> {
            try {
                render(ctx);
            } catch (Throwable t) {
                // render nigdy nie moze wywalic gry
                t.printStackTrace();
            }
        });
    }

    private static void render(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Set<UUID> targets = Radar.targets;
        if (mc.world == null || mc.player == null || targets.isEmpty()) return;

        // 1) najpierw zbieramy graczy - jesli nikogo nie ma, nie dotykamy bufora ani projekcji
        List<PlayerEntity> list = new ArrayList<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p != mc.player && targets.contains(p.getUuid())) list.add(p);
        }
        if (list.isEmpty()) return;

        Camera cam = ctx.camera();
        Vec3d camPos = cam.getPos();
        float delta = ctx.tickCounter().getTickDelta(false);

        int rgb = RadarConfig.I.colorRgb();
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        boolean esp = RadarConfig.I.esp;

        Matrix4f view = new Matrix4f().rotation(cam.getRotation().conjugate(new Quaternionf()));
        Matrix4f ident = new Matrix4f();

        Matrix4f oldProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        ProjectionType oldType = RenderSystem.getProjectionType();
        try {
            float aspect = (float) mc.getWindow().getFramebufferWidth() / mc.getWindow().getFramebufferHeight();
            float fov = (float) Math.toRadians(mc.options.getFov().getValue());
            RenderSystem.setProjectionMatrix(
                new Matrix4f().perspective(fov, aspect, 0.05f, mc.gameRenderer.getFarPlaneDistance()),
                ProjectionType.PERSPECTIVE);

            BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            for (PlayerEntity p : list) {
                Vec3d c = p.getLerpedPos(delta).subtract(camPos);
                float w = p.getWidth() / 2f, h = p.getHeight();

                // tracer: od celownika (0,0,-0.5 w przestrzeni kamery) do srodka ciala
                buf.vertex(ident, 0f, 0f, -0.5f).color(r, g, b, 255);
                buf.vertex(view, (float) c.x, (float) (c.y + h / 2), (float) c.z).color(r, g, b, 255);

                if (esp) {
                    float x0 = (float) c.x - w, x1 = (float) c.x + w;
                    float y0 = (float) c.y, y1 = (float) c.y + h;
                    float z0 = (float) c.z - w, z1 = (float) c.z + w;
                    float[][] k = {
                        {x0,y0,z0},{x1,y0,z0},{x1,y0,z1},{x0,y0,z1},
                        {x0,y1,z0},{x1,y1,z0},{x1,y1,z1},{x0,y1,z1}
                    };
                    for (int[] e : EDGES) {
                        buf.vertex(view, k[e[0]][0], k[e[0]][1], k[e[0]][2]).color(r, g, b, 255);
                        buf.vertex(view, k[e[1]][0], k[e[1]][1], k[e[1]][2]).color(r, g, b, 255);
                    }
                }
            }

            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.lineWidth(2.0f);
            BufferRenderer.drawWithGlobalProgram(buf.end());
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        } finally {
            RenderSystem.setProjectionMatrix(oldProj, oldType);
        }
    }
}
