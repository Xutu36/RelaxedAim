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
            draw(font, x, y, "Options: LockOn=" + (RelaxedAimConfig.isLockOnEffective() ? "ON" : "OFF")
                    + " | Assist=" + String.format("%.2f", RelaxedAimConfig.assistStrength())
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
            y += lineH;

            // 【Spike】Viewpoint 3D 状态 + 武器瞄准解耦验证
            try {
                final IsoPlayer pl = IsoPlayer.getInstance();
                final float aimLookDeg = (pl != null ? pl.getLookAngleRadians() : 0.0f) * 180.0f / (float) Math.PI;
                final float facingDeg = (pl != null ? pl.getDirectionAngle() : 0.0f);
                draw(font, x, y, "VP avail=" + ViewpointCompat.available + " active=" + ViewpointCompat.active
                        + " cap=" + ViewpointCompat.captured + " 3rd=" + ViewpointCompat.thirdPerson
                        + " free=" + ViewpointCompat.freeCam
                        + " gp=" + ViewpointCompat.gunplayDebug, 1.0f, 0.6f, 1.0f, 1.0f);
                y += lineH;
                if (!ViewpointCompat.available) {
                    String st = ViewpointCompat.debugStatus != null ? ViewpointCompat.debugStatus : "?";
                    if (st.length() > 120) {
                        st = st.substring(0, 120);
                    }
                    draw(font, x, y, "VP status: " + st, 1.0f, 0.4f, 0.4f, 1.0f);
                    y += lineH;
                }
                draw(font, x, y, String.format("VP camYaw=%.1f camPitch=%.1f | aimLook=%.1f facing=%.1f | cap=%.0fpx | det=%s%s",
                        ViewpointCompat.lookYawDeg, ViewpointCompat.lookPitchDeg,
                        aimLookDeg, facingDeg,
                        ViewpointAssist.captureRadiusPx(),
                        prop("relaxedaim.noChance", "0"), prop("relaxedaim.noDot", "0")),
                        1.0f, 0.9f, 0.4f, 1.0f);
                y += lineH;
                if (ViewpointCompat.available) {
                    draw(font, x, y, String.format("VP cross=(%.0f,%.0f) onTarget=%s shown=%s camUp=%.2f fovY=%.1f%s",
                            ViewpointCompat.crosshairX, ViewpointCompat.crosshairY,
                            ViewpointCompat.crosshairOnTarget, ViewpointCompat.crosshairShown,
                            ViewpointCompat.cameraUp, ViewpointCompat.frameFovY,
                            ViewpointCompat.debugMissing.isEmpty() ? "" : " miss=" + ViewpointCompat.debugMissing),
                            0.8f, 1.0f, 0.9f, 1.0f);
                    y += lineH;
                    draw(font, x, y, String.format("VPA tgt=%d has=%s dist=%.1f raw=%+.1f assist=%+.1f px=%+.0f %s",
                            ViewpointAssist.targetId, ViewpointAssist.hasTarget, ViewpointAssist.targetDist,
                            ViewpointAssist.rawYawDeg, ViewpointAssist.assistYawDeg, ViewpointAssist.aimXpx,
                            ViewpointAssist.debugReason),
                            1.0f, 0.8f, 0.5f, 1.0f);
                    y += lineH;
                    draw(font, x, y, String.format("VPA-pitch raw=%+.1f assist=%+.1f py=%+.0f",
                            ViewpointAssist.rawPitchDeg, ViewpointAssist.assistPitchDeg, ViewpointAssist.aimYpx),
                            1.0f, 0.75f, 0.5f, 1.0f);
                    y += lineH;
                    // 【v5.4】射线/命中部位探针：证明 Patch_VPShotRay / Patch_VPShotPart 确实每枪都在跑
                    draw(font, x, y, String.format("RAY tan=(%+.4f,%+.4f) n=%s last=%s",
                            ViewpointAssist.aimTanR, ViewpointAssist.aimTanU,
                            prop("relaxedaim.probeRay", "0"), prop("relaxedaim.lastRay", "-")),
                            0.7f, 1.0f, 0.7f, 1.0f);
                    y += lineH;
                    try {
                        final IsoPlayer pl2 = IsoPlayer.getInstance();
                        String bpTxt = "?";
                        if (pl2 != null) {
                            final zombie.core.physics.BallisticsController bc = pl2.getBallisticsController();
                            if (bc != null) {
                                final int bp = bc.getTargetedBodyPart(0);
                                final int bpc = bc.getCachedTargetedBodyPart(0);
                                bpTxt = "tgt=" + bp + "/" + bpc;
                            }
                        }
                        final int lastPart = (int) parseFloat(prop("relaxedaim.lastPart", "-1"), -1f);
                        draw(font, x, y, "HIT " + bpTxt
                                + " | shot=" + lastPart + " " + ragdollPartName(lastPart)
                                + " id=" + prop("relaxedaim.lastPartId", "-")
                                + " orig=" + prop("relaxedaim.lastPartOriginal", "-")
                                + " n=" + prop("relaxedaim.probePart", "0"), 0.7f, 1.0f, 1.0f, 1.0f);
                        y += lineH;
                    } catch (Throwable t) {
                    }
                    // 【v5.5】3D 锁定信息（对应 iso 的 Lock: ID/Dist/wDist）+ 相机来源 + 失锁原因
                    try {
                        draw(font, x, y, String.format("Lock3D: ID=%d dist=%.2fm lock=(%+.0f,%+.0f) snap=%.0fms %.0f%% %s",
                                ViewpointAssist.targetId, ViewpointAssist.targetDist,
                                ViewpointAssist.lockPx, ViewpointAssist.lockPy,
                                ViewpointAssist.lockSnapMs, ViewpointAssist.lockProgress * 100.0f,
                                ViewpointAssist.hasTarget ? "HELD" : "none"),
                                0.35f, 1.0f, 0.35f, 1.0f);
                        y += lineH;
                        if (ViewpointAssist.debugReleaseTimeMs > 0) {
                            final long agoMs = System.currentTimeMillis() - ViewpointAssist.debugReleaseTimeMs;
                            final String ago = agoMs < 1000 ? String.format("%dms", agoMs)
                                    : String.format("%.1fs", agoMs / 1000.0);
                            draw(font, x, y, "LastLost3D: " + ViewpointAssist.debugReleaseReason + " (" + ago + " ago)",
                                    0.9f, 0.6f, 0.4f, 1.0f);
                            y += lineH;
                        }
                        final IsoPlayer pc = IsoPlayer.getInstance();
                        draw(font, x, y, String.format("CAM vp=%s cam=(%.1f,%.1f,%.2f) aim=(%.1f,%.1f,%.2f) %s",
                                ViewpointAssist.camFromViewpoint,
                                ViewpointAssist.camX, ViewpointAssist.camY, ViewpointAssist.camZ,
                                pc != null ? pc.getAimOriginPosX() : 0.0f,
                                pc != null ? pc.getAimOriginPosY() : 0.0f,
                                pc != null ? pc.getAimOriginPosZ() : 0.0f,
                                ViewpointCompat.camPlaceDebug),
                                0.6f, 0.9f, 1.0f, 1.0f);
                        y += lineH;
                    } catch (Throwable t) {
                    }
                }
            } catch (Throwable t) {
            }

            // 右侧调试面板：实时打印鼠标与锁定目标的关键相对位置
            drawRightPanel();

            // 临时调试 HUD：显示配置读取状态与系统语言（受 showHud 控制，测试后移除）
            drawTempDebugHud();
        }

        // 锁定辅助 UI：范围圈 / 头部锁定指示（紫色圈，锁定目标或最近候选）
        drawLockAssistUI();
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
        if (ViewpointCompat.active) {
            drawViewpointLockUI();
            return;
        }
        if (!RelaxedAimConfig.isLockOnEffective()) {
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
                drawRing(sr, white, cx, cy, radius, 0.35f, 0.8f, 1.0f, RelaxedAimConfig.optionCaptureCircleAlpha * RelaxedAimConfig.optionHudAlpha, lw);
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
                drawRing(sr, white, nx, ny, 14.0f, 1.0f, 0.3f, 1.0f, RelaxedAimConfig.optionLockMarkerAlpha * RelaxedAimConfig.optionHudAlpha, lw);
            } catch (Exception e) {
            }
        }
    }

    /** 3D（Viewpoint）下的锁定指示：屏幕空间捕捉圈 + 锁定圈（iso 投影在 3D 无意义）。 */
    public static void drawViewpointLockUI() {
        try {
            if (!RelaxedAimConfig.isLockOnEffective()) {
                return;
            }
            final IsoPlayer p = IsoPlayer.getInstance();
            if (p == null || !p.isAiming()) {
                return;
            }
            if (IsoWorld.instance == null || IsoWorld.instance.currentCell == null) {
                return;
            }
            final int w = zombie.iso.IsoCamera.getScreenWidth(0);
            final int h = zombie.iso.IsoCamera.getScreenHeight(0);
            if (w <= 0 || h <= 0) {
                return;
            }
            final Texture white = Texture.getWhite();
            final SpriteRenderer sr = SpriteRenderer.instance;
            final float lw = 1.5f;
            final float alpha = RelaxedAimConfig.optionHudAlpha;

            // 锁定圈位置：按原 iso 瞄准时间算法从捕捉圈中心逐渐吸附到头部（ViewpointAssist.advanceLockRing）
            final float lx = w * 0.5f + ViewpointAssist.lockPx;
            final float ly = h * 0.5f + ViewpointAssist.lockPy;

            // 青色捕捉圈：屏幕中心，半径 = 配置捕获半径 × 倍率（与 ViewpointAssist 的捕捉判定完全一致）
            drawRing(sr, white, w * 0.5f, h * 0.5f, ViewpointAssist.captureRadiusPx(),
                    0.35f, 0.8f, 1.0f, RelaxedAimConfig.optionCaptureCircleAlpha * alpha, lw);
            // 紫色锁定圈：吸附后的位置；失锁后继续绘制直到回到捕捉圈中心
            if (ViewpointAssist.hasTarget
                    || Math.abs(ViewpointAssist.lockPx) + Math.abs(ViewpointAssist.lockPy) > 1.0f) {
                drawRing(sr, white, lx, ly, 14.0f,
                        1.0f, 0.3f, 1.0f, RelaxedAimConfig.optionLockMarkerAlpha * alpha, lw);
            }
        } catch (Throwable t) {
        }
    }

    public static String bodyPartName(int idx) {
        try {
            final Object[] vs = Class.forName("zombie.characters.BodyDamage.BodyPartType").getEnumConstants();
            if (vs != null && idx >= 0 && idx < vs.length) {
                return ((Enum<?>) vs[idx]).name();
            }
        } catch (Throwable t) {
        }
        return "";
    }

    /** 读取系统属性（跨加载器探针用），缺省返回 def。 */
    public static String prop(String key, String def) {
        try {
            final String v = System.getProperty(key);
            return v == null ? def : v;
        } catch (Throwable t) {
            return def;
        }
    }

    public static float parseFloat(String s, float def) {
        try {
            return Float.parseFloat(s.trim());
        } catch (Throwable t) {
            return def;
        }
    }

    /**
     * RagdollBodyPart 序号 → 名称。
     * Viewpoint/游戏弹道用的是 RagdollBodyPart（PELVIS=0, SPINE=1, HEAD=2, ..., COUNT=11），
     * 不是 BodyPartType（那里 Head=8），两者不能混用。
     */
    public static String ragdollPartName(int idx) {
        if (idx < 0) {
            return "-";
        }
        try {
            final Object[] vs = Class.forName("zombie.core.physics.RagdollBodyPart").getEnumConstants();
            if (vs != null && idx < vs.length) {
                return ((Enum<?>) vs[idx]).name();
            }
        } catch (Throwable t) {
        }
        return "?";
    }

    public static void draw(UIFont font, int x, int y, String text, float r, float g, float b, float a) {
        try {
            TextManager.instance.DrawString(font, x, y, text, r, g, b, a * RelaxedAimConfig.optionHudAlpha);
        } catch (Exception e) {
        }
    }
}
