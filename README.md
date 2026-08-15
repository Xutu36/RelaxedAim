# RelaxedAim

Project Zomboid Build 42 客户端辅助瞄准 Mod：在不改变原版枪械「不确定性」的前提下，
把弹道中心平滑吸附到锁定丧尸头部，降低瞄准微调的操作压力。

> 依赖：本 Mod 的 Java 补丁基于 **ZombieBuddy** 框架，玩家必须安装 ZombieBuddy 才能运行（见下文）。
> 开源协议：MIT

---

## 功能

- **自动锁定**：远程武器瞄准时，锁定原始鼠标瞄准点邻域内最近的合法丧尸（范围、最大距离可调）。
- **平滑吸附**：准心从鼠标位置按期望时间平滑移动到锁定丧尸**头部骨骼**（待机/奔跑/倒地姿态自适应，沿颈部→头部方向偏移）。
- **强锁定**：准心进入头部骨骼阈值内后完全锁定头部；退出/切换目标后对新目标重新平滑。
- **原版机制保留**：命中率、散布、缩圈、射程/遮挡/楼层限制完全走原版，仅移动弹道中心。
- **交互指示**：青色范围圈跟随鼠标（锁定后渐阔到释放半径）、紫色圈标记将被/正在锁定的头部。
- **临时热键**：默认键盘顶部数字行 `0`，随时启用/禁用辅助（可在模组设置中自定义，角色头顶显示提示）。
- **配置本地化**：配置项随系统语言显示（EN / 简体中文 CN / 繁体中文 CH）。

## 依赖：ZombieBuddy（必须）

本 Mod 的 Java 补丁通过 ZombieBuddy 的 Java agent 注入游戏。**每个玩家（客户端）都必须安装 ZombieBuddy**：

1. Steam 创意工坊订阅 [ZombieBuddy](https://steamcommunity.com/sharedfiles/filedetails/?id=3619862853)。
2. 运行 **ZombieBuddyInstaller.exe**（GitHub Releases 下载）并选择 Install，把 agent 打入游戏启动（Windows）。
   - 首次运行游戏时，ZombieBuddy 会弹出 Java Mod 审批框，选择允许 RelaxedAim.jar。

ZombieBuddy 是第三方框架（独立于本 Mod），本 Mod 不打包它的代码。

## 安装本 Mod

### 客户端（玩家）

- **发布后**：Steam 创意工坊订阅 RelaxedAim（客户端会自动下载），并在 Mod 管理器启用。
- **未发布 / 本地测试**：把 `RelaxedAim/`（含 `common/`、`42/`）复制到
  `%USERPROFILE%\Zomboid\mods\RelaxedAim\`，在游戏 Mod 管理器启用。

### 服务器（本地联机测试，未上工坊时）

1. 用面板/ FTP 把 `RelaxedAim/` 上传到服务器用户目录的 `Zomboid/mods/`。
2. 在服务器配置（`Server/<名>.ini` 或面板）把 `Mods=RelaxedAim` 加上（本地 mod 走 `Mods=`，`WorkshopItems=` 留空给工坊 ID）。
3. 重启服务器；客户端也须安装 ZombieBuddy + 本 Mod，mod 列表才能匹配。

## 配置

- **游戏设置 → 模组 → RelaxedAim**：启用开关、热键、锁定捕获半径、最大距离、锁定保持时间、UI 透明度、霰弹枪不锁定。
- 平滑吸附公式（已定稿硬编码）：
  ```
  effect   = clamp(assistStrength × snapStrengthScale, 0, 1)          // 1.0 × 1.0
  gain     = ln(1 + Aiming等级) / ln(1 + snapAimCap)                  // 对数衰减
  snapTime = clamp(snapMaxMs − (snapMaxMs − snapMinMs) × effect × gain, snapMinMs, snapMaxMs)
  ```
  平滑采用线性进度趋近，**实际吸附耗时 ≤ snapTime**（目标移动不影响）。

## 开发与构建

需要 JDK（本项目用 25）。从 `java/` 目录：

```powershell
.\gradlew.bat build             # 编译并安装 42/media/java/client/RelaxedAim.jar
.\gradlew.bat deployLocalMod    # 部署到 %USERPROFILE%\Zomboid\mods\RelaxedAim
```

`build.gradle` 中的游戏/ZombieBuddy/部署路径是本机硬编码，按需修改。

### 目录结构

```text
RelaxedAim/
├─ common/                    # 跨版本元数据与翻译
│  ├─ mod.info
│  └─ media/lua/shared/Translate/{EN,CN,CH}/UI.json
├─ 42/media/                  # B42 内容
│  ├─ lua/client/             # 模组设置、热键提示
│  └─ java/client/RelaxedAim.jar   # 构建产物
├─ java/                      # Java 补丁源码与 Gradle 构建
│  └─ src/com/relaxedaim/     # @Patch 补丁与核心逻辑
└─ signing/                   # ZombieBuddy 签名工具（可选）
```

## ZombieBuddy 签名（可选）

签名让玩家能在 ZombieBuddy 审批框中验证 JAR 出自你的作者密钥、且未被篡改。

1. 生成 Ed25519 密钥（Windows 无 openssl 可用本项目工具）：
   ```powershell
   cd signing; javac GenKey.java; java GenKey .\ed25519-private.der
   ```
2. 把私钥路径与你的 SteamID64 填到 `~/.gradle/gradle.properties`（或 `java/gradle.properties`）：
   ```properties
   zbsSteamID64=你的SteamID64
   zbsPrivateKeyFile=D:\...\ed25519-private.der
   ```
3. 构建：`.\gradlew.bat build` → 产物含 `RelaxedAim.jar.zbs`（与 JAR 一起分发）。
4. 在 Steam 个人资料简介发布 `JavaModZBS:<公钥>`（一次性），并把 SteamID64 与公钥提交到
   ZombieBuddy 的 `authors.json`（可选，保护隐私时用）。

> 私钥严禁提交到仓库。发布前每次重建 JAR 都要重新签名（build 会自动生成 .zbs）。

## 目录 / 发布清单

发布创意工坊前准备：

- [ ] `optionShowHud` 设为 false（隐藏调试 HUD）
- [ ] Mod 图标（`icon_128.png`、`icon_256.png`）与海报（`poster.png`）
- [ ] 工坊页面：简介、截图（可选视频）
- [ ] 若用 ZombieBuddy 签名：JAR 重新签名
- [ ] 在全新环境（另一台机器 / 干净 mods 目录）验证安装

## 开源协议

MIT License。见 [LICENSE](LICENSE)。

## 致谢

- [ZombieBuddy](https://steamcommunity.com/sharedfiles/filedetails/?id=3619862853) —— Java mod 补丁框架。
