# Viewpoint 3D 辅助瞄准：逆向结论与下一步（v5.4 计划）

> 依据：`VIEWPOINT_HANDOFF.md`（v5.3 交接） + 现网代码 + `console.txt` + 反编译
> `Viewpoint.jar`（0.1.5a-hotfix）与 `projectzomboid.jar`（B42.21）。
> 反编译产物在 `D:\PZMod\.vpx\src`、`D:\PZMod\.vpx\game`（工作区外，可随时删）。

> **实施状态（Lock-v5.4）**：§5.1 方案 A、§5.2、§5.4、§6 的探针已实现并部署（`BUILD SUCCESSFUL`，
> `VERIFIED : true`）；§5.3 保底按需求**搁置**，`Patch_ForceHeadPart.java` 已删除。
> 待进游戏实测：见 `VIEWPOINT_HANDOFF.md` §12.3 验证清单。

---

## 0. 一句话结论

之前的失败**不是**"VP 耦合太深、注入不进去"，而是**三个关键事实被误判**：

1. 部位枚举不是 `BodyPartType`，而是 `RagdollBodyPart`（HEAD = **2**，不是 8）；
   **8 = 左小臂**，会让伤害吃到 **0.05×** 的"四肢倍率"。
2. 3D 子弹方向**只由 `Look.yaw/pitch + sway` 决定**，不经过 `CrosshairAim.yaw`、
   `AimingReticle`、`Controls.reticle` → 所以"准心吸附了、子弹不动"是必然结果。
3. `HIT 11/11` 里的 11 是 `RagdollBodyPart.BODYPART_COUNT`（"无部位"哨兵），
   不是 `BodyPartType.UpperLeg_L` —— 用它判断"强制部位没生效"是误读。

**可行落点只有一个且很浅**：把辅助偏移加进**射击射线的 sway**（`Gunplay.swayRight/swayUp`
或 `Sight.turned`），射线就会真的指向头部，命中部位由几何自动得出（= 头）。
保底再加一条：把 `Patch_ForceHeadPart` 的常量改成 2。

---

## 1. 现状核对（实测）

| 项 | 结果 |
|---|---|
| git | 最后提交 `8ceb37a`；工作区仍有 M：`Patch_Core`、`RelaxedAimConfig`、`ViewpointAssist`、`ViewpointCompat`，未跟踪：`Patch_ForceHeadPart`、`Patch_VPReticleOffset`、`VIEWPOINT_HANDOFF.md` |
| 运行版本 | `console.txt:4667` → `RelaxedAim Lock-v5.3 loaded` |
| 补丁注入 | `console.txt:732` `patching viewpoint.CombatHooks.targetedPart`、`:4339` `patching viewpoint.input.Controls.reticle`、`:9153` `patching viewpoint.input.CrosshairAim.yaw` —— **全部注入成功**（"私有方法没注入"这条假设可排除，`ringX/ringY` 那条仍需单独验证） |
| VP 版本 | `Viewpoint.jar` = Gunplay Rework 0.1.6 内嵌，类数 1082 |

---

## 2. 3D 射击的真实链路（反编译证据）

```
shotStarting  → Gunplay.shotStarting → Shot.begin
shotGather    → Gunplay.gather       → Gathering.add      // 用 Look 做宽锥体全 cell 扫描
shotCandidates→ Gunplay.candidates   → Candidates.take    // 每候选 16 骨骼 → 13 段胶囊
hitListMade   → Gunplay.update       // 只算准心环半径/红点，不决定命中
真正结算       → Shot.resolve → Shot.draw
                  ├ line = Sight(相机点, Look.yaw, Look.pitch, 0, 0)
                  ├ drawn = Gunplay.swayRight() / swayUp()
                  ├ line.turned(drawn[0], drawn[1], way)      ← 子弹方向在这里
                  └ 从枪口沿 way 逐胶囊 Parts.meet → 命中对象 + 部位码
hitListMade   → Shot.chances         // chance 强制 100，回写 x/y/z
CombatManager.processHit             → isCameraTarget ? bc.getCachedTargetedBodyPart(id) : 11
                                     → processTargetedHit(part) → createCombatData(part)
                                     → applyRangeHitLocationDamage(part)
                                          isHead(2) → 伤害 ×3.0
                                          isArm/isLeg → 伤害 ×0.05
```

