package com.relaxedaim;

import java.util.List;

import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.core.Core;
import zombie.input.AimingReticle;
import zombie.inventory.types.HandWeapon;
import zombie.iso.IsoCamera;
import zombie.iso.IsoUtils;

/**
 * Phase 3: 目标锁定状态机（滞回式）。
 *
 * 规则（对应配置字段）：
 * 1. 筛选范围 lockRadiusPx：鼠标附近该屏幕像素范围内的合法丧尸进入候选。
 * 2. 候选内取「距离玩家最近」的一只锁定并缓存。
 * 3. 锁定后保持稳定：仅当锁定目标死亡 / 换层 / 出武器射程 / 不可见，
 *    或与鼠标的屏幕距离超过 lockRadiusPx * reFilterMultiplier 时才解除。
 * 4. 解除时记录原因（供 HUD 显示），下一次节流搜索帧重新锁定。
 *
 * 注意：开发阶段所有成员保持 public（见 RULES.md）。
 */
public final class TargetLockService {

    public static IsoZombie lockedTarget = null;
    public static int lockPlayerIndex = 0;
    public static float lockScreenDist = 0.0f;
    public static float lockWorldDist = 0.0f;

    public static String pendingReleaseReason = "none";
    public static String debugReleaseReason = "none";
    public static long debugReleaseTimeMs = 0L;

    // Debug 状态（供 HUD 读取）
    public static boolean debugHasLock = false;
    public static int debugLockId = -1;
    public static float debugLockScreenDist = 0.0f;
    public static float debugLockWorldDist = 0.0f;

    public TargetLockService() {
    }

    public static IsoZombie getLockedTarget() {
        return lockedTarget;
    }

    /**
     * 世界坐标 -> UI 屏幕像素（与游戏头顶名牌 getNameCoords 同一坐标系）：
     * (XToScreenExact / zoom) + fixJigglyModels。
     * 不除以 zoom 的话，滚轮缩放相机时标记会相对丧尸漂移（核心 bug 修复）。
     */
    public static float getZoom(int pIndex) {
        try {
            return Core.getInstance().getZoom(pIndex);
        } catch (Exception e) {
            return 1.0f;
        }
    }

    public static float worldScreenX(float x, float y, float z, int pIndex) {
        float fix = 0.0f;
        try {
            if (IsoCamera.cameras != null && pIndex >= 0 && pIndex < IsoCamera.cameras.length
                    && IsoCamera.cameras[pIndex] != null) {
                fix = IsoCamera.cameras[pIndex].fixJigglyModelsX;
            }
        } catch (Exception e) {
        }
        return IsoUtils.XToScreenExact(x, y, z, pIndex) / getZoom(pIndex) + fix;
    }

    public static float worldScreenY(float x, float y, float z, int pIndex) {
        float fix = 0.0f;
        try {
            if (IsoCamera.cameras != null && pIndex >= 0 && pIndex < IsoCamera.cameras.length
                    && IsoCamera.cameras[pIndex] != null) {
                fix = IsoCamera.cameras[pIndex].fixJigglyModelsY;
            }
        } catch (Exception e) {
        }
        return IsoUtils.YToScreenExact(x, y, z, pIndex) / getZoom(pIndex) + fix;
    }

    /** 瞄准点（胸口高度）的世界 -> UI 屏幕坐标。 */
    public static float aimScreenX(IsoZombie z, int pIndex) {
        return worldScreenX(z.getX(), z.getY(), z.getAimOriginPosZ(), pIndex);
    }

    public static float aimScreenY(IsoZombie z, int pIndex) {
        return worldScreenY(z.getX(), z.getY(), z.getAimOriginPosZ(), pIndex);
    }

    /** 世界空间瞄准点：用游戏完全相同的换算（AimingReticle.getX/getY → XToIso/YToIso）得出准星指向的世界坐标。 */
    public static float aimWorldX = 0.0f;
    public static float aimWorldY = 0.0f;
    public static float aimWorldZ = 0.0f;
    public static boolean aimWorldValid = false;

