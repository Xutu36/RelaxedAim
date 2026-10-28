# RelaxedAim 项目交接文档（含 Viewpoint 3D 适配进展）

> ⚠️ **2026-10-28 修订（v5.4）**：第 6 节「身体部位枚举」与第 10 节「HIT 行判据」**已过时/有误**。
> VP 与游戏弹道用的是 `RagdollBodyPart`（HEAD=**2**，COUNT=11），不是 `BodyPartType`（Head=8）；
> `HIT 11` 是"无部位"哨兵。3D 弹道的正确注入点与完整逆向证据见 **`VIEWPOINT_NEXT_STEPS.md`**，
> 变更记录见本文第 12 节。

> 目的：迁移工作栈时快速恢复上下文。最后更新：Lock-v5.7（3D 确定性弹道、隐藏 Gunplay 漂浮点）。
> 说明：中文为主，路径均为本机实测。

---

## 1. 版本与状态

- 当前 `RelaxedAimConfig.VERSION` = **`Lock-v5.3`**（VERSION_NOTES 见该文件）。
- Git 仓库：`https://github.com/Xutu36/RelaxedAim.git`，分支 `master`。
- 最后提交：`8ceb37a`（"Viewpoint 3D 适配检查点(路线1: 注入CrosshairAim.yaw)"，约对应 v4.5）。
- **v4.6 → v5.3 的改动尚未提交**（工作区有 M/新增：`Patch_Core`、`RelaxedAimConfig`、`ViewpointAssist`、`ViewpointCompat`，新增 `Patch_VPReticleOffset`、`Patch_ForceHeadPart`）。迁移前建议先 `git add -A && git commit`。
- 功能状态：
  - **俯视角（原版 iso）：完美工作**（锁定、吸附、爆头、捕捉圈、HUD 全部正常）。
  - **Viewpoint 3D：部分**。准心可"可见地"吸附到丧尸头部（`Controls.reticle` 像素补丁）；但**真实弹道/漂浮点无法跟随**（详见第 6 节），爆头不可靠。

---

## 2. 关键路径（本机）

