package com.relaxedaim;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.gameStates.IngameState;
import zombie.ui.TextManager;
import zombie.ui.UIFont;

/**
 * Phase 2 & 4: hook the in-game UI render loop so we can read aim state every frame
 * and draw visual diagnostics on the screen.
 */
@Patch(className = "zombie.gameStates.IngameState", methodName = "renderframeui")
public final class Patch_IngameState {

    private Patch_IngameState() {
    }

    @Patch.OnExit
    public static void afterRenderFrameUI() {
        // 1. Update targeting calculations
        AimAssistService.update();

        // 2. Draw HUD Debug Overlay Box
        drawDebugOverlay();

        // 3. Draw visual lock marker on the nearest zombie if locked
        drawTargetMarker();
    }

    public static void drawDebugOverlay() {
        final UIFont font = UIFont.Small;
        final TextManager textMgr = TextManager.instance;

        // Position of the debug panel (top left below general UI)
        int x = 20;
        int y = 140;
        final int lineH = 16;

        // Header
        textMgr.DrawString(font, x, y, "[RelaxedAim] Debug Monitor (Phase 2)", 0.3, 0.9, 0.9, 0.82);
        y += lineH;

        // Game/Aim State
        if (AimAssistService.debugIsAiming) {
            String stateStr = "State: AIMING";
            if (AimAssistService.debugHasRangedWeapon) {
                stateStr += " (Ranged: " + AimAssistService.debugWeaponName + ", MaxRange: " + AimAssistService.debugWeaponRange + ")";
                textMgr.DrawString(font, x, y, stateStr, 0.2, 1.0, 0.2, 0.9);
            } else {
                stateStr += " (Melee/None: " + AimAssistService.debugWeaponName + ")";
                textMgr.DrawString(font, x, y, stateStr, 1.0, 0.6, 0.0, 0.9);
            }
        } else {
            textMgr.DrawString(font, x, y, "State: IDLE (Aim to activate)", 0.6, 0.6, 0.6, 0.8);
        }
        y += lineH;

        // Target filters
        textMgr.DrawString(font, x, y, String.format("Zombies in Cell: %d", AimAssistService.debugTotalZombies), 0.9, 0.9, 0.9, 0.8);
        y += lineH;

        textMgr.DrawString(font, x, y, String.format("- Rejected (Different Floor): %d", AimAssistService.debugRejectedFloor), 0.8, 0.5, 0.5, 0.7);
        y += lineH;
        textMgr.DrawString(font, x, y, String.format("- Rejected (Out of Max Range): %d", AimAssistService.debugRejectedRange), 0.8, 0.5, 0.5, 0.7);
        y += lineH;
        textMgr.DrawString(font, x, y, String.format("- Rejected (Blocked/Invisible): %d", AimAssistService.debugRejectedVisibility), 0.8, 0.5, 0.5, 0.7);
        y += lineH;
        textMgr.DrawString(font, x, y, String.format("- Rejected (Too Far from Mouse): %d", AimAssistService.debugRejectedMouse), 0.8, 0.5, 0.5, 0.7);
        y += lineH;

        // Candidates summary
        if (AimAssistService.debugCandidateCount > 0) {
            textMgr.DrawString(font, x, y, String.format("Candidates Found: %d", AimAssistService.debugCandidateCount), 0.2, 1.0, 0.2, 0.9);
            y += lineH;
            textMgr.DrawString(font, x, y, String.format("Locked: Target (Dist: %.1f)", AimAssistService.debugNearestDistance), 0.9, 0.9, 0.2, 0.9);
        } else {
            textMgr.DrawString(font, x, y, "Candidates Found: 0", 0.8, 0.8, 0.8, 0.8);
        }
    }

    public static void drawTargetMarker() {
        if (!AimAssistService.debugIsAiming || !AimAssistService.debugHasRangedWeapon) {
            return;
        }

        if (AimAssistService.debugCandidateCount > 0 && AimAssistService.debugNearestScreenX != 0.0f) {
            final float x = AimAssistService.debugNearestScreenX;
            final float y = AimAssistService.debugNearestScreenY;

            // Draw a high-contrast crosshair/marker over the candidate's torso/head area
            // Feet are at (x, y), so we offset upward by 50 pixels for the chest/head
            TextManager.instance.DrawString(
                UIFont.Medium,
                x - 20,
                y - 50,
                "< LOCK >",
                0.2, 1.0, 0.2, 1.0 // Vivid Green
            );
        }
    }
}

