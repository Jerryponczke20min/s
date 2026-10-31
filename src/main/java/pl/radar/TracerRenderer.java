package pl.radar;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Tracery + ESP. Poczatek tracera jest zawsze w srodku ekranu (celownik) i porusza sie razem z nim.
 * Rysujemy wlasna macierza + projekcja, a aktualna macierz model-view gry jest cofana (odwrocona),
 * wiec ani kolysanie kamery, ani stan renderera gry nie przesuwa linii.
 */
public class TracerRenderer {
    private static final int[][] EDGES = {
        {0,1},{1,2},{2,3},{3,0}, {4,5},{5,6},{6,7},{7,4}, {0,4},{1,5},{2,6},{3,7}
    };

    public static void register() {
        WorldRenderEvents.LAST.register(ctx -> {
            try { render(ctx); } catch (Throwable t) { t.printStackTrace(); }
        });
    }

    private static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Set<UUID> targets = Radar.targets;
        if (mc.world == null || mc.player == null || targets.isEmpty()) return;

        List<Entity> list = new ArrayList<>();
        for (Entity e : mc.world.getEntities()) {
            if (e != mc.player && targets.contains(e.getUuid())) list.add(e);
        }
        if (list.isEmpty()) return;

        Camera cam = ctx.camera();
        Vec3d camPos = cam.getPos();
        float delta = ctx.tickCounter().getTickDelta(false);

        int rgb = RadarConfig.I.colorRgb();
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        boolean esp = RadarConfig.I.esp;

        // chcemy, zeby shader finalnie uzyl DOKLADNIE naszej macierzy: MV * (inv(MV) * nasza) = nasza
        Matrix4f inv = new Matrix4f(RenderSystem.getModelViewMatrix()).invert();
        Matrix4f rot = new Matrix4f().rotation(cam.getRotation().conjugate(new Quaternionf()));
        Matrix4f view = inv.mul(rot, new Matrix4f());   // dla punktow w swiecie (wzgledem kamery)
        Matrix4f ident = new Matrix4f(inv);             // dla punktu przy celowniku

        Matrix4f oldProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        ProjectionType oldType = RenderSystem.getProjectionType();
        try {
            float aspect = (float) mc.getWindow().getFramebufferWidth() / mc.getWindow().getFramebufferHeight();
            float fov = (float) Math.toRadians(mc.options.getFov().getValue());
            RenderSystem.setProjectionMatrix(
                new Matrix4f().perspective(fov, aspect, 0.05f, mc.gameRenderer.getFarPlaneDistance()),
                ProjectionType.PERSPECTIVE);

            BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            for (Entity e : list) {
                Vec3d c = e.getLerpedPos(delta).subtract(camPos);
                float w = e.getWidth() / 2f, h = e.getHeight();

                // tracer: od celownika (srodek ekranu) do srodka ciala
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
                    for (int[] ed : EDGES) {
                        buf.vertex(view, k[ed[0]][0], k[ed[0]][1], k[ed[0]][2]).color(r, g, b, 255);
                        buf.vertex(view, k[ed[1]][0], k[ed[1]][1], k[ed[1]][2]).color(r, g, b, 255);
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