### RelaxedAim 项目
- 项目根：`D:\PZMod\RelaxedAim`
- Java 源码：`D:\PZMod\RelaxedAim\java\src\com\relaxedaim\*.java`
- 构建：`D:\PZMod\RelaxedAim\java\build.gradle`、`settings.gradle`、`gradle.properties`（后者含 JDK 路径与签名私钥路径，**勿提交**，已被 .gitignore 排除）
- Lua：`D:\PZMod\RelaxedAim\42\media\lua\client\RelaxedAim_Options.lua`、`RelaxedAim_Hotkey.lua`
- 沙盒选项：`D:\PZMod\RelaxedAim\42\media\sandbox-options.txt`（`RelaxedAim.AssistStrength`）
- 翻译：`D:\PZMod\RelaxedAim\common\media\lua\shared\Translate\{EN,CN,CH}\{UI,Sandbox}.json`
- mod.info：`D:\PZMod\RelaxedAim\common\mod.info`（id=RelaxedAim，require=\ZombieBuddy）
- 规则：`D:\PZMod\RelaxedAim\RULES.md`
- README：`D:\PZMod\RelaxedAim\README.md`；`LICENSE`
- 签名：`D:\PZMod\RelaxedAim\signing\`（`ed25519-private.der`、`VerifyZBS.java`）
- 构建产物：`D:\PZMod\RelaxedAim\java\build\libs\RelaxedAim.jar`（+ `.zbs`）；安装到 `42\media\java\client\RelaxedAim.jar`

### 工坊介绍 / 发布文案
- `D:\PZMod\des_ZH.txt`、`D:\PZMod\des_EN.txt` —— RelaxedAim 的 Steam 创意工坊描述（BBCode，中/英）
- `D:\PZMod\创意工坊简介.md` —— RelaxedAim 工坊简介草稿（中英）
- （`D:\PZMod\des_ZH_LookBack.txt`、`des_EN_LookBack.txt` 属于另一个 Mod LookBack，勿混用）

### 发布/部署目录
- 本地 mods 目录（实际游玩）：`C:\Users\Administrator\Zomboid\mods\RelaxedAim`
- 工坊打包目录（**当前不存在，需重建**）：`C:\Users\Administrator\Zomboid\Workshop\RelaxedAim\`
  - 结构应为 `Contents\mods\RelaxedAim\{42,common}` + `preview.png` + `workshop.txt`
  - 历史 `workshop.txt`：`version=1`、`id=3784016562`、`title=RelaxedAim`、`tags=Build 42;Balance;Military;Misc;Multiplayer`、`visibility=public`
  - 现存 `C:\Users\Administrator\Zomboid\Workshop\ModTemplate\workshop.txt` 是模板（非本项目）

### ZombieBuddy（ZB）
- 源码克隆（新版，仅参考）：`D:\PZMod\ZombieBuddy-master\ZombieBuddy`
- 已安装（运行时实际加载）：`F:\SteamLibrary\steamapps\workshop\content\108600\3619862853\mods\ZombieBuddy\libs\ZombieBuddy.jar`（**v2.3.4**）
- 注意：已安装 jar 与克隆源码**版本不完全一致**（例如运行时 `Loader` 无 `g_mod_loaders`，只有 `g_jarLoadStatus`；无 `getInstrumentation()` 方法，但有包私有字段 `g_instrumentation`）。以**已安装 jar 的 javap 为准**。

### Viewpoint（VP）
- 本机：`F:\SteamLibrary\steamapps\workshop\content\108600\3809306528\mods\Viewpoint`
- 版本：**0.1.5a-hotfix**（mod.info），Java 类在 `42\media\java\client\Viewpoint.jar`
- 有 i18n 子 mod：`...\3822634333\mods\ViewpointI18n`

### 游戏 / JDK
- 游戏：`F:\SteamLibrary\steamapps\common\ProjectZomboid`（`projectzomboid.jar`），Build **42.21**
  - ⚠️ 游戏已从旧路径 `D:\steam\...` 迁到 `F:\SteamLibrary\...`；`build.gradle` 已改为 F:。
- JDK：`E:\JDK25`（`javap.exe`、`java.exe`、`jar.exe` 均在 `E:\JDK25\bin`）

---

## 3. 项目规则（RULES.md 摘要）

1. **开发期所有字段/方法用 `public`**：ZB 把 `@Patch` advice 内联进游戏类；访问 `private` 成员会 `IllegalAccessError` 秒退。
2. **每次改代码必须升 `VERSION` + 写 `VERSION_NOTES`**（`com.relaxedaim.RelaxedAimConfig`），重新构建并**完全重启游戏**。
3. Java 补丁启动时加载；**改 JAR 必须重启游戏**。
4. 不要改动参考项目（ZombieBuddy / Viewpoint）。
5. 含中文的文件用 Write/edit 工具写（勿用 PowerShell `Get-Content -Raw | Set-Content`，PS5.1 会按 ANSI 读导致中文乱码）。
6. ZB 类加载：mod 以"classpath 追加 / 独立加载器"方式加载；跨 mod 反射需用 `Instrumentation.getAllLoadedClasses()`（见第 5 节）。跨加载器传值用 **JVM System 属性**（避免 patch 类被复制导致静态不一致）。

---

## 4. 构建 / 部署 / 签名 / 验证

```powershell
# 构建 + 安装到 42 + 部署到 mods/RelaxedAim（并重新签名）
cd D:\PZMod\RelaxedAim\java
.\gradlew.bat clean build deployLocalMod --rerun-tasks
# 若首次运行被工具 kill，直接重试一次即可
```

- 部署任务 `deployLocalMod`（Sync）目标：`C:/Users/Administrator/Zomboid/mods/RelaxedAim`，含 common、42，以及 `build/libs/RelaxedAim.jar.zbs`。
- 签名：`zb-gradle-plugin` 的 `signJarZBS`，每次构建重新生成 `RelaxedAim.jar.zbs`（Ed25519）。
- 验证签名（公钥 `a140d928eed497c39427d85fb849a202e883146cca5dbfd3c8949796e1f7146a`）：
  ```powershell
  & "E:\JDK25\bin\java.exe" -cp "D:\PZMod\RelaxedAim\signing" VerifyZBS `
    "C:\Users\Administrator\Zomboid\mods\RelaxedAim\42\media\java\client\RelaxedAim.jar" `
    "C:\Users\Administrator\Zomboid\mods\RelaxedAim\42\media\java\client\RelaxedAim.jar.zbs" `
    "a140d928eed497c39427d85fb849a202e883146cca5dbfd3c8949796e1f7146a"
  # 期望 VERIFIED : true
  ```
