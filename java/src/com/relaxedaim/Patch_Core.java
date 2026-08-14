package com.relaxedaim;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.core.SpriteRenderer;
import zombie.core.textures.Texture;
import zombie.iso.IsoUtils;
import zombie.iso.IsoWorld;
import zombie.ui.TextManager;
import zombie.ui.UIFont;

@Patch(className = "zombie.core.Core", methodName = "EndFrameUI")
public final class Patch_Core {
    public Patch_Core() {
    }

    @Patch.OnEnter
    public static void enter() {
        if (TextManager.instance == null) {
            return;
        }

        final UIFont font = UIFont.Small;
        int x = 10;
        int y = 80;
        int lineH = 16;
        try {
            lineH = Math.max(14, (int) TextManager.instance.MeasureStringY(font, "Ag"));
        } catch (Exception e) {
        }

        draw(font, x, y, "RelaxedAim Lock-v1.0", 0.55f, 1.0f, 0.55f, 1.0f);
        y += lineH;
        draw(font, x, y, "Aiming: " + AimAssistService.debugIsAiming, 1.0f, 1.0f, 1.0f, 1.0f);
        y += lineH;
        draw(font, x, y, "Weapon: " + AimAssistService.debugWeaponName + " | Ranged: " + AimAssistService.debugHasRangedWeapon, 1.0f, 1.0f, 1.0f, 1.0f);
        y += lineH;
        draw(font, x, y, "ZombiesInCell: " + AimAssistService.debugTotalZombies + " | Candidates: " + AimAssistService.debugCandidateCount, 1.0f, 1.0f, 0.0f, 1.0f);
        y += lineH;
        if (TargetLockService.debugHasLock) {
            draw(font, x, y, "Lock: ID=" + TargetLockService.debugLockId
                    + " sDist=" + String.format("%.1f", TargetLockService.debugLockScreenDist)
                    + " wDist=" + String.format("%.1f", TargetLockService.debugLockWorldDist)
                    + " (R=" + (int) RelaxedAimConfig.lockRadiusPx + " x" + RelaxedAimConfig.reFilterMultiplier + ")",
                    0.35f, 1.0f, 0.35f, 1.0f);
        } else {
            draw(font, x, y, "Lock: none (R=" + (int) RelaxedAimConfig.lockRadiusPx
                    + "px x" + RelaxedAimConfig.reFilterMultiplier + ")", 0.5f, 0.5f, 0.5f, 1.0f);
        }

        // 在锁定丧尸的瞄准点（胸口/头部高度）绘制锁定环标记
        drawLockMarker();
    }

    /**
     * 在锁定目标上绘制「环 + 竖直连线 + 中心点」标记。
     * 与鼠标候选筛选使用同一投影（getAimOriginPosZ），保证「画出来的就是锁定的」。
     */
    public static void drawLockMarker() {
        if (IsoWorld.instance == null || IsoWorld.instance.currentCell == null) {
            return;
        }
        if (!IsoPlayer.hasInstance()) {
            return;
        }
        final IsoPlayer player = IsoPlayer.getInstance();
        if (player == null || !player.isAiming()) {
            return;
        }
        final IsoZombie z = TargetLockService.getLockedTarget();
        if (z == null) {
            return;
        }

        int pIndex = 0;
        try {
            pIndex = IsoPlayer.getPlayerIndex();
        } catch (Exception e) {
        }

        final float projZ;
        try {
            projZ = z.getAimOriginPosZ();
        } catch (Exception e) {
            return;
        }

        final float sx = IsoUtils.XToScreenExact(z.getX(), z.getY(), projZ, pIndex);
        final float sy = IsoUtils.YToScreenExact(z.getX(), z.getY(), projZ, pIndex);
        final float footSy = IsoUtils.YToScreenExact(z.getX(), z.getY(), z.getZ(), pIndex);

        final Texture white = Texture.getWhite();
        final SpriteRenderer sr = SpriteRenderer.instance;
        final float r = RelaxedAimConfig.markerR;
        final float g = RelaxedAimConfig.markerG;
        final float b = RelaxedAimConfig.markerB;
        final float a = RelaxedAimConfig.markerA;
        final float radius = RelaxedAimConfig.markerRadiusPx;
        final float lw = RelaxedAimConfig.markerLineWidth;

        // 竖直连线：从瞄准点向下到脚底，明确指向「哪一只」
        sr.renderline(white, (int) sx, (int) sy, (int) sx, (int) footSy, r, g, b, a, lw);

        // 锁定环：多段线段近似圆
        final int segments = 24;
        for (int i = 0; i < segments; i++) {
            double a0 = 2.0 * Math.PI * i / segments;
            double a1 = 2.0 * Math.PI * (i + 1) / segments;
            int x1 = (int) (sx + radius * Math.cos(a0));
            int y1 = (int) (sy + radius * Math.sin(a0));
            int x2 = (int) (sx + radius * Math.cos(a1));
            int y2 = (int) (sy + radius * Math.sin(a1));
            sr.renderline(white, x1, y1, x2, y2, r, g, b, a, lw);
        }

        // 中心点
        int dot = 5;
        sr.renderi(white, (int) sx - dot / 2, (int) sy - dot / 2, dot, dot, r, g, b, a, null);
    }

    public static void draw(UIFont font, int x, int y, String text, float r, float g, float b, float a) {
        try {
            TextManager.instance.DrawString(font, x, y, text, r, g, b, a);
        } catch (Exception e) {
        }
    }
}