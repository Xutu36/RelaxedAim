package com.relaxedaim;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * 手柄瞄准灵敏度：缩放 AimingMode.lerpAiming 的移动速度（仅手柄玩家触发，不影响鼠标）。
 *
 * 本体中手柄准星位置 aimingPositions[i] 由 lerpAiming 每帧向「摇杆目标」线性插值：
 *   out = prev + (target - prev) * f，f = clamp(lerpRate * GameTime.multiplier, 0, 1)
 * 本补丁在 OnExit 重建 f 并按模组设置 optionGamepadSensitivity 缩放后重新插值：
 *   f' = clamp(f * speed, 0, 1) → out' = prev + (target - prev) * f'
 * 仅在「上一帧与目标不重合、且本帧尚未到达目标」（即真正处于插值移动中）时缩放，
 * 吸附/到位等快照分支保持原样。捕捉圈（inputAim ← aimingPositions）随之同速移动。
 *
 * 注意：开发阶段所有成员保持 public（见 RULES.md）。
 */
@Patch(className = "zombie.input.AimingMode", methodName = "lerpAiming")
public final class Patch_AimingModeLerp {

    public static float gamepadPrevX = 0.0f;
    public static float gamepadPrevY = 0.0f;
    public static boolean gamepadPrevValid = false;

    public Patch_AimingModeLerp() {
    }

    @Patch.OnExit
    public static void exit(
            @Patch.Argument(value = 0) zombie.characters.IsoPlayer player,
            @Patch.Argument(value = 2) float targetX,
            @Patch.Argument(value = 3) float targetY,
            @Patch.Argument(value = 7) zombie.iso.Vector2 out) {
        try {
            final float speed = RelaxedAimConfig.optionGamepadSensitivity;
            if (speed == 1.0f) {
                gamepadPrevValid = false;
                return;
            }
            // 仅辅助瞄准场景（手持远程武器）生效
            final zombie.inventory.types.HandWeapon w = AimAssistService.getActiveWeapon(player);
            if (w == null || !w.isRanged()) {
                gamepadPrevValid = false;
                return;
            }
            final float outX = out.x;
            final float outY = out.y;
            if (!gamepadPrevValid) {
                gamepadPrevValid = true;
                gamepadPrevX = outX;
                gamepadPrevY = outY;
                return;
            }
            final float prevX = gamepadPrevX;
            final float prevY = gamepadPrevY;
            final float dx = targetX - prevX;
            final float dy = targetY - prevY;
            final float distPrevTarget = (float) Math.sqrt(dx * dx + dy * dy);
            final float toTargetX = targetX - outX;
            final float toTargetY = targetY - outY;
            final float distOutTarget = (float) Math.sqrt(toTargetX * toTargetX + toTargetY * toTargetY);
            // 已在目标位置，或本帧直接吸附到目标：保持原样（不缩放）
            if (distPrevTarget < 0.001f || distOutTarget < 0.001f) {
                gamepadPrevX = outX;
                gamepadPrevY = outY;
                return;
            }
            // 重建本帧插值因子 f = (out - prev) / (target - prev)
            float f;
            if (Math.abs(dx) >= Math.abs(dy)) {
                f = (outX - prevX) / dx;
            } else {
                f = (outY - prevY) / dy;
            }
            if (f < 0f) {
                f = 0f;
            }
            if (f > 1f) {
                f = 1f;
            }
            float f2 = f * speed;
            if (f2 < 0f) {
                f2 = 0f;
            }
            if (f2 > 1f) {
                f2 = 1f;
            }
            out.x = prevX + dx * f2;
            out.y = prevY + dy * f2;
            gamepadPrevX = out.x;
            gamepadPrevY = out.y;
        } catch (Throwable t) {
        }
    }
}
