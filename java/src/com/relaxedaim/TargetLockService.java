package com.relaxedaim;

import java.util.List;

import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.inventory.types.HandWeapon;
import zombie.iso.IsoUtils;

/**
 * Phase 3: 目标锁定状态机（滞回式）。
 *
 * 规则（对应配置字段）：
 * 1. 筛选范围 lockRadiusPx：鼠标附近该屏幕像素范围内的合法丧尸进入候选。
 * 2. 候选内取「距离玩家最近」的一只锁定并缓存。
 * 3. 锁定后保持稳定：仅当锁定目标死亡 / 换层 / 出武器射程 / 不可见，
 *    或与鼠标的屏幕距离超过 lockRadiusPx * reFilterMultiplier 时才解除。
 * 4. 解除后下一次节流搜索帧重新锁定，避免目标在相邻丧尸间抖动。
 */
public final class TargetLockService {

    public static IsoZombie lockedTarget = null;
    public static int playerIndex = 0;
    public static float lockScreenDist = 0.0f;
    public static float lockWorldDist = 0.0f;

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
     * 每帧调用（玩家瞄准且持远程武器时）。
     *
     * @param candidates 节流搜索帧刚算出的候选集合；非搜索帧传 null（此时只做锁定校验）。
     */
    public static void update(IsoPlayer player, HandWeapon weapon, int pIndex,
                              int mouseX, int mouseY, List<IsoZombie> candidates) {
        playerIndex = pIndex;

        if (lockedTarget != null) {
            if (isLockValid(player, weapon, mouseX, mouseY)) {
                updateDebug();
                return; // 锁定仍有效，保持不变（滞回区）
            }
            releaseLock();
            // 解除后若本帧已有候选则立即重新锁定，否则等下一次搜索帧
            if (candidates != null) {
                tryAcquire(candidates, player, mouseX, mouseY);
            }
        } else if (candidates != null) {
            tryAcquire(candidates, player, mouseX, mouseY);
        }

        updateDebug();
    }

    /** 清除锁定（未瞄准 / 未持枪时调用）。 */
    public static void clearLock() {
        if (lockedTarget != null) {
            releaseLock();
        }
        lockedTarget = null;
    }

    /** 锁定有效性校验（每帧对锁定目标做一次，开销极小）。 */
    public static boolean isLockValid(IsoPlayer player, HandWeapon weapon, int mouseX, int mouseY) {
        final IsoZombie z = lockedTarget;
        if (z == null) {
            return false;
        }
        try {
            if (z.isDead() || !z.isAlive()) {
                return false;
            }
            if (Math.abs(z.getZ() - player.getZ()) > AimAssistService.FLOOR_TOLERANCE) {
                return false;
            }
            if (weapon == null || !weapon.isRanged()) {
                return false;
            }
            final float worldDist = player.DistTo(z);
            if (worldDist > weapon.getMaxRange()) {
                return false;
            }
            if (z.getSquare() != null && !z.getSquare().getCanSee(playerIndex)) {
                return false;
            }
            final float sx = IsoUtils.XToScreenExact(z.getX(), z.getY(), z.getAimOriginPosZ(), playerIndex);
            final float sy = IsoUtils.YToScreenExact(z.getX(), z.getY(), z.getAimOriginPosZ(), playerIndex);
            final float screenDist = IsoUtils.DistanceTo(mouseX, mouseY, sx, sy);
            lockScreenDist = screenDist;
            lockWorldDist = worldDist;
            return screenDist <= RelaxedAimConfig.lockRadiusPx * RelaxedAimConfig.reFilterMultiplier;
        } catch (Exception e) {
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
            System.out.println("[RelaxedAim] LOCK acquired: zombieId=" + safeId(best)
                    + " worldDist=" + String.format("%.2f", lockWorldDist)
                    + " screenDist=" + String.format("%.1f", lockScreenDist));
        }
    }

    public static float computeScreenDist(IsoZombie z, int mouseX, int mouseY) {
        try {
            final float sx = IsoUtils.XToScreenExact(z.getX(), z.getY(), z.getAimOriginPosZ(), playerIndex);
            final float sy = IsoUtils.YToScreenExact(z.getX(), z.getY(), z.getAimOriginPosZ(), playerIndex);
            return IsoUtils.DistanceTo(mouseX, mouseY, sx, sy);
        } catch (Exception e) {
            return 0.0f;
        }
    }

    public static void releaseLock() {
        if (lockedTarget != null) {
            System.out.println("[RelaxedAim] LOCK released: zombieId=" + safeId(lockedTarget)
                    + " reason=invalid");
        }
        lockedTarget = null;
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