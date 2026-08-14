package com.relaxedaim;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

/**
 * 配置中心。
 *
 * 本地（交互/表现）项通过「游戏设置 - 模组」页面（PZAPI.ModOptions）提供，
 * 值保存在 <Zomboid>/ModOptions.ini，本类运行期读取并缓存（1s 节流）。
 * 服务器（平衡）项通过「沙盒设置」（sandbox-options.txt）提供，从 Lua SandboxVars 读取。
 * 修改后重新构建部署并完全重启游戏即可生效。
 */
public final class RelaxedAimConfig {

    /** 版本号唯一来源（HUD 与主菜单显示），每次改动必须升级 + 更新 VERSION_NOTES。 */
    public static final String VERSION = "Lock-v1.9";
    public static final String VERSION_NOTES = "头部偏移改 neck→head 方向姿态自适应; showHud开关; 本地5项+服务器辅助强度分两组; 平滑准心吸附(强度+瞄准等级); 临时热键(可自设)";

    // ========== 本地设置（游戏设置-模组，ModOptions.ini） ==========

    /** 启用丧尸自动锁定与标记。默认 true。 */
    public static boolean optionLockOn = true;

    /** 锁定范围圈（UI）。默认 true。 */
    public static boolean optionShowLockRange = true;

    /** 高亮最近将被锁定丧尸（UI）。默认 true。 */
    public static boolean optionHighlightNearest = true;

    /** 装备霰弹枪瞄准时取消辅助锁定。默认 true。 */
    public static boolean optionShotgunNoLock = true;

    /** 显示 OverlayHUD（调试信息与右侧面板）。发布时设为 false。默认 true。 */
    public static boolean optionShowHud = true;

    /** 锁定捕获半径（世界瓦片）：瞄准点到丧尸的水平距离在此内纳入候选。默认 1.5。 */
    public static float optionLockRadiusWorld = 1.5f;

    /** 最大锁定距离（世界瓦片）：超过该距离不锁定（与武器射程取较小值）。默认 20。 */
    public static float optionMaxLockDistance = 20.0f;

    /** 锁定保持时间（毫秒）：锁定目标暂时失效时（遮挡/短暂偏离等）保留锁定的时长，超过后释放；0 表示立即释放。默认 0。 */
    public static int optionLockHoldTimeMs = 0;

    /** OverlayHUD 透明度（0-1）。默认 1.0。 */
    public static float optionHudAlpha = 1.0f;

    public static long lastOptionsReadMs = 0L;
    public static final long OPTIONS_REFRESH_MS = 1000L;
    public static boolean optionsReadFailed = false;

    // ========== 服务器设置（沙盒设置，SandboxVars.RelaxedAim.assistStrength） ==========

    /**
     * 辅助强度（0-1）：与玩家 Aiming 瞄准等级共同决定「从得到锁定单位到准心平滑移动至锁定丧尸头部」所需时间。
     * 强度越高、瞄准等级越高 → 平滑吸附越快。默认 0.5。
     */
    public static float assistStrength = 0.5f;

