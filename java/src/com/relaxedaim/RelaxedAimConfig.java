package com.relaxedaim;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

/**
 * 配置中心。
 *
 * 配置项经「游戏设置 - 模组」页面（PZAPI.ModOptions）提供，
 * 值保存在 <Zomboid>/ModOptions.ini，本类运行期读取并缓存（1s 节流）。
 *
 * 注：AssistStrength 及平滑公式参数为「临时测试项」，测试出合适值后将硬编码并移出配置。
 */
public final class RelaxedAimConfig {

    /** 版本号唯一来源（HUD 与主菜单显示），每次改动必须升级 + 更新 VERSION_NOTES。 */
    public static final String VERSION = "Lock-v2.6";
    public static final String VERSION_NOTES = "移除OverlayHUD配置项(showHud/hudAlpha,发布默认隐藏); 重新签名(ZBS)";

    // ========== 本地设置（游戏设置-模组，ModOptions.ini） ==========

    /** 启用丧尸自动锁定（基础值，来自模组设置）。默认 true。 */
    public static boolean optionLockOn = true;

    /** 热键翻转标志：临时启用/禁用（不写入配置文件，避免被配置刷新覆盖）。 */
    public static boolean hotkeyToggled = false;

    /** 实际是否启用辅助 = 基础值 XOR 热键翻转。 */
    public static boolean isLockOnEffective() {
        return optionLockOn != hotkeyToggled;
    }

    /** 装备霰弹枪瞄准时取消辅助锁定。默认 true。 */
    public static boolean optionShotgunNoLock = true;

    /** 显示 OverlayHUD（调试信息与右侧面板）。发布时设 false。默认 true。 */
    public static boolean optionShowHud = false;

    /** OverlayHUD 透明度（0-1）。默认 1.0。 */
    public static float optionHudAlpha = 1.0f;

    /** 锁定捕获半径（世界瓦片）。默认 1.5。 */
    public static float optionLockRadiusWorld = 1.5f;

    /** 最大锁定距离（世界瓦片）。默认 25。 */
    public static float optionMaxLockDistance = 25.0f;

    /** 锁定保持时间（毫秒）。默认 500。 */
    public static int optionLockHoldTimeMs = 500;

    /** 临时启用/禁用热键键码（PZAPI keybind，默认顶部数字行 0 = 48）。 */
    public static int optionToggleKey = 48;

    // ---------- 平滑吸附公式参数（已定稿硬编码，非配置项） ----------

    /** 辅助强度：吸附效率公式系数。 */
    public static float assistStrength = 1.0f;

    /** 吸附时间上限（毫秒）。 */
    public static float snapMaxMs = 1000f;

    /** 吸附时间下限（毫秒）。 */
    public static float snapMinMs = 100f;

    /** 辅助强度换算系数。 */
    public static float snapStrengthScale = 1.0f;

    /** 瞄准等级收益封顶（对数衰减）。 */
    public static float snapAimCap = 10f;

    /** 强锁定屏幕距离阈值（像素）：准心距锁定头部骨骼小于该值时进入强锁定（完全吸附，不再平滑）。 */
    public static final float STRONG_LOCK_THRESHOLD_PX = 15f;

    public static long lastOptionsReadMs = 0L;
    public static final long OPTIONS_REFRESH_MS = 1000L;
    public static boolean optionsReadFailed = false;

    /** ModOptions.ini 路径（PZAPI.ModOptions:save 写入的用户目录文件，实际位于 <userdir>/Lua/ModOptions.ini）。 */
    public static File getModOptionsFile() {
        try {
            final String home = System.getProperty("user.home");
            if (home == null || home.isEmpty()) {
                return null;
            }
            return new File(home + File.separator + "Zomboid" + File.separator + "Lua" + File.separator + "ModOptions.ini");
        } catch (Throwable t) {
            return null;
        }
    }

    /** 节流刷新本地设置（1 秒一次）。文件不存在/读取失败时保持当前值。 */
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
            float lockRadiusWorld = optionLockRadiusWorld;
            float maxLockDistance = optionMaxLockDistance;
            int lockHoldTimeMs = optionLockHoldTimeMs;
            int toggleKey = optionToggleKey;
            final BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                final String[] t = line.split("\\|");
                if (t.length >= 4 && "RelaxedAim".equals(t[1])) {
                    final String id = t[2];
                    final String val = t[3];
                    if ("lockOn".equals(id)) {
                        lockOn = Boolean.parseBoolean(val);
                    } else if ("shotgunNoLock".equals(id)) {
                        shotgunNoLock = Boolean.parseBoolean(val);
                    } else if ("lockRadius".equals(id)) {
                        lockRadiusWorld = parseFloat(val, optionLockRadiusWorld);
                    } else if ("maxLockDistance".equals(id)) {
                        maxLockDistance = parseFloat(val, optionMaxLockDistance);
                    } else if ("lockHoldTimeMs".equals(id)) {
                        lockHoldTimeMs = (int) parseFloat(val, optionLockHoldTimeMs);
                    } else if ("toggleKey".equals(id)) {
                        toggleKey = (int) parseFloat(val, optionToggleKey);
                    }
                }
            }
            br.close();
            optionLockOn = lockOn;
            optionShotgunNoLock = shotgunNoLock;
            optionLockRadiusWorld = lockRadiusWorld;
            optionMaxLockDistance = maxLockDistance;
            optionLockHoldTimeMs = lockHoldTimeMs;
            optionToggleKey = toggleKey;
            optionsReadFailed = false;
        } catch (Throwable t) {
            optionsReadFailed = true;
        }
    }

    private static float parseFloat(String s, float def) {
        try {
            return Float.parseFloat(s.trim());
        } catch (Throwable t) {
            return def;
        }
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    // ========== 算法参数（暂为静态常量） ==========

    /** 重筛选距离倍率（必须 > 1）。 */
    public static float reFilterMultiplier = 1.5f;

    /** 虚拟空间回退半径（屏幕像素，×zoom 换算）：仅当原始鼠标世界瞄准点不可用时使用。 */
    public static float lockRadiusPx = 70.0f;

    /** 单次搜索候选上限。 */
    public static int maxCandidates = 64;

    /** 候选全扫描节流帧数。 */
    public static int searchIntervalFrames = 5;

    /** 头部瞄准偏移（瓦片）：沿「颈部→头部」方向。姿态自适应。 */
    public static float headAimOffset = 0.15f;

    /** 调试日志开关。默认 false。 */
    public static boolean debugConsoleLogs = false;

    public RelaxedAimConfig() {
    }
}
