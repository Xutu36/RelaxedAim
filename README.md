# 忘关就是开了？（RelaxedAim）

Project Zomboid Build 42 客户端辅助瞄准 Mod（枪械辅助自动瞄准）。

> **依赖**：本 Mod 是 **Java Mod**，核心功能由 Java 补丁实现，必须安装 **ZombieBuddy**（Java Mod 框架）才能运行。
> **协议**：MIT License

---

## 中文 / 简介

这是**枪械辅助自动瞄准 Mod**。原版手枪/步枪只有精准命中丧尸头部才会造成较高伤害，玩起来像体验奇怪的 FPS。本 Mod 在不改变原版枪械「不确定性」的前提下，帮你**更快、更稳**地把准心对准丧尸头部，降低瞄准微调的操作压力。锁定、吸附、命中全部在客户端本地完成。

## English / Introduction

A **gun-assist auto-aim mod**. Vanilla pistols/rifles only deal high damage on precise headshots — it can feel like a strangely designed FPS. This mod helps you put the crosshair on a zombie's head **faster and more steadily**, without removing vanilla gunplay uncertainty. Locking, snapping and hit resolution all happen locally on the client.

---

## 功能 / Features

- **自动锁定**：远程武器瞄准时，锁定原始鼠标瞄准点邻域内最近的合法丧尸（范围、最大距离可调）。
- **平滑吸附**：准心从鼠标位置按期望时间平滑移动到锁定丧尸**头部骨骼**（待机/奔跑/倒地姿态自适应，沿颈部→头部方向偏移）；进入强锁定阈值后完全锁定头部。
- **锁定保持**：短暂遮挡不丢锁定（可配置保持时间）；鼠标移出范围立即释放并重筛。
- **原版机制保留**：命中率、散布、缩圈、射程/遮挡/楼层限制完全走原版，仅移动弹道中心。
- **临时开关**：默认键盘顶部数字行 `0` 键随时启用/禁用辅助（模组设置中可自定义按键，角色头顶显示提示）。
- **清晰 UI**：青色捕获范围圈（锁定后渐阔到释放半径）、紫色圈标记将被/正在锁定的头部。
- **手柄支持**：手柄瞄准（右摇杆）同样支持自动锁定与平滑吸附，手柄十字键上（模组设置可自定义手柄热键）随时开关辅助。
- **配置本地化**：配置项随系统语言显示（EN / 简体中文 / 繁体中文）。

- **Auto lock-on**: locks the nearest valid zombie near your aim point (configurable capture radius & max distance).
- **Smooth snapping**: the crosshair smoothly moves to the locked zombie's **head bone** (posture-aware, offset along the neck→head direction), then fully locks within the strong-lock threshold.
- **Lock hold**: brief occlusion no longer drops the lock (configurable); moving the mouse out of range releases and re-filters immediately.
- **Vanilla preserved**: hit chance, spread, reticle contraction, range/line-of-sight/floor limits all stay vanilla — we only move the ballistic center to the head.
- **Quick toggle**: default `0` key (top row), remappable in mod settings (overhead text notification).
- **Clear UI**: a cyan capture-range circle (expands to the release radius when locked) and a purple marker on the target's head.
- **Gamepad support**: works with controller aiming (right stick) — auto lock-on & smooth snapping; toggle with D-pad Up (remappable in mod settings).
- **Localized**: settings follow the system language (EN / Simplified / Traditional Chinese).

---

## 依赖 / Dependency：ZombieBuddy（必须）

本 Mod 的 Java 补丁通过 ZombieBuddy 的 Java agent 注入游戏。**每个玩家（客户端）都必须安装 ZombieBuddy**：

