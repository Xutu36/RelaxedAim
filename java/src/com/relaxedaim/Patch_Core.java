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

        // 调试/信息 HUD（发布时可用 optionShowHud=false 隐藏）
        if (RelaxedAimConfig.optionShowHud) {
            draw(font, x, y, "RelaxedAim " + RelaxedAimConfig.VERSION, 0.55f, 1.0f, 0.55f, 1.0f);
            y += lineH;
            draw(font, x, y, "Aiming: " + AimAssistService.debugIsAiming, 1.0f, 1.0f, 1.0f, 1.0f);
            y += lineH;
            draw(font, x, y, "Weapon: " + AimAssistService.debugWeaponName + " | Ranged: " + AimAssistService.debugHasRangedWeapon, 1.0f, 1.0f, 1.0f, 1.0f);
            y += lineH;
            draw(font, x, y, "ZombiesInCell: " + AimAssistService.debugTotalZombies + " | Candidates: " + AimAssistService.debugCandidateCount, 1.0f, 1.0f, 0.0f, 1.0f);
            y += lineH;
            draw(font, x, y, "Options: LockOn=" + (RelaxedAimConfig.optionLockOn ? "ON" : "OFF")
                    + " | Assist=" + String.format("%.2f", RelaxedAimConfig.assistStrength)
                    + " | Skip=" + AimAssistService.debugSkipReason
                    + (RelaxedAimConfig.optionsReadFailed ? " [READ FAILED]" : ""), 0.7f, 0.8f, 1.0f, 1.0f);
            y += lineH;
            if (TargetLockService.debugHasLock) {
                draw(font, x, y, "Lock: ID=" + TargetLockService.debugLockId
                        + " sDist=" + String.format("%.1f", TargetLockService.debugLockScreenDist)
                        + " wDist=" + String.format("%.1f", TargetLockService.debugLockWorldDist)
                        + " (R=" + RelaxedAimConfig.optionLockRadiusWorld + " x" + RelaxedAimConfig.reFilterMultiplier + ")",
                        0.35f, 1.0f, 0.35f, 1.0f);
            } else {
                // 失锁后显示原因，便于排查
                String reason = "";
                long age = System.currentTimeMillis() - TargetLockService.debugReleaseTimeMs;
                if (TargetLockService.debugReleaseTimeMs > 0) {
                    reason = " (" + TargetLockService.debugReleaseReason + ")";
                }
                draw(font, x, y, "Lock: none" + reason
                        + " (R=" + RelaxedAimConfig.optionLockRadiusWorld
                        + " x" + RelaxedAimConfig.reFilterMultiplier + ")", 0.5f, 0.5f, 0.5f, 1.0f);
            }
            y += lineH;

            // 常显最近一次失锁原因（含已重新锁定后，仍保留最近失锁原因便于排查）
            if (TargetLockService.debugReleaseTimeMs > 0) {
                long agoMs = System.currentTimeMillis() - TargetLockService.debugReleaseTimeMs;
                String ago = agoMs < 1000 ? String.format("%dms", agoMs)
                        : String.format("%.1fs", agoMs / 1000.0);
                draw(font, x, y, "LastLost: " + TargetLockService.debugReleaseReason + " (" + ago + " ago)", 0.9f, 0.6f, 0.4f, 1.0f);
            }

            // 右侧调试面板：实时打印鼠标与锁定目标的关键相对位置
            drawRightPanel();
        }

        // 锁定辅助 UI：范围圈 / 头部锁定指示（紫色圈，锁定目标或最近候选）
        drawLockAssistUI();

        // 临时调试 HUD：显示配置读取状态与系统语言（定位配置未生效/翻译问题，测试后移除）
        drawTempDebugHud();
    }

    /** 临时调试 HUD：右侧面板下方，始终绘制（不受 showHud 控制）。 */
    public static void drawTempDebugHud() {
        try {
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
            int y = 270;

            String lang = "?";
            try {
                lang = zombie.core.Core.getInstance().getOptionLanguageName();
            } catch (Exception e) {
            }
            java.io.File f = null;
            try {
                f = RelaxedAimConfig.getModOptionsFile();
            } catch (Exception e) {
            }
            final String fExists = (f != null && f.exists()) ? "YES" : "NO";

            addRightLine(font, screenW, y, "[DBG] readFailed=" + RelaxedAimConfig.optionsReadFailed, 1.0f, 0.8f, 0.3f, 1.0f);
            y += lineH;
            addRightLine(font, screenW, y, "[DBG] Lang=" + lang, 0.6f, 1.0f, 0.6f, 1.0f);
            y += lineH;
            addRightLine(font, screenW, y, "[DBG] ModOptions.ini exists=" + fExists
                    + (f != null ? " (" + f.getPath() + ")" : ""), 0.7f, 0.8f, 1.0f, 1.0f);
        } catch (Exception e) {
        }
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
                    vDist, wDist, RelaxedAimConfig.optionLockRadiusWorld), 1.0f, 1.0f, 0.0f, 1.0f);
        } catch (Exception e) {
        }
    }

    public static void addRightLine(UIFont font, int screenW, int y, String text, float r, float g, float b, float a) {
        try {
            final float tw = TextManager.instance.MeasureStringX(font, text);
            TextManager.instance.DrawString(font, (int) (screenW - tw - 10), y, text, r, g, b, a * RelaxedAimConfig.optionHudAlpha);
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

    /** 范围圈动画半径（瓦片）：未锁定时收敛到捕获半径，锁定时平滑扩到释放半径。 */
    public static float uiRangeRadiusTiles = 1.5f;

    /**
     * 锁定辅助 UI（核心交互指示，由 optionLockOn 统一控制）：
     *  - 青色范围圈：始终跟随玩家鼠标（原始鼠标瞄准点），未锁定时为捕获半径
     *    （指示「现在瞄准哪一片」），锁定后平滑扩大到释放半径
     *    （lockRadiusWorld × reFilterMultiplier，指示「鼠标偏多少会切换/丢失目标」）。
     *  - 紫色圈：画在将被/正在锁定丧尸的头部骨骼处。
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

        // 仅远程武器显示锁定圈；霰弹枪且启用「霰弹枪不锁定」时也不显示
        try {
            final zombie.inventory.types.HandWeapon weapon = AimAssistService.getActiveWeapon(player);
            if (weapon == null || !weapon.isRanged()) {
                return;
            }
            if (RelaxedAimConfig.optionShotgunNoLock && AimAssistService.isShotgun(weapon)) {
                return;
            }
        } catch (Exception e) {
            return;
        }

        final Texture white = Texture.getWhite();
        final SpriteRenderer sr = SpriteRenderer.instance;
        final float lw = 1.5f;

        // 青色范围圈：始终跟随鼠标（原始鼠标瞄准点）
        if (TargetLockService.rawAimWorldValid) {
            try {
                final float zoom = TargetLockService.getZoom(pIndex);
                final float cx = TargetLockService.worldScreenX(TargetLockService.rawAimWorldX,
                        TargetLockService.rawAimWorldY, TargetLockService.rawAimWorldZ, pIndex);
                final float cy = TargetLockService.worldScreenY(TargetLockService.rawAimWorldX,
                        TargetLockService.rawAimWorldY, TargetLockService.rawAimWorldZ, pIndex);
                // 目标半径：锁定后为释放半径（捕获 × reFilter）
                final float targetTiles = TargetLockService.getLockedTarget() != null
                        ? RelaxedAimConfig.optionLockRadiusWorld * RelaxedAimConfig.reFilterMultiplier
                        : RelaxedAimConfig.optionLockRadiusWorld;
                // 平滑过渡（渐阔/渐收）
                uiRangeRadiusTiles += (targetTiles - uiRangeRadiusTiles) * 0.1f;
                final float radius = uiRangeRadiusTiles * 32.0f * zombie.core.Core.tileScale / zoom;
                drawRing(sr, white, cx, cy, radius, 0.35f, 0.8f, 1.0f, 0.6f * RelaxedAimConfig.optionHudAlpha, lw);
            } catch (Exception e) {
            }
        }

        // 紫色圈：画在「将被/正在锁定」丧尸头部骨骼（Bip01_Head）精确位置，有锁定时指向锁定目标，否则最近候选
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
                drawRing(sr, white, nx, ny, 14.0f, 1.0f, 0.3f, 1.0f, 0.9f * RelaxedAimConfig.optionHudAlpha, lw);
            } catch (Exception e) {
            }
        }
    }

    public static void draw(UIFont font, int x, int y, String text, float r, float g, float b, float a) {
        try {
            TextManager.instance.DrawString(font, x, y, text, r, g, b, a * RelaxedAimConfig.optionHudAlpha);
        } catch (Exception e) {
        }
    }
}
