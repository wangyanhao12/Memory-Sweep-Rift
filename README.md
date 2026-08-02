# MemorySweep（Rift 版,Minecraft 1.13.2）

一个适用于 **Minecraft Java 版 1.13.2(Rift 加载器)** 的自动内存清理模组。

## ⚠️ 在开始之前:请先读完这一段

Rift 是 2018 年 Forge 迟迟不肯适配 1.13.2("The Flattening" 改动太大)期间,社区做的临时替代方案。这个项目**已经废弃超过 7 年**(我核对过,最活跃的分支 `Chocohead/Rift` 的 1.13.2 分支最后一次提交是 2019 年 2 月;它依赖的 Gradle 插件 `Chocohead/ForgeGradle`(moderniser 分支)最后一次提交是 2019 年 3 月),这意味着:

1. **本项目里 Java 源码部分(逻辑代码)我是有把握的** —— `ServerTickable`、`CommandAdder`、`MinecraftStartListener` 这几个接口,以及 1.13.2 原生 Brigadier 指令系统(`CommandSource`、`Commands`、`StringTextComponent`)的方法签名,都是对照 Rift 真实源码和当时的 Forge 1.13.2 Javadoc/真实模组源码核对过的,而且已经用本地 JDK 8 编译验证通过。
2. **但构建工具链这块,风险明显比之前几个版本都大**:官方推荐的构建方式依赖:
   - `com.github.Chocohead:ForgeGradle:moderniser-SNAPSHOT` —— 一个通过 JitPack 从 GitHub 源码直接构建的、7 年没更新的 Gradle 插件
   - `www.dimdev.org/maven/`、`repo.strezz.org` —— 两个个人/社区托管的 Maven 仓库,已经 7 年没有确认活跃过,我在这个环境里没法验证它们现在是否还能访问(dimdev.org 直接拒绝了我的自动化访问请求)
   - 项目用的 Gradle 版本(4.10.3,和当年 ForgeGradle 2 系配套的常见版本)是我根据这套工具链的年代**推断**出来的,不是从官方文档里直接确认到的精确要求版本
3. **也就是说:就算这份代码写得完全正确,构建这一步仍然有实打实的可能会卡在"某个 7 年前的服务器/插件已经不能用了"这种我们俩都无能为力的地方**,这跟前面 Fabric/NeoForge/Babric 那几个"改代码就能修好"的报错性质不一样。

如果构建时卡在**下载/解析 ForgeGradle 插件本身**或者 **dimdev.org / strezz.org 相关的依赖**上,大概率就是遇到了这类无法绕过的基础设施问题;如果卡在**编译我写的 Java 代码**上,那大概率是我哪里判断错了,可以贴日志给我继续排查。

## 功能

- **`/memorysweep`** —— 立即手动执行一次内存清理(1.13.2 原生指令,支持 Tab 补全)。
- **`/memorysweep status`**(附加功能)—— 查看当前内存使用情况。
- **定时自动清理** —— 默认每 **15 分钟**清理一次,可在配置文件中调整。
- **使用率自动清理** —— 堆内存使用率达到阈值(默认 **80%**)时自动清理,同一冷却周期内(默认 **2 分钟**)只执行一次。

## 环境要求

| 项目 | 版本 |
|---|---|
| Minecraft | Java 版 1.13.2 |
| Rift | Chocohead/Rift,`newerest` 分支(1.13.2 专用) |
| Java | 8 |
| Gradle(仅开发环境) | 4.10.3(推断值,如果构建失败可以尝试 4.9/4.10/5.0 等相近版本) |

## 指令权限

`/memorysweep` 要求权限等级 2(相当于原版 OP),对应这个年代 Minecraft 经典的 0-4 级权限体系(与后来 26.1 引入的具名权限系统是两套不同机制)。

## 配置文件

`config/memorysweep.properties`,字段和之前几个版本一致:

| 字段 | 默认值 | 说明 |
|---|---|---|
| `autoCleanupEnabled` | `true` | 是否启用定时自动清理 |
| `intervalMinutes` | `15` | 定时清理间隔(分钟) |
| `usageBasedCleanupEnabled` | `true` | 是否启用使用率触发清理 |
| `memoryUsageThresholdPercent` | `80` | 触发阈值(1-99) |
| `usageCheckCooldownSeconds` | `120` | 使用率触发的冷却时间(秒) |
| `usageCheckIntervalSeconds` | `5` | 检查频率(秒) |
| `logToConsole` | `true` | 是否输出到控制台 |

修改后需要重启才能生效。

## 构建方法

```bash
./gradlew build
```

构建产物在 `build/libs/`。项目自带 `.github/workflows/build.yml`,也可以推到 GitHub 用 Actions 云端构建(步骤和之前几个版本一样)。

**如果构建失败**,把完整报错日志发给我。请留意报错发生在哪个阶段——是"下载/解析插件"阶段,还是"编译 Java 代码"阶段,这决定了问题出在无法绕开的外部基础设施,还是代码本身。

## 项目结构

```
memorysweep-rift/
├── build.gradle
├── settings.gradle
├── gradlew / gradlew.bat / gradle/wrapper/...
├── .github/workflows/build.yml
├── LICENSE
├── README.md
└── src/main/
    ├── java/com/memorysweep/
    │   ├── MemorySweepListener.java   # 同时实现三个 Rift 监听接口(启动/tick/指令)
    │   ├── MemoryMonitor.java         # 核心清理逻辑
    │   └── config/MemorySweepConfig.java
    └── resources/
        └── riftmod.json                # Rift 模组描述文件
```

## 个性化

发布前建议编辑 `src/main/resources/riftmod.json` 里的 `authors` 字段。