1. Steam 创意工坊订阅 [ZombieBuddy](https://steamcommunity.com/sharedfiles/filedetails/?id=3619862853)。
2. 运行 **ZombieBuddyInstaller.exe** 选择 Install（GitHub Releases 下载；国内用户可用作者提供的国内镜像）。首次进入游戏时，在 ZombieBuddy 审批框选择 **Allow**。

This Java mod runs through ZombieBuddy's Java agent. **Every player must install ZombieBuddy first**:

1. Subscribe to [ZombieBuddy](https://steamcommunity.com/sharedfiles/filedetails/?id=3619862853) on the Steam Workshop.
2. Run **ZombieBuddyInstaller.exe** (from GitHub Releases) and choose Install. On first launch, choose **Allow** in the ZombieBuddy approval dialog.

---

## 安装 / Installation

### 客户端（玩家） / Client

- **发布后**：Steam 创意工坊订阅 RelaxedAim（客户端自动下载），在 Mod 管理器启用。
- **未发布 / 本地测试**：把 `RelaxedAim/`（含 `common/`、`42/`）复制到 `%USERPROFILE%\Zomboid\mods\RelaxedAim\`，在游戏 Mod 管理器启用。

- **Published**: subscribe on the Steam Workshop and enable in the mod manager.
- **Local / unpublished**: copy `RelaxedAim/` (with `common/` and `42/`) into `%USERPROFILE%\Zomboid\mods\RelaxedAim\` and enable it.

### 服务器（本地联机测试） / Server

1. 用面板/ FTP 把 `RelaxedAim/` 上传到服务器 `Zomboid/mods/`。
2. 服务器配置 `Mods=RelaxedAim`（本地 mod 走 `Mods=`，工坊 mod 在 `WorkshopItems=` 填工坊 ID）。
3. 重启服务器；客户端也须安装 ZombieBuddy + 本 Mod，mod 列表才能匹配。

1. Upload `RelaxedAim/` to the server's `Zomboid/mods/`.
2. Set `Mods=RelaxedAim` in the server config.
3. Restart; clients must also have ZombieBuddy + this mod so the mod lists match.

---

## 配置 / Configuration

- **本地（游戏设置 → 模组 → RelaxedAim）**：启用、热键、捕获半径、最大距离、保持时间、霰弹枪不锁定等。
- **沙盒（服务器/单机世界 → 沙盒选项 → RelaxedAim）**：辅助强度 `AssistStrength`（服务器平衡；多人中所有玩家统一使用服务器值）。

- **Local (Options → Mods → RelaxedAim)**: enable, hotkey, capture radius, max distance, hold time, shotgun no-lock, etc.
- **Sandbox (server/world → Sandbox → RelaxedAim)**: Assist strength `AssistStrength` (server balance; synced to all players in multiplayer).

---

## 开发与构建 / Build

需要 JDK（本项目用 25）。从 `java/` 目录：

```powershell
.\gradlew.bat build             # 编译并安装 42/media/java/client/RelaxedAim.jar
.\gradlew.bat deployLocalMod    # 部署到 %USERPROFILE%\Zomboid\mods\RelaxedAim
```

> `build.gradle` 中的游戏/ZombieBuddy/部署路径为本机硬编码，按需修改。`signJarZBS` 每次构建都会重新生成 `.zbs` 签名。

Requires JDK (this project uses 25). From `java/`:

```powershell
.\gradlew.bat build             # compile & install the JAR
.\gradlew.bat deployLocalMod    # deploy to %USERPROFILE%\Zomboid\mods\RelaxedAim
```

> Paths in `build.gradle` are machine-specific; adjust as needed. `signJarZBS` re-signs on every build.

### 目录结构 / Structure

```text
RelaxedAim/
├─ common/                    # 跨版本元数据与翻译 (metadata & translations)
│  ├─ mod.info
│  └─ media/lua/shared/Translate/{EN,CN,CH}/*.json
├─ 42/media/                  # B42 内容 (B42 content)
│  ├─ sandbox-options.txt     # 沙盒：辅助强度 (sandbox: AssistStrength)
│  ├─ lua/client/             # 模组设置、热键提示 (options, hotkey)
│  └─ java/client/RelaxedAim.jar   # 构建产物 (build output)
├─ java/                      # Java 补丁源码与 Gradle 构建 (Java patches & Gradle)
└─ signing/                   # 签名工具 (optional signing tool)
```

---

## ZombieBuddy 签名 / Signing

<<<<<<< HEAD
本项目的作者公钥已合入 ZombieBuddy 的 `authors.json`（GitHub PR，由 Zed 签名缓存），每次构建都会为 `RelaxedAim.jar` 生成匹配的 `.zbs` 签名（Ed25519）。玩家安装后由 ZombieBuddy 校验：先从 `authors.json` 取公钥，若列表未刷新则回退读取作者 Steam 个人资料简介中的 `JavaModZBS:<公钥>`。公钥：`a140d928eed497c39427d85fb849a202e883146cca5dbfd3c8949796e1f7146a`。详见 [ZombieBuddy ModSigning](https://github.com/zed-0xff/ZombieBuddy/blob/master/doc/ModSigning.md)。

The author's public key has been merged into ZombieBuddy's `authors.json` (via a GitHub PR, signed & cached by Zed). Every build produces a matching `.zbs` Ed25519 signature for `RelaxedAim.jar`. ZombieBuddy verifies from `authors.json` first; if the list is stale it falls back to the `JavaModZBS:<pubkey>` string in the author's Steam profile summary. Public key: `a140d928eed497c39427d85fb849a202e883146cca5dbfd3c8949796e1f7146a`. See ZombieBuddy's ModSigning doc for details.
=======
本项目当前以**未签名**状态发布（玩家在 ZombieBuddy 审批框按未签名确认即可）。签名（`.zbs`）需作者公钥进入 ZombieBuddy 的 `authors.json`（经 GitHub PR 合并、由 Zed 公钥签名缓存）后才能离线校验。详见 `doc/` 与 [ZombieBuddy ModSigning](https://github.com/zed-0xff/ZombieBuddy/blob/master/doc/ModSigning.md)。

This project is currently published **unsigned** (players confirm it as unsigned in the ZombieBuddy approval dialog). Signing (`.zbs`) requires the author's public key to be added to ZombieBuddy's `authors.json` (via a GitHub PR; the list is signed by Zed and cached). See ZombieBuddy's ModSigning doc for details.
>>>>>>> b84758bb4f6b7310ac1a97f06849bfb2d1b3cba5

---

## 开源 / Open Source

- 本项目是开源项目，所有玩家与开发者都可以对源码学习、审查、共同开发。
- MIT License（见 `LICENSE`）。
- 致谢：[ZombieBuddy](https://steamcommunity.com/sharedfiles/filedetails/?id=3619862853) —— Java mod 补丁框架。

- This project is open source; everyone is welcome to study, audit and co-develop.
- MIT License (see `LICENSE`).
- Credits: [ZombieBuddy](https://steamcommunity.com/sharedfiles/filedetails/?id=3619862853) — the Java mod framework.
<<<<<<< HEAD
=======

>>>>>>> b84758bb4f6b7310ac1a97f06849bfb2d1b3cba5