- 同步到工坊（目录需先重建）：
  ```powershell
  $t="C:\Users\Administrator\Zomboid\Workshop\RelaxedAim\Contents\mods\RelaxedAim"
  robocopy "D:\PZMod\RelaxedAim\common" "$t\common" /MIR
  robocopy "D:\PZMod\RelaxedAim\42" "$t\42" /MIR
  Copy-Item "D:\PZMod\RelaxedAim\java\build\libs\RelaxedAim.jar.zbs" "$t\42\media\java\client\RelaxedAim.jar.zbs" -Force
  ```
- 反编译/查询：
  ```powershell
  & "E:\JDK25\bin\javap.exe" -classpath <jar> -p [-c] <class>
  # 扫描 jar 解压目录引用：rg -a -l "<符号>" <解压目录>
  ```

---

## 5. Viewpoint 状态读取（已完成，关键经验）

- 反射直连失败原因：ZB 给 mod 独立 ClassLoader，`Class.forName("viewpoint.*", 本类加载器)` 抛 `ClassNotFoundException`；且 ZB 内部字段名随版本变化。
- **可行方法**：ZB 是 Java agent → 反射取 `me.zed_0xff.zombie_buddy.Loader` 的包私有字段 `g_instrumentation`（`getDeclaredField("g_instrumentation"); setAccessible(true)`），得到 `java.lang.instrument.Instrumentation`（**用 public 接口 `Instrumentation.getAllLoadedClasses()` 调用**；从 `sun.instrument.InstrumentationImpl` 反射取方法会 `IllegalAccessException`）。
- 用 `getAllLoadedClasses()` 找到 `viewpoint.core.View` → 取 `view.getClassLoader()` → `Class.forName(兄弟类, false, 该 loader)` 加载 `viewpoint.input.Look` 等。
- 字段绑定要**全部按可选处理**（缺字段不致命），因为 VP 版本会删/改字段（例：`viewpoint.input.ThirdPerson` 已删除，改用 `viewpoint.core.View.thirdPerson`；新增 `viewpoint.input.AimAssist`、`View.crosshairX/Y` 等）。
- 实现文件：`ViewpointCompat.java`（`viewpoint*.available/active/thirdPerson/captured/freeCam/lookYawDeg/lookPitchDeg/crosshair*/camera*/frameFovY`；每帧 `refresh()`，懒解析 2s 重试；`forceGunplayOff()` 现未自动调用）。

---

## 6. Viewpoint 内部结构（逆向所得，0.1.5a-hotfix）

**状态类**
- `viewpoint.core.View`（public）：`enabled, thirdPerson, shoulder, crosshairX/Y, ringX/ringY, swayRight/Up, crosshairOnTarget, crosshairShown, ON_TARGET[float[]], cameraX/Y/Up, frameFovY`；`fovY(boolean)`, `halfDiagonal`, `crosshairSnapshot(boolean)`。
- `viewpoint.input.Look`（public）：`yaw, pitch, captured`（public static volatile）。**相机朝向**。
- `viewpoint.input.ThirdPerson`：**已不存在**（用 `View.thirdPerson`）。

**瞄准/准星**
- `viewpoint.input.Controls.reticle(int pIndex,int def,boolean isX)`：第一人称返回 **屏幕中心 + `View.ringX/ringY` 偏移**（`round(size*(1+ring)/2)`）→ 这是**绘制的十字准心**位置。
- `Controls.aim(IsoPlayer)`：`setForwardDirection(cos(CrosshairAim.yaw),sin(CrosshairAim.yaw))`；FP(`owns`) 时 `setTargetVerticalAimAngle(0)`；`traceShot`。
- `CrosshairAim.yaw(IsoGameCharacter)`/`pitch(...)`（包私有）：`Look.yaw + turn` / `Look.pitch + lift`（turn/lift 由 raycast 得到）。
- `ArmsPitch.posed(AnimationPlayer)`（public）：用 `player.getForwardDirection` + `AnimationPlayer.getRenderedAngle()` 摆手臂/枪。
- `viewpoint.input.AimAssist`：**手柄专用**瞄准辅助（`wanted() = ON && PadLook.holds()`）；`Target` record `(yaw,pitch,width,yawRate,pitchRate,at,snapUntil)`；`publish(Target)`。

