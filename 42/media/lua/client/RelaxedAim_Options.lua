-- RelaxedAim 模组选项：显示在「游戏设置 - 模组」页面（PZAPI.ModOptions 为游戏自带库）。
-- 值由 PZAPI.ModOptions:save() 写入 <Zomboid>/ModOptions.ini，Java 侧每帧读取（1s 节流），无需回写。

if not PZAPI or not PZAPI.ModOptions then return end

local options = PZAPI.ModOptions:create("RelaxedAim", "RelaxedAim")

options:addTickBox("lockOn", "UI_RelaxedAim_LockOn", true, "UI_RelaxedAim_LockOn_desc")
options:addTickBox("shotgunNoLock", "UI_RelaxedAim_ShotgunNoLock", true, "UI_RelaxedAim_ShotgunNoLock_desc")
options:addTickBox("showLockRange", "UI_RelaxedAim_ShowLockRange", true, "UI_RelaxedAim_ShowLockRange_desc")
options:addTickBox("highlightNearest", "UI_RelaxedAim_HighlightNearest", true, "UI_RelaxedAim_HighlightNearest_desc")
