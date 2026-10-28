package com.relaxedaim;

import java.util.ArrayList;

import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.inventory.types.HandWeapon;
import zombie.iso.IsoCell;
import zombie.iso.IsoWorld;

/**
 * Viewpoint（3D，Gunplay ON）下的水平(yaw)+垂直(pitch)辅助锁定。
 *
 * 【几何】全部在 Viewpoint 自己的「相机/弹道空间」里算：
 *  - 该空间 up = 世界 z × 2.4494896（见 viewpoint.gunplay.Bodies.put、CameraStep、CrosshairAim）
 *  - 相机位置直接取 Viewpoint 的 CrosshairAim.camera(place)（Shot.draw 用的同一个点），
 *    第三人称/肩射/车内都自动包含相机偏移
 *  - 目标点 = Viewpoint 头胶囊中心（Spine1→Head 的 1.46 处，见 viewpoint.gunplay.Parts.SPEC），
 *    而不是头骨骼本身（头骨骼在视觉上落在脖子根部）
 *  - 把目标向量投影到相机基（forward/right/up）得到切值 tanR / tanU：
 *        tanR = (v·right)/(v·forward)   tanU = (v·up)/(v·forward)
 *    这两个数同时用于：Patch_VPShotRay（叠进射击射线）与 Patch_VPReticleOffset（乘 ppd 得准心像素），
 *    因此准心看得见的位置 = 子弹实际飞向的位置。
 *
 * 【捕捉范围】用「头部在屏幕上的像素距离」判定，与画出来的青色捕捉圈完全一致；
 * 半径 = 模组设置里的捕获半径（世界瓦片）× CAPTURE_PX_PER_TILE，因此该配置在 3D 下同样生效。
 *
 * 【锁定动画】复用原 iso 的瞄准时间算法（TargetLockService.computeSnapTimeMs，progress² 趋近）：
 * 捕捉后逐渐吸附到头部；丢失锁定后回落到捕捉圈中心。
 *
 * 注意：开发阶段所有成员保持 public（见 RULES.md）。
 */
public final class ViewpointAssist {

    /** 世界 z → Viewpoint「up」单位的换算（VP 内部固定为 2.4494896）。 */
    public static final float Z_SCALE = 2.4494896f;

    /**
     * 3D 捕捉半径倍率：屏幕像素 / 世界瓦片。
     * 配置的捕获半径（默认 1.5 瓦片）× 40 = 60px ≈ 原来 7° 捕捉锥在 1080p/103°FOV 下的屏幕半径；
     * 改配置即可同时缩放「画出来的圈」和「实际能捕捉到的范围」，两者严格一致。
     */
    public static final float CAPTURE_PX_PER_TILE = 40.0f;

    public static IsoZombie target = null;
    public static int targetId = -1;

    /** 平滑后的吸附量（度，仅用于 HUD 显示）。 */
    public static float assistYawDeg = 0.0f;
    public static float assistPitchDeg = 0.0f;
    /** 本帧的原始（未平滑）吸附量（度，仅用于 HUD 显示）。 */
    public static float rawYawDeg = 0.0f;
    public static float rawPitchDeg = 0.0f;

    public static float targetDist = 0.0f;
    public static float aimXpx = 0.0f;
    public static float aimYpx = 0.0f;
    /** 当前写入的切值（tan）偏移：射线与准心像素共用同一组数。 */
    public static double aimTanR = 0.0;
    public static double aimTanU = 0.0;
    public static boolean hasTarget = false;
    public static String debugReason = "none";

    // ---- 相机空间调试 ----
    public static float camX = 0.0f;
    public static float camY = 0.0f;
    public static float camZ = 0.0f;
    public static boolean camFromViewpoint = false;

    // ---- 3D 锁定圈动画（像素，相对屏幕中心） ----
    public static float lockPx = 0.0f;
    public static float lockPy = 0.0f;
    public static boolean lockRingActive = false;
    public static long lockSnapStartMs = 0L;
    /** 当前吸附进度（0~1，供 HUD）。 */
    public static float lockProgress = 0.0f;
    /** 本次锁定的吸附时长（毫秒，供 HUD）。 */
    public static float lockSnapMs = 0.0f;

