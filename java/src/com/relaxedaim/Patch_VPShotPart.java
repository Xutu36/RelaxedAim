package com.relaxedaim;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * 诊断探针：记录 Viewpoint 在一次射击结算里，给每个被命中目标分配的「部位码」。
 *
 * viewpoint.gunplay.Shot.part(targetId, original) 由 CombatHooks.targetedPart 调用
 * （VP 的 Patch_TargetedPart 把 BallisticsController.getCachedTargetedBodyPart 的返回值
 * 转到这条链上），返回值是 RagdollBodyPart 的序号：
 *   PELVIS=0, SPINE=1, HEAD=2, ... LEFT_LOWER_ARM=8, ... COUNT=11（11 = 无部位）
 * 而 CombatManager.applyRangeHitLocationDamage 用 isHead(2) 判定 ×3.0 头部伤害，
 * isArm/isLeg 判定 ×0.05 四肢伤害。
 *
 * 本补丁不改任何返回值，只把结果写入系统属性 relaxedaim.lastPart / lastPartId，
 * 供 HUD 直接显示 —— 用于验证「射线偏移后是否真的命中头部」，
 * 避免再出现「HIT 行显示 11 就断定无效」的误判（11 是"无部位"哨兵）。
 *
 * 跨加载器：同样不引用 com.relaxedaim 的类，只用 System 属性。
 *
 * 注意：开发阶段所有成员保持 public（见 RULES.md）。
 */
@Patch(className = "viewpoint.gunplay.Shot", methodName = "part")
public final class Patch_VPShotPart {

    /** RagdollBodyPart.BODYPART_COUNT.ordinal()（"无部位"）。 */
    public static final int NO_PART = 11;

    public Patch_VPShotPart() {
    }

    @Patch.OnExit
    public static void exit(@Patch.Argument(value = 0) int targetId,
                            @Patch.Argument(value = 1) int original,
                            @Patch.Return(readOnly = true) int ret) {
        try {
            System.setProperty("relaxedaim.lastPart", Integer.toString(ret));
            System.setProperty("relaxedaim.lastPartId", Integer.toString(targetId));
            System.setProperty("relaxedaim.lastPartOriginal", Integer.toString(original));
            int n = 0;
            try {
                final String c = System.getProperty("relaxedaim.probePart");
                n = c == null ? 0 : Integer.parseInt(c);
            } catch (Throwable t) {
                n = 0;
            }
            System.setProperty("relaxedaim.probePart", Integer.toString(n + 1));
        } catch (Throwable t) {
        }
    }
}