**射击/命中（gunplay 系统）**
- `viewpoint.gunplay.Gunplay`（public static）：设置 `ON/RANGE/RING/RING_OPACITY`（`LiveSettings`，`Controls/Gunplay`）；方法 `snapshot/reticle/shotStarting/gather/candidates/hitListMade/cameraTarget/barrelTarget/targetedPart/pelletCount`。
  - `gather(player, attackVars, hitInfos)` → `Gathering.add(player, weapon, muzzle, **Look.yaw, Look.pitch**, hitInfos)`：**射击追踪方向 = 相机 Look**。
- `viewpoint.gunplay.Sight`：位置 + 前/右/上基向量（`x,y,up,fx..`），`set(...)`/`offset(...)`。
- `viewpoint.gunplay.Assist / Aim / Candidates / Gathering / Focus / Sway / Shot / Odds / Reticle`：内部吸附/候选/命中计算。
- `viewpoint.CombatHooks`：**游戏↔Gunplay 的桥**：`aimedFromReticle/shotStarting/shotGather/shotCandidates/hitListMade/missedShot/cameraTarget/barrelTarget/targetedPart/pelletCount/...`（ZB patch 内联点到 `BallisticsController`/`CombatManager`/`EffectsManager` 等）。
- 游戏侧：`BallisticsController.update()` 用 `AimingReticle.getX/getY`（= `getXA*zoom`；`getXA` 被 VP patch 为 `Controls.reticle`）→ `IsoUtils.XToIso` → `Bullet.updateBallistics...`。但 VP 的 gunplay 会覆写 `BallisticsController.cameraTarget/barrelTarget/targetedPart`。
- **Gunplay OFF**：恢复原版 iso 弹道，但**在 3D 下系统性偏移**（手动瞄头也难爆头）。**Gunplay ON**：弹道打向"漂浮点"（Gunplay 修正后的 3D 瞄准点），手动瞄头可爆头。
- 漂浮点：即 Gunplay 的逻辑瞄准点；由 `SceneDrawer.crosshair(...)` 写到 `View.ringX/ringY`（`onScreen` 投影）；准心由 `Controls.reticle` 读 `ringX/ringY` 绘制。
- `Viewpoint` patched 目标（部分）：`AimingReticle.getXA/getYA`、`IsoPlayer.setAngleFromAim`、`IsoGameCharacter.getLookAngleRadians`、`BallisticsController.*`（经 CombatHooks）等。
- `viewpoint.platform.LiveSettings$Toggle`：`get()` / **`set(boolean)`**（可强制开关，如 Gunplay）。

**身体部位枚举** `zombie.characters.BodyDamage.BodyPartType`（ordinal）：0 Hand_L,1 Hand_R,2 ForeArm_L,3 ForeArm_R,4 UpperArm_L,5 UpperArm_R,6 Torso_Upper,7 Torso_Lower,**8 Head**,9 Neck,10 Groin,11 UpperLeg_L,12 UpperLeg_R,13 LowerLeg_L,14 LowerLeg_R,15 Foot_L,16 Foot_R,17 MAX。

**像素↔角度换算**（VP 自身用）：`pixels = angle(rad) * (screenHeight/2) / tan(frameFovY/2)`；实测约 **5.8 px/度**（19°→~110px）。

---

## 7. 可行实现路径（供迁移后决策）

- **A. Hook gunplay 瞄准（推荐但难）**：在 Gunplay 的 `Sight`/`Aim`/`Assist` 层面把"锁定头部"作为目标，让漂浮点+弹道+准心一致吸头。需深逆向、易受 VP 更新影响。
- **B. 复用 VP 手柄 `AimAssist`**：`AimAssist.publish(Target)` + 让 `PadLook.holds()` 对鼠标返回 true。可能拖相机（手柄辅助会转视角），需评估。
- **C. RelaxedAim 自实现 3D 弹道修正**：等于重写 Gunplay 的 `Sight`/投影/骨骼求交，工作量巨大、维护风险高（VP 仍在更新）。**不建议**。
- **D. 只保留"准心可见吸附"（现状）**：3D 下准心吸附可用，爆头交玩家；iso 完美。最稳。
- **E. 与 Viewpoint 作者沟通**，请求外部目标注入 API（最稳、长期）。

