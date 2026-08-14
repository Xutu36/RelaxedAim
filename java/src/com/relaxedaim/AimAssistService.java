package com.relaxedaim;

import java.util.ArrayList;
import java.util.List;

import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.inventory.InventoryItem;
import zombie.inventory.types.HandWeapon;
import zombie.iso.IsoCell;
import zombie.iso.IsoUtils;
import zombie.iso.IsoWorld;

/**
 * Phase 2 + Phase 3: 只读瞄准状态采集、候选丧尸侦测与目标锁定。
 *
 * VERSION: Lock-v1.0 - 引入 TargetLockService 滞回式锁定与鼠标筛选范围
 */
public final class AimAssistService {

    public static final float MAX_WORLD_SCAN_RADIUS_TILES = 60.0f;
    public static final float FLOOR_TOLERANCE = 0.5f;

    private static int frameCounter = 0;
    private static int lastLogFrame = 0;
    private static final int LOG_INTERVAL_FRAMES = 30;

    // Debug state（用于外部访问）
    public static boolean debugIsAiming = false;
    public static boolean debugHasRangedWeapon = false;
    public static String debugWeaponName = "-";
    public static float debugWeaponRange = 0.0f;
    public static int debugMouseX = 0;
    public static int debugMouseY = 0;
    public static float debugPlayerZ = 0.0f;
    public static int debugTotalZombies = 0;
    public static int debugCandidateCount = 0;

    private static boolean lastActive = false;
    private static int lastCandidateCount = 0;

    private AimAssistService() {
    }

    public static boolean isReady() {
        return true;
    }

    public static void markNotAiming() {
        debugIsAiming = false;
        debugCandidateCount = 0;
        TargetLockService.clearLock();
    }

    /**
     * 只在玩家瞄准时调用的更新方法
     *
     * @param player 玩家实例（已确认非null）
     * @param mouseX 鼠标X坐标
     * @param mouseY 鼠标Y坐标
     * @param playerX 玩家X坐标
     * @param playerY 玩家Y坐标
     * @param playerZ 玩家Z坐标
     */
    public static void updateAiming(IsoPlayer player, int mouseX, int mouseY,
                                     float playerX, float playerY, float playerZ) {
        frameCounter++;

        // 更新调试状态
        debugMouseX = mouseX;
        debugMouseY = mouseY;
        debugPlayerZ = playerZ;
        debugIsAiming = true;

        // 【安全检查1】确保游戏世界实例存在
        if (IsoWorld.instance == null) {
            System.out.println("[RelaxedAim DEBUG] IsoWorld.instance is null");
            return;
        }

        // 【安全检查2】确保当前单元格已加载
        final IsoCell cell = IsoWorld.instance.currentCell;
        if (cell == null) {
            System.out.println("[RelaxedAim DEBUG] currentCell is null");
            return;
        }

        // 【安全检查3】确保丧尸列表可用
        final ArrayList<IsoZombie> zombies;
        try {
            zombies = cell.getZombieList();
            if (zombies == null) {
                System.out.println("[RelaxedAim DEBUG] Zombie list is null");
                return;
            }
        } catch (Exception e) {
            System.out.println("[RelaxedAim DEBUG] Exception getting zombie list: " + e.getMessage());
            return;
        }

        // 【安全检查4】获取武器信息
        final HandWeapon weapon;
        try {
            weapon = getActiveWeapon(player);
        } catch (Exception e) {
            System.out.println("[RelaxedAim DEBUG] Exception getting weapon: " + e.getMessage());
            return;
        }

        if (weapon != null) {
            debugHasRangedWeapon = weapon.isRanged();
            try {
                debugWeaponName = weapon.getName();
            } catch (Exception e) {
                debugWeaponName = "?";
            }
            debugWeaponRange = weapon.getMaxRange();
        } else {
            debugWeaponName = "-";
            debugHasRangedWeapon = false;
            debugWeaponRange = 0.0f;
        }

        if (weapon == null || !weapon.isRanged()) {
            TargetLockService.clearLock();
            return;
        }

        final int pIndex = getPlayerIndex();

        // 节流搜索帧：重新扫描候选集合
        List<IsoZombie> candidates = null;
        if (frameCounter % RelaxedAimConfig.searchIntervalFrames == 0) {
            System.out.println("[RelaxedAim DEBUG] === Frame " + frameCounter + " (AIMING) ===");
            System.out.println("[RelaxedAim DEBUG] Mouse: (" + mouseX + ", " + mouseY + ")");
            System.out.println("[RelaxedAim DEBUG] Player pos: (" + playerX + ", " + playerY + ", " + playerZ + ")");
            System.out.println("[RelaxedAim DEBUG] Weapon: " + debugWeaponName + ", ranged: " + debugHasRangedWeapon + ", range: " + debugWeaponRange);
            System.out.println("[RelaxedAim DEBUG] LockRadius: " + RelaxedAimConfig.lockRadiusPx
                    + "px, reFilter x" + RelaxedAimConfig.reFilterMultiplier);
            System.out.println("[RelaxedAim DEBUG] === Starting zombie scan ===");
            candidates = findCandidates(player, weapon, zombies, pIndex, mouseX, mouseY);
            debugCandidateCount = candidates.size();
            System.out.println("[RelaxedAim DEBUG] Scan complete. Candidates: " + debugCandidateCount);
        }

        // 锁定状态机：每帧校验，节流帧（candidates != null）尝试获取/更新
        TargetLockService.update(player, weapon, pIndex, mouseX, mouseY, candidates);

        debugLog(true, debugCandidateCount);
    }

