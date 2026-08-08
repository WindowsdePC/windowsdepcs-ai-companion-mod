# WindowsdePC's AI Companion Mod

WindowsdePC's AI Companion Mod 是同时面向 Minecraft 26.2 与 1.20.1 的 AI 玩家与游戏增强模组。
它以 Fabric `FakePlayer` 作为 AI 身份，通过 OpenAI Chat Completions 兼容接口取得受约束的
动作决策，并提供游戏内配置、提示词管理、目标模式、天眼快照与可选玩法增强。

当前版本：[v0.9.9](https://github.com/WindowsdePC/windowsdepcs-ai-companion-mod/releases/tag/v0.9.9)

## 下载版本

| Minecraft | 加载器 | Java | 发行文件 |
| --- | --- | --- | --- |
| 26.2 | Fabric | 25 | `windowsdepcs-ai-companion-mc26.2-fabric-0.9.9.jar` |
| 1.20.1 | Fabric | 17 | `windowsdepcs-ai-companion-mc1.20.1-fabric-0.9.9.jar` |
| 1.20.1 | Forge | 17 | `windowsdepcs-ai-companion-mc1.20.1-forge-0.9.9.jar` |

三个文件互相替代，只安装与当前游戏版本和加载器完全匹配的一个。

0.8.3 起修复两个 1.20.1 版本的客户端入口与命令树：V+B、F8、C、G 采用 1.20.1 API
直接轮询且不写入原版“控制”列表；旧版分支不再保留 26.2 包名、渲染源码或金矛突进资源。

## 依赖模组

### 必要依赖

| 目标版本 | 必须安装 | 网址 |
| --- | --- | --- |
| Minecraft 26.2 Fabric | Fabric Loader 0.19.3+ | [Fabric 官方安装页](https://fabricmc.net/use/installer/) |
| Minecraft 26.2 Fabric | Fabric API 0.155.2+26.2+ | [Fabric API](https://modrinth.com/mod/fabric-api) |
| Minecraft 26.2 Fabric | EclipseUI 或 Cloth Config，二选一 | [EclipseUI](https://modrinth.com/mod/eclipseui) / [Cloth Config](https://modrinth.com/mod/cloth-config) |
| Minecraft 1.20.1 Fabric | Fabric Loader 0.16.0+ | [Fabric 官方安装页](https://fabricmc.net/use/installer/) |
| Minecraft 1.20.1 Fabric | Fabric API 0.92.0+1.20.1+ | [Fabric API](https://modrinth.com/mod/fabric-api) |
| Minecraft 1.20.1 Forge | Minecraft Forge 47.x | [Forge 1.20.1 下载页](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html) |

26.2 Fabric 版使用 Java 25；两个 1.20.1 版本使用 Java 17。两个 UI 库同时存在时优先
EclipseUI；已安装 EclipseUI 时不需要 Cloth Config。两个 1.20.1 兼容版当前不要求 UI 库。

1.20.1 Fabric 的依赖名称仍是旧版 `Fabric Loader` 与 `Fabric API`，不是 26.2 的对应 JAR；
1.20.1 Forge 的加载器名称是 `Minecraft Forge 47.x`，不安装 Fabric Loader、Fabric API、
EclipseUI，也不使用 26.2 Fabric 的文件。模组文件名中的 `mc1.20.1-fabric`、
`mc1.20.1-forge` 与 `mc26.2-fabric` 必须和实例逐字匹配。

### 可选依赖

以下模组都不是启动所必需的依赖；未安装时相应兼容入口会自动停用，核心功能仍可使用。

| 模组 | 适用发行目标 | 用途 | 网址 |
| --- | --- | --- | --- |
| Cloth Config API | Minecraft 1.20.1 Forge（优先推荐） | 低版本配置生态兼容；1.20.1 不使用 EclipseUI | [Cloth Config](https://modrinth.com/mod/cloth-config) |
| Simple Voice Chat | 选择与加载器/游戏版本匹配的版本 | 女仆语音转写兼容通道 | [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) |
| Sophisticated Backpacks | Minecraft 1.20.1 Forge | 女仆外部背包识别 | [Sophisticated Backpacks](https://www.curseforge.com/minecraft/mc-mods/sophisticated-backpacks) |
| Traveler's Backpack | Minecraft 1.20.1 Fabric 或 Forge | 女仆外部背包识别 | [Traveler's Backpack](https://modrinth.com/mod/travelersbackpack) |
| Inventory Profiles Next | 选择与加载器/游戏版本匹配的版本 | 女仆标准容器的一键整理兼容 | [Inventory Profiles Next](https://modrinth.com/mod/inventory-profiles-next) |
| Cloth Config API | Minecraft 1.20.1 Fabric 或 Forge（优先支持） | 低版本配置界面与顶部快捷栏 | [Cloth Config](https://modrinth.com/mod/cloth-config) |
| Mod Menu | Minecraft 26.2/1.20.1 Fabric | 从模组列表打开完整配置中心 | [Mod Menu](https://modrinth.com/mod/modmenu) |

EclipseUI 与 Cloth Config 对 26.2 Fabric 版属于“二选一”的条件必要依赖，不应同时被理解为
两个都必须安装。外部背包的内部私有槽位始终由对应背包模组管理。

## 主要功能

### 结构、群系与维度导航

- 默认关闭；管理员在“高级设置”中启用服务器导航后，玩家可用默认 `G` 键打开搜索界面。
- 目录来自服务器当前的群系、结构与维度注册表，包含模组注册内容。
- AR 模式显示方向、目标维度、剩余方块和进度条；传送模式仅允许管理员使用。
- “仁慈的虚空”会在玩家跌出三个原版维度时将其送回本维度安全高空，并提供缓降与落地前无伤保护。
- 世界边界选项只扩展到原版安全最大值；真正无限高度、64 位坐标与“持续渲染到崩溃”受游戏引擎和存档格式限制，不会伪装成可用功能。

### AI 玩家

- 0.9.1 起 AI 通过原版玩家列表生命周期加入世界；命令只有在玩家列表与当前世界均验证成功后才报告创建成功。
- 单个 AI 在命令玩家脚边生成，批量 AI 在玩家身边分散生成，避免与玩家或彼此完全重叠而看不见。
- 创建、批量创建、列出和移除具有独立名称与 UUID 的可见 `FakePlayer`。
- 猎人、队友、PvP 教练和空闲四种任务模式。
- 选择当前在线玩家作为目标，并为指定 AI 分配提示词预设。
- 通过异步 OpenAI 兼容 API 请求取得 `say`、`move`、`wait` 白名单动作。
- 天眼快照记录目标当时的维度、坐标与采集时间，不传送 AI。
- `/aiplayer positions` 从服务器查询所有已登记 AI 当前所在维度与 XYZ 坐标。
- 按住 `F8` 显示 AI 位置 HUD；每次重新按下都会向服务器刷新一次，松开立即关闭。
- 支持 Mojang 纹理值与签名形式的自定义皮肤数据。
- AI 身份会按稳定 UUID 持久化；服务器重启后恢复名称、位置、模式、目标、提示词与皮肤。
- 0.9.9 起 AI 身份按世界存档隔离；恢复失败或未同时存在于玩家列表和当前维度实体表中的记录会被清除，不再出现在列表、F8 HUD 或传送入口中。
- AI 使用独立的原版 `PlayerAdvancements` 进度存档；可用 `/aiplayer identity <名称>` 和
  `/aiplayer advancements <名称>` 查询身份与已完成进度。
- 每个 AI 可独立开启自动连续决策，间隔可设为 5 秒至 1 小时；设置随身份存档恢复。
- 自动决策沿用模式提示词、天眼快照和 `say`、`move`、`wait` 白名单，不执行任意命令。
- 可创建持久化多 AI 协作组；成员共享任务、提案、严格多数票共识和领队选举。
- 协作组的领队、任务与最近通过的共识会加入成员的后续手动或自动决策观察信息。
- 玩家可开启由 1～16 名已登记 AI 观看的直播会话；AI 依据服务器提供的维度、位置、生命、饥饿和主手物品生成事实约束弹幕。
- 直播会话与累计弹幕数会持久化；弹幕间隔限制为 10～600 秒，每名玩家只允许一个并发请求，玩家离线时不会调用 API。

### AI 竞技场

- 通过 `/ai battle` 运行只包含已登记 AI 玩家的服务器竞技比赛。
- 支持 `1v1`、`2v2` 和 3～8 名 AI 的混战；2v2 的前两名与后两名分别组成一队。
- 参赛 AI 会使用铁剑和盾牌，根据距离与生命值选择追击、后撤、格挡、普通攻击或重击。
- 每名 AI 有两次有限治疗机会；低生命值时可搭建最多两格高的临时圆石掩体。
- 比赛自动判断淘汰、队伍胜利和 10 分钟超时平局，可随时查询状态或由管理员安全停止。
- 开始前会保存参赛 AI 的位置、生命值、主副手和无敌状态；结束时全部恢复，临时掩体也会还原为原方块。

### AI 助手球

- `/aiplayer orb chat <消息>` 使用现有 OpenAI 兼容 API 与玩家私聊，不控制世界实体，也不会执行命令。
- `/aiplayer orb remind <分钟> <内容>` 创建 1 分钟至 7 天的提醒；支持查看与按编号取消。
- 每名玩家可保存最多 128 个命名坐标，并可覆盖同名坐标、列出或删除。
- `/aiplayer orb explore` 显示当前维度和位置，以及同维度最近的三个已保存坐标与距离。
- 坐标和待触发提醒按玩家 UUID 持久化；离线期间到期的提醒会保留到玩家再次在线。
- API 回复、坐标名称、提醒数量和文本长度均有上限，客户端不会获得服务器 API 令牌。

### AI 摄影与相册

- 新增正式注册物品 `ai_companion:camera`（AI 相机），可在工具与实用物品标签取得，也可合成。
- 手持相机右键会在服务器端创建照片条目，记录维度、XYZ、视角、拍摄时间、天气和脚下方块。
- 每位玩家拥有按 UUID 隔离的持久化相册，最多保存 256 张；支持分页列表、详情、说明和删除。
- `/aiplayer album review <编号>` 将照片元数据、场景摘要和玩家说明交给 AI 评价。
- AI 被明确禁止声称看见没有提供的像素细节；同时每位玩家最多只有一个并发评价请求。
- 相机拍摄有 1 秒服务端冷却。照片条目不会存储客户端屏幕像素或自动写出 PNG 文件。

### 旅行日志与冒险图鉴

- 玩家进入新的群系、维度、村庄或世界结构时，服务器会自动写入按 UUID 隔离的旅行日志。
- 遗迹类结构与其他特殊结构会分开归类；同一地点不会因反复经过而重复记录。
- 每条记录包含地点名称、维度、XYZ 和首次发现时间，每位玩家最多保存 512 条。
- `/aiplayer travel list [页码]`、`show` 和 `stats` 可浏览冒险图鉴。
- 可把同维度且在地点 256 格范围内拍摄的相册照片关联到日志，形成带照片编号的旅行条目。

### Minecraft 日报

- 服务器自动收集玩家上线/离线与跨维度、天气变化与 Minecraft 日期、AI 创建/移除/模式变化及竞技场开始/结束事件。
- 事件严格分为玩家事件、世界事件和 AI 事件；服务器自然日期变更时自动归档一期结构化日报。
- `/aiplayer news today` 可生成或刷新今日版，`list` 与 `show` 可浏览最近 64 期存档。
- `/aiplayer news ai <日报编号>` 使用现有 OpenAI 兼容 API 对已记录事实进行编辑，并把 AI 版保存到同一期日报。
- AI 只能使用服务器提供的事件材料；没有材料的栏目会明确显示“暂无记录”，不会虚构玩家或世界事件。

### 家具与 AI 休闲互动

- 新增可合成、可放置的沙发、电视、电脑和台灯，全部加入“功能方块”创造模式标签。
- 四种家具使用独立方块模型；台灯提供 12 级照明，关闭模组后不会改写原版方块。
- `/aiplayer furniture sit <AI名>` 会让同维度 AI 坐到命令玩家附近 8 格内最近的沙发；竞技场中的 AI 不可入座。
- 入座时清除剩余移动并保持蹲坐姿态；`stand` 起身，`chat` 在家具区进行一次受白名单执行器保护的 AI 对话。

### AI 音乐合奏

- 玩家可邀请 1～8 名已登记 AI 组成临时乐队；AI 必须与玩家同维度、距离不超过 64 格且未参加竞技场。
- 合奏开始后，玩家左键敲击音符盒，AI 会在各自位置挥手并用原版竖琴音色跟随演奏。
- 内置和声、回声、低音三种编排；音高始终折叠在原版音符盒的 0～24 范围内。
- 每位 AI 同一时间只参加一场合奏，队列和成员数均有上限；AI 离开维度或距离过远时跳过该音符。
- 5 分钟没有新音符时会自动结束；合奏只保存于当前服务器会话，不改写 AI 的普通任务或装备。

```text
/aiplayer music start MusicAI,HelperAI harmony
/aiplayer music style echo
/aiplayer music status
/aiplayer music stop
```

### AI 宠物竞技

- 每位玩家可创建最多 8 只持久化竞技宠物，并分配速度、力量和耐力三项初始属性。
- 初始属性单项为 10～100、总和不超过 180；训练每 30 秒可将一项属性提高 1 点，最高 100。
- 竞速以速度为主、耐力为辅；战斗以力量为主、耐力为辅，并保留受控随机变化和耐力决胜规则。
- 比赛由服务器结算，永久记录胜场、负场、参赛次数、训练次数与综合评分。
- 宠物名称全服唯一；训练受所有权保护，排行榜最多显示综合评分最高的 10 只宠物。

```text
/aiplayer pet create Swift 80 40 60
/aiplayer pet train Swift speed
/aiplayer pet race Swift Tank
/aiplayer pet battle Swift Tank
/aiplayer pet list
/aiplayer pet leaderboard
```

### AI 模拟社会

- 已登记的 AI 可加入最多 128 名居民的持久化模拟社会，并拥有独立住宅、工作、余额、精力、声望和关系网。
- 住宅保存维度与 XYZ；职业包括农夫、建筑师、矿工、商人、守卫和艺术家，不同职业有固定工资。
- 工作需要住宅、职业和至少 15 点精力，每 60 秒最多结算一次；收入、精力和声望由服务器统一修改。
- 两名 AI 社交时会双向提高关系值并消耗少量精力；关系限制在 -100～100，不能与自己社交。
- 管理员可执行受余额保护的信用点交易；社会排行榜按余额、声望和名称稳定排序。
- 全部资料以独立 JSON 原子保存，不包含 API 令牌，也不修改 AI 的竞技场装备、身份或普通任务。

```text
/aiplayer society enroll BuilderAI
/aiplayer society home BuilderAI
/aiplayer society job BuilderAI builder
/aiplayer society work BuilderAI
/aiplayer society rest BuilderAI
/aiplayer society socialize BuilderAI FarmerAI
/aiplayer society trade BuilderAI FarmerAI 10
/aiplayer society status BuilderAI
/aiplayer society leaderboard
```

### 世界天气事件

- 服务器一次只运行一个持久化自然事件：极光、流星雨、沙尘暴或增强雷暴。
- 极光与流星雨只在主世界夜晚生效，日出自动结束；沙尘暴只影响沙漠群系内玩家。
- 流星雨会在玩家附近掉落正式注册的 `ai_companion:star_shard` 星辰碎片；碎片可转换为原版紫水晶碎片。
- 星辰碎片加入通用 `c:star_shards` 标签；1.20.1 Forge 版同时提供旧版 `forge:star_shards` 标签。
- 增强雷暴驱动原版雷雨并增加电火花表现；事件效果不会在其他维度错误施加。
- 无事件时服务器会以低概率自然生成 5～10 分钟事件；管理员也可手动启动 1～30 分钟事件。
- 0.8.6 起可查询最近 32 次事件、下一次自动检查与当前候选事件；每名玩家可独立关闭事件通知。
- 0.8.7 起可用 `/aiplayer weather stats [事件类型]` 汇总最近 32 次事件的自然/管理员次数与计划总时长。
- 0.8.8 起四类自然事件拥有独立的 0～1000 自动生成权重；权重 0 会关闭该类型的自然生成，但管理员仍可手动启动。
- 0.8.9 起自动事件默认有 30 分钟持久化冷却；存在多个合格候选时不会连续自然生成同一种事件。
- 0.9.0 起管理员可创建未来 7 天内的持久化事件日程；到期后服务器在合法时段自动执行，最多保存 32 个预约。
- 管理员可持久化修改自动生成开关、30～3600 秒检查间隔、1/1～1/10000 单次概率和 1～30 分钟时长范围。

```text
/aiplayer weather status
/aiplayer weather start aurora 5
/aiplayer weather start meteor 5
/aiplayer weather start sandstorm 5
/aiplayer weather start thunder 5
/aiplayer weather stop
/aiplayer weather forecast
/aiplayer weather history [数量]
/aiplayer weather stats [aurora|meteor|sandstorm|thunder]
/aiplayer weather notify <on|off>
/aiplayer weather config status
/aiplayer weather config enabled <on|off>
/aiplayer weather config interval <秒>
/aiplayer weather config chance <概率分母>
/aiplayer weather config duration <最短分钟> <最长分钟>
/aiplayer weather config weight <aurora|meteor|sandstorm|thunder> <0-1000>
/aiplayer weather config cooldown <0-1440分钟>
/aiplayer weather schedule add <aurora|meteor|sandstorm|thunder> <延迟分钟> <持续分钟>
/aiplayer weather schedule list
/aiplayer weather schedule cancel <编号>
```

### AI 女仆

- 在统一设置的“AI系统 → AI 女仆”中输入名字，选择 7 个内置皮肤之一并召唤。
- 支持用系统文件选择器导入本地 64×64 / 128×128 皮肤和自定义披风；本地文件不会上传到仓库。
- 可点击 LittleSkin 按钮打开皮肤站；女仆接受文字 AI 指令，并在头顶显示当前心情。
- 女仆提示词绑定召唤者 UUID 与名称，聊天内容不能冒充主人或绕过所有权规则。
- 所有者可把女仆收回为带身份数据的背包物品、从背包重新召唤，或把所有权转让给在线玩家。
- 可选 Simple Voice Chat 检测不改变模组的必需依赖；语音识别提供者产生的转写可经专用通道发送给女仆。
- 第三版成长系统从 0 级、20 点最大生命开始；执行安全工作会积累工作经验，所有者可主动选择工作经验或玩家经验升级，每级增加 2 点最大生命。
- 玩家等级费用按前 N 级的实际经验点收取（例如前 4 级为 40 点），不会再把当前显示等级直接减去 N。
- 女仆生物背包默认解锁两排（18 格），每级再解锁 2 格；标准 9×6 箱子容器使常见一键整理模组可以按普通容器兼容。
- UI 上方标题标明“生物背包”，并注明下方是“玩家背包”；另有两个外部背包槽，最多背上两个背包。
- 每个已装备的有效背包额外解锁 9 个生物背包格。兼容检测支持 `c:backpacks`、`fabric:backpacks`、常见背包模组命名空间和 backpack/satchel 物品 ID，不要求安装特定模组。
- 外部背包物品及其数据组件会完整持久化；其他模组背包内部私有槽位仍由对应模组管理，本模组不会复制或改写其内部物品。

### 双 UI 配置

同时按 `V+B` 打开配置界面；组合键可在模组界面中修改，不注册到原版“控制”列表。

EclipseUI 模式提供分类卡片、图标、开关、滑块、下拉菜单和说明文字；未安装 EclipseUI
时由 Cloth Config 依赖启用同一套完整九分类管理中心，不再先显示只有摘要、需要返回才能操作的中间页。
两个 1.20.1 兼容版使用各自的旧版原生九分类管理界面，不要求安装 26.2 的 UI 库。

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
方块扫雷、2048 与 AI 猜拳；“性能优化”现已提供客户端附加渲染距离控制。

### 小游戏中心

`0.5.9` 已完成设计文档中的五个小游戏。打开完整管理中心并选择“小游戏中心”即可游玩。

#### 贪吃蛇

- 24×18 像素棋盘，可用方向键或 `WASD` 控制。
- 苹果、金苹果和钻石分别提供 1、3、5 分，分数越高移动越快。
- 自动保存最高分；10 分解锁金苹果蛇皮肤，25 分解锁钻石蛇皮肤。
- 单局达到 30 分解锁“贪吃蛇大师”称号。
- 空格或 `P` 暂停，`R` 重新开始；小游戏不会暂停整个世界。

#### Minecraft 俄罗斯方块

- 标准 10×20 棋盘、七袋随机、七种方块、旋转、软降、硬降、幽灵落点、消行、分数与等级。
- 自动保存最高分、最佳消行和累计消行数。
- 消除至少一行并正常结束后，由服务器按奖励分数和层级权重发放真实物品。
- 奖励使用服务器会话、合理用时检查、重复提交保护和 60 秒冷却；服务器直接把真实物品放入玩家物品栏，背包已满时掉落在玩家身边。
- 内部结算命令静默执行，结果只显示在动作栏，不向聊天栏发送指令反馈。
- 方向键或 `A/D` 移动，`↑/W/X` 旋转，`↓/S` 软降，空格硬降，`P` 暂停，`R` 重开。

#### Minecraft 方块扫雷

- 初级 9×9/10、中级 16×16/40、专家 30×16/99 三档经典棋盘；第一次翻开的 3×3 区域不会生成 TNT。
- 左键翻开、右键插旗；也可用 `F` 或界面按钮切换翻开/插旗操作。
- 空白区域会自动展开；已翻开的数字在周围旗帜数量正确时支持连开。
- 按难度分别保存最佳时间，并自动保存总胜场、当前连胜与最佳连胜。
- 正常获胜后由服务器按完成用时换算奖励分数并加权发放，带会话、用时、重复提交和 60 秒冷却校验；真实物品进入玩家物品栏，背包已满时掉落在玩家身边。
- 内部结算命令静默执行，结果只显示在动作栏，不向聊天栏发送指令反馈。

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

### 望远镜生物发光

- 0.9.3 删除 F3+B 增强及相关渲染 Mixin，F3+B 完全恢复原版碰撞箱行为。
- 玩家连续使用原版望远镜 1 秒后，范围内生物获得原版光灵箭的发光效果。
- 默认半径 10 区块、持续 120 秒、默认开启；每次举镜只触发一次，放下后重新计时。
- 完整 UI 可修改开关、半径（1～32 区块）、观察时间（1～10 秒）和持续时间（1～600 秒）。
- 设置按玩家 UUID 在服务器持久化；命令入口为 `/aiplayer spyglass status`。
- 0.9.4 新增“全部生物 / 仅非玩家生物 / 仅敌对生物”三种目标条件；默认仍为全部生物。
- 0.9.5 新增每位玩家独立的触发冷却，默认 10 秒、范围 1～600 秒；冷却期间再次举镜会显示剩余秒数。
- 0.9.6 新增单次命中上限，默认 256、范围 1～1024；超出上限时按与观察者的距离优先标记最近目标。

### 平滑屏幕缩放

“客户端增强”提供默认关闭的屏幕缩放。开启后按住默认 `C` 键即可缩放，松开后恢复原视野。

- 缩放直接调整当前客户端相机的最终视野，不改写原版 FOV 设置。
- 快捷键、1.5～12 倍缩放倍率和 0～1 秒过渡时间均可在双 UI 中调整。
- 只在已进入世界且没有打开其他界面时响应，聊天、菜单和断开连接时会平滑复原。
- 插值按真实帧间隔计算，不依赖服务器 Tick 速度，也不会向服务器发送数据。

### 客户端附加渲染优化

“性能优化”提供默认关闭的附加渲染距离控制，只影响本模组增加的渲染工作。

- 限制任意装备模式的非护甲 3D 物品模型处理距离。
- 固定模式始终使用设置的最大距离；自适应模式以 60 帧采样周期逐级调整距离。
- 帧率低于目标 5 FPS 时每次缩短 8 格；高于目标 10 FPS 时每次恢复 8 格，形成滞回区间以避免频繁抖动。
- 目标帧率可设为 30～240，最大距离可设为 16～256 格，最小距离不会超过最大值。
- 不修改原版区块、实体、粒子或服务器模拟距离；关闭后立即恢复原有渲染行为。

### 金矛突进

- 默认开启，金矛无需附魔即可获得二级突进的水平冲量。
- 默认每 15 次消耗 1 点耐久，每 30 次消耗 2 点饥饿值。
- 创造模式不消耗耐久或饥饿。
- 金矛从原版突进附魔支持标签中排除；其他材质的矛仍需正常附魔。
- 开关、消耗间隔、饥饿点数和冲量强度均可在 UI 中修改。

### 任意物品装备与物品栏洗牌

- “游戏增强”中的任意物品装备模式默认关闭；开启后，木镐、工作台等任意物品都可放入四个装备槽。
- 非护甲物品会使用原版物品模型渲染，并随头部、胸部、腿部或脚部动作移动；原版护甲和可穿戴物仍沿用原版渲染。
- 模式开关旁提供“打乱非快捷栏物品”按钮，随机打乱背包栏、四个装备槽和副手，九格快捷栏保持不变。
- 装备槽仍保持单件容量；洗牌过程不会吞掉、复制或超量堆叠物品。

### 分级小游戏奖励

- 俄罗斯方块分数和扫雷完成用时会换算为奖励分数；高品质层级需要更高分数。
- 奖励池依次包含普通材料/杂物/木制品、煤炭、铜、红石、铁与金、青金石、附魔类、钻石和下界合金。
- 支持粗矿、锭、粒、工具、雪球、鸡蛋、末影珍珠、风弹、经验瓶，以及等级 1～255 的附魔书。
- 同层级价值越高的物品权重越低；实际物品由服务器放入背包，背包满时掉落在玩家身边。
- 发奖后会在聊天中公告：获奖玩家看到“你已获得…奖励”，其他玩家看到获奖者名称。

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
/aiplayer positions
/aiplayer hunt <AI名> <玩家名>
/aiplayer team <AI名> <玩家名>
/aiplayer coach <AI名> <玩家名>
/aiplayer idle <AI名>
/aiplayer eye <AI名>
/aiplayer ask <AI名> <任务>
/aiplayer automatic status [AI名]
/aiplayer automatic enable <AI名> [间隔秒]
/aiplayer automatic disable <AI名>
/aicoop status [协作组ID]
/aicoop create <协作组ID> <AI一,AI二[,更多AI]>
/aicoop task <协作组ID> <共享任务>
/aicoop propose <协作组ID> <AI名> <提案>
/aicoop vote <协作组ID> <提案编号> <AI名> <true|false>
/aicoop leader-vote <协作组ID> <投票AI> <候选AI>
/aicoop remove <协作组ID>
/aiplayer prompt list
/aiplayer prompt assign <AI名> <预设ID>
/aiplayer feature status
/aiplayer shuffle-inventory
/aiplayer minigame start tetris <会话ID>
/aiplayer minigame finish tetris <会话ID> <分数> <消行数>
/aiplayer minigame start minesweeper <会话ID>
/aiplayer minigame finish minesweeper <会话ID> <用时Tick>
/ai battle
/ai battle 1v1 <AI一> <AI二>
/ai battle 2v2 <队伍一AI一> <队伍一AI二> <队伍二AI一> <队伍二AI二>
/ai battle free-for-all <AI一> <AI二> <AI三> [更多AI]
/ai battle status
/ai battle stop
/aiplayer orb chat <消息>
/aiplayer orb explore
/aiplayer orb waypoint save <名称>
/aiplayer orb waypoint list
/aiplayer orb waypoint remove <名称>
/aiplayer orb remind <1-10080分钟> <内容>
/aiplayer orb reminders list
/aiplayer orb reminders cancel <编号>
/aiplayer album list [页码]
/aiplayer album show <照片编号>
/aiplayer album caption <照片编号> <说明>
/aiplayer album review <照片编号>
/aiplayer album delete <照片编号>
/aiplayer travel list [页码]
/aiplayer travel show <日志编号>
/aiplayer travel stats
/aiplayer travel photo link <日志编号> <照片编号>
/aiplayer travel photo unlink <日志编号>
/aiplayer news today
/aiplayer news list [页码]
/aiplayer news show <日报编号>
/aiplayer news ai <日报编号>
/aiplayer live start <AI名[,更多AI]> [间隔秒]
/aiplayer live status
/aiplayer live interval <10-600秒>
/aiplayer live stop
/aiplayer furniture sit <AI名>
/aiplayer furniture stand <AI名>
/aiplayer furniture chat <AI名> <消息>
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

- `windowsdepcs-ai-companion-mc26.2-fabric-0.8.2.jar`：26.2 Fabric 正式模组。
- `windowsdepcs-ai-companion-mc26.2-fabric-0.8.2-sources.jar`：本地源码包。
- `windowsdepcs-ai-companion-mc26.2-fabric-0.8.2-javadoc.jar`：本地 Java API 文档包。

普通玩家只安装第一个正式模组 JAR；不要把 sources 或 Javadoc JAR 放进 `mods/`。
从 v0.8.0 起，GitHub Release 同时附加三个平台的正式模组 JAR，不上传 Sources 或 Javadoc JAR。

## 许可证

本项目使用 [MIT License](LICENSE)。
