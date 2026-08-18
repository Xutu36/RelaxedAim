-- RelaxedAim 本地设置：显示在「游戏设置 - 模组」页面（PZAPI.ModOptions 为游戏自带库）。
-- 值由 PZAPI.ModOptions:save() 写入 <Zomboid>/Lua/ModOptions.ini，Java 侧每帧读取（1s 节流）。

if not PZAPI or not PZAPI.ModOptions then return end

-- Mod 名称随语言：中文环境显示中文名，其余语言回退英文（getText 解析 UI_RelaxedAim_ModName）。
local options = PZAPI.ModOptions:create("RelaxedAim", "UI_RelaxedAim_ModName")

local config = {}

config.lockOn = options:addTickBox("lockOn", "UI_RelaxedAim_LockOn", true, "UI_RelaxedAim_LockOn_desc")
options:addDescription("UI_RelaxedAim_LockOn_desc")

config.toggleKey = options:addKeyBind("toggleKey", "UI_RelaxedAim_ToggleKey", 48, "UI_RelaxedAim_ToggleKey_desc")
options:addDescription("UI_RelaxedAim_ToggleKey_desc")

-- 手柄热键：选择手柄按键随时开关辅助（默认十字键上）。值写入 ModOptions.ini 为 combobox|RelaxedAim|gamepadToggle|<1-based index>
config.gamepadToggle = options:addComboBox("gamepadToggle", "UI_RelaxedAim_GamepadToggle", "UI_RelaxedAim_GamepadToggle_desc")
config.gamepadToggle:addItem("UI_RelaxedAim_PadBtn_DPadUp", true)
config.gamepadToggle:addItem("UI_RelaxedAim_PadBtn_DPadDown")
config.gamepadToggle:addItem("UI_RelaxedAim_PadBtn_DPadLeft")
config.gamepadToggle:addItem("UI_RelaxedAim_PadBtn_DPadRight")
config.gamepadToggle:addItem("UI_RelaxedAim_PadBtn_A")
config.gamepadToggle:addItem("UI_RelaxedAim_PadBtn_B")
config.gamepadToggle:addItem("UI_RelaxedAim_PadBtn_X")
config.gamepadToggle:addItem("UI_RelaxedAim_PadBtn_Y")
config.gamepadToggle:addItem("UI_RelaxedAim_PadBtn_LB")
config.gamepadToggle:addItem("UI_RelaxedAim_PadBtn_RB")
config.gamepadToggle:addItem("UI_RelaxedAim_PadBtn_L3")
config.gamepadToggle:addItem("UI_RelaxedAim_PadBtn_R3")
config.gamepadToggle:addItem("UI_RelaxedAim_PadBtn_Back")
config.gamepadToggle:addItem("UI_RelaxedAim_PadBtn_Start")
options:addDescription("UI_RelaxedAim_GamepadToggle_desc")

config.lockRadius = options:addSlider("lockRadius", "UI_RelaxedAim_LockRadius", 0.5, 2.0, 0.1, 1.5, "UI_RelaxedAim_LockRadius_desc")
options:addDescription("UI_RelaxedAim_LockRadius_desc")

config.maxLockDistance = options:addSlider("maxLockDistance", "UI_RelaxedAim_MaxLockDistance", 20, 100, 1, 25, "UI_RelaxedAim_MaxLockDistance_desc")
options:addDescription("UI_RelaxedAim_MaxLockDistance_desc")

config.lockHoldTimeMs = options:addSlider("lockHoldTimeMs", "UI_RelaxedAim_LockHoldTimeMs", 0, 3000, 100, 500, "UI_RelaxedAim_LockHoldTimeMs_desc")
options:addDescription("UI_RelaxedAim_LockHoldTimeMs_desc")

config.shotgunNoLock = options:addTickBox("shotgunNoLock", "UI_RelaxedAim_ShotgunNoLock", true, "UI_RelaxedAim_ShotgunNoLock_desc")
options:addDescription("UI_RelaxedAim_ShotgunNoLock_desc")

