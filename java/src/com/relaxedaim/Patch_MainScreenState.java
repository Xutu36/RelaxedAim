package com.relaxedaim;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.ui.TextManager;
import zombie.ui.UIFont;

/** Phase 1: proves that RelaxedAim has loaded and can patch the game's menu UI. */
@Patch(className = "zombie.gameStates.MainScreenState", methodName = "renderBackground")
public final class Patch_MainScreenState {
    // Advice is inlined into MainScreenState, so this state must be visible to that game class.
    public static boolean logged;

    private Patch_MainScreenState() {
    }

    @Patch.OnExit
    public static void afterRenderBackground() {
        TextManager.instance.DrawString(
            UIFont.Medium,
            8,
            8,
            "RelaxedAim loaded",
            0.55,
            1.0,
            0.55,
            1.0
        );

        if (!logged) {
            logged = true;
            System.out.println("[RelaxedAim] RelaxedAim loaded");
        }
    }
}
