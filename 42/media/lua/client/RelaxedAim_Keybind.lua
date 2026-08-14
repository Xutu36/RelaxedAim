-- RelaxedAim 临时启用/禁用热键。
-- 追加到游戏的 keyBinding 表：会出现在「设置 → 键位」中，玩家可自行重设。
-- 同时立即注册到 Core（Java 侧 GameKeyboard.isKeyPressed("Toggle RelaxedAim") 使用）。

require "keyBinding"

local bind = {}
bind.value = "Toggle RelaxedAim"
bind.key = Keyboard.KEY_H -- 35
bind.alt = 0
table.insert(keyBinding, bind)

if getCore ~= nil and getCore() ~= nil then
    getCore():addKeyBinding("Toggle RelaxedAim", Keyboard.KEY_H, 0, false, false, false)
end
