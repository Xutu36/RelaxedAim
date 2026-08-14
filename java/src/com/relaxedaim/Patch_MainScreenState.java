package com.relaxedaim;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.ui.TextManager;
import zombie.ui.UIFont;

/**
 * Phase 1: proves that RelaxedAim has loaded and can patch the game's menu UI.
 * 
 * VERSION: 跟随 RelaxedAimConfig.VERSION（唯一来源）
 */
@Patch(className = "zombie.gameStates.MainScreenState", methodName = "renderBackground")
public final class Patch_MainScreenState {

    // 【重要】必须public，否则ZombieBuddy无法访问导致IllegalAccessError
    public static boolean logged = false;

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
                "RelaxedAim " + RelaxedAimConfig.VERSION,
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
            System.out.println("[RelaxedAim] RelaxedAim " + RelaxedAimConfig.VERSION + " loaded");
            System.out.println("[RelaxedAim] Notes: " + RelaxedAimConfig.VERSION_NOTES);
            System.out.println("[RelaxedAim] ========================================");
        }
    }
}
