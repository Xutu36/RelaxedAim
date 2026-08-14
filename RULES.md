# RelaxedAim 开发规则（必读）

以下规则适用于 `D:\PZMod\RelaxedAim` 的全部 Java 代码，每次改动都必须遵守。

## 规则 1：开发阶段全部使用 public

- 所有**字段**和**方法**统一使用 `public` 关键字，不要写 `private`。
- 原因：ZombieBuddy 会把 `@Patch` advice 方法体**内联**到游戏类中执行。
  如果 advice 方法访问了 `private` 成员，运行时会产生 `IllegalAccessError`，导致游戏**秒退**。
- 例外：工具类的私有构造器可保留 `private`（但写 `public` 也无害）。
- 后续发布前可再收紧为 `private`，开发期间一律 `public`。

## 规则 2：每次改动必须升级版本号

- 版本号唯一来源：`com.relaxedaim.RelaxedAimConfig.VERSION`。
- 每次修改 Java 代码后，必须：
  1. 升级 `VERSION`（如 `Lock-v1.1` → `Lock-v1.2`）；
  2. 在 `VERSION_NOTES` 中写一句本次改动的简述；
  3. 重新 `deployLocalMod` 并**完全重启游戏**。
- 原因：左上角 HUD 与主菜单都显示该版本号。若构建/部署失败或 JAR 未更新，
  游戏内版本号会停留在旧版本，从而第一时间发现发布问题。

## 规则 3：JAR 更新必须完全重启游戏

- Java Patch 在启动时加载，重载存档不会加载新代码。
- 修改 Java 后：`cd D:\PZMod\RelaxedAim\java && .\gradlew.bat deployLocalMod`，然后退出游戏重新启动。

## 规则 4：不要在两个参考项目里开发

- `ZombieBuddy-master`、`ZBHelloWorld-master` 保持原样，仅作参考。
- 所有开发在 `D:\PZMod\RelaxedAim` 内进行。
