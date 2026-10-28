package com.relaxedaim;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * 把 RelaxedAim 的吸附量（屏幕像素）叠加到 Viewpoint 的十字准心输出
 * viewpoint.input.Controls.reticle(int pIndex, int def, boolean isX)。
 *
 * 已实测：此补丁能让「十字准心」明显吸附到锁定目标（漂浮点/弹道仍走 VP gunplay 自己的锚点）。
 * 像素偏移经系统属性 "relaxedaim.aimXpx"/"relaxedaim.aimYpx"（由 ViewpointAssist 每帧换算）传递。
 *
 * 跨加载器：advice 内联进 Viewpoint 类加载器，不引用 com.relaxedaim 类。
 *
 * 注意：开发阶段所有成员保持 public（见 RULES.md）。
 */
@Patch(className = "viewpoint.input.Controls", methodName = "reticle")
public final class Patch_VPReticleOffset {

    public Patch_VPReticleOffset() {
    }

    @Patch.OnExit
    public static void exit(@Patch.Argument(value = 0) int pIndex,
                            @Patch.Argument(value = 2) boolean isX,
                            @Patch.Return(readOnly = false) int ret) {
        try {
            int local;
            try {
                local = zombie.characters.IsoPlayer.getPlayerIndex();
            } catch (Throwable t) {
                return;
            }
            if (pIndex != local) {
                return;
            }
            final zombie.characters.IsoPlayer p = zombie.characters.IsoPlayer.getInstance();
            if (p == null || !p.isAiming()) {
                return;
            }
            final String key = isX ? "relaxedaim.aimXpx" : "relaxedaim.aimYpx";
            final String v = System.getProperty(key);
            if (v == null) {
                return;
            }
            final double px = Double.parseDouble(v);
            if (px == 0.0) {
                return;
            }
            ret += (int) Math.round(px);
        } catch (Throwable t) {
        }
    }
}
