# Mino++: Classic

在 Minecraft 1.7.10 中还原一个特定的著名卡牌游戏。本项目是 [Mino++: Expanded](https://github.com/) (1.20.1, Architectury) 的 1.7.10 移植版。

- Mod ID：`minopp`，包名：`com.hfstudio.minopp`
- 硬依赖：[GTNHLib](https://github.com/GTNewHorizons/GTNHLib)（配置框架、JSON 方块模型、JvmDowngrader 运行时）

## 与 1.20.1 版本的差异

| 1.20.1 | 1.7.10 |
| --- | --- |
| YetAnotherConfigLib 机器人配置界面 | 原生 GuiScreen（`AutoPlayerScreen`） |
| ForgeConfigSpec 客户端配置 | GTNHLib `@Config`（模组列表中可直接编辑） |
| `Inventory#swapPaint` Mixin | `MouseEvent` 滚轮事件 |
| FOV Mixin / 事件 | `FOVUpdateEvent` |
| BlockState `part` 属性 | 元数据 + GTNHLib 方块状态属性，沿用原版 JSON 模型 |
| 发光（Glowing）效果 | 获胜者周围的粒子效果（1.7.10 无发光效果） |
| 东方 Project 女仆兼容 | 未移植（仅 1.20.1 Forge） |

## 操作备注

默认交互模式（配置 `classicInteraction=false`）：

- 手牌以弧形展开在屏幕底部，轮到自己时所有可出的牌会金边高亮并略微抬起。
- 手持手牌右键进入交互模式：光标指向卡牌即选中，左键 / 空格出牌，A/D（或方向键、滚轮）切换，Q 摸牌 / 跳过，再次右键（或 Esc）恢复自由移动。
- 交互模式左下角有「摸牌/跳过」与「MINO!」按钮。
- 选择万能牌颜色时，桌面牌堆周围会升起四根立体色柱，指向并点击即可选色（也可按 1-4 或点底部按钮）。

经典交互模式（`classicInteraction=true`）：

- 手牌竖向展开在右侧，滚轮选牌；右击牌堆抽牌或表示不出，右击牌桌其他区域出牌。
- 对着牌桌以外的地方右键也可进入交互模式，此时用 W/S 切换。

通用：

- 只能在自己的回合、手牌不超过 2 张时喊 Mino：点「MINO!」按钮（或聊天栏输入 Mino）立即喊出，也可以出牌时按住 Ctrl 一起喊。不在自己回合或手牌更多时喊会被拒绝（不罚牌）；喊过之后再摸牌，喊话失效。
- 不是自己的回合、牌不能出或正在等待其他玩家决定时，不会弹出选色 / 选人界面；界面打开期间局面变化（回合结束、牌局结束等）会自动关闭并提示原因。
- 如果忘记了在出牌同时喊 Mino，可以在聊天栏打出 Mino 补上。
- 要质疑某人没有喊 Mino，直接殴打对方即可。
- 如果牌桌打掉了但还持有绑定到牌桌的手牌，手持右键天空解绑。

## 构建

```
./gradlew build
```

## 牌局设置（牌桌 → 规则与卡牌）

空手右键牌桌打开大厅，`Z` 进入「牌局设置」，`Q`/`E` 切换选项卡，鼠标悬停显示说明：

| 选项卡 | 操作 |
| --- | --- |
| 我的设计 | 本地保存最多 8 套方案（`config/minopp/designs.json`）。左键应用到牌桌，右键用当前设置覆盖，`X`/`Delete` 删除，空位点击保存 |
| 组合包 | 左键：使用该包的预设组合（替换牌组）；右键：单独加入/移除此包，可自由混搭 |
| 当家规则 | 开关类规则点击切换；数值类左键 +1、右键 -1（Shift 为 5） |
| 卡牌 | 左键加入/移除，右键 +1，Shift+右键 -1，Ctrl+点击恢复默认。右上角为勾选，下方为数量；带四色角标的牌按「每色数量 ×4」调整 |
| 牌桌 | 座位数（2-20）、起始手牌、牌组份数（`A` = 自动：超过 4 人每 4 人加一份，并保证手牌和牌堆够用）等 |
| 风格 | 选择你的卡牌主题 |

## 卡牌主题包（纯客户端）

主题来自任意资源包，安装了相同主题的玩家能看到你打出的牌使用你的主题。资源包结构：

```
assets/minopp/themes.json
    { "themes": [ { "id": "my_theme", "name": "My Theme", "fallback": "classic" } ] }
assets/minopp/textures/cards/my_theme/
    frame_<颜色>.png      牌面底（red/yellow/green/blue/pink/teal/orange/purple/wild）
    num_<0-9>.png         数字
    symbol_<牌型>.png     功能牌图案（牌型 id 见 CardTypes）
    flex_<颜色>.png       弹性牌的第二色覆盖层
    mark_power.png        能量数字标记
    back.png / back_dark.png   牌背
```

所有图层均为 32x50，主题未提供的图层会回退到 `fallback` 主题。多个资源包的 `themes.json` 会合并。

## 团队模式

- 在「规则与卡牌」的模式分类中开启「团队模式」，并设置「队伍数量」（2-10）。
- 入座的玩家和机器人自动加入人数最少的队伍；在大厅中左键 / 右键点击座位切换队伍（自己的座位或机器人的座位），按 T 或「自动分队」可按座位交替平均分配。
- 只有每个队伍人数相等（且每队至少一人）时才能开始游戏，大厅顶部会显示各队人数。
- 同队玩家一起获胜；机器人不会把针对性效果用在队友身上。

## 服务端配置（`config/minopp/minopp-server.cfg`）

- `allowPeeking`（默认 `false`）：是否允许偷看他人手牌。开启后，玩家手中卡牌朝向自己的一面会显示牌面，站在其身后即可看到；关闭时服务器不会把其他玩家的牌面（以及牌堆顺序）发送给客户端，即使使用修改过的客户端也看不到。
