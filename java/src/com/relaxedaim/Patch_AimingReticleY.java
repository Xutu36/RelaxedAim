package com.relaxedaim;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * 锁定核心：覆盖 AimingReticle.getYA，使准星/瞄准/弹道对准锁定丧尸头部。
 * 与 Patch_AimingReticleX 配套，逻辑同 getXA 的 Y 分量。
 *
 * 注意：开发阶段所有成员保持 public（见 RULES.md）。
 */
@Patch(className = "zombie.input.AimingReticle", methodName = "getYA")
public final class Patch_AimingReticleY {

    public Patch_AimingReticleY() {
    }

    @Patch.OnExit
    public static void exit(@Patch.Argument(value = 0) int pIndex, @Patch.Return(readOnly = false) int ret) {
        final int v = TargetLockService.overrideReticleY(pIndex);
        if (v != Integer.MIN_VALUE) {
            ret = v;
        }
    }
}
