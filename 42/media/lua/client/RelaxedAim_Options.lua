-- RelaxedAim 本地设置（交互/表现）：显示在「游戏设置 - 模组」页面（PZAPI.ModOptions 为游戏自带库）。
-- 服务器平衡设置（辅助强度 AssistStrength）在「沙盒设置」，见 42/media/sandbox-options.txt。
-- 值由 PZAPI.ModOptions:save() 写入 <Zomboid>/ModOptions.ini，Java 侧每帧读取（1s 节流）。

if not PZAPI or not PZAPI.ModOptions then return end

local options = PZAPI.ModOptions:create("RelaxedAim", "RelaxedAim")

options:addTickBox("lockOn", "UI_RelaxedAim_LockOn", true, "UI_RelaxedAim_LockOn_desc")
options:addSlider("lockRadius", "UI_RelaxedAim_LockRadius", 0.5, 5.0, 0.1, 1.5, "UI_RelaxedAim_LockRadius_desc")
options:addSlider("maxLockDistance", "UI_RelaxedAim_MaxLockDistance", 5, 60, 1, 20, "UI_RelaxedAim_MaxLockDistance_desc")
options:addSlider("lockHoldTimeMs", "UI_RelaxedAim_LockHoldTimeMs", 0, 3000, 100, 0, "UI_RelaxedAim_LockHoldTimeMs_desc")
options:addSlider("hudAlpha", "UI_RelaxedAim_HudAlpha", 0.0, 1.0, 0.05, 1.0, "UI_RelaxedAim_HudAlpha_desc")
options:addTickBox("showHud", "UI_RelaxedAim_ShowHud", true, "UI_RelaxedAim_ShowHud_desc")
options:addTickBox("shotgunNoLock", "UI_RelaxedAim_ShotgunNoLock", true, "UI_RelaxedAim_ShotgunNoLock_desc")
options:addTickBox("showLockRange", "UI_RelaxedAim_ShowLockRange", true, "UI_RelaxedAim_ShowLockRange_desc")
options:addTickBox("highlightNearest", "UI_RelaxedAim_HighlightNearest", true, "UI_RelaxedAim_HighlightNearest_desc")