    public static long lastSandboxReadMs = 0L;
    public static final long SANDBOX_REFRESH_MS = 1000L;

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
            boolean showLockRange = optionShowLockRange;
            boolean highlightNearest = optionHighlightNearest;
            boolean shotgunNoLock = optionShotgunNoLock;
            boolean showHud = optionShowHud;
            float lockRadiusWorld = optionLockRadiusWorld;
            float maxLockDistance = optionMaxLockDistance;
            int lockHoldTimeMs = optionLockHoldTimeMs;
            float hudAlpha = optionHudAlpha;
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
                    } else if ("showLockRange".equals(id)) {
                        showLockRange = Boolean.parseBoolean(val);
                    } else if ("highlightNearest".equals(id)) {
                        highlightNearest = Boolean.parseBoolean(val);
                    } else if ("showHud".equals(id)) {
                        showHud = Boolean.parseBoolean(val);
                    } else if ("lockRadius".equals(id)) {
                        lockRadiusWorld = parseFloat(val, optionLockRadiusWorld);
                    } else if ("maxLockDistance".equals(id)) {
                        maxLockDistance = parseFloat(val, optionMaxLockDistance);
                    } else if ("lockHoldTimeMs".equals(id)) {
                        lockHoldTimeMs = (int) parseFloat(val, optionLockHoldTimeMs);
                    } else if ("hudAlpha".equals(id)) {
                        hudAlpha = clamp01(parseFloat(val, optionHudAlpha));
                    }
                }
            }
            br.close();
            if (lockOn != optionLockOn || shotgunNoLock != optionShotgunNoLock
                    || showLockRange != optionShowLockRange || highlightNearest != optionHighlightNearest
                    || showHud != optionShowHud || lockRadiusWorld != optionLockRadiusWorld
                    || maxLockDistance != optionMaxLockDistance || lockHoldTimeMs != optionLockHoldTimeMs
                    || hudAlpha != optionHudAlpha) {
                System.out.println("[RelaxedAim] Local options -> LockOn=" + lockOn
                        + " Radius=" + lockRadiusWorld + " MaxDist=" + maxLockDistance
                        + " Hold=" + lockHoldTimeMs + "ms HudAlpha=" + hudAlpha
                        + " ShowHud=" + showHud);
            }
            optionLockOn = lockOn;
            optionShowLockRange = showLockRange;
            optionHighlightNearest = highlightNearest;
            optionShotgunNoLock = shotgunNoLock;
            optionShowHud = showHud;
            optionLockRadiusWorld = lockRadiusWorld;
            optionMaxLockDistance = maxLockDistance;
            optionLockHoldTimeMs = lockHoldTimeMs;
            optionHudAlpha = hudAlpha;
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

    /** 节流刷新服务器沙盒设置（1 秒一次）。从 Lua 全局表 SandboxVars.RelaxedAim.<id> 读取。 */
    public static void refreshSandboxOptions() {
        final long now = System.currentTimeMillis();
        if (now - lastSandboxReadMs < SANDBOX_REFRESH_MS) {
            return;
        }
        lastSandboxReadMs = now;
        try {
            final float strength = getSandboxDouble("RelaxedAim", "AssistStrength", 0.5f);
            if (strength != assistStrength) {
                System.out.println("[RelaxedAim] Sandbox assistStrength -> " + strength);
            }
            assistStrength = clamp01(strength);
        } catch (Throwable t) {
        }
    }

    /** 从 Lua 全局表 SandboxVars.<page>.<id> 读取 double 沙盒选项。 */
    public static float getSandboxDouble(String page, String id, float defaultValue) {
        try {
            final Object env = zombie.Lua.LuaManager.env;
            if (env instanceof se.krka.kahlua.vm.KahluaTable) {
                final Object sv = ((se.krka.kahlua.vm.KahluaTable) env).rawget("SandboxVars");
                if (sv instanceof se.krka.kahlua.vm.KahluaTable) {
                    final Object pg = ((se.krka.kahlua.vm.KahluaTable) sv).rawget(page);
                    if (pg instanceof se.krka.kahlua.vm.KahluaTable) {
                        final Object val = ((se.krka.kahlua.vm.KahluaTable) pg).rawget(id);
                        if (val instanceof Number) {
                            return ((Number) val).floatValue();
                        }
                    }
                }
            }
        } catch (Throwable t) {
        }
        return defaultValue;
    }

    // ========== 算法参数（暂为静态常量，后续可转配置） ==========

    /** 重筛选距离倍率（必须 > 1）：锁定后，仅当锁定目标与原始鼠标的距离超过 lockRadiusWorld * 该值 时才重新搜索。 */
    public static float reFilterMultiplier = 1.5f;

    /** 虚拟空间回退半径（屏幕像素，×zoom 换算）：仅当原始鼠标世界瞄准点不可用时使用。 */
    public static float lockRadiusPx = 70.0f;

    /** 单次搜索最多保留的候选数量（有限遍历上限，达到即停止收集）。 */
    public static int maxCandidates = 64;

    /** 完整搜索节流帧数（每 N 帧做一次候选扫描；锁定校验每帧仍进行）。 */
    public static int searchIntervalFrames = 5;

    /**
     * 头部瞄准偏移（瓦片）：沿「颈部骨骼 → 头部骨骼」方向再偏移的量，用于把锁定点从
     * 「颈部/头骨底部」调整到「视觉头部」。方向随姿态自适应：待机低头沿头骨倾斜方向，
     * 倒地沿贴地的头骨方向，不适用简单 Z 轴偏移。
     */
    public static float headAimOffset = 0.15f;

    /** 调试：控制 [RelaxedAim DEBUG] / [RelaxedAim AIMLOG] / Phase2 等调试日志是否打印。默认 false。 */
    public static boolean debugConsoleLogs = false;

    public RelaxedAimConfig() {
    }
}
