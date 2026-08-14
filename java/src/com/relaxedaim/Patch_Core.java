package com.relaxedaim;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.core.textures.Texture;
import zombie.input.AimingReticle;
import zombie.input.Mouse;
import zombie.iso.IsoUtils;
import zombie.iso.IsoWorld;
import zombie.ui.TextManager;
import zombie.ui.UIFont;

/**
 * EndFrameUI Overlay：HUD 调试信息 + 锁定标记（环/竖线/中心点 + 屏外箭头）。
 *
 * 注意：开发阶段所有成员保持 public（见 RULES.md）。
 */
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

        draw(font, x, y, "RelaxedAim " + RelaxedAimConfig.VERSION, 0.55f, 1.0f, 0.55f, 1.0f);
        y += lineH;
        draw(font, x, y, "Aiming: " + AimAssistService.debugIsAiming, 1.0f, 1.0f, 1.0f, 1.0f);
        y += lineH;
        draw(font, x, y, "Weapon: " + AimAssistService.debugWeaponName + " | Ranged: " + AimAssistService.debugHasRangedWeapon, 1.0f, 1.0f, 1.0f, 1.0f);
        y += lineH;
        draw(font, x, y, "ZombiesInCell: " + AimAssistService.debugTotalZombies + " | Candidates: " + AimAssistService.debugCandidateCount, 1.0f, 1.0f, 0.0f, 1.0f);
        y += lineH;
        draw(font, x, y, "Options: LockOn=" + (RelaxedAimConfig.optionLockOn ? "ON" : "OFF")
                + " | ShotgunNoLock=" + (RelaxedAimConfig.optionShotgunNoLock ? "ON" : "OFF")
                + " | Skip=" + AimAssistService.debugSkipReason
                + (RelaxedAimConfig.optionsReadFailed ? " [READ FAILED]" : ""), 0.7f, 0.8f, 1.0f, 1.0f);
        y += lineH;
        if (TargetLockService.debugHasLock) {
            draw(font, x, y, "Lock: ID=" + TargetLockService.debugLockId
                    + " sDist=" + String.format("%.1f", TargetLockService.debugLockScreenDist)
                    + " wDist=" + String.format("%.1f", TargetLockService.debugLockWorldDist)
                    + " (R=" + (int) RelaxedAimConfig.lockRadiusPx + " x" + RelaxedAimConfig.reFilterMultiplier + ")",
                    0.35f, 1.0f, 0.35f, 1.0f);
        } else {
            // 失锁后显示原因，便于排查
            String reason = "";
            long age = System.currentTimeMillis() - TargetLockService.debugReleaseTimeMs;
            if (TargetLockService.debugReleaseTimeMs > 0) {
                reason = " (" + TargetLockService.debugReleaseReason + ")";
            }
            draw(font, x, y, "Lock: none" + reason
                    + " (R=" + (int) RelaxedAimConfig.lockRadiusPx
                    + "px x" + RelaxedAimConfig.reFilterMultiplier + ")", 0.5f, 0.5f, 0.5f, 1.0f);
        }
        y += lineH;

        // 常显最近一次失锁原因（含已重新锁定后，仍保留最近失锁原因便于排查）
        if (TargetLockService.debugReleaseTimeMs > 0) {
            long agoMs = System.currentTimeMillis() - TargetLockService.debugReleaseTimeMs;
            String ago = agoMs < 1000 ? String.format("%dms", agoMs)
                    : String.format("%.1fs", agoMs / 1000.0);
            draw(font, x, y, "LastLost: " + TargetLockService.debugReleaseReason + " (" + ago + " ago)", 0.9f, 0.6f, 0.4f, 1.0f);
        }

        // 在锁定丧尸的瞄准点（胸口/头部高度）绘制锁定环标记
        drawLockMarker();

        // 右侧调试面板：实时打印鼠标与锁定目标的关键相对位置（用于排查缩放/坐标偏差）
        drawRightPanel();
    }

    /** 右侧对齐调试面板：zoom、鼠标原始/虚拟坐标、锁定目标虚拟/UI 坐标及相对差。 */
    public static void drawRightPanel() {
        final UIFont font = UIFont.Small;
        final int screenW;
        try {
            screenW = Core.width;
        } catch (Exception e) {
            return;
        }
        int lineH = 16;
        try {
            lineH = Math.max(14, (int) TextManager.instance.MeasureStringY(font, "Ag"));
        } catch (Exception e) {
        }

        int pIndex = 0;
        try {
            pIndex = IsoPlayer.getPlayerIndex();
        } catch (Exception e) {
        }

        float zoom = 1.0f;
        int mouseX = 0;
        int mouseY = 0;
        try {
            zoom = TargetLockService.getZoom(pIndex);
            mouseX = Mouse.getX();
            mouseY = Mouse.getY();
        } catch (Exception e) {
        }

        // 逐行收集，右对齐绘制（x = screenW - 文本宽 - 10）
        int y = 80;
        addRightLine(font, screenW, y, String.format("RelaxedAim Debug | zoom=%.3f tileScale=%d", zoom, zombie.core.Core.tileScale), 0.55f, 1.0f, 0.55f, 1.0f);
        y += lineH;
        addRightLine(font, screenW, y, String.format("Mouse raw=(%d,%d)  ret=(%d,%d)",
                mouseX, mouseY,
                AimingReticle.getX(pIndex), AimingReticle.getY(pIndex)), 1.0f, 1.0f, 1.0f, 1.0f);
        y += lineH;
        addRightLine(font, screenW, y, String.format("AimWorld=(%.2f,%.2f) %s",
                TargetLockService.aimWorldX, TargetLockService.aimWorldY,
                TargetLockService.aimWorldValid ? "ok" : "INVALID"), 1.0f, 0.8f, 0.6f, 1.0f);
        y += lineH;

        final IsoZombie z = TargetLockService.getLockedTarget();
        if (z == null) {
            addRightLine(font, screenW, y, "Target: none", 0.5f, 0.5f, 0.5f, 1.0f);
            return;
        }
        try {
            addRightLine(font, screenW, y, String.format("Target id=%d world=(%.1f,%.1f,z%.0f)",
                    TargetLockService.safeId(z), z.getX(), z.getY(), z.getZ()), 0.35f, 1.0f, 0.35f, 1.0f);
            y += lineH;
            final float tvx = TargetLockService.aimVirtualX(z, pIndex);
            final float tvy = TargetLockService.aimVirtualY(z, pIndex);
            final float tux = TargetLockService.aimScreenX(z, pIndex);
            final float tuy = TargetLockService.aimScreenY(z, pIndex);
            final float mvx = TargetLockService.mouseVirtualX(mouseX, pIndex);
            final float mvy = TargetLockService.mouseVirtualY(mouseY, pIndex);
            addRightLine(font, screenW, y, String.format("Target v=(%.0f,%.0f)  UI=(%.0f,%.0f)", tvx, tvy, tux, tuy), 0.7f, 1.0f, 1.0f, 1.0f);
            y += lineH;
            addRightLine(font, screenW, y, String.format("Delta v=(%.0f,%.0f) UI=(%.0f,%.0f)",
                    mvx - tvx, mvy - tvy, mouseX - tux, mouseY - tuy), 1.0f, 0.8f, 0.4f, 1.0f);
            y += lineH;
            final float vDist = IsoUtils.DistanceTo(mvx, mvy, tvx, tvy);
            final float wDist = TargetLockService.aimWorldValid ? TargetLockService.aimWorldDistTo(z) : 0.0f;
            addRightLine(font, screenW, y, String.format("Dist v=%.1f  world=%.2f (Rw=%.2f)",
                    vDist, wDist, RelaxedAimConfig.lockRadiusWorld), 1.0f, 1.0f, 0.0f, 1.0f);
        } catch (Exception e) {
        }
    }

    public static void addRightLine(UIFont font, int screenW, int y, String text, float r, float g, float b, float a) {
        try {
            final float tw = TextManager.instance.MeasureStringX(font, text);
            TextManager.instance.DrawString(font, (int) (screenW - tw - 10), y, text, r, g, b, a);
        } catch (Exception e) {
        }
    }

    /**
     * 在锁定目标上绘制「环 + 竖直连线 + 中心点」标记；目标在屏幕外时改画指向目标的边缘箭头。
     * 与鼠标候选筛选使用同一投影（getAimOriginPosZ），保证「画出来的就是锁定的」。
     */
    public static void drawLockMarker() {
        if (!RelaxedAimConfig.optionLockOn) {
            return;
        }
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

        final float sx = TargetLockService.aimScreenX(z, pIndex);
        final float sy = TargetLockService.aimScreenY(z, pIndex);
        final float footSy = TargetLockService.worldScreenY(z.getX(), z.getY(), z.getZ(), pIndex);

        final Texture white = Texture.getWhite();
        final SpriteRenderer sr = SpriteRenderer.instance;
        final float r = RelaxedAimConfig.markerR;
        final float g = RelaxedAimConfig.markerG;
        final float b = RelaxedAimConfig.markerB;
        final float a = RelaxedAimConfig.markerA;
        final float radius = RelaxedAimConfig.markerRadiusPx;
        final float lw = RelaxedAimConfig.markerLineWidth;

        // 屏幕可视区域（留白）
        final float m = 26.0f;
        final float vw = Core.width;
        final float vh = Core.height;
        final boolean offScreen = sx < m || sx > vw - m || sy < m || sy > vh - m;

        if (offScreen) {
            drawOffScreenArrow(sr, white, sx, sy, m, vw, vh, r, g, b, a, lw);
            return;
        }

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

    /** 屏外指示：在屏幕边缘画一个指向目标的三角形箭头。 */
    public static void drawOffScreenArrow(SpriteRenderer sr, Texture white, float sx, float sy,
            float m, float vw, float vh, float r, float g, float b, float a, float lw) {
        final float px = Math.max(m, Math.min(vw - m, sx));
        final float py = Math.max(m, Math.min(vh - m, sy));
        final double angle = Math.atan2(sy - py, sx - px); // 从箭头指向目标
        final float len = 15.0f;
        final float halfBase = 8.0f;
        // 尖端
        final float tipX = (float) (px + len * Math.cos(angle));
        final float tipY = (float) (py + len * Math.sin(angle));
        // 垂直方向单位向量
        final float bx = (float) (-Math.sin(angle));
        final float by = (float) (Math.cos(angle));
        final float b1x = px + bx * halfBase;
        final float b1y = py + by * halfBase;
        final float b2x = px - bx * halfBase;
        final float b2y = py - by * halfBase;
        sr.renderline(white, (int) tipX, (int) tipY, (int) b1x, (int) b1y, r, g, b, a, lw);
        sr.renderline(white, (int) b1x, (int) b1y, (int) b2x, (int) b2y, r, g, b, a, lw);
        sr.renderline(white, (int) b2x, (int) b2y, (int) tipX, (int) tipY, r, g, b, a, lw);
    }

    public static void draw(UIFont font, int x, int y, String text, float r, float g, float b, float a) {
        try {
            TextManager.instance.DrawString(font, x, y, text, r, g, b, a);
        } catch (Exception e) {
        }
    }
}