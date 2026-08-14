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
    public static final String VERSION = "Lock-v1.5";
    public static final String VERSION_NOTES = "锁定改用世界空间比较(复刻游戏准星 AimingReticle+XToIso); 新增瞄准/目标坐标日志";

    // ========== 模组选项（来自 游戏设置-模组，PZAPI.ModOptions 写入 ModOptions.ini） ==========

    /** 选项：启用丧尸自动锁定与标记。默认 true。 */
    public static boolean optionLockOn = true;

    /** 选项：装备霰弹枪瞄准时取消辅助锁定（霰弹枪用于大面积杀伤）。默认 true。 */
    public static boolean optionShotgunNoLock = true;

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
            final BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                final String[] t = line.split("\\|");
                if (t.length >= 4 && "RelaxedAim".equals(t[1])) {
                    if ("lockOn".equals(t[2])) {
                        lockOn = Boolean.parseBoolean(t[3]);
                    } else if ("shotgunNoLock".equals(t[2])) {
                        shotgunNoLock = Boolean.parseBoolean(t[3]);
                    }
                }
            }
            br.close();
            if (lockOn != optionLockOn || shotgunNoLock != optionShotgunNoLock) {
                System.out.println("[RelaxedAim] Mod options -> LockOn=" + lockOn
                        + ", ShotgunNoLock=" + shotgunNoLock);
            }
            optionLockOn = lockOn;
            optionShotgunNoLock = shotgunNoLock;
            optionsReadFailed = false;
        } catch (Throwable t) {
            optionsReadFailed = true;
        }
    }

    /** 筛选范围（屏幕像素）：鼠标到丧尸「瞄准点」的屏幕距离在此范围内才纳入候选。 */
    public static float lockRadiusPx = 70.0f;

    /** 筛选范围（世界瓦片）：准星世界瞄准点到丧尸的水平距离（世界空间主比较，任意缩放下与准星一致）。 */
    public static float lockRadiusWorld = 1.5f;

    /** 调试：每个搜索帧打印瞄准点/鼠标/最近丧尸的坐标对比（用于排查坐标偏差）。 */
    public static boolean debugLogCoordinates = true;

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