    private static HandWeapon getActiveWeapon(final IsoPlayer player) throws Exception {
        InventoryItem item = player.getPrimaryHandItem();
        if (item instanceof HandWeapon) {
            return (HandWeapon) item;
        }
        item = player.getSecondaryHandItem();
        if (item instanceof HandWeapon) {
            return (HandWeapon) item;
        }
        return player.getUseHandWeapon();
    }

    private static int playerIndex = -1;

    private static int getPlayerIndex() {
        try {
            playerIndex = IsoPlayer.getPlayerIndex();
        } catch (Exception e) {
            System.out.println("[RelaxedAim DEBUG] Exception getting player index: " + e.getMessage());
            playerIndex = 0;
        }
        return playerIndex;
    }

    /**
     * 单遍扫描：廉价过滤（存活/楼层/射程/可见性）后，仅收集「鼠标附近 lockRadiusPx 内」的丧尸。
     * 候选数量达到 maxCandidates 即停止（有限遍历）。
     * 投影统一使用 getAimOriginPosZ()（瞄准点/胸口高度），与锁定标记一致。
     */
    private static List<IsoZombie> findCandidates(final IsoPlayer player, final HandWeapon weapon,
            final ArrayList<IsoZombie> zombies, int playerIndex, int mouseX, int mouseY) {
        final List<IsoZombie> candidates = new ArrayList<>();

        debugTotalZombies = 0;
        int rejectedFloor = 0;
        int rejectedRange = 0;
        int rejectedVisibility = 0;
        int rejectedMouse = 0;

        if (zombies == null || zombies.isEmpty()) {
            System.out.println("[RelaxedAim DEBUG] No zombies in cell");
            return candidates;
        }

        debugTotalZombies = zombies.size();

        final float playerZ;
        try {
            playerZ = player.getZ();
        } catch (Exception e) {
            System.out.println("[RelaxedAim DEBUG] Exception getting player Z: " + e.getMessage());
            return candidates;
        }

        float maxRange = weapon.getMaxRange();
        if (maxRange <= 0.0f || maxRange > MAX_WORLD_SCAN_RADIUS_TILES) {
            maxRange = MAX_WORLD_SCAN_RADIUS_TILES;
        }

        final float lockRadius = RelaxedAimConfig.lockRadiusPx;
        final int maxCands = RelaxedAimConfig.maxCandidates;

        float minScreenDist = Float.MAX_VALUE;

        for (final IsoZombie zombie : zombies) {
            if (candidates.size() >= maxCands) {
                break; // 有限遍历上限，达到即停止收集
            }
            if (zombie == null || zombie.isDead() || !zombie.isAlive()) {
                continue;
            }

            // Floor check
            float zombieZ;
            try {
                zombieZ = zombie.getZ();
            } catch (Exception e) {
                continue; // 无法获取Z坐标，跳过
            }

            if (Math.abs(zombieZ - playerZ) > FLOOR_TOLERANCE) {
                rejectedFloor++;
                continue;
            }

            // Range check
            final float dist;
            try {
                dist = player.DistTo(zombie);
            } catch (Exception e) {
                continue; // 无法计算距离，跳过
            }

            if (dist > maxRange) {
                rejectedRange++;
                continue;
            }

            // Visibility check
            try {
                if (zombie.getSquare() != null && !zombie.getSquare().getCanSee(playerIndex)) {
                    rejectedVisibility++;
                    continue;
                }
            } catch (Exception e) {
                // 可见性检查失败，继续处理（不跳过）
            }

            // Mouse proximity check（与锁定标记一致：瞄准点投影）
            try {
                final float projZ = zombie.getAimOriginPosZ();
                final float sx = IsoUtils.XToScreenExact(zombie.getX(), zombie.getY(), projZ, playerIndex);
                final float sy = IsoUtils.YToScreenExact(zombie.getX(), zombie.getY(), projZ, playerIndex);
                final float screenDist = IsoUtils.DistanceTo(mouseX, mouseY, sx, sy);
                if (screenDist <= lockRadius) {
                    candidates.add(zombie);
                    if (screenDist < minScreenDist) {
                        minScreenDist = screenDist;
                    }
                } else {
                    rejectedMouse++;
                }
            } catch (Exception e) {
                // 投影失败，跳过此丧尸
            }
        }

        System.out.println("[RelaxedAim DEBUG] Filter results: total=" + debugTotalZombies
                + ", floor=" + rejectedFloor + ", range=" + rejectedRange
                + ", visibility=" + rejectedVisibility + ", mouse=" + rejectedMouse
                + ", final=" + candidates.size()
                + ", minScreenDist=" + (minScreenDist == Float.MAX_VALUE ? "-" : String.format("%.2f", minScreenDist)));

        return candidates;
    }

    private static void debugLog(boolean active, int candidateCount) {
        if (active == lastActive && candidateCount == lastCandidateCount) {
            return;
        }
        if (frameCounter - lastLogFrame < LOG_INTERVAL_FRAMES) {
            return;
        }
        lastActive = active;
        lastCandidateCount = candidateCount;
        lastLogFrame = frameCounter;
        System.out.println("[RelaxedAim] Phase2 active=" + active + ", candidates=" + candidateCount);
    }
}