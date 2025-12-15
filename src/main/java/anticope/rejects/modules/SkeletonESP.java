package anticope.rejects.modules;

import anticope.rejects.MeteorRejectsAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class SkeletonESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<SettingColor> colorSetting = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Color of the skeleton.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    private final Setting<Boolean> distanceColors = sgGeneral.add(new BoolSetting.Builder()
        .name("distance-colors")
        .description("Change skeleton color based on distance.")
        .defaultValue(false)
        .build()
    );

    private final Freecam freecam;

    public SkeletonESP() {
        super(MeteorRejectsAddon.CATEGORY, "skeleton-esp", "Renders player skeletons.");
        freecam = Modules.get().get(Freecam.class);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        List<AbstractClientPlayerEntity> players = mc.world.getPlayers();

        for (AbstractClientPlayerEntity player : players) {
            // Skip self in first-person unless freecam is active
            if (!freecam.isActive() && mc.options.getPerspective() == Perspective.FIRST_PERSON && player == mc.player) {
                continue;
            }

            Color skeletonColor = distanceColors.get()
                ? getColorFromDistance(player)
                : PlayerUtils.getPlayerColor((PlayerEntity) player, colorSetting.get());

            drawPlayerSkeleton(event, player, skeletonColor);
        }
    }

    private void drawPlayerSkeleton(Render3DEvent event, AbstractClientPlayerEntity player, Color color) {
        Vec3d basePos = player.getLerpedPos(event.tickDelta);
        float yawRad = (float) Math.toRadians(-player.getBodyYaw());

        boolean sneaking = player.isSneaking();

        // Skeleton dimensions
        double shoulderWidth = 0.35;
        double hipWidth = 0.15;
        double armLength = 0.55;
        double legLength = 0.7;

        // Vertical positions
        double pelvisHeight = sneaking ? 0.6 : 0.7;
        double chestHeight = sneaking ? 1.05 : 1.35;
        double headTopHeight = sneaking ? 1.4 : 1.7;

        // Forward offset for sneaking
        Vec3d sneakOffset = sneaking ? new Vec3d(0, 0, 0.25).rotateY(yawRad) : Vec3d.ZERO;

        // Core positions
        Vec3d pelvisCenter = basePos.add(0, pelvisHeight, 0).add(sneakOffset);
        Vec3d chestCenter = basePos.add(0, chestHeight, 0);
        Vec3d headTop = basePos.add(0, headTopHeight, 0);

        // Shoulders (rotated with body)
        Vec3d leftShoulder = chestCenter.add(new Vec3d(-shoulderWidth, 0, 0).rotateY(yawRad));
        Vec3d rightShoulder = chestCenter.add(new Vec3d(shoulderWidth, 0, 0).rotateY(yawRad));

        // Arms (hang straight down from shoulders)
        Vec3d leftHand = leftShoulder.add(0, -armLength, 0);
        Vec3d rightHand = rightShoulder.add(0, -armLength, 0);

        // Hips (rotated with body)
        Vec3d leftHip = pelvisCenter.add(new Vec3d(-hipWidth, 0, 0).rotateY(yawRad));
        Vec3d rightHip = pelvisCenter.add(new Vec3d(hipWidth, 0, 0).rotateY(yawRad));

        // Feet (at ground level, aligned with hips)
        Vec3d leftFoot = basePos.add(new Vec3d(-hipWidth, 0, 0).rotateY(yawRad)).add(sneakOffset);
        Vec3d rightFoot = basePos.add(new Vec3d(hipWidth, 0, 0).rotateY(yawRad)).add(sneakOffset);

        // === Draw Skeleton ===

        // Spine (pelvis to chest)
        event.renderer.line(pelvisCenter.x, pelvisCenter.y, pelvisCenter.z,
            chestCenter.x, chestCenter.y, chestCenter.z, color);

        // Head (chest to top of head)
        event.renderer.line(chestCenter.x, chestCenter.y, chestCenter.z,
            headTop.x, headTop.y, headTop.z, color);

        // Shoulders
        event.renderer.line(leftShoulder.x, leftShoulder.y, leftShoulder.z,
            rightShoulder.x, rightShoulder.y, rightShoulder.z, color);

        // Left arm
        event.renderer.line(leftShoulder.x, leftShoulder.y, leftShoulder.z,
            leftHand.x, leftHand.y, leftHand.z, color);

        // Right arm
        event.renderer.line(rightShoulder.x, rightShoulder.y, rightShoulder.z,
            rightHand.x, rightHand.y, rightHand.z, color);

        // Pelvis
        event.renderer.line(leftHip.x, leftHip.y, leftHip.z,
            rightHip.x, rightHip.y, rightHip.z, color);

        // Left leg
        event.renderer.line(leftHip.x, leftHip.y, leftHip.z,
            leftFoot.x, leftFoot.y, leftFoot.z, color);

        // Right leg
        event.renderer.line(rightHip.x, rightHip.y, rightHip.z,
            rightFoot.x, rightFoot.y, rightFoot.z, color);
    }

    private Color getColorFromDistance(AbstractClientPlayerEntity player) {
        Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        double distance = mc.gameRenderer.getCamera().getCameraPos().distanceTo(playerPos);
        double percent = Math.min(1.0, distance / 60.0);

        int r, g;

        if (percent < 0.5) {
            r = 255;
            g = (int) (255 * percent / 0.5);
        } else {
            g = 255;
            r = 255 - (int) (255 * (percent - 0.5) / 0.5);
        }

        return new Color(r, g, 0, 255);
    }
}
