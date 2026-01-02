package anticope.rejects.modules;

import anticope.rejects.MeteorRejectsAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedList;
import java.util.Queue;

public class AntiCrash extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> log = sgGeneral.add(new BoolSetting.Builder()
            .name("log")
            .description("Logs when crash packet detected.")
            .defaultValue(false)
            .build()
    );

    private final Queue<Long> explosionTimestamps = new LinkedList<>();
    private static final int MAX_EXPLOSIONS_PER_SECOND = 20;
    private static final float MAX_EXPLOSION_RADIUS = 20.0f;
    private static final double MAX_KNOCKBACK_COMPONENT = 50.0;

    public AntiCrash() {
        super(MeteorRejectsAddon.CATEGORY, "anti-crash", "Attempts to cancel packets that may crash the client.");
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof ExplosionS2CPacket packet) {//ExplodeBypass Fix
            Vec3d explodePos = packet.center();
            Vec3d playerKnockback = new Vec3d(0, 0, 0);
            if(packet.playerKnockback().isPresent()) {
                playerKnockback = packet.playerKnockback().get();
            }

            long currentTime = System.currentTimeMillis();
            explosionTimestamps.add(currentTime);

            while (!explosionTimestamps.isEmpty() && explosionTimestamps.peek() < currentTime - 1000) {
                explosionTimestamps.poll();
            }

            if (explosionTimestamps.size() > MAX_EXPLOSIONS_PER_SECOND) {
                cancel(event, "explosion spam (" + explosionTimestamps.size() + "/s)");
                return;
            }

            if (packet.radius() > MAX_EXPLOSION_RADIUS) {
                cancel(event, "high explosion radius (" + String.format("%.2f", packet.radius()) + ")");
                return;
            }

            double maxKnockback = Math.max(Math.abs(playerKnockback.x),
                                          Math.max(Math.abs(playerKnockback.y), Math.abs(playerKnockback.z)));
            if (maxKnockback > MAX_KNOCKBACK_COMPONENT) {
                cancel(event, "extreme knockback (" + String.format("%.2f", maxKnockback) + ")");
                return;
            }

            if (explodePos.getX() > 30_000_000 || explodePos.getY() > 30_000_000 || explodePos.getZ() > 30_000_000 ||
                explodePos.getX() < -30_000_000 || explodePos.getY() < -30_000_000 || explodePos.getZ() < -30_000_000) {
                cancel(event, "explosion outside world border");
                return;
            }

            if (playerKnockback.x > 30_000_000 || playerKnockback.y > 30_000_000 || playerKnockback.z > 30_000_000 ||
                playerKnockback.x < -30_000_000 || playerKnockback.y < -30_000_000 || playerKnockback.z < -30_000_000) {
                cancel(event, "knockback outside world border");
                return;
            }
        } else if (event.packet instanceof ParticleS2CPacket packet) {
            // too many particles
            if (packet.getCount() > 100_000) {
                cancel(event, "particle spam (" + packet.getCount() + " particles)");
            }
        } else if (event.packet instanceof PlayerPositionLookS2CPacket packet) {
            Vec3d playerPos = packet.change().position();
            // out of world movement
            if (playerPos.x > 30_000_000 || playerPos.y > 30_000_000 || playerPos.z > 30_000_000 ||
                playerPos.x < -30_000_000 || playerPos.y < -30_000_000 || playerPos.z < -30_000_000) {
                cancel(event, "position outside world border");
            }
        } else if (event.packet instanceof EntityVelocityUpdateS2CPacket packet) {
            Vec3d velocity = packet.getVelocity();
            if (velocity.getX() > 30_000_000 || velocity.getY() > 30_000_000 || velocity.getZ() > 30_000_000 ||
                velocity.getX() < -30_000_000 || velocity.getY() < -30_000_000 || velocity.getZ() < -30_000_000) {
                cancel(event, "velocity outside world border");
            }
        } else if (event.packet instanceof EntitiesDestroyS2CPacket packet) {
            //DestroySelf Fix
            if (mc.player != null) {
                int playerId = mc.player.getId();
                for (int entityId : packet.getEntityIds().toIntArray()) {
                    if (entityId == playerId) {
                        cancel(event, "attempted to destroy player entity (DestroySelf exploit)");
                        return;
                    }
                }
            }
        } else if (event.packet instanceof EntitySpawnS2CPacket packet) {
            if (mc.player != null && packet.getEntityId() == mc.player.getId()) {
                cancel(event, "attempted to spawn entity with player ID (DestroySelf exploit)");
            }
        }
    }

    private void cancel(PacketEvent.Receive event, String reason) {
        if (log.get()) {
            warning("Cancelled crash packet: " + reason);
        }
        event.cancel();
    }
}
