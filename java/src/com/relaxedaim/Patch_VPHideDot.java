package com.relaxedaim;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * 隐藏 Viewpoint Gunplay 的「扰动漂浮点」。
 *
 * 屏幕中心那个会随机漂移的小点由 viewpoint.SceneDrawer.drawCrosshair() 用 GL scissor 直接画，
 * 开关是 viewpoint.core.View.crosshairShown（每帧由 View.crosshairSnapshot(boolean) 赋值）。
 * 我们通过把 crosshairSnapshot 的入参改成 false，让 crosshairShown 恒为 false：
 *   crosshairShown = CROSSHAIR.get() && false → false
 * 于是 SceneDrawer 不再画那个点，也不影响其它任何东西（该字段只被 drawCrosshair 的判断使用）。
 *
 * 为什么不 patch drawCrosshair：那是私有方法，版本间更容易改；crosshairSnapshot 是
 * public static + 单 boolean 参数，签名稳定。
 *
 * 跨加载器：advice 内联进 viewpoint 的类加载器，不引用 com.relaxedaim 的类。
 *
 * 注意：开发阶段所有成员保持 public（见 RULES.md）。
 */
@Patch(className = "viewpoint.core.View", methodName = "crosshairSnapshot")
public final class Patch_VPHideDot {

    public Patch_VPHideDot() {
    }

    @Patch.OnEnter
    public static void enter(@Patch.Argument(value = 0, readOnly = false) boolean shown) {
        try {
            shown = false;
            if (System.getProperty("relaxedaim.noDot") == null) {
                System.setProperty("relaxedaim.noDot", "1");
            }
        } catch (Throwable t) {
        }
    }
}
