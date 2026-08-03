# WindowsdePC's AI Companion Mod

WindowsdePC's AI Companion Mod 是面向 Minecraft 26.2 Fabric 的 AI 玩家与游戏增强模组。
它以 Fabric `FakePlayer` 作为 AI 身份，通过 OpenAI Chat Completions 兼容接口取得受约束的
动作决策，并提供游戏内配置、提示词管理、目标模式、天眼快照与可选玩法增强。

当前版本：`0.5.2`

## 环境要求

- Minecraft `26.2`
- Fabric Loader `0.19.3` 或更高版本
- Fabric API `0.155.2+26.2` 或兼容版本
- Java `25`
- 下列 UI 支持库二选一：
  - [EclipseUI](https://modrinth.com/mod/eclipseui) `1.0.5+mc26.2-rc-2` 或兼容版本（优先）
  - [Cloth Config](https://modrinth.com/mod/cloth-config) `26.2.155` 或兼容版本（备用）

当两个 UI 库同时安装时，模组只使用 EclipseUI；安装 EclipseUI 后不需要 Cloth Config。
如果两者都未安装，客户端会明确报告缺少 UI 支持库。

## 主要功能

### AI 玩家

- 创建、批量创建、列出和移除具有独立名称与 UUID 的可见 `FakePlayer`。
- 猎人、队友、PvP 教练和空闲四种任务模式。
- 选择当前在线玩家作为目标，并为指定 AI 分配提示词预设。
- 通过异步 OpenAI 兼容 API 请求取得 `say`、`move`、`wait` 白名单动作。
- 天眼快照记录目标当时的维度、坐标与采集时间，不传送 AI。
- 支持 Mojang 纹理值与签名形式的自定义皮肤数据。

### 双 UI 配置

同时按 `V+B` 打开配置界面；组合键可在模组界面中修改，不注册到原版“控制”列表。

EclipseUI 模式提供分类卡片、图标、开关、滑块、下拉菜单和说明文字；未安装 EclipseUI
时使用 Cloth Config 备用界面。两种后端都包含九个主栏目：

1. AI系统
2. 快捷键修改
3. 游戏增强
4. 客户端增强
5. 小游戏中心
6. 休闲系统
7. 性能优化
8. 兼容设置
9. 高级设置

“AI系统”可进入完整 AI 管理器，继续使用批量生成、在线玩家选择、模式分配、API 配置和
多行提示词编辑等既有功能。小游戏、休闲和性能栏目是后续设计文档功能的固定入口，相关
功能会按版本逐项启用。

### 小游戏中心

`0.5.2` 首批开放两个可直接游玩的客户端小游戏：

- **贪吃蛇**：方向键或 WASD 控制像素蛇。红苹果、金苹果和钻石分别提供不同分数，
  游戏会保存本地最高分；达到 500 分解锁“贪吃蛇大师”称号记录。
- **Minecraft 俄罗斯方块**：包含七种标准方块、旋转墙踢、软降、硬降、消行、等级和
  最高分记录。完成至少一行后结算铁锭、金锭或钻石奖励；服务器会检查分数范围，并对
  矿物奖励设置 60 秒冷却。

从模组完整管理器进入“小游戏中心”，点击对应按钮开始。小游戏不会暂停世界；按 Esc
或“返回小游戏中心”可回到管理器。成绩保存在客户端配置目录，不会写入世界存档。

### F3+B 原版发光轮廓

“客户端增强”与完整管理器的“其他”页包含 `F3+B 发光轮廓` 开关，默认开启。

- 按下 F3+B 后，实体碰撞箱线框会替换为原版光灵箭使用的发光轮廓。
- 实现直接接入 `Minecraft.shouldEntityAppearGlowing`，不复制轮廓渲染算法。
- 仅改变当前客户端的渲染判定，不给实体写入发光状态，也不影响服务器或其他玩家。
- 关闭选项后恢复原版 F3+B 实体碰撞箱样式。
- 从 0.4.1 升级时，旧客户端配置会自动迁移为默认开启。

### 金矛突进

- 默认开启，金矛无需附魔即可获得二级突进的水平冲量。
- 默认每 15 次消耗 1 点耐久，每 30 次消耗 2 点饥饿值。
- 创造模式不消耗耐久或饥饿。
- 金矛从原版突进附魔支持标签中排除；其他材质的矛仍需正常附魔。
- 开关、消耗间隔、饥饿点数和冲量强度均可在 UI 中修改。

## API 与密钥

模组支持 OpenAI Chat Completions 兼容接口。API 地址、模型和令牌可在完整 AI 管理器中
配置；客户端不会保存令牌。专用服务器推荐使用环境变量：

```bash
MCAI_API_KEY=your_api_key
```

不要把 API 密钥、GitHub Token、服务器配置或 `.env` 文件提交到仓库。

## 游戏内命令

UI 是主要入口，管理员也可以使用命令：

```text
/aiplayer create <名称>
/aiplayer create-many <名称前缀> <1-20>
/aiplayer remove <AI名>
/aiplayer list
/aiplayer hunt <AI名> <玩家名>
/aiplayer team <AI名> <玩家名>
/aiplayer coach <AI名> <玩家名>
/aiplayer idle <AI名>
/aiplayer eye <AI名>
/aiplayer ask <AI名> <任务>
/aiplayer prompt list
/aiplayer prompt assign <AI名> <预设ID>
/aiplayer feature status
```

修改服务器 API、全局提示词、AI 分配和玩法数值需要管理员权限。

## 构建

仓库包含 Gradle Wrapper。使用 JDK 25：

Linux / macOS：

```bash
./gradlew clean build
```

Windows PowerShell：

```powershell
.\gradlew.bat clean build
```

构建产物位于 `build/libs/`：

- `windowsdepcs-ai-companion-0.5.2.jar`：正式模组，放入 `mods/`。
- `windowsdepcs-ai-companion-0.5.2-sources.jar`：源码包，供开发工具使用。
- `windowsdepcs-ai-companion-0.5.2-javadoc.jar`：Java API 文档包。

普通玩家只安装第一个正式模组 JAR；不要把 sources 或 Javadoc JAR 放进 `mods/`。

## 当前边界

0.5.2 保留现有 AI 决策与任务框架，但复杂地形寻路、完整战斗/挖掘/合成/背包执行器、
多 AI 共识与领队选举、Simple Voice Chat 协议等仍在后续版本开发。设计路线见
[`docs/My Mod Design Document.md`](docs/My%20Mod%20Design%20Document.md)。

## 许可证

本项目使用 [MIT License](LICENSE)。
