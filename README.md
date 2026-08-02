# WindowsdePC's AI Companion Mod 0.4.1（Fabric 26.2）

包含 OpenAI Chat Completions 兼容 API、Fabric FakePlayer、猎人/队友/PvP 教练模式、
天眼快照、统一游戏内配置 UI，以及可配置的金矛二级突进功能。

## 构建

要求 JDK 25：

```bash
./gradlew clean build
```

Windows：

```powershell
.\gradlew.bat clean build
```

输出：

- `windowsdepcs-ai-companion-0.4.1.jar`：放进 `mods/` 的正式模组；
- `windowsdepcs-ai-companion-0.4.1-sources.jar`：源码；
- `windowsdepcs-ai-companion-0.4.1-javadoc.jar`：开发文档。

## 统一游戏内 UI

进入世界或服务器后同时按 `V+B` 打开。可在“其他”栏目把两个按键修改为任意
A-Z 字母组合。

栏目：

- `AI 管理`：批量生成 1-20 个 AI、展开选择当前在线玩家、选择追杀/队友/PvP
  教练模式、分配提示词并触发 AI 决策；
- `API`：设置 OpenAI 兼容接口地址、模型和令牌；
- `提示词`：编辑、创建、删除、恢复并分配命名预设；
- `其他`：设置金矛突进、消耗数值、冲量强度和 UI 快捷键。

内置提示词：

- `idle`：自由生存玩家；
- `hunter`：猎人/追杀；
- `teammate`：队友协作；
- `pvp_coach`：与指定玩家对练并提供 PvP 建议。

提示词允许正常玩家可以进行的探索、挖掘、建造、合成、战斗、交易和合作，只明确
禁止作弊、管理员命令、创造能力、传送、复制和未授权透视信息。`{targets}` 会在
决策时替换为 UI 中选择的目标玩家。

修改服务器配置、全局提示词和 AI 分配需要管理员权限。API 令牌不会写入客户端
设置文件；推荐专用服务器使用 `MCAI_API_KEY` 环境变量。

## 金矛突进

- 默认开启；
- 金矛无需附魔，攻击实体时获得原版突进 II 的水平冲量，默认强度 `0.916`；
- 每15次突进消耗1点耐久；
- 每30次突进消耗2点饥饿值，即一个完整鸡腿图标；
- 创造模式不消耗耐久或饥饿；
- 金矛从原版 `enchantable/lunge` 标签中排除，不能正常获得突进附魔；
- 木、石、铜、铁、钻石、下界合金矛仍需正常附魔才能获得原版突进；
- 开关与所有数值可在“其他”栏目修改。

## 命令备用入口

```text
/aiplayer create <名称>
/aiplayer create-many <名称前缀> <1-20>
/aiplayer hunt <AI名> <玩家名>
/aiplayer team <AI名> <玩家名>
/aiplayer coach <AI名> <玩家名>
/aiplayer idle <AI名>
/aiplayer ask <AI名> <任务>
/aiplayer prompt list
/aiplayer prompt assign <AI名> <预设ID>
/aiplayer feature status
```

## 当前边界

- 已实现：可见 FakePlayer、API 调用、统一 UI、模式与目标、可编辑提示词、短距离
  `say/move/wait` 动作、天眼快照和金矛突进。
- 仍未实现：复杂地形寻路、完整挖掘/战斗/背包执行器、AI 语音协议，以及客户端
  透视高亮渲染。
- PvP 教练目前拥有独立目标状态和提示词，但受现有 `say/move/wait` 执行器限制，
  尚不能完成完整自动 PvP。
- 目前模组版本为 Minecraft Fabric `26.2`。