关键代码位置：

- `Shot.draw`：`D:\PZMod\.vpx\src\viewpoint\gunplay\Shot.java:166-208`（`way` 在 175 行）
- `Gunplay.swayRight/Up`：`Gunplay.java:250-256`（**唯一调用者**就是 `Shot.java:173-174`）
- `Sight.turned`：`Sight.java:48-56`（**唯一调用点** `Shot.java:175`）
- `Gathering.near`：`Gathering.java:148-159`，锥体判据 `perp ≤ 1.0*d + 1.5`
- 部位判定 `Parts.meet`：`Parts.java:76-86`，头胶囊 `SPEC[2]`，半径 `0.06*1.5 = 0.09`
- 枚举 `RagdollBodyPart`：`game\zombie\core\physics\RagdollBodyPart.java`（PELVIS0/SPINE1/**HEAD2**/…/COUNT11）
- 伤害倍率：`CombatManager.java:3319-3336`，`CombatConfigKey.HEAD_HIT_DAMAGE_SPLIT_MODIFIER = 3.0`，`LEG_HIT_... = 0.05`
- 强制部位补丁的宿主：`BallisticsController.getCachedTargetedBodyPart`（VP 自己 patch 它，再转 `CombatHooks.targetedPart`）

---

## 3. 三个被误判的事实（对应交接文档要改的地方）

### 3.1 部位枚举空间错了（交接文档第 145 节、实验 9）

`getCachedTargetedBodyPart` 返回的是 **`RagdollBodyPart` 序号**（VP 的 `Parts.PART_*` 与它一一对应）：

| 值 | 含义 | 伤害 |
|---|---|---|
| 2 | HEAD | ×3.0 |
| 1 | SPINE | ×1.0 |
| **8** | **LEFT_LOWER_ARM** | **×0.05** |
| 11 | COUNT = "无部位" | 走非部位结算 |

所以 `Patch_ForceHeadPart` 里的 `HEAD = 8` 是**错的**：一旦真的生效，是把爆头改成"打中左小臂"，
伤害直接砍到 5%。这比"没生效"更糟，而且**看起来像没生效**（因为不会有爆头反馈）。

`HIT` 行显示的 11 也说明：当时那一帧**没有任何部位命中**（没开枪或没结算），不能拿来当判据。

### 3.2 子弹方向不经过准心链（实验 3/4/5/8）

- `Shot.draw` 只用 `Look.yaw / Look.pitch + sway`；
- `CrosshairAim.yaw`、`Controls.reticle`、`AimingReticle.getX/getY` 只影响：
  身体朝向、手臂姿态、以及 Gunplay **关闭**时的原版 iso 弹道。
- 因此"准心可见吸附"（`Patch_VPReticleOffset`）和"子弹跟随"是两条完全独立的链路，
  前者成功不代表后者能有任何影响。

### 3.3 sway 是"切空间偏移"，可以直接叠加

`Sight.turned` 的计算是 `f + r*d + u*d2`（单位向量叠加），`d/d2` 无量纲（tan 值）；
`Sway.radius` 由 `cone()` 得出也是 tan 值。所以：

```
way = normalize(forward + right*tan(θyaw) + up*tan(θpitch))
```

**直接把 `tan(角度)` 加到 sway 上，射线就精确转到目标上**——不需要重写投影、骨骼求交、
也不需要碰 `BallisticsController`。

---

## 4. 目标一定在候选里（可行性保证）

`Gathering.near` 的判据极宽：`perp ≤ 1.0*along + 1.5`。
10 m 处允许偏离 ~±49°，1 m 处几乎全向（只受最大射程和 32 个候选上限约束）。
我们的捕捉锥只有 7°、辅助上限 12°，**远小于**候选锥 → 射线偏移不会因为"目标不在候选列表"而失效。

同时命中的部位由射线与胶囊的**真实几何**决定（头胶囊半径 0.09 m ≈ 10 m 处 0.5°），
所以只要角度算准、平滑收敛，`Parts.meet` 就会返回 2。

