package pl.radar;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Tracery, ESP i hitboxy. Poczatek tracera jest w srodku ekranu (celownik).
 * Rysujemy wlasna macierza + projekcja, a macierz model-view gry jest cofana,
 * wiec stan renderera gry nie przesuwa linii. Najlepiej dziala ze stabilizacja kamery (zakladka PvP).
 */
public class TracerRenderer {
    private static final int[][] EDGES = {
        {0,1},{1,2},{2,3},{3,0}, {4,5},{5,6},{6,7},{7,4}, {0,4},{1,5},{2,6},{3,7}
    };

    /** Jeden odcinek: albo od celownika (fromCrosshair), albo miedzy dwoma punktami swiata (wzgledem kamery). */
    private record Seg(boolean fromCrosshair, float ax, float ay, float az, float bx, float by, float bz, int rgb) {}

    public static void register() {
        WorldRenderEvents.LAST.register(ctx -> {
            try { render(ctx); } catch (Throwable t) { t.printStackTrace(); }
        });
    }

    private static void addBox(List<Seg> out, float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
        float[][] k = {
            {x0,y0,z0},{x1,y0,z0},{x1,y0,z1},{x0,y0,z1},
            {x0,y1,z0},{x1,y1,z0},{x1,y1,z1},{x0,y1,z1}
        };
        for (int[] ed : EDGES) {
            out.add(new Seg(false, k[ed[0]][0], k[ed[0]][1], k[ed[0]][2], k[ed[1]][0], k[ed[1]][1], k[ed[1]][2], rgb));
        }
    }

    private static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        RadarConfig cfg = RadarConfig.I;
        if (mc.world == null || mc.player == null) return;

        Set<UUID> targets = Radar.targets;
        Set<BlockPos> blocks = Radar.blockTargets;
        boolean hitboxes = cfg.hitPlayers || cfg.hitMobs;
        if (targets.isEmpty() && blocks.isEmpty() && !hitboxes) return;

        Camera cam = ctx.camera();
        Vec3d camPos = cam.getPos();
        float delta = ctx.tickCounter().getTickDelta(false);
        int traceRgb = cfg.colorRgb(), hitRgb = cfg.hitRgb();
        boolean esp = cfg.esp;
        double hitRange2 = cfg.radar() * cfg.radar();

        // 1) zbieramy odcinki - jesli nic nie ma, nie dotykamy bufora ani projekcji
        List<Seg> segs = new ArrayList<>();
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player) continue;
            Vec3d c = e.getLerpedPos(delta).subtract(camPos);

            if (targets.contains(e.getUuid())) {
                float w = e.getWidth() / 2f, h = e.getHeight();
                segs.add(new Seg(true, 0, 0, 0, (float) c.x, (float) (c.y + h / 2), (float) c.z, traceRgb));
                if (esp) {
                    addBox(segs, (float) c.x - w, (float) c.y, (float) c.z - w,
                        (float) c.x + w, (float) c.y + h, (float) c.z + w, traceRgb);
                }
            }

            if (hitboxes && mc.player.squaredDistanceTo(e) <= hitRange2) {
                boolean show = e instanceof PlayerEntity ? cfg.hitPlayers : (e instanceof LivingEntity && cfg.hitMobs);
                if (show) {
                    Box b = e.getBoundingBox();
                    Vec3d off = e.getLerpedPos(delta).subtract(e.getPos()).subtract(camPos);
                    addBox(segs, (float) (b.minX + off.x), (float) (b.minY + off.y), (float) (b.minZ + off.z),
                        (float) (b.maxX + off.x), (float) (b.maxY + off.y), (float) (b.maxZ + off.z), hitRgb);
                    if (cfg.hitLook && e instanceof LivingEntity) {
                        Vec3d eye = c.add(0, e.getEyeHeight(e.getPose()), 0);
                        Vec3d end = eye.add(e.getRotationVec(delta).multiply(2.0));
                        segs.add(new Seg(false, (float) eye.x, (float) eye.y, (float) eye.z,
                            (float) end.x, (float) end.y, (float) end.z, 0x4F8CFF));
                    }
                }
            }
        }

        // bloki (np. tabliczki z napisem)
        for (BlockPos p : blocks) {
            float x0 = (float) (p.getX() - camPos.x), y0 = (float) (p.getY() - camPos.y), z0 = (float) (p.getZ() - camPos.z);
            segs.add(new Seg(true, 0, 0, 0, x0 + 0.5f, y0 + 0.5f, z0 + 0.5f, traceRgb));
            if (esp) addBox(segs, x0, y0, z0, x0 + 1, y0 + 1, z0 + 1, traceRgb);
        }
        if (segs.isEmpty()) return;

        // 2) macierze
        Matrix4f inv = new Matrix4f(RenderSystem.getModelViewMatrix()).invert();
        Matrix4f rot = new Matrix4f().rotation(cam.getRotation().conjugate(new Quaternionf()));
        Matrix4f view = inv.mul(rot, new Matrix4f());
        Matrix4f ident = new Matrix4f(inv);

        Matrix4f oldProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        ProjectionType oldType = RenderSystem.getProjectionType();
        try {
            float aspect = (float) mc.getWindow().getFramebufferWidth() / mc.getWindow().getFramebufferHeight();
            float fov = (float) Math.toRadians(mc.options.getFov().getValue());
            RenderSystem.setProjectionMatrix(
                new Matrix4f().perspective(fov, aspect, 0.05f, mc.gameRenderer.getFarPlaneDistance()),
                ProjectionType.PERSPECTIVE);

            BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            for (Seg s : segs) {
                int r = (s.rgb >> 16) & 255, g = (s.rgb >> 8) & 255, b = s.rgb & 255;
                if (s.fromCrosshair) buf.vertex(ident, 0f, 0f, -0.5f).color(r, g, b, 255);
                else buf.vertex(view, s.ax, s.ay, s.az).color(r, g, b, 255);
                buf.vertex(view, s.bx, s.by, s.bz).color(r, g, b, 255);
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
