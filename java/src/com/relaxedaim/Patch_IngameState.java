package com.relaxedaim;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoPlayer;
import zombie.iso.IsoWorld;

/**
 * Phase 2: hook the in-game UI render loop to collect aim state.
 *
 * VERSION: ConsoleDebug-v4.2 - 使用Callbacks绘制Overlay，修复候选丧尸恒为0
 */
@Patch(className = "zombie.gameStates.IngameState", methodName = "renderframeui")
public final class Patch_IngameState {

    // 【重要】必须public，否则ZombieBuddy无法访问导致IllegalAccessError
    public static int callCounter = 0;
    public static boolean hasLoggedInit = false;

    private Patch_IngameState() {
    }

    @Patch.OnExit
    public static void afterRenderFrameUI() {
        callCounter++;

        // 初始化日志（只打印一次到console）
        if (!hasLoggedInit) {
            hasLoggedInit = true;
            System.out.println("[RelaxedAim] Patch_IngameState initialized (update-only)");
        }

        // 每帧（任意状态）刷新配置 + 检测热键，保证菜单改动与快捷键在非瞄准状态也生效
        AimAssistService.tickFrame();

        // 【安全检查1】确保游戏世界实例存在
        if (IsoWorld.instance == null) {
            return;
        }

        // 【安全检查2】确保当前单元格已加载
        if (IsoWorld.instance.currentCell == null) {
            return;
        }

        // 【安全检查3】确保玩家实例存在
        if (!IsoPlayer.hasInstance()) {
            return;
        }

        final IsoPlayer player = IsoPlayer.getInstance();
        // 【安全检查4】玩家对象非空
        if (player == null) {
            return;
        }

        // 【安全检查5】确保玩家已完全加载（位置有效）
        final float playerX, playerY, playerZ;
        final int playerIndex;
        try {
            playerX = player.getX();
            playerY = player.getY();
            playerZ = player.getZ();
            playerIndex = player.getIndex();

            // 检查位置是否有效（不是NaN或无穷大）
            if (Float.isNaN(playerX) || Float.isNaN(playerY) || Float.isNaN(playerZ) ||
                Float.isInfinite(playerX) || Float.isInfinite(playerY) || Float.isInfinite(playerZ)) {
                return;
            }
        } catch (Exception e) {
            return;
        }

        // 获取基础信息（输入瞄准点、瞄准状态）。鼠标=Mouse.getX/getY；手柄=摇杆准星×zoom（同一虚拟像素空间）
        final int mouseX, mouseY;
        final boolean isAiming;
        try {
            TargetLockService.updateInputAim(playerIndex);
            mouseX = (int) (TargetLockService.inputAimXA * TargetLockService.getZoom(playerIndex));
            mouseY = (int) (TargetLockService.inputAimYA * TargetLockService.getZoom(playerIndex));
            isAiming = player.isAiming();
        } catch (Exception e) {
            return;
        }

        // 只有在瞄准时才执行详细处理和日志输出
        if (!isAiming) {
            AimAssistService.markNotAiming();
            return;
        }

        // 【瞄准时】执行详细逻辑
        try {
            AimAssistService.updateAiming(player, mouseX, mouseY, playerX, playerY, playerZ);
        } catch (Exception e) {
            System.out.println("[RelaxedAim] ERROR during aiming update: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