---

## 8. 已尝试路径与失败原因（按时间）

1. **反射读取 VP 状态**：初期 `ClassNotFound`（加载器隔离）+ 字段改名。→ 已用 `Instrumentation.getAllLoadedClasses()` + `View.classloader` + 可选字段绑定解决（成功）。
2. **注入 `IsoGameCharacter.setForwardDirection`（旋转身体前向）**：只转动身体/facing，不动武器瞄准；且该法每帧被多次调用、反馈叠加 → **手臂/枪抖动**。失败。
3. **注入 `viewpoint.input.CrosshairAim.yaw`（手臂瞄准角）**：手臂/facing 偏移，`aimLook` 也偏，但**子弹不变**（弹道不走它）。半成功（视觉手臂）。
4. **偏移 `AimingReticle.getX/getY`（像素）**：无效果（Gunplay ON 时子弹不走它）。
5. **偏移 `viewpoint.input.Controls.reticle`（像素）**：**准心（绘制）可见吸附到头部**（你认可手感），但**漂浮点/子弹不变**。半成功（保留）。
6. **偏移 `View.ringX/ringY`（patch 私有 `SceneDrawer.crosshair` OnExit，反射写）**：无效果（私有方法补丁疑似未注入，或 ring 只影响绘制）。
7. **强制关闭 Gunplay（`Gunplay.ON.set(false)`）**：能关；此时准星偏移能带动原版弹道，**但原版 iso 弹道在 3D 系统性偏移 → 无法稳定爆头**（手动也不行）。方向错误，已撤回（`Lock-v5.1`）。
8. **路线A：patch `Gunplay.gather` 期间临时偏移 `Look.yaw/pitch`**：无效果（命中判定应在 `BallisticsController.cameraTarget/barrelTarget/targetedPart` 之后，或补丁未注入/被恢复覆盖）。
9. **强制命中部位=Head（patch `viewpoint.CombatHooks.targetedPart` → 返回 8）**：无效果（`HIT` 仍 11，不爆头）→ 伤害判定不读 `getTargetedBodyPart`。

结论：**VP 的瞄准/命中高度耦合，简单注入点均无效**；要真正实现需走 A/B/E，或接受 D。

---

## 9. 当前 Java 文件职责（`java/src/com/relaxedaim/`）

- `RelaxedAimConfig.java`：配置（版本、本地 ModOptions 解析、沙盒、常量）。
- `AimAssistService.java`：每帧入口 `tickFrame()`（刷新配置/VP 状态/驱动 `ViewpointAssist`）、iso 候选/锁定主逻辑、`effectiveMaxLockRange`。
- `TargetLockService.java`：iso 锁定状态机、平滑、命中/保持；3D 下 `isLockAimingActive` 返回 false（禁用 iso 准星覆写）。
- `Patch_IngameState.java`：`IngameState.renderframeui` 钩子；3D 时跳过 iso 更新。
- `Patch_Core.java`：`Core.EndFrameUI` HUD + 捕捉圈/锁定圈绘制（3D 走 `drawViewpointLockUI` 屏幕空间）。
- `Patch_AimingReticleX/Y.java`：iso 准星覆写（`getXA/getYA`）。
- `Patch_AimingModeLerp.java`、`Patch_MainScreenState.java`：其它。
- `Patch_CrosshairAimYaw.java`：给 `viewpoint.input.CrosshairAim.yaw` 加偏移（手臂/枪视觉）。
- `Patch_VPReticleOffset.java`：给 `viewpoint.input.Controls.reticle` 加像素偏移（**准心可见吸附，唯一 3D 有效项**）。
- `Patch_ForceHeadPart.java`：锁定强制命中部位=Head（**实测无效**，可删/留待查）。
- `ViewpointCompat.java`：VP 状态反射（Instrumentation + 可选字段）。
- `ViewpointAssist.java`：3D 水平+垂直吸附计算（近距优先 + 保持时间 + 平滑 → 写系统属性 `aimXpx/aimYpx/aimYawDeg/aimPitchDeg/locked`）。