-- ============================================================================
-- 自定义键位捕获：原版 ISSetKeybindDialog/MainOptions.keyPressHandler 与 PZAPI
-- keybind 选项不兼容（会 "attempted index of non-table"）。拦截我们的键位按钮，
-- 打开一个简单按键捕获对话框（参考信标模组思路，仅单键）。
-- ============================================================================
local function installKeybindCapture()
    if not PZAPI or not PZAPI.ModOptions then return end
    if not config or not config.toggleKey or not MainOptions then return end
    if config._captureInstalled then return end
    config._captureInstalled = true

    pcall(require, "ISUI/ISPanel")
    pcall(require, "ISUI/ISButton")
    pcall(require, "OptionScreens/MainOptions")
    if not ISPanel or not ISButton or not MainOptions then
        config._captureInstalled = false
        return
    end

    local CaptureDialog = ISPanel:derive("RelaxedAimKeybindCapture")

    function CaptureDialog:new(option)
        local screenW = getCore():getScreenWidth()
        local screenH = getCore():getScreenHeight()
        local o = ISPanel:new(screenW / 2 - 220, screenH / 2 - 70, 440, 140)
        setmetatable(o, self)
        self.__index = self
        o.option = option
        o.backgroundColor = { r = 0, g = 0, b = 0, a = 0.92 }
        o.borderColor = { r = 0.6, g = 0.6, b = 0.6, a = 1 }
        o:setWantKeyEvents(true)
        return o
    end

    function CaptureDialog:createChildren()
        self.cancelBtn = ISButton:new(self.width / 2 - 50, self.height - 42, 100, 24,
            getText("UI_Cancel"), self, CaptureDialog.onCancel)
        self.cancelBtn:initialise()
        self.cancelBtn:instantiate()
        self:addChild(self.cancelBtn)
    end

    function CaptureDialog:onCancel()
        self:destroy()
    end

    function CaptureDialog:prerender()
        ISPanel.prerender(self)
        self:drawTextCentre(getText("UI_RelaxedAim_PressKey"), self.width / 2, 22, 1, 1, 1, 1, UIFont.Medium)
        self:drawTextCentre(getText("UI_RelaxedAim_PressKeyHint"), self.width / 2, 48, 0.7, 0.7, 0.7, 1, UIFont.Small)
    end

    function CaptureDialog:onKeyRelease(key)
        if self.destroyed then return end
        if key == 27 or key == nil then
            self:destroy()
            return
        end
        if key > 0 then
            local opt = self.option
            opt.key = key
            opt.element.keyCode = key
            if opt.element.btn then
                opt.element.btn.keyCode = key
                if opt.element.btn.setTitle then
                    opt.element.btn:setTitle(getKeyName(key))
                end
            end
            if MainOptions.instance and MainOptions.instance.gameOptions then
                MainOptions.instance.gameOptions.changed = true
            end
            self:destroy()
        end
    end

    function CaptureDialog:destroy()
        if self.destroyed then return end
        self.destroyed = true
        self:setVisible(false)
        self:removeFromUIManager()
        if GameKeyboard and GameKeyboard.setDoLuaKeyPressed then
            GameKeyboard.setDoLuaKeyPressed(true)
        end
    end

    local originalOnKeyBindingBtnPress = MainOptions.onKeyBindingBtnPress

    function MainOptions:onKeyBindingBtnPress(button, x, y)
        local elem = config.toggleKey.element
        local isOurs = elem ~= nil and (elem == button or (elem.btn ~= nil and elem.btn == button))
        if isOurs then
            local dlg = CaptureDialog:new(config.toggleKey)
            dlg:initialise()
            dlg:instantiate()
            dlg:setCapture(true)
            dlg:setAlwaysOnTop(true)
            dlg:addToUIManager()
            if GameKeyboard and GameKeyboard.setDoLuaKeyPressed then
                GameKeyboard.setDoLuaKeyPressed(false)
            end
            return
        end
        return originalOnKeyBindingBtnPress(self, button, x, y)
    end

    MainOptions._relaxedAimKeybindWrapped = originalOnKeyBindingBtnPress
end

installKeybindCapture()
if Events and Events.OnMainMenuEnter then
    Events.OnMainMenuEnter.Add(installKeybindCapture)
end
if Events and Events.OnGameStart then
    Events.OnGameStart.Add(installKeybindCapture)
end
