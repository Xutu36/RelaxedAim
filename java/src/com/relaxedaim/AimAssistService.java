package com.relaxedaim;

import java.util.ArrayList;
import java.util.List;

import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.core.Core;
import zombie.input.AimingReticle;
import zombie.input.Mouse;
import zombie.inventory.InventoryItem;
import zombie.inventory.types.HandWeapon;
import zombie.iso.IsoCell;
import zombie.iso.IsoUtils;
import zombie.iso.IsoWorld;

/**
 * Phase 2 + Phase 3: 只读瞄准状态采集、候选丧尸侦测与目标锁定。
 *
 * VERSION: Lock-v1.1 - 全public; 失锁原因; 屏外箭头(见Patch_Core)
 */
public final class AimAssistService {

    public static final float MAX_WORLD_SCAN_RADIUS_TILES = 60.0f;
    public static final float FLOOR_TOLERANCE = 0.5f;

    public static int frameCounter = 0;
    public static int lastLogFrame = 0;
    public static final int LOG_INTERVAL_FRAMES = 30;

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
    public static String debugSkipReason = "none";

    public static boolean lastActive = false;
    public static int lastCandidateCount = 0;

    public AimAssistService() {
    }

    public static boolean isReady() {
        return true;
    }

    public static void markNotAiming() {
        debugIsAiming = false;
        debugCandidateCount = 0;
        TargetLockService.clearLock("aim");
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

        // 模组选项刷新（1s 节流，读 ModOptions.ini）
        RelaxedAimConfig.refreshModOptions();

        // 锁定总开关关闭时：只更新瞄准调试状态，不做候选/锁定
        if (!RelaxedAimConfig.optionLockOn) {
            debugIsAiming = true;
            debugCandidateCount = 0;
            debugSkipReason = "lockoff";
            TargetLockService.clearLock("lockoff");
            return;
        }
        debugSkipReason = "none";

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
            TargetLockService.clearLock("aim");
            return;
        }

        // 霰弹枪战术（大面积杀伤而非精确爆头）时，按选项关闭辅助锁定
        if (RelaxedAimConfig.optionShotgunNoLock && isShotgun(weapon)) {
            debugSkipReason = "shotgun";
            debugCandidateCount = 0;
            TargetLockService.clearLock("shotgun");
            return;
        }

        final int pIndex = getPlayerIndex();

        // 每帧更新世界瞄准点（复刻游戏准星换算），供候选筛选与锁定校验使用
        TargetLockService.updateAimWorld(player, pIndex);

        // 节流搜索帧：重新扫描候选集合
        List<IsoZombie> candidates = null;
        if (frameCounter % RelaxedAimConfig.searchIntervalFrames == 0) {
            System.out.println("[RelaxedAim DEBUG] === Frame " + frameCounter + " (AIMING) ===");
            candidates = findCandidates(player, weapon, zombies, pIndex, mouseX, mouseY);
            debugCandidateCount = candidates.size();
            logAimDebug(player, pIndex, mouseX, mouseY, candidates);
            System.out.println("[RelaxedAim DEBUG] Scan complete. Candidates: " + debugCandidateCount);
        }

        // 锁定状态机：每帧校验，节流帧（candidates != null）尝试获取/更新
        TargetLockService.update(player, weapon, pIndex, mouseX, mouseY, candidates);