    /** 复刻游戏 calculateAimVector 的准星世界点：XToIso(pIndex, AimingReticle.getX, getY, aimOriginPosZ)。 */
    public static void updateAimWorld(IsoPlayer player, int pIndex) {
        aimWorldValid = false;
        try {
            final int retX = AimingReticle.getX(pIndex);
            final int retY = AimingReticle.getY(pIndex);
            final float z = player.getAimOriginPosZ();
            aimWorldX = IsoUtils.XToIso(pIndex, retX, retY, z);
            aimWorldY = IsoUtils.YToIso(pIndex, retX, retY, z);
            aimWorldZ = z;
            aimWorldValid = true;
        } catch (Exception e) {
        }
    }

    /** 丧尸到世界瞄准点的水平距离（瓦片）。 */
    public static float aimWorldDistTo(IsoZombie z) {
        final float dx = z.getX() - aimWorldX;
        final float dy = z.getY() - aimWorldY;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // ========== 原始鼠标世界瞄准点（锁定簿记用，不受 reticle 覆盖影响） ==========

    /**
     * 锁定生效期间 reticle 被覆盖为锁定丧尸头部，导致 aimWorld 恒指向锁定目标，
     * 释放/重筛判定必须用「原始鼠标」算出的世界瞄准点，否则移动鼠标永远无法释放锁定。
     */
    public static float rawAimWorldX = 0.0f;
    public static float rawAimWorldY = 0.0f;
    public static float rawAimWorldZ = 0.0f;
    public static boolean rawAimWorldValid = false;

    /** 用原始鼠标（Mouse.getXA/YA × zoom → XToIso）计算世界瞄准点。 */
    public static void updateRawAimWorld(IsoPlayer player, int pIndex) {
        rawAimWorldValid = false;
        try {
            final float zoom = getZoom(pIndex);
            final float rx = zombie.input.Mouse.getXA() * zoom;
            final float ry = zombie.input.Mouse.getYA() * zoom;
            final float z = player.getAimOriginPosZ();
            rawAimWorldX = IsoUtils.XToIso(pIndex, rx, ry, z);
            rawAimWorldY = IsoUtils.YToIso(pIndex, rx, ry, z);
            rawAimWorldZ = z;
            rawAimWorldValid = true;
        } catch (Exception e) {
        }
    }

    public static float rawAimWorldDistTo(IsoZombie z) {
        final float dx = z.getX() - rawAimWorldX;
        final float dy = z.getY() - rawAimWorldY;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // ========== 锁定核心：覆盖 AimingReticle 使准星/弹道对准锁定丧尸头部 ==========

    /** 复用 Vector3，避免每次分配。 */
    public static final zombie.iso.Vector3 sHeadPos = new zombie.iso.Vector3();
    public static final zombie.iso.Vector3 sNeckPos = new zombie.iso.Vector3();

    /**
     * 头部世界瞄准点 = Bip01_Head 骨骼 + 沿「颈部→头部」方向的偏移（headAimOffset）。
     * 方向随姿态自适应：待机低头沿头骨倾斜方向、奔跑略前倾、倒地沿贴地的头骨方向，不依赖简单 Z 轴。
     */
    public static boolean headAimWorldPos(IsoZombie z, zombie.iso.Vector3 out) {
        try {
            zombie.CombatManager.getBoneWorldPos(z, "Bip01_Head", out);
            zombie.CombatManager.getBoneWorldPos(z, "Bip01_Neck", sNeckPos);
            float dx = out.x - sNeckPos.x;
            float dy = out.y - sNeckPos.y;
            float dz = out.z - sNeckPos.z;
            final float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 0.001f) {
                dx /= len;
                dy /= len;
                dz /= len;
                out.x += dx * RelaxedAimConfig.headAimOffset;
                out.y += dy * RelaxedAimConfig.headAimOffset;
                out.z += dz * RelaxedAimConfig.headAimOffset;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- 平滑准心吸附 ----------

    /** 平滑吸附：当前准星（reticle）屏幕位置，从原始鼠标向锁定头部平滑移动。 */
    public static int smoothFrameStamp = -1;
    public static boolean smoothInit = false;
    public static float smoothX = 0.0f;
    public static float smoothY = 0.0f;
    public static long snapStartMs = 0L;

    /** 强锁定状态：准心进入强锁定阈值内后完全吸附头部，不再平滑；换目标后重置。 */
    public static boolean strongLock = false;

    /**
     * 平滑时间（毫秒）：对数衰减公式（瞄准等级收益递减）。
     * effect = clamp(assist * snapStrengthScale, 0, 1) * ln(1+aimLevel)/ln(1+snapAimCap)
     * snapTime = snapMaxMs - (snapMaxMs - snapMinMs) * effect
     */
    public static float computeSnapTimeMs(int pIndex) {
        try {
            final float effect = Math.min(1f, Math.max(0f, RelaxedAimConfig.assistStrength()
                    * RelaxedAimConfig.snapStrengthScale));
            int aimLevel = 0;
            try {
                aimLevel = zombie.characters.IsoPlayer.getPlayer(pIndex)
                        .getPerkLevel(zombie.characters.skills.PerkFactory.Perks.Aiming);
            } catch (Exception e) {
            }
            final float cap = Math.max(1f, RelaxedAimConfig.snapAimCap);
            final float gain = (float) (Math.log(1.0 + aimLevel) / Math.log(1.0 + cap));
            float ms = RelaxedAimConfig.snapMaxMs
                    - (RelaxedAimConfig.snapMaxMs - RelaxedAimConfig.snapMinMs) * effect * gain;
            final float min = Math.max(10f, RelaxedAimConfig.snapMinMs);
            final float max = Math.max(min, RelaxedAimConfig.snapMaxMs);
            return ms < min ? min : (ms > max ? max : ms);
        } catch (Exception e) {
            return 500.0f;
        }
    }

    /**
     * 每帧推进一次平滑值（以帧号去重）。
     * 用「线性进度」趋近：progress = elapsed/snapTime，f = progress²（加速趋近），
     * 保证在 snapTime 内必然到达头部（progress=1 时 f=1 → 精确锁定），
     * 无论目标以何种速度移动，实际辅助锁定耗时都不会超过期望值。
     */
    public static void updateLockedSmooth(int pIndex) {
        final int frame = AimAssistService.frameCounter;
        if (frame == smoothFrameStamp) {
            return;
        }
        smoothFrameStamp = frame;
        try {
            headAimWorldPos(lockedTarget, sHeadPos);
            final float tx = worldScreenX(sHeadPos.x, sHeadPos.y, sHeadPos.z, pIndex);
            final float ty = worldScreenY(sHeadPos.x, sHeadPos.y, sHeadPos.z, pIndex);
            // 强锁定：完全吸附头部，不再平滑
            if (strongLock) {
                smoothX = tx;
                smoothY = ty;
                return;
            }
            final long now = System.currentTimeMillis();
            if (!smoothInit) {
                smoothInit = true;
                smoothX = zombie.input.Mouse.getXA();
                smoothY = zombie.input.Mouse.getYA();
                snapStartMs = now;
            }
            final float snapMs = computeSnapTimeMs(pIndex);
            float progress = snapMs <= 0f ? 1f : (now - snapStartMs) / snapMs;
            if (progress < 0f) {
                progress = 0f;
            }
            if (progress > 1f) {
                progress = 1f;
            }
            // 加速趋近：progress=1 时 f=1 → 精确到达当前头部位置
            final float f = progress * progress;
            smoothX += (tx - smoothX) * f;
            smoothY += (ty - smoothY) * f;
            // 进入强锁定阈值：此后完全吸附头部
            final float dx = smoothX - tx;
            final float dy = smoothY - ty;
            if (dx * dx + dy * dy < RelaxedAimConfig.STRONG_LOCK_THRESHOLD_PX
                    * RelaxedAimConfig.STRONG_LOCK_THRESHOLD_PX) {
                strongLock = true;
            }
        } catch (Exception e) {
        }
    }

    /**
     * 锁定瞄准是否生效（本地玩家 + 持远程武器瞄准 + 非霰弹枪开关 + 有锁定目标）。
     * 供 AimingReticle.getXA/getYA 覆盖判定使用。
     */
    public static boolean isLockAimingActive(int pIndex) {
        if (!RelaxedAimConfig.isLockOnEffective() || lockedTarget == null) {
            return false;
        }
        try {
            final int local = zombie.characters.IsoPlayer.getPlayerIndex();
            if (pIndex != local) {
                return false;
            }
            final zombie.characters.IsoPlayer player = zombie.characters.IsoPlayer.getPlayer(pIndex);
            if (player == null || !player.isAiming()) {
                return false;
            }
            final HandWeapon weapon = AimAssistService.getActiveWeapon(player);
            if (weapon == null || !weapon.isRanged()) {
                return false;
            }
            if (RelaxedAimConfig.optionShotgunNoLock && AimAssistService.isShotgun(weapon)) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 返回锁定丧尸头部的屏幕X（平滑）；不生效返回 Integer.MIN_VALUE。 */
    public static int overrideReticleX(int pIndex) {
        try {
            if (!isLockAimingActive(pIndex)) {
                smoothInit = false;
                return Integer.MIN_VALUE;
            }
            updateLockedSmooth(pIndex);
            return (int) smoothX;
        } catch (Exception e) {
            return Integer.MIN_VALUE;
        }
    }

    public static int overrideReticleY(int pIndex) {
        try {
            if (!isLockAimingActive(pIndex)) {
                smoothInit = false;
                return Integer.MIN_VALUE;
            }
            updateLockedSmooth(pIndex);
            return (int) smoothY;
        } catch (Exception e) {
            return Integer.MIN_VALUE;
        }
    }

    /**
     * 鼠标「虚拟空间」坐标 = 原始像素 × zoom。
     * 游戏准星/瞄准（AimingReticle.getX/getY）正是 `Mouse.getXA() * Core.getZoom()`，
     * 并用该值经 XToIso 求世界瞄准点。候选筛选/锁定校验必须用同一空间：
     * 比较 `mouse×zoom` 与 `XToScreenExact(丧尸)`，即可在任何缩放等级下与准星严格对齐。
     */
    public static float mouseVirtualX(float mouseX, int pIndex) {
        return mouseX * getZoom(pIndex);
    }

    public static float mouseVirtualY(float mouseY, int pIndex) {
        return mouseY * getZoom(pIndex);
    }

    /** 丧尸瞄准点在「虚拟空间」的坐标 = XToScreenExact（不含 offX/zoom/fix，与准星换算同一基准）。 */
    public static float aimVirtualX(IsoZombie z, int pIndex) {
        return IsoUtils.XToScreenExact(z.getX(), z.getY(), z.getAimOriginPosZ(), pIndex);
    }

    public static float aimVirtualY(IsoZombie z, int pIndex) {
        return IsoUtils.YToScreenExact(z.getX(), z.getY(), z.getAimOriginPosZ(), pIndex);
    }

    /**
     * 鼠标筛选半径（虚拟空间单位）按相机 zoom 缩放：
     * 屏幕像素 × zoom 即虚拟单位，保证任意缩放下的「屏幕可见半径」恒定。
     */
    public static float effectiveLockRadiusPx(int pIndex) {
        return RelaxedAimConfig.lockRadiusPx * getZoom(pIndex);
    }

    public static float effectiveReFilterRadiusPx(int pIndex) {
        return RelaxedAimConfig.lockRadiusPx * RelaxedAimConfig.reFilterMultiplier * getZoom(pIndex);
    }

    /**
     * 每帧调用（玩家瞄准且持远程武器时）。
     *
     * @param candidates 节流搜索帧刚算出的候选集合；非搜索帧传 null（此时只做锁定校验）。
     */
    public static void update(IsoPlayer player, HandWeapon weapon, int pIndex,
                              int mouseX, int mouseY, List<IsoZombie> candidates) {
        lockPlayerIndex = pIndex;

        if (lockedTarget != null) {
            if (isLockValid(player, weapon, mouseX, mouseY)) {
                invalidSinceMs = 0L;
                updateDebug();
                return; // 锁定仍有效，保持不变（滞回区）
            }
            // 保持时间：非「死亡」原因在短暂失效时暂不释放；鼠标移出为主动切换意图，立即释放。
            // 例外：击倒/倒地中的丧尸（击倒位移属瞬时，且可能在锁定圈内）即使 mouse 原因也享受保持。
            boolean holdAllowed = !"dead".equals(pendingReleaseReason);
            if ("mouse".equals(pendingReleaseReason)) {
                boolean down = false;
                try {
                    down = lockedTarget.isKnockedDown() || lockedTarget.isProne();
                } catch (Exception e) {
                }
                holdAllowed = down;
            }
            if (holdAllowed && RelaxedAimConfig.optionLockHoldTimeMs > 0) {
                final long now = System.currentTimeMillis();
                if (invalidSinceMs == 0L) {
                    invalidSinceMs = now;
                }
                if (now - invalidSinceMs < RelaxedAimConfig.optionLockHoldTimeMs) {
                    updateDebug();
                    return; // 在保持时间内，暂不释放
                }
            }
            invalidSinceMs = 0L;
            releaseLock(pendingReleaseReason);
            // 解除后若本帧已有候选则立即重新锁定，否则等下一次搜索帧
            if (candidates != null) {
                tryAcquire(candidates, player, mouseX, mouseY);
            }
        } else if (candidates != null) {
            tryAcquire(candidates, player, mouseX, mouseY);
        }

        updateDebug();
    }

    /** 锁定暂时失效的起始时间（毫秒），配合 optionLockHoldTimeMs 使用。 */
    public static long invalidSinceMs = 0L;

    /** 清除锁定（未瞄准 / 未持枪时调用）。完全释放：下次平滑从鼠标重新开始。 */
    public static void clearLock(String reason) {
        if (lockedTarget != null) {
            releaseLock(reason);
        }
        lockedTarget = null;
        invalidSinceMs = 0L;
        smoothInit = false;
    }

    /** 锁定有效性校验（每帧对锁定目标做一次，开销极小）。失败时把原因写入 pendingReleaseReason。 */
    public static boolean isLockValid(IsoPlayer player, HandWeapon weapon, int mouseX, int mouseY) {
        final IsoZombie z = lockedTarget;
        if (z == null) {
            pendingReleaseReason = "invalid";
            return false;
        }
        try {
            if (z.isDead() || !z.isAlive()) {
                pendingReleaseReason = "dead";
                return false;
            }
            // 击倒/倒地丧尸：放宽楼层判定、跳过遮挡判定（倒地时这些检查易误判导致丢锁定）
            final boolean down = z.isKnockedDown() || z.isProne();
            final float floorTol = down ? 2.0f : AimAssistService.FLOOR_TOLERANCE;
            if (Math.abs(z.getZ() - player.getZ()) > floorTol) {
                pendingReleaseReason = "floor";
                return false;
            }
            if (!down && z.getSquare() != null && !z.getSquare().getCanSee(lockPlayerIndex)) {
                pendingReleaseReason = "occluded";
                return false;
            }
            if (weapon == null || !weapon.isRanged()) {
                pendingReleaseReason = "aim";
                return false;
            }
            final float worldDist = player.DistTo(z);
            final float maxRange = weapon.getMaxRange();
            final float lockMax = RelaxedAimConfig.optionMaxLockDistance;
            if (worldDist > (lockMax > 0f && lockMax < maxRange ? lockMax : maxRange)) {
                pendingReleaseReason = "range";
                return false;
            }
            // 释放/重筛用「原始鼠标」世界瞄准点（reticle 覆盖期间 aimWorld 恒为锁定目标，不可用于释放判定）
            if (rawAimWorldValid) {
                final float aimDist = rawAimWorldDistTo(z);
                lockScreenDist = aimDist;
                lockWorldDist = worldDist;
                if (aimDist > RelaxedAimConfig.optionLockRadiusWorld * RelaxedAimConfig.reFilterMultiplier) {
                    pendingReleaseReason = "mouse";
                    return false;
                }
            } else {
                final float sx = aimVirtualX(z, lockPlayerIndex);
                final float sy = aimVirtualY(z, lockPlayerIndex);
                final float screenDist = IsoUtils.DistanceTo(
                        mouseVirtualX(mouseX, lockPlayerIndex), mouseVirtualY(mouseY, lockPlayerIndex), sx, sy);
                lockScreenDist = screenDist;
                lockWorldDist = worldDist;
                if (screenDist > effectiveReFilterRadiusPx(lockPlayerIndex)) {
                    pendingReleaseReason = "mouse";
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            pendingReleaseReason = "invalid";
            return false;
        }
    }

    /** 从候选集合（已按鼠标筛选范围过滤）中取距离玩家最近的一只。 */
    public static void tryAcquire(List<IsoZombie> candidates, IsoPlayer player, int mouseX, int mouseY) {
        IsoZombie best = null;
        float bestWorldDist = Float.MAX_VALUE;
        float bestScreenDist = 0.0f;
        for (final IsoZombie z : candidates) {
            if (z == null || z.isDead() || !z.isAlive()) {
                continue;
            }
            try {
                final float d = player.DistTo(z);
                if (d < bestWorldDist) {
                    bestWorldDist = d;
                    best = z;
                    bestScreenDist = computeScreenDist(z, mouseX, mouseY);
                }
            } catch (Exception e) {
            }
        }
        if (best != null) {
            lockedTarget = best;
            lockWorldDist = bestWorldDist;
            lockScreenDist = bestScreenDist;
            // 新目标：重置强锁定；重置平滑时间窗（保留当前准心位置作为起点，避免跳变）
            strongLock = false;
            snapStartMs = System.currentTimeMillis();
            System.out.println("[RelaxedAim] LOCK acquired: zombieId=" + safeId(best)
                    + " worldDist=" + String.format("%.2f", lockWorldDist)
                    + " screenDist=" + String.format("%.1f", lockScreenDist));
        }
    }

    public static float computeScreenDist(IsoZombie z, int mouseX, int mouseY) {
        try {
            if (rawAimWorldValid) {
                return rawAimWorldDistTo(z);
            }
            final float sx = aimVirtualX(z, lockPlayerIndex);
            final float sy = aimVirtualY(z, lockPlayerIndex);
            return IsoUtils.DistanceTo(
                    mouseVirtualX(mouseX, lockPlayerIndex), mouseVirtualY(mouseY, lockPlayerIndex), sx, sy);
        } catch (Exception e) {
            return 0.0f;
        }
    }

    public static void releaseLock(String reason) {
        if (lockedTarget != null) {
            System.out.println("[RelaxedAim] LOCK released: zombieId=" + safeId(lockedTarget) + " reason=" + reason);
        }
        debugReleaseReason = reason;
        debugReleaseTimeMs = System.currentTimeMillis();
        lockedTarget = null;
        strongLock = false;
        // 不重置 smoothInit：若紧随其后立即重吸，则从当前准心位置继续平滑，避免跳变；
        // 真正释放（无重吸）时由 overrideReticleX 的未激活分支把 smoothInit 置 false。
    }

    public static int safeId(IsoZombie z) {
        try {
            return z.zombieId;
        } catch (Exception e) {
            return -1;
        }
    }

    public static void updateDebug() {
        debugHasLock = lockedTarget != null;
        debugLockId = lockedTarget != null ? safeId(lockedTarget) : -1;
        debugLockScreenDist = lockScreenDist;
        debugLockWorldDist = lockWorldDist;
    }
}