package pl.radar;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.Set;

/**
 * Tracery rysowane wlasna macierza (tylko obrot kamery + wlasna projekcja),
 * wiec nie sa dotkniete view bobbingiem (drganiem podczas chodzenia).
 */
public class TracerRenderer {
    public static void register() {
        WorldRenderEvents.LAST.register(ctx -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            Set<java.util.UUID> targets = Radar.targets;
            if (mc.world == null || mc.player == null || targets.isEmpty()) return;

            Camera cam = ctx.camera();
            Vec3d camPos = cam.getPos();
            float delta = ctx.tickCounter().getTickDelta(false);

            int rgb = RadarConfig.I.colorRgb();
            int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;

            Matrix4f view = new Matrix4f().rotation(cam.getRotation().conjugate(new Quaternionf()));
            Vec3d start = Vec3d.fromPolar(cam.getPitch(), cam.getYaw()).multiply(0.5); // srodek ekranu

            // wlasna projekcja bez bobbingu
            Matrix4f oldProj = new Matrix4f(RenderSystem.getProjectionMatrix());
            ProjectionType oldType = RenderSystem.getProjectionType();
            float aspect = (float) mc.getWindow().getFramebufferWidth() / mc.getWindow().getFramebufferHeight();
            float fov = (float) Math.toRadians(mc.options.getFov().getValue());
            Matrix4f proj = new Matrix4f().perspective(fov, aspect, 0.05f, mc.gameRenderer.getFarPlaneDistance());
            RenderSystem.setProjectionMatrix(proj, ProjectionType.PERSPECTIVE);

            BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (!targets.contains(p.getUuid())) continue;
                Vec3d t = p.getLerpedPos(delta).add(0, p.getHeight() / 2.0, 0).subtract(camPos);
                buf.vertex(view, (float) start.x, (float) start.y, (float) start.z).color(r, g, b, 255);
                buf.vertex(view, (float) t.x, (float) t.y, (float) t.z).color(r, g, b, 255);
            }

            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.lineWidth(2.0f);
            BufferRenderer.drawWithGlobalProgram(buf.end());
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();

            RenderSystem.setProjectionMatrix(oldProj, oldType);
        });
    }
}
