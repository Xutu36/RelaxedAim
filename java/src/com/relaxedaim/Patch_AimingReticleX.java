package com.relaxedaim;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * 锁定核心：覆盖 AimingReticle.getXA，使准星/瞄准/弹道对准锁定丧尸头部。
 *
 * 游戏瞄准链（IsoPlayer.calculateAimVector、BallisticsController.update、准星渲染）都以
 * AimingReticle 为根：getX = getXA × zoom 用于瞄准数学，准星渲染直接用 getXA。
 * 在「锁定有效」时把 getXA 覆盖为锁定丧尸头部骨骼（Bip01_Head）的世界→屏幕投影
 * （worldScreenX，与 getNameCoords/锁定标记同一换算）：
 *   - 准星吸附到丧尸头部；
 *   - 角色转身朝向丧尸，且锁定期间不随鼠标移动改变；
 *   - 弹道中心对准头部（命中率/散布仍走原版）。
 * 解锁/关闭时返回原值，原版行为完全不变。
 *
 * 注意：开发阶段所有成员保持 public（见 RULES.md）。
 */
@Patch(className = "zombie.input.AimingReticle", methodName = "getXA")
public final class Patch_AimingReticleX {

    public Patch_AimingReticleX() {
    }

    @Patch.OnExit
    public static void exit(@Patch.Argument(value = 0) int pIndex, @Patch.Return(readOnly = false) int ret) {
        final int v = TargetLockService.overrideReticleX(pIndex);
        if (v != Integer.MIN_VALUE) {
            ret = v;
        }
    }
}
