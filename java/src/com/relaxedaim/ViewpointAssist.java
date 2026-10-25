package com.relaxedaim;

import java.util.ArrayList;

import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.inventory.types.HandWeapon;
import zombie.iso.IsoCell;
import zombie.iso.IsoWorld;

/**
 * Viewpoint（3D）下的水平(yaw)辅助锁定。
 *
 * 机制：每帧在相机前向（Controls.aim 用的 (cos(Look.yaw), sin(Look.yaw)) 约定）与「锁定丧尸头部」
 * 之间求带符号夹角，钳制到最大吸附角后平滑，写入 JVM 系统属性 "relaxedaim.aimYawDeg"；
 * 由 Patch_CrosshairAimYaw 在 viewpoint.input.CrosshairAim.yaw 上叠加该偏移。
 * 因此只转动武器瞄准/枪口/弹道，**相机 Look 不动**。
 *
 * 候选/锁定：仅在近距(≤ MAX_ACQUIRE_DIST) + 角锥(ACQUIRE_CONE)内选「离玩家最近」的丧尸；
 * 保持带滞回与保持时间(optionLockHoldTimeMs)防抖。
 *
 * 注意：开发阶段所有成员保持 public（见 RULES.md）。
 */
public final class ViewpointAssist {

    public static IsoZombie target = null;
    public static int targetId = -1;

    /** 平滑后的实际注入偏移（度）。 */
    public static float assistYawDeg = 0.0f;
    /** 本帧期望偏移（度，未平滑）。 */
    public static float rawYawDeg = 0.0f;
    /** 锁定目标距离（米）。 */
    public static float targetDist = 0.0f;
    public static boolean hasTarget = false;
    public static String debugReason = "none";

    /** 最大吸附角（度）。 */
    public static final float MAX_ASSIST_DEG = 12.0f;
    /** 进入锁定的角锥半角（度）。 */
    public static final float ACQUIRE_CONE_DEG = 7.0f;
    /** 保持/释放的角锥半角（度）。 */
    public static final float RELEASE_CONE_DEG = 12.0f;
    /** 3D 辅助的最远获取距离（米）。超出则不吸附（可用武器射程更小者）。 */
    public static final float MAX_ACQUIRE_DIST = 20.0f;
    /** 平滑速率（1/秒，指数趋近）。 */
    public static final float SMOOTH_RATE = 20.0f;

    private static long lastMs = 0L;
    private static long invalidSinceMs = 0L;

    public ViewpointAssist() {
    }

    private static void writeProp(float deg) {
        try {
            System.setProperty("relaxedaim.aimYawDeg", Float.toString(deg));
        } catch (Throwable t) {
        }
    }

    /** Viewpoint 未激活 / 未瞄准：偏移归零。 */
    public static void idle() {
        target = null;
        targetId = -1;
        hasTarget = false;
        assistYawDeg = 0.0f;
        rawYawDeg = 0.0f;
        targetDist = 0.0f;
        invalidSinceMs = 0L;
        debugReason = "idle";
        writeProp(RelaxedAimConfig.optionSpikeAimOffsetDeg);
    }

