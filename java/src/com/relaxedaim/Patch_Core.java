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

        // 锁定辅助 UI：范围圈 / 头部锁定指示（紫色圈，锁定目标或最近候选）
        drawLockAssistUI();

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

    /** 多段线段近似圆环。 */
    public static void drawRing(SpriteRenderer sr, Texture white, float cx, float cy, float radius,
            float r, float g, float b, float a, float lw) {
        final int segments = 32;
        for (int i = 0; i < segments; i++) {
            double a0 = 2.0 * Math.PI * i / segments;
            double a1 = 2.0 * Math.PI * (i + 1) / segments;
            int x1 = (int) (cx + radius * Math.cos(a0));
            int y1 = (int) (cy + radius * Math.sin(a0));
            int x2 = (int) (cx + radius * Math.cos(a1));
            int y2 = (int) (cy + radius * Math.sin(a1));
            sr.renderline(white, x1, y1, x2, y2, r, g, b, a, lw);
        }
    }

    /**
     * 锁定辅助 UI（由两个 bool 控制，见 RelaxedAimConfig）：
     *  - optionShowLockRange：以当前世界瞄准点为中心画锁定范围圈（半径 lockRadiusWorld）。
     *  - optionHighlightNearest：高亮范围内最近将被锁定的丧尸。
     */
    public static void drawLockAssistUI() {
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
        int pIndex = 0;
        try {
            pIndex = IsoPlayer.getPlayerIndex();
        } catch (Exception e) {
        }

        final Texture white = Texture.getWhite();
        final SpriteRenderer sr = SpriteRenderer.instance;
        final float lw = 1.5f;

        // 范围圈
        if (RelaxedAimConfig.optionShowLockRange && TargetLockService.aimWorldValid) {
            try {
                final float zoom = TargetLockService.getZoom(pIndex);
                final float cx = TargetLockService.worldScreenX(TargetLockService.aimWorldX,
                        TargetLockService.aimWorldY, TargetLockService.aimWorldZ, pIndex);
                final float cy = TargetLockService.worldScreenY(TargetLockService.aimWorldX,
                        TargetLockService.aimWorldY, TargetLockService.aimWorldZ, pIndex);
                // 屏幕像素半径 ≈ 世界瓦片 × 32 × tileScale / zoom（iso 近似，用户可按 bool 取舍）
                final float radius = RelaxedAimConfig.lockRadiusWorld * 32.0f * zombie.core.Core.tileScale / zoom;
                drawRing(sr, white, cx, cy, radius, 0.35f, 0.8f, 1.0f, 0.6f, lw);
            } catch (Exception e) {
            }
        }

        // 紫色圈：画在「将被/正在锁定」丧尸头部骨骼（Bip01_Head）精确位置，有锁定时指向锁定目标，否则最近候选
        if (RelaxedAimConfig.optionHighlightNearest) {
            final IsoZombie n = TargetLockService.getLockedTarget() != null
                    ? TargetLockService.getLockedTarget()
                    : AimAssistService.debugNearestCandidate;
            if (n != null) {
                try {
                    TargetLockService.headAimWorldPos(n, TargetLockService.sHeadPos);
                    final float nx = TargetLockService.worldScreenX(TargetLockService.sHeadPos.x,
                            TargetLockService.sHeadPos.y, TargetLockService.sHeadPos.z, pIndex);
                    final float ny = TargetLockService.worldScreenY(TargetLockService.sHeadPos.x,
                            TargetLockService.sHeadPos.y, TargetLockService.sHeadPos.z, pIndex);
                    drawRing(sr, white, nx, ny, 14.0f, 1.0f, 0.3f, 1.0f, 0.9f, lw);
                } catch (Exception e) {
                }
            }
        }
    }

    public static void draw(UIFont font, int x, int y, String text, float r, float g, float b, float a) {
        try {
            TextManager.instance.DrawString(font, x, y, text, r, g, b, a);
        } catch (Exception e) {
        }
    }
}