---

## 5. v5.4 实施方案（最小改动）

### 5.1 主动作：让射线跟随（新增补丁，二选一）

**方案 A（首选，1 个补丁类）**

```java
@Patch(className = "viewpoint.gunplay.Sight", methodName = "turned")
public final class Patch_VPShotRay {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(value = 0, readOnly = false) double right,
                             @Patch.Argument(value = 1, readOnly = false) double up) {
        // 读系统属性 relaxedaim.aimTanR / aimTanU，做角度→tan 叠加
        right += tanR;  up += tanU;
    }
}
```

只影响 `Shot.java:175` 这一次调用（已确认全仓唯一），霰弹枪 `Pellets` 的方向由 `way` 派生，同样受益。

**方案 B（语义更收敛，2 个补丁类）**

```java
@Patch(className = "viewpoint.gunplay.Gunplay", methodName = "swayRight")
... @Patch.OnExit public static void exit(@Patch.Return(readOnly = false) double ret) { ret += tanR; }
// swayUp 同理
```

两者都是包私有（`Sight` 甚至是包私有类）。ZB 用 ByteBuddy 按**方法名**匹配目标类改写后的
类型描述（其 publicizer 会把目标类成员提权），advice 体是内联进目标类的，所以包私有不是障碍；
真正的失败模式只有"方法名找不到"（VP 改版）。建议先做 A，A 若报
`Method ... not found in viewpoint.gunplay.Sight` 再切 B。

### 5.2 数值统一（消除两套换算）

`ViewpointAssist` 目前输出"度"，再由 `writePixels` 换成像素给 reticle。改为**只输出 tan**：

- `tanR = tan(θyaw)`，`tanU = tan(θpitch)`
- 射线：`right += tanR; up += tanU`
- 准心像素：`px = tanR * ppd`，`ppd = (h/2)/tan(fovY/2)`（可再乘 `Panini.strength()` 的 `centreScale()`）

这样"准心画在哪"和"子弹打向哪"用的是**同一个数**，不会再出现两边对不上的问题。
（现有 `radians*ppd` 在小角度与 tan 差 < 1%，但要彻底一致就一起换。）

### 5.3 保底：强制头部（**已搁置**，v5.4 未启用）

先看 5.1 的几何效果，再决定要不要这层"等效"保险。恢复时用下面这份（注意 `HEAD = 2`、
`RagdollBodyPart`、且只在 `ret != 11` 时覆盖，避免凭空造命中）：

```java
@Patch(className = "viewpoint.CombatHooks", methodName = "targetedPart")
public final class Patch_VPForceHead {
    public static final int HEAD = 2;      // RagdollBodyPart.BODYPART_HEAD（不是 BodyPartType.Head=8）
    public static final int NO_PART = 11;  // RagdollBodyPart.BODYPART_COUNT

    @Patch.OnExit
    public static void exit(@Patch.Return(readOnly = false) int ret) {
        try {
            if (ret == NO_PART) return;                                  // 这一枪没命中任何部位
            if (!"1".equals(System.getProperty("relaxedaim.locked"))) return;
            final zombie.characters.IsoPlayer p = zombie.characters.IsoPlayer.getInstance();
            if (p == null || !p.isAiming()) return;
            ret = HEAD;
        } catch (Throwable t) {
        }
    }
}
```

已知副作用：几何打中腿/躯干也按头部结算（×3.0 伤害 + 头部血雾），所以更适合当"打不准时的兜底"，
默认应做成可选开关。

### 5.4 门控：只在 Gunplay ON 时启用 3D 辅助

`sway` 只在 Gunplay 的结算路径里使用；Gunplay OFF 时只有 reticle 偏移生效，
而那条路在 3D 下是系统性偏移的（交接文档实验 7 已验证）。
所以需要新增只读的 `ViewpointCompat.gunplayOn()`（反射 `viewpoint.gunplay.Gunplay.ON`
→ `LiveSettings$Toggle.get()`，现有 `forceGunplayOff` 的代码可直接复用）。
Gunplay OFF 时：3D 辅助整体停用，HUD 提示"3D 辅助需要 Gunplay ON"。

---