    // ---- 失锁诊断 ----
    public static String debugReleaseReason = "none";
    public static long debugReleaseTimeMs = 0L;

    /** 水平吸附上限（度）。 */
    public static final float MAX_ASSIST_DEG = 12.0f;
    /** 垂直方向需要覆盖"瞄倒地丧尸头部"这种大俯角，故上限远大于水平。 */
    public static final float MAX_ASSIST_PITCH_DEG = 55.0f;
    public static final float MAX_ACQUIRE_DIST = 20.0f;
    /**
     * 3D 吸附时间平衡系数（硬编码）：3D 枪战难度曲线比俯视角陡得多，
     * 吸附时间 × 0.5 = 吸附速度快一倍（与 iso 的 computeSnapTimeMs 同一套曲线，只缩放时间）。
     */
    public static final float VP_SNAP_SCALE = 0.5f;
    /** 锁定期间重扫"有没有更近的丧尸"的间隔帧数（只影响切换优先级，不影响锁定本身）。 */
    public static final int RESCAN_FRAMES = 6;
    /** 切换目标所需的最小距离优势（瓦片）：避免两只同距离丧尸之间反复横跳。 */
    public static final float NEAREST_SWITCH_MARGIN = 0.5f;
    /** 比较"谁更近"时的平手阈值（瓦片），平手时用屏幕距离打破。 */
    public static final float NEAREST_TIE_TILES = 0.02f;

    private static long lastMs = 0L;
    private static long invalidSinceMs = 0L;
    public static int frameSeq = 0;

    public ViewpointAssist() {
    }

    // ========== 屏幕投影（与 Viewpoint 绘制同一套 Panini 映射） ==========

    public static int scrW = 1920;
    public static int scrH = 1080;
    public static double tanHalfX = 1.0;
    public static double tanHalfY = 0.6;
    public static double paniniHalfX = 1.0;
    public static double paniniHalfY = 0.6;
    public static double paniniUsed = 0.0;

    /** 每帧刷新投影参数（fov / 分辨率 / Panini 强度）。 */
    private static void updateProjection() {
        int h = 1080;
        int w = 1920;
        try {
            h = zombie.iso.IsoCamera.getScreenHeight(0);
            w = zombie.iso.IsoCamera.getScreenWidth(0);
        } catch (Throwable t) {
        }
        if (h <= 0) {
            h = 1080;
        }
        if (w <= 0) {
            w = (int) (h * 16.0 / 9.0);
        }
        scrH = h;
        scrW = w;
        float fovY = ViewpointCompat.frameFovY;
        if (fovY <= 0.1f) {
            fovY = (float) Math.toRadians(103.0);
        }
        tanHalfY = Math.tan(fovY * 0.5);
        tanHalfX = tanHalfY * ((double) w / (double) h);
        final double s = ViewpointCompat.paniniStrength;
        if (s > 0.0) {
            final double a = Math.atan(tanHalfX);
            paniniHalfX = (s + 1.0) * Math.sin(a) / (s + Math.cos(a));
            paniniHalfY = paniniHalfX * tanHalfY / tanHalfX;
            paniniUsed = s;
        } else {
            paniniHalfX = tanHalfX;
            paniniHalfY = tanHalfY;
            paniniUsed = 0.0;
        }
    }

    /** 切值 → 屏幕像素（相对屏幕中心，y 向下）。与 viewpoint.render.Panini.toScreen 一致。 */
    public static void project(double tr, double tu, float[] out) {
        if (paniniUsed > 0.0) {
            final double dx = tr * tanHalfX;
            final double dy = tu * tanHalfY;
            final double d3 = 1.0 / Math.sqrt(1.0 + dx * dx);
            final double d4 = (paniniUsed + 1.0) * d3 / (paniniUsed + d3);
            out[0] = (float) ((scrW * 0.5) * (dx * d4 / paniniHalfX));
            out[1] = (float) (-(scrH * 0.5) * (dy * d4 / paniniHalfY));
        } else {
            final double ppd = (scrH * 0.5) / tanHalfY;
            out[0] = (float) (tr * ppd);
            out[1] = (float) (-tu * ppd);
        }
    }

