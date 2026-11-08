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

- 右击牌堆抽牌或表示不出，右击其他区域出牌。
- 出牌同时按住 Ctrl 以喊 Mino。这个操作应该在手中还有两张牌时进行，因为出牌之后就只剩一张了。
- 如果忘记了在出牌同时喊 Mino，可以在聊天栏打出 Mino 补上。
- 要质疑某人没有喊 Mino，直接殴打对方即可。
- 如果牌桌打掉了但还持有绑定到牌桌的手牌，手持右键天空解绑。

## 构建

```
./gradlew build
```