        debugLog(true, debugCandidateCount);
    }

    public static HandWeapon getActiveWeapon(final IsoPlayer player) throws Exception {
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

    public static int playerIndex = -1;

    public static int getPlayerIndex() {
        try {
            playerIndex = IsoPlayer.getPlayerIndex();
        } catch (Exception e) {
            System.out.println("[RelaxedAim DEBUG] Exception getting player index: " + e.getMessage());
            playerIndex = 0;
        }
        return playerIndex;
    }

    /** 判断武器是否为霰弹枪：弹药类型为 shotgun_shells（覆盖所有原版霰弹枪）。 */
    public static boolean isShotgun(HandWeapon weapon) {
        try {
            if (weapon == null) {
                return false;
            }
            final zombie.scripting.objects.AmmoType at = weapon.getAmmoType();
            if (at == null) {
                return false;
            }
            if (at == zombie.scripting.objects.AmmoType.SHOTGUN_SHELLS) {
                return true;
            }
            final String key = at.getItemKey();
            if (key != null && key.toLowerCase().contains("shotgun")) {
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    /**
     * 单遍扫描：廉价过滤（存活/楼层/射程/可见性）后，仅收集「鼠标附近 lockRadiusPx 内」的丧尸。
     * 候选数量达到 maxCandidates 即停止（有限遍历）。
     * 投影统一使用 getAimOriginPosZ()（瞄准点/胸口高度），与锁定标记一致。
     */
    public static List<IsoZombie> findCandidates(final IsoPlayer player, final HandWeapon weapon,
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

        final float lockRadius = TargetLockService.effectiveLockRadiusPx(playerIndex);
        final float lockRadiusWorld = RelaxedAimConfig.lockRadiusWorld;
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

            // World-space proximity check（与游戏准星完全一致：AimingReticle→XToIso 的世界瞄准点）
            try {
                if (TargetLockService.aimWorldValid) {
                    final float worldDist = TargetLockService.aimWorldDistTo(zombie);
                    if (worldDist <= lockRadiusWorld) {
                        candidates.add(zombie);
                        if (worldDist < minScreenDist) {
                            minScreenDist = worldDist;
                        }
                    } else {
                        rejectedMouse++;
                    }
                } else {
                    // 回退：虚拟空间（mouse×zoom vs XToScreenExact）
                    final float sx = TargetLockService.aimVirtualX(zombie, playerIndex);
                    final float sy = TargetLockService.aimVirtualY(zombie, playerIndex);
                    final float screenDist = IsoUtils.DistanceTo(
                            TargetLockService.mouseVirtualX(mouseX, playerIndex),
                            TargetLockService.mouseVirtualY(mouseY, playerIndex), sx, sy);
                    if (screenDist <= lockRadius) {
                        candidates.add(zombie);
                        if (screenDist < minScreenDist) {
                            minScreenDist = screenDist;
                        }
                    } else {
                        rejectedMouse++;
                    }
                }
            } catch (Exception e) {
                // 投影失败，跳过此丧尸
            }
        }

        System.out.println("[RelaxedAim DEBUG] Filter results: total=" + debugTotalZombies
                + ", floor=" + rejectedFloor + ", range=" + rejectedRange
                + ", visibility=" + rejectedVisibility + ", mouse=" + rejectedMouse
                + ", final=" + candidates.size()
                + ", minDist=" + (minScreenDist == Float.MAX_VALUE ? "-" : String.format("%.2f", minScreenDist)));

        return candidates;
    }

    public static void debugLog(boolean active, int candidateCount) {
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

    /**
     * 坐标对比日志（每个搜索帧打印一行）：用于排查缩放/坐标偏差。
     * 输出：zoom、tileScale、准星原始(retRaw)/换算(ret)、世界瞄准点(aimWorld)、
     * 最近候选丧尸的世界坐标与世界距离、以及虚拟空间对照（mouse×zoom vs XToScreenExact）。
     */
    public static void logAimDebug(IsoPlayer player, int pIndex, int mouseX, int mouseY, List<IsoZombie> candidates) {
        try {
            if (!RelaxedAimConfig.debugLogCoordinates) {
                return;
            }
            final float zoom = TargetLockService.getZoom(pIndex);
            final int retX = AimingReticle.getX(pIndex);
            final int retY = AimingReticle.getY(pIndex);
            final StringBuilder sb = new StringBuilder();
            sb.append("[RelaxedAim AIMLOG] zoom=").append(String.format("%.3f", zoom))
              .append(" tileScale=").append(Core.tileScale)
              .append(" mouse=(").append(mouseX).append(",").append(mouseY).append(")")
              .append(" retRaw=(").append(Mouse.getXA()).append(",").append(Mouse.getYA()).append(")")
              .append(" ret=(").append(retX).append(",").append(retY).append(")")
              .append(" aimWorld=(").append(String.format("%.2f", TargetLockService.aimWorldX))
              .append(",").append(String.format("%.2f", TargetLockService.aimWorldY)).append(")")
              .append(" aimWorldValid=").append(TargetLockService.aimWorldValid)
              .append(" cand=").append(candidates == null ? "-" : String.valueOf(candidates.size()));
            if (candidates != null && !candidates.isEmpty()) {
                final IsoZombie n = candidates.get(0);
                float bestW = Float.MAX_VALUE;
                for (final IsoZombie z : candidates) {
                    final float w = TargetLockService.aimWorldDistTo(z);
                    if (w < bestW) {
                        bestW = w;
                    }
                }
                sb.append(" nearWorld=(").append(String.format("%.2f", n.getX()))
                  .append(",").append(String.format("%.2f", n.getY())).append(")")
                  .append(" minWorldDist=").append(String.format("%.2f", bestW));
            }
            System.out.println(sb.toString());
        } catch (Exception e) {
        }
    }
}