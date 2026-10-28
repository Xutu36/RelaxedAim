package com.relaxedaim;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * 3D（Viewpoint Gunplay）下移除原版枪械的「命中率」与由此推导的「不确定散布」。
 *
 * 【原理】Viewpoint 的 gunplay 并没有另起一套命中率，它直接复用游戏的
 *   CombatManager.calculateHitChanceData(...).hitChance
 * 把它归一化成 0~1 的概率，然后：
 *  - Aim.cone() → Reference.radius(..., chance) 得到「瞄准环半径」aim.radius
 *  - Gunplay.update()：focus.tighten(aim.radius, floor) → sway.start/advance 用该半径
 *  - Sway 在该半径内做 10+10 条正弦叠加的随机游走，得到 right/up
 *  - Shot.draw() 用 sway 偏转子弹方向 → 于是「明明瞄准了却打不中」
 *  - 屏幕上的漂浮点（View.crosshairX/Y）也是这条 sway 画出来的
 *
 * Odds.ask(...) 是上述概率的唯一出口（两个 chance() 重载都走它）。
 * 让它把「合法方向」的命中率拉满为 1.0f（只放过恰好 0 的"打不了"哨兵）：
 *  - 命中率恒为 100%，不再把远距离/低技能的候选剔掉（Candidates.seated / Shot.shootable）
 *  - Reference.radius(..., 1.0) 返回 0 → aim.radius = 0 → sway 半径 0 → right/up 恒 0
 *    → 子弹严格沿「相机 + RelaxedAim 的 tan 偏移」飞，不再随机散布
 *  - 瞄准环随之收成最小（不再显示一个与真实弹道无关的大圈）
 *
 * 只影响 Viewpoint 的 gunplay（3D）；iso 原版弹道完全不变。
 * 霰弹枪的弹丸散射（Pellets 用的 Gunplay.pattern）不受影响，保持原有手感。
 * 车内"这个方向打不了"的判定（chance==0）保持原样，不会变成穿车射击。
 *
 * 跨加载器：advice 内联进 viewpoint 的类加载器，不引用 com.relaxedaim 的类。
 *
 * 注意：开发阶段所有成员保持 public（见 RULES.md）。
 */
@Patch(className = "viewpoint.gunplay.Odds", methodName = "ask")
public final class Patch_VPHitChance {

    public Patch_VPHitChance() {
    }

    @Patch.OnExit
    public static void exit(@Patch.Return(readOnly = false) float ret) {
        try {
            // 游戏用「恰好 0」表示"这个方向打不了"（车窗外角度外、驾车时双手武器等），
            // Viewpoint 的 Candidates.seated 正是靠 chance>0 剔除这些目标 —— 必须保留。
            // 其它情况（含负值：合法但命中率被扣成负数）一律拉满，否则
            // Reference.radiusAt(负值) 会返回最大值 8.0，散布反而变得极大。
            if (ret != 0.0f) {
                ret = 1.0f;
            }
            if (System.getProperty("relaxedaim.noChance") == null) {
                System.setProperty("relaxedaim.noChance", "1");
            }
        } catch (Throwable t) {
        }
    }
}