    public static final float[] sPx = new float[2];
    public static final double[] sTans = new double[2];

    /** 3D 捕捉半径（屏幕像素）：配置的捕获半径 × 倍率。 */
    public static float captureRadiusPx() {
        float r = RelaxedAimConfig.optionLockRadiusWorld * CAPTURE_PX_PER_TILE;
        if (r < 8.0f) {
            r = 8.0f;
        }
        return r;
    }

    private static void bumpFrame() {
        frameSeq++;
        try {
            System.setProperty("relaxedaim.frame", Integer.toString(frameSeq));
        } catch (Throwable t) {
        }
    }

    /** 射线补丁（Patch_VPShotRay）的开关属性：只有本类每帧驱动时才算"启用"。 */
    private static void setRayOn(boolean on) {
        try {
            System.setProperty("relaxedaim.rayOn", on ? "1" : "0");
        } catch (Throwable t) {
        }
    }

    /**
     * 把 tan 偏移写系统属性。
     * 同一组 tan 有两个消费者，保证准心与弹道严格一致：
     *  - Patch_VPShotRay：叠加到 viewpoint.gunplay.Sight.turned 的 right/up 参数 → 子弹射线
     *  - Patch_VPReticleOffset：乘投影换成像素 → 绘制的十字准心
     */
    private static void publish() {
        assistYawDeg = (float) Math.toDegrees(Math.atan(aimTanR));
        assistPitchDeg = (float) Math.toDegrees(Math.atan(aimTanU));
        try {
            System.setProperty("relaxedaim.aimYawDeg", Float.toString(assistYawDeg));
            System.setProperty("relaxedaim.aimPitchDeg", Float.toString(assistPitchDeg));
            System.setProperty("relaxedaim.aimTanR", Double.toString(aimTanR));
            System.setProperty("relaxedaim.aimTanU", Double.toString(aimTanU));
            System.setProperty("relaxedaim.locked", hasTarget ? "1" : "0");
        } catch (Throwable t) {
        }
        project(aimTanR, aimTanU, sPx);
        aimXpx = sPx[0];
        aimYpx = sPx[1];
        try {
            System.setProperty("relaxedaim.aimXpx", Float.toString(aimXpx));
            System.setProperty("relaxedaim.aimYpx", Float.toString(aimYpx));
        } catch (Throwable t) {
        }
    }

    /** Viewpoint 未激活 / 未瞄准：偏移归零、锁定圈归位。 */
    public static void idle() {
        bumpFrame();
        if (target != null) {
            recordRelease("idle");
        }
        target = null;
        targetId = -1;
        hasTarget = false;
        assistYawDeg = 0.0f;
        rawYawDeg = 0.0f;
        assistPitchDeg = 0.0f;
        rawPitchDeg = 0.0f;
        aimTanR = 0.0;
        aimTanU = 0.0;
        targetDist = 0.0f;
        invalidSinceMs = 0L;
        lockPx = 0.0f;
        lockPy = 0.0f;
        lockRingActive = false;
        lockProgress = 0.0f;
        lockSnapMs = 0.0f;
        debugReason = "idle";
        publish();
        setRayOn(false);
    }

