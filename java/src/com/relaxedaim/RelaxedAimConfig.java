package com.relaxedaim;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

/**
 * Phase 3: 目标选择算法配置。
 *
 * 用户可配置项通过「游戏设置 - 模组」页面（PZAPI.ModOptions）提供，
 * 值保存在 <Zomboid>/ModOptions.ini，本类运行期读取并缓存（1s 节流）。
 * 修改后重新构建部署并完全重启游戏即可生效。
 */
public final class RelaxedAimConfig {

    /** 版本号唯一来源（HUD 与主菜单显示），每次改动必须升级 + 更新 VERSION_NOTES。 */
    public static final String VERSION = "Lock-v1.8";
    public static final String VERSION_NOTES = "头部瞄准点可配置偏移(站立自适应,倒地不抬); 移除旧小圆+竖线标记; 紫色圈改用头部骨骼投影; 修复锁定后移动鼠标不释放(锁定簿记改用原始鼠标)";

    // ========== 模组选项（来自 游戏设置-模组，PZAPI.ModOptions 写入 ModOptions.ini） ==========

    /** 选项：启用丧尸自动锁定与标记。默认 true。 */
    public static boolean optionLockOn = true;

    /** 选项：装备霰弹枪瞄准时取消辅助锁定（霰弹枪用于大面积杀伤）。默认 true。 */
    public static boolean optionShotgunNoLock = true;

    /** 选项（UI）：绘制锁定范围圈（以当前瞄准点为中心，半径 lockRadiusWorld）。默认 true。 */
    public static boolean optionShowLockRange = true;

    /** 选项（UI）：高亮范围内最近将被锁定的丧尸。默认 true。 */
    public static boolean optionHighlightNearest = true;

    public static long lastOptionsReadMs = 0L;
    public static final long OPTIONS_REFRESH_MS = 1000L;
    public static boolean optionsReadFailed = false;

    /** ModOptions.ini 路径（PZAPI.ModOptions:save 写入的用户目录文件）。 */
    public static File getModOptionsFile() {
        try {
            final String home = System.getProperty("user.home");
            if (home == null || home.isEmpty()) {
                return null;
            }
            return new File(home + File.separator + "Zomboid" + File.separator + "ModOptions.ini");
        } catch (Throwable t) {
            return null;
        }
    }

    /** 节流刷新模组选项（1 秒一次）。文件不存在/读取失败时保持当前值。 */
    public static void refreshModOptions() {
        final long now = System.currentTimeMillis();
        if (now - lastOptionsReadMs < OPTIONS_REFRESH_MS) {
            return;
        }
        lastOptionsReadMs = now;
        try {
            final File f = getModOptionsFile();
            if (f == null || !f.exists()) {
                return;
            }
            boolean lockOn = optionLockOn;
            boolean shotgunNoLock = optionShotgunNoLock;
            boolean showLockRange = optionShowLockRange;
            boolean highlightNearest = optionHighlightNearest;
            final BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                final String[] t = line.split("\\|");
                if (t.length >= 4 && "RelaxedAim".equals(t[1])) {
                    if ("lockOn".equals(t[2])) {
                        lockOn = Boolean.parseBoolean(t[3]);
                    } else if ("shotgunNoLock".equals(t[2])) {
                        shotgunNoLock = Boolean.parseBoolean(t[3]);
                    } else if ("showLockRange".equals(t[2])) {
                        showLockRange = Boolean.parseBoolean(t[3]);
                    } else if ("highlightNearest".equals(t[2])) {
                        highlightNearest = Boolean.parseBoolean(t[3]);
                    }
                }
            }
            br.close();
            if (lockOn != optionLockOn || shotgunNoLock != optionShotgunNoLock
                    || showLockRange != optionShowLockRange || highlightNearest != optionHighlightNearest) {
                System.out.println("[RelaxedAim] Mod options -> LockOn=" + lockOn
                        + ", ShotgunNoLock=" + shotgunNoLock
                        + ", ShowLockRange=" + showLockRange
                        + ", HighlightNearest=" + highlightNearest);
            }
            optionLockOn = lockOn;
            optionShotgunNoLock = shotgunNoLock;
            optionShowLockRange = showLockRange;
            optionHighlightNearest = highlightNearest;
            optionsReadFailed = false;
        } catch (Throwable t) {
            optionsReadFailed = true;
        }
    }

    /** 筛选范围（屏幕像素）：鼠标到丧尸「瞄准点」的屏幕距离在此范围内才纳入候选。 */
    public static float lockRadiusPx = 70.0f;

    /** 筛选范围（世界瓦片）：准星世界瞄准点到丧尸的水平距离（世界空间主比较，任意缩放下与准星一致）。 */
    public static float lockRadiusWorld = 1.5f;

    /**
     * 头部瞄准偏移（世界Z，瓦片）：在头部骨骼 Bip01_Head 基础上沿世界向上再抬高的量，
     * 用于把锁定点从「颈部/头骨底部」调整到「视觉头部」。站立时生效；倒地（isProne）时不抬，
     * 以适配姿态（倒地的头已在贴地位置）。
     */
    public static float headAimOffsetZ = 0.05f;

    /** 调试：控制 [RelaxedAim DEBUG] / [RelaxedAim AIMLOG] / Phase2 等调试日志是否打印。默认 false（功能已正常，跳过噪音）。 */
    public static boolean debugConsoleLogs = false;

    /** 重筛选距离倍率（必须 > 1）：锁定后，仅当锁定目标与鼠标的屏幕距离超过 lockRadiusPx * 该值 时才重新搜索。 */
    public static float reFilterMultiplier = 1.5f;

    /** 单次搜索最多保留的候选数量（有限遍历上限，达到即停止收集）。 */
    public static int maxCandidates = 64;

    /** 完整搜索节流帧数（每 N 帧做一次候选扫描；锁定校验每帧仍进行）。 */
    public static int searchIntervalFrames = 5;

    /** 锁定标记：环颜色 RGBA。 */
    public static float markerR = 1.0f;
    public static float markerG = 0.35f;
    public static float markerB = 0.15f;
    public static float markerA = 1.0f;

    /** 锁定标记：环的屏幕半径（像素）。 */
    public static float markerRadiusPx = 18.0f;

    /** 锁定标记：环/竖线线宽（像素）。 */
    public static float markerLineWidth = 2.5f;

    public RelaxedAimConfig() {
    }
}
