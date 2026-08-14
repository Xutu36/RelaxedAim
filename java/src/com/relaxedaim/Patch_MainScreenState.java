package com.relaxedaim;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.ui.TextManager;
import zombie.ui.UIFont;

/**
 * Phase 1: proves that RelaxedAim has loaded and can patch the game's menu UI.
 * 
 * VERSION: ConsoleDebug-v4.2 - 修复候选丧尸恒为0与Overlay渲染链路
 */
@Patch(className = "zombie.gameStates.MainScreenState", methodName = "renderBackground")
public final class Patch_MainScreenState {

    // 【重要】必须public，否则ZombieBuddy无法访问导致IllegalAccessError
    public static boolean logged = false;

    // 版本标识
    public static final String VERSION = "ConsoleDebug-v4.2";
    public static final String BUILD_TIME = "2026-08-14";

    private Patch_MainScreenState() {
    }

    @Patch.OnExit
    public static void afterRenderBackground() {
        // 【安全检查】确保TextManager可用
        if (TextManager.instance == null) {
            System.out.println("[RelaxedAim] TextManager.instance is null, skipping render");
            return;
        }
        
        // 在左上角显示加载信息和版本号
        try {
            TextManager.instance.DrawString(
                UIFont.Medium,
                8,
                8,
                "RelaxedAim " + VERSION,
                0.55,
                1.0,
                0.55,
                1.0
            );
        } catch (Exception e) {
            System.out.println("[RelaxedAim] ERROR rendering text: " + e.getMessage());
        }

        if (!logged) {
            logged = true;
            System.out.println("[RelaxedAim] ========================================");
            System.out.println("[RelaxedAim] RelaxedAim " + VERSION + " loaded");
            System.out.println("[RelaxedAim] Build: " + BUILD_TIME);
            System.out.println("[RelaxedAim] Mode: EndFrameUI Overlay + Console Debug (aiming only)");
            System.out.println("[RelaxedAim] Fix: Use Callbacks.onEndFrameUI overlay + IsoUtils X/YToScreenExact");
            System.out.println("[RelaxedAim] ========================================");
        }
    }
}
