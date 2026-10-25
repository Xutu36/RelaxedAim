package com.relaxedaim;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * 【Spike】在 Viewpoint 的武器瞄准角源头 viewpoint.input.CrosshairAim.yaw(IsoGameCharacter) 上叠加偏移。
 *
 * 为什么选这里：Controls.aim()/lookAngle()、ArmsPitch（手臂/枪口朝向）、弹道、准星都从
 * CrosshairAim.yaw 取值；而相机由 viewpoint.input.Look.yaw 单独控制。
 * 所以在这里加偏移 = 只转动「瞄准/枪口/弹道」，相机与画面朝向不动 —— 正是「只平移准心、不抢鼠标」。
 *
 * 跨加载器注意：本 advice 会被 ZB 内联进 Viewpoint 的类加载器。为避免我们的静态类被复制到该
 * 加载器后读到不同的静态值，偏移量通过 JVM 全局的 System 属性 "relaxedaim.spike" 传递
 * （由 AimAssistService.tickFrame 每帧写入），advice 只引用 JDK 的 System 与游戏类 IsoPlayer。
 *
 * 偏移以「度/弧度」为单位：ret(yaw, 弧度) += radians(offset)。
 *
 * 注意：开发阶段所有成员保持 public（见 RULES.md）。
 */
@Patch(className = "viewpoint.input.CrosshairAim", methodName = "yaw")
public final class Patch_CrosshairAimYaw {

    public Patch_CrosshairAimYaw() {
    }

    @Patch.OnExit
    public static void exit(@Patch.Argument(value = 0) zombie.characters.IsoGameCharacter ch,
                            @Patch.Return(readOnly = false) float ret) {
        try {
            if (!(ch instanceof zombie.characters.IsoPlayer)) {
                return;
            }
            if (ch != zombie.characters.IsoPlayer.getInstance()) {
                return;
            }
            if (!((zombie.characters.IsoPlayer) ch).isAiming()) {
                return;
            }
            final String v = System.getProperty("relaxedaim.aimYawDeg");
            if (v == null) {
                return;
            }
            final double off = Double.parseDouble(v);
            if (off == 0.0) {
                return;
            }
            ret += (float) Math.toRadians(off);
        } catch (Throwable t) {
        }
    }
}