## 6. 这次别再靠猜：验证手段

1. **补丁计数探针**（最重要）：每个 advice 里 `System.setProperty("relaxedaim.probe.<name>", String.valueOf(++n))`，
   HUD 直接显示 → 一眼看出补丁是不是每枪都在跑。之前 8 条路线全部缺少这个，导致
   "无效"到底是"没注入"还是"注入了没用"永远分不清。
2. **最近一枪记录**：`Shot.draw` 的 part 结果用系统属性带出来（`relaxedaim.lastPart`），
   开一枪 HUD 立刻显示 `2/1/11`。
3. **HUD 的 HIT 行**改为打印 `RagdollBodyPart.values()[i].name()`，别再显示裸数字。
4. `console.txt` 过滤 `[RelaxedAim][VP]` 与 `patching viewpoint`：
   出现 `Method ... not found in viewpoint.gunplay.X` 就是 VP 改版了。
5. **对拍用例**：平地、8 m、站立丧尸、瞄准躯干开一枪 → 期望 `lastPart == 2`、
   伤害出现头部血雾/×3。

---

## 7. 风险

- VP 每次更新（0.1.6 已经重写过 gunplay）都可能改 `Sight`/`Sway`/`Gunplay` 的字段与方法名。
  补丁失败只是"不生效"，不会崩（建议所有 advice 全部 try/catch 包裹）。
- 第三人称/肩射、车内、霰弹枪（`Pellets` 用 `way` 派生方向）、倒地丧尸需要分别验证。
- 强制部位（5.3）会让"几何打腿也判头"；若 5.1 生效，建议把它降级为可选/默认关。
- 头部命中在原版还有 `RagdollBodyPart.getBodyPartType(2)` 的 50% Head / 50% Neck 随机，
  这是**伤口部位**的随机，不影响 `isHead(2)` 的 ×3 伤害倍率。

---

## 8. 工程待办（与 3D 无关但该做）

- [ ] 先提交 `Lock-v5.3`（`git add -A && git commit`），保证可回退。
- [ ] 清理实验残留：`optionSpikeAimOffsetDeg`、`ViewpointCompat.spikeApplyCount`、
  右侧 `[DBG]` 面板、`forceGunplayOff`（改为只读 `gunplayOn()`）。
- [ ] `optionShowHud` 发布前设 false。
- [ ] 发布：重建 `C:\Users\Administrator\Zomboid\Workshop\RelaxedAim\`（common/42 + preview.png + workshop.txt）、
  更新 `des_ZH/EN.txt` 与 `创意工坊简介.md`、重新签名并 `VerifyZBS`。
- [ ] `README.md` 补一节"与 Viewpoint 的兼容说明/已知限制"。

---

## 9. 备选路线（若 5.1 被 VP 改版打断）

- **退回 D**：只保留准心可见吸附（现状），iso 完美、3D 交给玩家手动。
- **联系作者**：请求一个"外部目标注入"接口（`AimAssist.publish` 目前只服务手柄）。
  这是长期最稳的方案，优先级取决于 5.1 的稳定性。
- 不建议自研 3D 弹道（等于重写 `Sight`/`Parts`/`Obstacles`），维护成本远高于收益。

---

## 10. 实验对照表（旧结论 → 新解释）

| 旧实验 | 旧结论 | 新解释 |
|---|---|---|
| 3 `CrosshairAim.yaw` 偏移 | 手臂动、子弹不动 | 子弹不走这条链 |
| 4 `AimingReticle.getX/Y` 偏移 | 无效果 | Gunplay ON 时结算不走它 |
| 5 `Controls.reticle` 偏移 | 准心可见吸附 | 只影响绘制 + iso 弹道 |
| 6 `View.ringX/ringY` | 无效果 | 那是"环心投影"，不是结算输入 |
| 7 强制关 Gunplay | 3D 下系统性偏移 | 原版 iso 弹道不适用 3D |
| 8 偏移 `Look.yaw` 于 gather 期间 | 无效果 | gather 只收集候选，不决定方向 |
| 9 强制 `targetedPart=8` | 无效果 | **常量错了（8=左小臂 ×0.05）**，且需命中才生效 |
