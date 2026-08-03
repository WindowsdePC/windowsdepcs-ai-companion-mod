# WindowsdePC's AI Companion Mod

WindowsdePC's AI Companion Mod 是面向 Minecraft 26.2 Fabric 的 AI 玩家与游戏增强模组。
它以 Fabric `FakePlayer` 作为 AI 身份，通过 OpenAI Chat Completions 兼容接口取得受约束的
动作决策，并提供游戏内配置、提示词管理、目标模式、天眼快照与可选玩法增强。

当前版本：`0.5.6`

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
多行提示词编辑等既有功能。“小游戏中心”已开放贪吃蛇、Minecraft 俄罗斯方块、Minecraft
方块扫雷、2048 与 AI 猜拳；休闲和性能栏目是后续设计文档功能的固定入口，相关功能会按版本逐项启用。

### 小游戏中心

`0.5.6` 已完成设计文档中的五个小游戏。打开完整管理中心并选择“小游戏中心”即可游玩。

#### 贪吃蛇

- 24×18 像素棋盘，可用方向键或 `WASD` 控制。
- 苹果、金苹果和钻石分别提供 1、3、5 分，分数越高移动越快。
- 自动保存最高分；10 分解锁金苹果蛇皮肤，25 分解锁钻石蛇皮肤。
- 单局达到 30 分解锁“贪吃蛇大师”称号。
- 空格或 `P` 暂停，`R` 重新开始；小游戏不会暂停整个世界。

#### Minecraft 俄罗斯方块

- 标准 10×20 棋盘、七袋随机、七种方块、旋转、软降、硬降、幽灵落点、消行、分数与等级。
- 自动保存最高分、最佳消行和累计消行数。
- 消除至少一行并正常结束后，由服务器随机发放粗铁、粗金或钻石。
- 奖励使用服务器会话、合理用时检查、重复提交保护和 60 秒冷却；物品不会由客户端凭空生成。
- 方向键或 `A/D` 移动，`↑/W/X` 旋转，`↓/S` 软降，空格硬降，`P` 暂停，`R` 重开。

#### Minecraft 方块扫雷

- 初级 9×9/10、中级 16×16/40、专家 30×16/99 三档经典棋盘；第一次翻开的 3×3 区域不会生成 TNT。
- 左键翻开、右键插旗；也可用 `F` 或界面按钮切换翻开/插旗操作。
- 空白区域会自动展开；已翻开的数字在周围旗帜数量正确时支持连开。
- 按难度分别保存最佳时间，并自动保存总胜场、当前连胜与最佳连胜。
- 正常获胜后由服务器随机发放粗铁、粗金或钻石，带会话、用时、重复提交和 60 秒冷却校验。

#### Minecraft 2048

- 标准 4×4 棋盘，使用方向键或 `WASD` 滑动数字方块。
- 相同数字每次移动只合并一次；每次有效移动后随机生成 2 或 4。
- 可点击“撤销一步”或按 `U` 恢复最近一次有效移动前的棋盘与分数。
- 自动保存最高分、历史最大方块与成功合成 2048 的次数。
- 达到 2048 后可按 `C` 继续挑战 4096 与更高数字；`P` 暂停，`R` 重开。

#### AI 猜拳

- 玩家可选择石头、剪刀或布，与本地 AI 进行不限轮数的猜拳。
- AI 提供冷静、好胜、淘气三种人格；不同人格使用不同的加权随机与玩家历史策略。
- 自动保存胜、负、平、当前连胜与最佳连胜。
- AI 获胜时会说“看来今天我的运气不错。”；失败时会说“下一次我一定赢回来。”。

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
/aiplayer minigame start tetris <会话ID>
/aiplayer minigame finish tetris <会话ID> <分数> <消行数>
/aiplayer minigame start minesweeper <会话ID>
/aiplayer minigame finish minesweeper <会话ID> <用时Tick>
```

修改服务器 API、全局提示词、AI 分配和玩法数值需要管理员权限。
`minigame` 子命令由游戏界面自动调用，普通玩家无需手动输入。

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

- `windowsdepcs-ai-companion-0.5.6.jar`：正式模组，放入 `mods/`。
- `windowsdepcs-ai-companion-0.5.6-sources.jar`：源码包，供开发工具使用。
- `windowsdepcs-ai-companion-0.5.6-javadoc.jar`：Java API 文档包。

普通玩家只安装第一个正式模组 JAR；不要把 sources 或 Javadoc JAR 放进 `mods/`。

## 当前边界

0.5.6 保留现有 AI 决策与任务框架，但复杂地形寻路、完整战斗/挖掘/合成/背包执行器、
多 AI 共识与领队选举、Simple Voice Chat 协议等仍在后续版本开发。设计路线见
[`docs/My Mod Design Document.md`](docs/My%20Mod%20Design%20Document.md)。

## 许可证

本项目使用 [MIT License](LICENSE)。