    /** 每帧（Viewpoint 激活且瞄准中）调用。 */
    public static void update(IsoPlayer player, int pIndex) {
        final long now = System.currentTimeMillis();
        float dt = (now - lastMs) / 1000.0f;
        lastMs = now;
        if (dt <= 0.0f || dt > 0.2f) {
            dt = 1.0f / 60.0f;
        }

        float desiredDeg = 0.0f;
        hasTarget = false;
        targetDist = 0.0f;
        debugReason = "none";

        try {
            if (!RelaxedAimConfig.isLockOnEffective()) {
                debugReason = "lockoff";
                clearTarget();
            } else {
                final HandWeapon weapon = AimAssistService.getActiveWeapon(player);
                if (weapon == null || !weapon.isRanged()
                        || (RelaxedAimConfig.optionShotgunNoLock && AimAssistService.isShotgun(weapon))) {
                    debugReason = "no-weapon";
                    clearTarget();
                } else {
                    final IsoCell cell = IsoWorld.instance != null ? IsoWorld.instance.currentCell : null;
                    final ArrayList<IsoZombie> zombies = cell != null ? cell.getZombieList() : null;
                    if (zombies == null) {
                        debugReason = "no-cell";
                        clearTarget();
                    } else {
                        final float camYawRad = (float) Math.toRadians(ViewpointCompat.lookYawDeg);
                        final float fx = (float) Math.cos(camYawRad);
                        final float fy = (float) Math.sin(camYawRad);
                        final float ox = player.getAimOriginPosX();
                        final float oy = player.getAimOriginPosY();
                        final float pz = player.getZ();
                        final float maxRange = AimAssistService.effectiveMaxLockRange(weapon);
                        final float acqDist = Math.min(maxRange, MAX_ACQUIRE_DIST);

                        // 校验现有锁定（滞回 + 保持时间）
                        if (target != null) {
                            if (!isStillValid(target, fx, fy, ox, oy, pz, acqDist)) {
                                if (invalidSinceMs == 0L) {
                                    invalidSinceMs = now;
                                }
                                if (now - invalidSinceMs >= Math.max(0L, RelaxedAimConfig.optionLockHoldTimeMs)) {
                                    clearTarget();
                                }
                            } else {
                                invalidSinceMs = 0L;
                            }
                        }
                        // 重新获取（近距优先）
                        if (target == null) {
                            target = acquire(zombies, fx, fy, ox, oy, pz, acqDist);
                            targetId = target != null ? TargetLockService.safeId(target) : -1;
                            invalidSinceMs = 0L;
                            if (target == null) {
                                debugReason = "none";
                            }
                        }
                        if (target != null) {
                            final float[] res = angleToHead(target, fx, fy, ox, oy);
                            if (res != null) {
                                float off = res[0];
                                rawYawDeg = (float) Math.toDegrees(off);
                                final float max = (float) Math.toRadians(MAX_ASSIST_DEG);
                                if (off > max) {
                                    off = max;
                                }
                                if (off < -max) {
                                    off = -max;
                                }
                                desiredDeg = (float) Math.toDegrees(off);
                                hasTarget = true;
                                targetDist = player_dist(target, ox, oy);
                                debugReason = "lock";
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            debugReason = "err:" + t.getClass().getSimpleName();
        }

        if (!hasTarget) {
            rawYawDeg = 0.0f;
        }

        final float k = Math.min(1.0f, dt * SMOOTH_RATE);
        assistYawDeg += (desiredDeg - assistYawDeg) * k;
        if (Math.abs(assistYawDeg) < 0.01f && desiredDeg == 0.0f) {
            assistYawDeg = 0.0f;
        }

        writeProp(assistYawDeg + RelaxedAimConfig.optionSpikeAimOffsetDeg);
    }

    private static void clearTarget() {
        target = null;
        targetId = -1;
        invalidSinceMs = 0L;
    }

    private static boolean isStillValid(IsoZombie z, float fx, float fy, float ox, float oy, float pz, float acqDist) {
        try {
            if (z.isDead() || !z.isAlive()) {
                return false;
            }
            if (Math.abs(z.getZ() - pz) > AimAssistService.FLOOR_TOLERANCE) {
                return false;
            }
            if (player_dist(z, ox, oy) > acqDist) {
                return false;
            }
            final float[] res = angleToHead(z, fx, fy, ox, oy);
            if (res == null) {
                return false;
            }
            return Math.abs(Math.toDegrees(res[0])) <= RELEASE_CONE_DEG;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 带符号夹角（弧度）：从相机前向 (fx,fy) 转到「丧尸头部」方向；失败 null。 */
    private static float[] angleToHead(IsoZombie z, float fx, float fy, float ox, float oy) {
        try {
            if (!TargetLockService.headAimWorldPos(z, TargetLockService.sHeadPos)) {
                return null;
            }
            float dx = TargetLockService.sHeadPos.x - ox;
            float dy = TargetLockService.sHeadPos.y - oy;
            final float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 0.001f) {
                return null;
            }
            dx /= len;
            dy /= len;
            final float cross = fx * dy - fy * dx;
            final float dot = fx * dx + fy * dy;
            return new float[]{(float) Math.atan2(cross, dot)};
        } catch (Throwable t) {
            return null;
        }
    }

    /** 在近距 + 角锥内选「离玩家最近」的丧尸。 */
    private static IsoZombie acquire(ArrayList<IsoZombie> zombies, float fx, float fy,
                                     float ox, float oy, float pz, float acqDist) {
        IsoZombie best = null;
        float bestDist = Float.MAX_VALUE;
        float bestAbs = Float.MAX_VALUE;
        final float cone = (float) Math.toRadians(ACQUIRE_CONE_DEG);
        for (final IsoZombie z : zombies) {
            if (z == null || z.isDead() || !z.isAlive()) {
                continue;
            }
            try {
                if (Math.abs(z.getZ() - pz) > AimAssistService.FLOOR_TOLERANCE) {
                    continue;
                }
                final float d = player_dist(z, ox, oy);
                if (d > acqDist) {
                    continue;
                }
                final float[] res = angleToHead(z, fx, fy, ox, oy);
                if (res == null) {
                    continue;
                }
                final float abs = Math.abs(res[0]);
                if (abs > cone) {
                    continue;
                }
                // 近距优先；距离接近时取角度更小
                if (d < bestDist - 0.5f || (Math.abs(d - bestDist) <= 0.5f && abs < bestAbs)) {
                    bestDist = d;
                    bestAbs = abs;
                    best = z;
                }
            } catch (Throwable t) {
            }
        }
        return best;
    }

    private static float player_dist(IsoZombie z, float ox, float oy) {
        final float dx = z.getX() - ox;
        final float dy = z.getY() - oy;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
