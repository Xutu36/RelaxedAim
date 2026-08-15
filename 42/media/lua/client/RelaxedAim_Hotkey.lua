-- RelaxedAim 热键头顶提示：Java 检测到热键切换后写入 Lua 全局标志，
-- 本脚本在每帧玩家更新时用 getText（随系统语言）显示「辅助瞄准：开/关」。

local installed = false

local function onPlayerUpdate(player)
    if RelaxedAimToggleNotify == true then
        local key = RelaxedAimToggleText or "UI_RelaxedAim_On"
        local text = key
        local ok, v = pcall(function() return getText(key) end)
        if ok and type(v) == "string" and v ~= "" and v ~= key then
            text = v
        end
        local isOn = RelaxedAimToggleOn == true
        if player ~= nil then
            if isOn then
                HaloTextHelper.addGoodText(player, text)
            else
                HaloTextHelper.addBadText(player, text)
            end
        end
        RelaxedAimToggleNotify = false
    end
end

local function install()
    if installed then
        return
    end
    installed = true
    Events.OnPlayerUpdate.Add(onPlayerUpdate)
end

install()
if Events and Events.OnGameStart then
    Events.OnGameStart.Add(install)
end
