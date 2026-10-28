package com.relaxedaim;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * Viewpoint 3D（Gunplay ON）下，让「子弹射线」跟随 RelaxedAim 的锁定目标。
 *
 * 【为什么打这里】
 * Viewpoint 的命中判定是纯几何的：viewpoint.gunplay.Shot.draw() 用
 *   line = Sight(相机点, Look.yaw, Look.pitch, 0, 0)
 *   line.turned(Gunplay.swayRight(), Gunplay.swayUp(), way)
 * 得到子弹方向 way，再从枪口沿 way 逐个骨骼胶囊做 Parts.meet 求交，
 * 命中对象与命中部位（RagdollBodyPart：HEAD=2）都由这次求交的几何结果决定。
 *
 * Sight.turned(d, d2, out) 的实现是 out = normalize(forward + right*d + up*d2)，
 * d / d2 是无量纲的切值（tan），所以在这里叠加 tan 偏移，射线就会精确转到目标头部，
 * 命中部位也随之自动变成头部 —— 不需要重写投影/求交，也不碰 BallisticsController。
 *
 * 【影响面】实测 Sight.turned 在整个 Viewpoint 里只有 Shot.java:175 一个调用点
 * （霰弹枪 Pellets 的方向也由 way 派生），因此本补丁只影响射击结算，
 * 不影响相机、准心绘制与手臂姿态。
 *
 * 【跨加载器】advice 会被 ZB 内联进 viewpoint 的类加载器执行，那里看不到 com.relaxedaim
 * 的类，所以本类内部一律不调用自有方法，只读 JDK 的 System 属性传值（见 RULES.md 规则 6）。
 *
 * 注意：开发阶段所有成员保持 public（见 RULES.md）。
 */
@Patch(className = "viewpoint.gunplay.Sight", methodName = "turned")
public final class Patch_VPShotRay {

    public Patch_VPShotRay() {
    }

    /**
     * @param right 第 0 个参数：相机右向切值（可写）
     * @param up    第 1 个参数：相机上向切值（可写）
     */
    @Patch.OnEnter
    public static void enter(@Patch.Argument(value = 0, readOnly = false) double right,
                             @Patch.Argument(value = 1, readOnly = false) double up) {
        try {
            final String on = System.getProperty("relaxedaim.rayOn");
            if (on == null || !"1".equals(on)) {
                return;
            }
            final String rs = System.getProperty("relaxedaim.aimTanR");
            final String us = System.getProperty("relaxedaim.aimTanU");
            double tr = 0.0;
            double tu = 0.0;
            try {
                tr = rs == null ? 0.0 : Double.parseDouble(rs);
            } catch (Throwable t) {
                tr = 0.0;
            }
            try {
                tu = us == null ? 0.0 : Double.parseDouble(us);
            } catch (Throwable t) {
                tu = 0.0;
            }
            if (tr == 0.0 && tu == 0.0) {
                return;
            }
            right += tr;
            up += tu;
            // 探针：证明补丁确实每枪都在执行（HUD 显示）
            int n = 0;
            try {
                final String c = System.getProperty("relaxedaim.probeRay");
                n = c == null ? 0 : Integer.parseInt(c);
            } catch (Throwable t) {
                n = 0;
            }
            System.setProperty("relaxedaim.probeRay", Integer.toString(n + 1));
            System.setProperty("relaxedaim.lastRay",
                    String.format(java.util.Locale.ROOT, "%.5f,%.5f", tr, tu));
        } catch (Throwable t) {
        }
    }
}