**跨加载器传值（系统属性名）**：`relaxedaim.aimXpx`、`relaxedaim.aimYpx`、`relaxedaim.aimYawDeg`、`relaxedaim.aimPitchDeg`、`relaxedaim.locked`、`relaxedaim.frame`。

---

## 10. 注意事项 / 坑

- 游戏已从 `D:` 迁到 `F:\SteamLibrary\...`；`build.gradle` 的 `gameDir`、`zombieBuddyJar` 已指向 F:。
- `gradlew clean build deployLocalMod` 有时首次会被工具进程 kill → **重试一次**即可。
- `%TEMP%\opencode` 会被系统清理（`VerifyZBS` 已移至项目 `signing\` 下）。
- 含中文文本用 Write/edit 工具；PowerShell `-Raw|Set-Content` 会破坏 UTF-8。
- 修改 Java 后必须**完全重启游戏**。
- `Viewpoint` 更新频繁（当前 Gunplay Rework 0.1.6 内嵌），深钩子易失效。
- `HIT` 行读取 `getTargetedBodyPart(0)/getCachedTargetedBodyPart(0)`，Gunplay ON 时被 VP 覆写为固定值（如 11），**不可作为可靠判据**；需要真实命中部位应从 `HitInfo`/命中事件读。
- 捕捉圈半径目前写死 48px（3D 屏幕空间），与真实 7° 捕捉角换算未对齐，可按 `tan(7°)*ppd` 动态化。

---

## 11. 建议的下一步

1. 先提交当前工作区（`Lock-v5.3`）以便回退。
2. 若继续 3D 爆头：走 **A**（深挖 `viewpoint.gunplay.Sight/Aim/Assist` 的目标注入）或 **E**（联系 VP 作者获取注入接口）；**不要**再试已失败的简单点（6/7/8/9）。
3. 若求稳发布：走 **D**（3D 仅保留准心可见吸附；iso 完美），并把 3D 相关实验项（spike 滑块、ForceHeadPart 等）清理隐藏。
4. 恢复发布前：重建 `Workshop\RelaxedAim` 打包目录、更新 `des_*.txt`/`创意工坊简介.md`、重新签名验证。

---

## 12. v5.4 变更记录（2026-10-28）

### 12.1 新增/修改

| 文件 | 作用 |
|---|---|
| `Patch_VPShotRay.java`（新） | patch `viewpoint.gunplay.Sight.turned`：OnEnter 给 `right/up` 两个参数叠加 `relaxedaim.aimTanR/aimTanU` → **子弹射线跟随锁定目标**。全 Viewpoint 唯一调用点是 `Shot.draw`（霰弹枪方向也由 `way` 派生），只影响射击结算 |
| `Patch_VPShotPart.java`（新） | patch `viewpoint.gunplay.Shot.part`：只读记录本次射击给每个目标分配的 `RagdollBodyPart` 序号 → `relaxedaim.lastPart`（诊断探针，不改行为） |
| `ViewpointAssist.java` | 吸附量改为输出 **tan**（`relaxedaim.aimTanR/aimTanU`）；准心像素 = tan × ppd，与射线同源；新增 `relaxedaim.rayOn` |
| `ViewpointCompat.java` | 新增 `gunplayOn/gunplayKnown/gunplayDebug` + `refreshGunplay()`（反射 `Gunplay.ON` 的 `Toggle.get()`，2s 重试） |
| `AimAssistService.java` | 3D 辅助门控：`gunplayOn == false` 时整条 3D 辅助停用（避免"准心动、弹道不动"） |
| `Patch_Core.java` | HUD 新增 `RAY tan=(..)` 与 `HIT ... shot=<部位名>` 探针行；新增 `ragdollPartName()` |
| `Patch_ForceHeadPart.java` | **已删除**（保底方案搁置）。恢复代码见 `VIEWPOINT_NEXT_STEPS.md` §5.3，注意 `HEAD=2`（`RagdollBodyPart`） |

### 12.2 新增系统属性（跨加载器传值）

`relaxedaim.aimTanR`、`relaxedaim.aimTanU`、`relaxedaim.rayOn`、
`relaxedaim.probeRay`、`relaxedaim.lastRay`、`relaxedaim.lastPart`、
`relaxedaim.lastPartId`、`relaxedaim.lastPartOriginal`、`relaxedaim.probePart`

### 12.3 下次进游戏的验证清单

1. HUD `VP avail=.. gp=ON`：`gp` 必须是 ON。
2. 锁定后开一枪：`RAY tan` 非 0 且 `n=` 递增 → 射线补丁在跑；
   `HIT ... shot=2 BODYPART_HEAD` → 几何上真的命中头部（伤害 ×3.0）。
3. `console.txt` 应出现 `patching viewpoint.gunplay.Sight.turned` 与 `patching viewpoint.gunplay.Shot.part`；
   若出现 `Method turned not found in viewpoint.gunplay.Sight` → VP 改版，需重新逆向。
4. `shot=11` 表示这一枪没命中任何部位（不是"打中大腿"，更不是"补丁无效"）。
5. 构建/部署/签名已验证：`BUILD SUCCESSFUL` + `VERIFIED : true`（jar SHA-256 `ac80c90c...`）。

### 12.4 v5.5：3D 瞄准几何重做（实测反馈后的修正）

实测反馈：能爆头，但①准心/锁定圈总在头上方；②第三人称偏左上/右上；③倒地丧尸偏正上方；④车内偏正上方；⑤3D 没有 iso 那样的锁定圈吸附动画与锁定信息 HUD。

**根因（三项）**

1. **俯视角的「颈部→头部」补偿被 3D 复用**：`headAimOffset=0.15` 是给 iso 投影压缩做的补偿，3D 相机是真实透视，叠加后瞄准点被抬到头顶上方。
   → `TargetLockService.headAimWorldPos(z, out, applyIsoOffset)` 新增布尔重载，3D 传 `false`，iso 保持 `true`。
2. **垂直角度用了世界 z，没有换算到 Viewpoint 的相机空间**：VP 的弹道/相机空间里 `up = 世界 z × 2.4494896`
   （见 `viewpoint.gunplay.Bodies.put`、`CameraStep`、`CrosshairAim`）。旧代码用 `atan2(dz_world, dist)`，
   垂直偏差小 2.4 倍；倒地丧尸需要 ~25° 俯角却被 `MAX_ASSIST_PITCH_DEG=10` 钳死，于是锁定圈停在头上方。
   → 全部改在相机空间算，垂直上限放宽到 55°。
3. **角度起点用了玩家胸口，不是相机**：第三人称/肩射/车内的相机与胸口有横向+纵向偏移，所以偏左上/右上。
   → 新增 `ViewpointCompat.readCameraPlace()`，反射调用 `viewpoint.input.CrosshairAim.camera(double[])`
   取 **Shot.draw 使用的同一个相机点**；取不到时退回玩家瞄准原点。
4. 附带：准心像素改为走 VP 自己的 Panini 映射（`viewpoint.render.Panini.ofView(...).toScreen`，
   strength 反射自 `Panini.strength()`），否则偏移量会比真实投影小约 10~15%。

**新增能力**

- **吸附本身改成 iso 的瞄准时间算法**（不再是指示圈单独动画、准心瞬移）：
  `aimTan` 按 `computeSnapTimeMs` + `progress²` 从捕捉时的位置滑向头部，`progress=1` 后完全跟随；
  失锁后 `aimTan` 以 ~12/s 归零，锁定圈（画在吸附后的准心位置）随之回落到捕捉圈中心。
- HUD 新增 `Lock3D: ID=.. dist=..m lock=(..) snap=..ms ..%`、`LastLost3D: <原因>`、`CAM vp=.. cam=(..) aim=(..)`
  —— 3D 下也能像 iso 一样看锁定对象、吸附进度与失锁原因。

**新增系统属性/调试**：`relaxedaim.aimTanR/aimTanU`（相机空间切值，射线与像素同源）。

**下次验证重点**：第三人称/车内锁定是否还偏；倒地丧尸是否落到头上；`CAM vp=true` 表示相机点解析成功。
若 `CAM vp=false`，说明 `CrosshairAim.camera` 反射失败（VP 改版），此时退回胸口起点、第三人称仍会偏。

### 12.5 v5.6：瞄准点回到头、捕捉圈与配置对齐

实测 v5.5：`CAM vp=true`，第一/第三人称、倒地、车内都能稳定爆头，但准心落在**脖子根部**。

1. **瞄准点改成 Viewpoint 头胶囊中心**：`Spine1 + 1.46 × (Head − Spine1)`
   （`Parts.SPEC` 的头胶囊是 `[1.34, 1.58]`，中点 1.46，即 VP 判定爆头用的那个体积中心）。
   直接删掉补偿会让准心落到头骨骼（视觉上的脖子根），而旧的 `headAimOffset = 0.15`（世界单位）
   在修正几何后大约会超出头顶 3 倍——所以不是"恢复旧值"，而是"按 VP 的胶囊重算"。
   常量在 `TargetLockService.VP_HEAD_CAPSULE_T`（1.34=胶囊下缘，1.58=上缘，可微调）。
2. **3D 捕捉改成屏幕像素判定**：目标头部投影到屏幕后，与屏幕中心的像素距离 ≤ 半径才算进入捕捉；
   半径 = 模组设置「锁定捕获半径」× 40 px/瓦片（默认 1.5 → 60px，约等于原来 7° 捕捉锥的屏幕半径）。
   捕捉圈按同一半径绘制，所以**画多大就能捕多大**；释放仍用 ×`reFilterMultiplier`(1.5) 滞回 + 保持时间。
3. **删除【测试】武器瞄准偏转**：Lua 滑块、三语言翻译、Java 字段与解析、HUD 里的 `spike=` 全部移除。
   `Patch_CrosshairAimYaw`（手臂/身体朝向跟随）保留，现在直接取真实吸附角 `relaxedaim.aimYawDeg`。

### 12.6 v5.7：3D 确定性弹道 + 隐藏 Gunplay 漂浮点

实测 v5.6：近距离体验符合预期，但**远距离明明锁得很准也打不中**。

**根因**：Viewpoint 没有自己的命中率，它复用游戏的 `CombatManager.calculateHitChanceData().hitChance`，并把它
**换算成瞄准环半径**，再让 `Sway` 在该半径内做随机游走，最后 `Shot.draw` 用 sway 偏转子弹方向：
`Odds.ask` → `Aim.cone` → `Reference.radius` → `Focus.tighten` → `Sway.start/advance` → `Shot.draw`。
技能低/距离远 → 半径大 → 子弹在准心附近乱飞 → 看得见锁上了却打空。屏幕中心那个漂移的小点就是这条 sway。

**修法（两个补丁）**

1. `Patch_VPHitChance`：patch `viewpoint.gunplay.Odds.ask`（命中率唯一出口），OnExit 把**除"恰好 0"以外**的值拉满为 `1.0f`。
   - 命中率恒 100% → `Candidates.seated`/`Shot.shootable` 不再按概率剔除远距离目标；
   - `Reference.radius(..., 1.0) = 0` → `aim.radius = 0` → `Focus.tighten` 返回 0 → `Sway` 半径 0
     → `right/up` 恒 0 → 子弹严格沿「相机 + RelaxedAim 的 tan 偏移」飞，不再随机散布；瞄准环也随之收起。
   - **只放过恰好 0**：游戏用 0 表示"这个方向打不了"（车窗外角度外、驾车用双手武器），
     VP 的 `Candidates.seated` 靠它剔除，否则会变成穿车射击。
     负值（合法但命中率被扣成负数）必须一起拉满——`Reference.radiusAt(负值)` 会返回最大值 8.0，散布反而爆炸。
2. `Patch_VPHideDot`：patch `viewpoint.core.View.crosshairSnapshot`，OnEnter 把入参改成 `false`
   → `crosshairShown = false` → `SceneDrawer.drawFrame` 不再调用 `drawCrosshair()`，漂浮点消失。
   （选它而不是私有 `drawCrosshair`：public static + 单 boolean 参数，签名最稳。）

**诊断**：HUD 的 `det=11` = 两个补丁都在跑（第 1 位命中率、第 2 位隐藏点）；`det=0..` 说明补丁没注入，
去 `console.txt` 查 `Method ... not found`。

**副作用**：瞄准环收成最小（`targetChance=100` 的正常表现）；霰弹枪弹丸散射（`Gunplay.pattern`）不受影响；
iso 完全不变。
