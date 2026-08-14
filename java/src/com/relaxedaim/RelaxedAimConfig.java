package com.relaxedaim;

/**
 * Phase 3: 目标选择算法配置。
 *
 * 现阶段以静态字段提供，后续 Phase 7 会接入真实配置文件/UI。
 * 修改后重新构建部署并完全重启游戏即可生效。
 */
public final class RelaxedAimConfig {

    /** 筛选范围（屏幕像素）：鼠标到丧尸「瞄准点」的屏幕距离在此范围内才纳入候选。 */
    public static float lockRadiusPx = 70.0f;

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