    public static void update(IsoPlayer player, int pIndex) {
        bumpFrame();
        setRayOn(true);
        final long now = System.currentTimeMillis();
        float dt = (now - lastMs) / 1000.0f;
        lastMs = now;
        if (dt <= 0.0f || dt > 0.2f) {
            dt = 1.0f / 60.0f;
        }

        double desTanR = 0.0;
        double desTanU = 0.0;
        hasTarget = false;
        targetDist = 0.0f;
        debugReason = "none";
        rawYawDeg = 0.0f;
        rawPitchDeg = 0.0f;

        try {
            updateBasis();
            updateProjection();
            if (!RelaxedAimConfig.isLockOnEffective()) {
                debugReason = "lockoff";
                releaseTarget("lockoff");
            } else {
                final HandWeapon weapon = AimAssistService.getActiveWeapon(player);
                if (weapon == null || !weapon.isRanged()
                        || (RelaxedAimConfig.optionShotgunNoLock && AimAssistService.isShotgun(weapon))) {
                    debugReason = "no-weapon";
                    releaseTarget("aim");
                } else {
                    final IsoCell cell = IsoWorld.instance != null ? IsoWorld.instance.currentCell : null;
                    final ArrayList<IsoZombie> zombies = cell != null ? cell.getZombieList() : null;
                    if (zombies == null) {
                        debugReason = "no-cell";
                        releaseTarget("cell");
                    } else {
                        final float maxRange = AimAssistService.effectiveMaxLockRange(weapon);
                        final float acqDist = Math.min(maxRange, MAX_ACQUIRE_DIST);
                        final float pz = player.getZ();
                        final float px = player.getX();
                        final float py = player.getY();
                        final float capturePx = captureRadiusPx();
                        final float releasePx = capturePx * RelaxedAimConfig.reFilterMultiplier;

                        if (target != null) {
                            if (!isStillValid(target, pz, acqDist, px, py, releasePx)) {
                                if (invalidSinceMs == 0L) {
                                    invalidSinceMs = now;
                                }
                                if (now - invalidSinceMs >= Math.max(0L, RelaxedAimConfig.optionLockHoldTimeMs)) {
                                    releaseTarget("lost");
                                }
                            } else {
                                invalidSinceMs = 0L;
                                // 最近优先：锁定期间周期性重扫捕捉圈，若有明显更近的丧尸就切换
                                if ((frameSeq % RESCAN_FRAMES) == 0) {
                                    final IsoZombie nearer = acquire(zombies, pz, acqDist, px, py, capturePx);
                                    if (nearer != null && nearer != target) {
                                        final float dn = player_dist(nearer, px, py);
                                        final float dc = player_dist(target, px, py);
                                        if (dn < dc - NEAREST_SWITCH_MARGIN) {
                                            releaseTarget("nearer");
                                            target = nearer;
                                            targetId = TargetLockService.safeId(nearer);
                                        }
                                    }
                                }
                            }
                        }
                        if (target == null) {
                            target = acquire(zombies, pz, acqDist, px, py, capturePx);
                            targetId = target != null ? TargetLockService.safeId(target) : -1;
                            invalidSinceMs = 0L;
                            if (target == null) {
                                debugReason = "none";
                            }
                        }
                        if (target != null) {
                            if (headTans(target, sTans)) {
                                double tr = sTans[0];
                                double tu = sTans[1];
                                rawYawDeg = (float) Math.toDegrees(Math.atan(tr));
                                rawPitchDeg = (float) Math.toDegrees(Math.atan(tu));
                                final double maxR = Math.tan(Math.toRadians(MAX_ASSIST_DEG));
                                final double maxU = Math.tan(Math.toRadians(MAX_ASSIST_PITCH_DEG));
                                if (tr > maxR) {
                                    tr = maxR;
                                }
                                if (tr < -maxR) {
                                    tr = -maxR;
                                }
                                if (tu > maxU) {
                                    tu = maxU;
                                }
                                if (tu < -maxU) {
                                    tu = -maxU;
                                }
                                desTanR = tr;
                                desTanU = tu;
                                hasTarget = true;
                                targetDist = player_dist(target, px, py);
                                debugReason = "lock";
                            } else {
                                debugReason = "no-head";
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            debugReason = "err:" + t.getClass().getSimpleName();
        }

        // 吸附推进：复用原 iso 的瞄准时间算法（computeSnapTimeMs + progress²），
        // 捕捉后逐渐滑向头部、到达后完全跟随；失锁则快速归零（锁定圈随之回落到捕捉圈中心）。
        if (hasTarget) {
            if (!lockRingActive) {
                lockRingActive = true;
                lockSnapStartMs = now;
            }
            float snapMs = 1.0f;
            try {
                // 3D 下枪战难度曲线比俯视角陡得多：吸附时间乘 0.5（= 快一倍）作为平衡系数
                snapMs = Math.max(1.0f, TargetLockService.computeSnapTimeMs(pIndex) * VP_SNAP_SCALE);
            } catch (Throwable t) {
            }
            lockSnapMs = snapMs;
            float prog = (now - lockSnapStartMs) / snapMs;
            if (prog < 0.0f) {
                prog = 0.0f;
            }
            if (prog > 1.0f) {
                prog = 1.0f;
            }
            lockProgress = prog;
            final float f = prog * prog;
            aimTanR += (desTanR - aimTanR) * f;
            aimTanU += (desTanU - aimTanU) * f;
            if (prog >= 1.0f) {
                aimTanR = desTanR;
                aimTanU = desTanU;
            }
        } else {
            lockRingActive = false;
            lockProgress = 0.0f;
            final float k = Math.min(1.0f, dt * 12.0f);
            aimTanR += (0.0 - aimTanR) * k;
            aimTanU += (0.0 - aimTanU) * k;
            if (Math.abs(aimTanR) < 0.0002) {
                aimTanR = 0.0;
            }
            if (Math.abs(aimTanU) < 0.0002) {
                aimTanU = 0.0;
            }
        }

        publish();
        // 锁定圈画在吸附后的准心位置（= 子弹实际方向）；失锁后它会随 aimTan 归零回到捕捉圈中心
        lockPx = aimXpx;
        lockPy = aimYpx;
    }

    // ========== 相机基（Viewpoint 的「相机/弹道空间」） ==========

    /** 相机前向（单位向量，up 空间）。 */
    public static double fx = 1.0, fy = 0.0, fu = 0.0;
    /** 相机右向。 */
    public static double rx = 0.0, ry = 1.0, ru = 0.0;
    /** 相机上向。 */
    public static double ux = 0.0, uy = 0.0, uz = 1.0;

    /** 本帧目标瞄准点（头胶囊中心）的 up 空间坐标。 */
    public static double sHeadUpX = 0.0;
    public static double sHeadUpY = 0.0;
    public static double sHeadUpZ = 0.0;

    /**
     * 计算相机位置与基向量。
     * 相机位置优先取 Viewpoint 的 CrosshairAim.camera(place)（= Shot.draw 的射线起点），
     * 取不到时退回玩家瞄准原点（此时只是少了对第三人称/车内偏移的补偿）。
     */
    private static void updateBasis() {
        final double yaw = Math.toRadians(ViewpointCompat.lookYawDeg);
        final double pitch = Math.toRadians(ViewpointCompat.lookPitchDeg);
        final double cy = Math.cos(yaw);
        final double sy = Math.sin(yaw);
        final double cp = Math.cos(pitch);
        final double sp = Math.sin(pitch);
        fx = cy * cp;
        fy = sy * cp;
        fu = sp;
        rx = -sy;
        ry = cy;
        ru = 0.0;
        // up = forward × right（与 viewpoint.gunplay.Sight.set 的 ux/uy/uu 一致）
        ux = -fu * ry;
        uy = fu * rx;
        uz = fx * ry - fy * rx;

        camFromViewpoint = ViewpointCompat.readCameraPlace();
        if (camFromViewpoint) {
            camX = (float) ViewpointCompat.camPlaceX;
            camY = (float) ViewpointCompat.camPlaceY;
            camZ = (float) ViewpointCompat.camPlaceZ;
        } else {
            final IsoPlayer p = IsoPlayer.getInstance();
            camX = p != null ? p.getAimOriginPosX() : 0.0f;
            camY = p != null ? p.getAimOriginPosY() : 0.0f;
            camZ = (p != null ? p.getAimOriginPosZ() : 0.0f) * Z_SCALE;
        }
    }

    /** 把目标瞄准点换算到 up 空间（世界 z × 2.4494896 + 地形起伏）。 */
    private static boolean headAim(IsoZombie z) {
        // 3D 用 Viewpoint 头胶囊中心（Spine1→Head 的 1.46），不是头骨骼本身
        if (!TargetLockService.headAimWorldPos(z, TargetLockService.sHeadPos, false)) {
            return false;
        }
        sHeadUpX = TargetLockService.sHeadPos.x;
        sHeadUpY = TargetLockService.sHeadPos.y;
        // 地形起伏：Viewpoint 的骨骼胶囊会加上 Ground.under(角色)（= FarRelief.Lift.scene，相对相机地面），
        // 我们的瞄准点必须加同一个量，否则在坡地上会整体偏低/偏高（视坡向而定）。
        // 注意用角色自身的 x/y（Bodies 就是这么取的），倒地时头部离角色中心较远，取头部坐标会有偏差。
        sHeadUpZ = (double) TargetLockService.sHeadPos.z * Z_SCALE
                + ViewpointCompat.reliefAt(z.getX(), z.getY());
        return true;
    }

    /** 目标瞄准点在相机空间里的切值（tanR, tanU）；不可达（在身后/骨骼缺失）返回 false。 */
    private static boolean headTans(IsoZombie z, double[] out) {
        if (!headAim(z)) {
            return false;
        }
        final double vx = sHeadUpX - camX;
        final double vy = sHeadUpY - camY;
        final double vz = sHeadUpZ - camZ;
        final double fd = vx * fx + vy * fy + vz * fu;
        if (fd <= 0.2) {
            return false;
        }
        out[0] = (vx * rx + vy * ry + vz * ru) / fd;
        out[1] = (vx * ux + vy * uy + vz * uz) / fd;
        return true;
    }

    /** 目标瞄准点在屏幕上的像素偏移（相对屏幕中心）；不可达返回 null。 */
    private static float[] headScreenPx(IsoZombie z) {
        if (!headTans(z, sTans)) {
            return null;
        }
        project(sTans[0], sTans[1], sPx);
        return sPx;
    }

    private static void releaseTarget(String reason) {
        if (target != null) {
            recordRelease(reason);
        }
        target = null;
        targetId = -1;
        invalidSinceMs = 0L;
        lockRingActive = false;
    }

    private static void recordRelease(String reason) {
        debugReleaseReason = reason;
        debugReleaseTimeMs = System.currentTimeMillis();
    }

    private static boolean isStillValid(IsoZombie z, float pz, float acqDist,
                                        float px, float py, float releasePx) {
        try {
            if (z.isDead() || !z.isAlive()) {
                debugReason = "dead";
                return false;
            }
            if (Math.abs(z.getZ() - pz) > AimAssistService.FLOOR_TOLERANCE) {
                debugReason = "floor";
                return false;
            }
            if (player_dist(z, px, py) > acqDist) {
                debugReason = "range";
                return false;
            }
            final float[] sp = headScreenPx(z);
            if (sp == null) {
                debugReason = "no-head";
                return false;
            }
            final float d = (float) Math.sqrt(sp[0] * sp[0] + sp[1] * sp[1]);
            if (d > releasePx) {
                debugReason = "mouse";
                return false;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static IsoZombie acquire(ArrayList<IsoZombie> zombies, float pz, float acqDist,
                                     float px, float py, float capturePx) {
        IsoZombie best = null;
        float bestDist = Float.MAX_VALUE;
        float bestScreen = Float.MAX_VALUE;
        for (final IsoZombie z : zombies) {
            if (z == null || z.isDead() || !z.isAlive()) {
                continue;
            }
            try {
                if (Math.abs(z.getZ() - pz) > AimAssistService.FLOOR_TOLERANCE) {
                    continue;
                }
                final float d = player_dist(z, px, py);
                if (d > acqDist) {
                    continue;
                }
                final float[] sp = headScreenPx(z);
                if (sp == null) {
                    continue;
                }
                final float sd = (float) Math.sqrt(sp[0] * sp[0] + sp[1] * sp[1]);
                if (sd > capturePx) {
                    continue;
                }
                if (d < bestDist - NEAREST_TIE_TILES
                        || (Math.abs(d - bestDist) <= NEAREST_TIE_TILES && sd < bestScreen)) {
                    bestDist = d;
                    bestScreen = sd;
                    best = z;
                }
            } catch (Throwable t) {
            }
        }
        return best;
    }

    private static float player_dist(IsoZombie z, float px, float py) {
        final float dx = z.getX() - px;
        final float dy = z.getY() - py;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
