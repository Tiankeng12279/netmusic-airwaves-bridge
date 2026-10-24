# NetMusic Airwaves Bridge

让 **NetMusic** 的网易云音乐 CD 通过 **Create: Airwaves** 的发射机在游戏世界里广播的 NeoForge 附属模组。

- **MC 版本**：1.21.1　**加载器**：NeoForge 21.1.x　**Java**：21
- **硬依赖**：`netmusic`（1.5.2+）、`airwaves`（0.1.x）、`create`（6.0.10+）
- **当前版本**：0.16.1（`build/libs/netmusic-airwaves-0.16.1+mc1.21.1.jar`）

## 模型与方块状态

两种方块的模型由 blockstate 属性驱动，属性由方块实体在播放状态/唱片数量变化、世界加载时自动同步：

| 属性 | 取值 | 唱片播放器 | 列表唱片播放器 |
|---|---|---|---|
| `facing` | north/east/south/west | ✓（blockstate 里做 y 旋转） | ✓（同左） |
| `play_state` | `none`（空机）/ `idle`（有片未播）/ `playing`（播放中）/ `paused`（已暂停） | ✓ | ✓ |
| `fill` | `empty`（0 张）/ `quarter`（1–4）/ `half`（5–8）/ `three_quarters`（9–12）/ `full`（13–16） | — | ✓ |

**唱片播放器**已接入“电台”模型组（C 组，4 个模型），用 `variants`：`facing × play_state` 共 16 条：
`models/block/netmusic_radio_modulator.json`（none）及 `_idle` / `_playing` / `_paused`，图集 `textures/block/device_atlas.png`，物品模型显示 none 态。

**列表唱片播放器**已接入“唱片机（带顶部唱片架）”模型组（A 组，12 个模型 = 3 状态 × 4 档架上唱片数），同样用 `variants`（`facing × play_state × fill` 共 80 条）：

| 方块状态 | → 模型 | 说明 |
|---|---|---|
| `play_state=none` | `music_player/closed_<n>` | 合盖关机（防尘盖盖上） |
| `play_state=idle` | `music_player/idle_<n>` | 开盖待机（空转盘、唱臂归位） |
| `play_state=playing` | `music_player/playing_<n>` | 播放中（唱片上转盘、唱臂 45°） |
| `play_state=paused` | `music_player/idle_<n>` | 暂停复用开盖待机（A 组无暂停态） |
| `fill=empty / quarter / half / three_quarters / full` | `<n>` = 0 / 1 / 3 / 5 / 5 | 顶部唱片架上的唱片数，13+ 张封顶 5 张显示 |

- 模型：`assets/netmusic_airwaves/models/block/music_player/<state>_<n>.json`（已内置 `render_type: cutout`，转盘/唱片圆边裁切正常）
- 图集：`textures/block/music_player_atlas.png`（64×128，1 px = 1 模型单位）
- 物品模型显示 `closed_0`（合盖无唱片）

方块正面**没有任何程序化渲染**（世界内频谱柱已移除，BER 只画 Airwaves 线缆），外观完全由上述模型决定；实时频谱只存在于列表唱片播放器的 GUI 面板中。A 组的已知限制：转盘/唱片静态不旋转、架上唱片为 5 种固定脊背配色、无独立暂停造型。

## 玩法概览

两种唱片播放器方块，共用同一套方块实体与全部播放逻辑，区别在交互形态与外观：

| | NetMusic 唱片播放器 | NetMusic 列表唱片播放器 |
|---|---|---|
| 注册 ID | `netmusic_airwaves:netmusic_radio_modulator` | `netmusic_airwaves:netmusic_spectrum_monitor` |
| 交互 | 无 GUI：手持 CD 右键载入、空手右键弹出、**潜行+空手右键**切换移位模式 | 完整 GUI：16 槽播放列表 + 背包拖放 + 播放模式/方向/移位切换 + 传输控制（含下一首）+ 歌词进度 + 频谱面板 |
| 外观 | 电台机身（play_state 驱动，C 组模型） | 木质唱机 + 顶部唱片架（play_state × fill 驱动，A 组模型） |
| 合成 | 紫水晶 + 铜锭 + Airwaves 铜线 + 音符盒 | 唱片播放器 + 铜锭 + 玻璃 + 红石（升级合成） |

两者都：用 Airwaves 线缆连接发射机后广播（频率/功率/信号质量/天气衰减全部由 Airwaves 负责）、红石脉冲切换播放/停止、输出比较器信号（播放 15 / 有唱片 7）、可作为 Create 显示链路数据源（歌名/歌手/状态/进度/线缆质量）。

## 播放逻辑（两种方块通用）

### 播放模式（GUI 左侧按钮循环切换，持久化）
- **顺序**：播完按方向（正序/逆序，GUI 可切）播下一个有唱片的栏位
- **随机**：从**其余 15 个栏位**随机选一张（不会连续重复；仅剩一张则停止）
- **单曲循环**：反复播放当前张

### 下一首按钮（⏭）
按当前模式**直接切到下一张**：随机→随机换一张；顺序/循环→按栏位顺序换（末尾绕回，循环模式按按钮不重播当前张）；移位模式→执行移位逻辑。

### 移位弹出模式（流水线）
GUI 里“模式: 普通/移位”切换；无 GUI 的唱片播放器用潜行+空手右键切换。

- **栏位角色**：正序时第 1 栏=输入栏（立即播放）、第 16 栏=输出栏（可被漏斗抽走）、中间栏锁定；逆序时输入/输出对调
- **顺序推进**：一张播完 → 整列向输出端移位一位（播完的旧片进输出栏、输入栏空出）→ 输入栏新片接着播
- **随机推进**：播放的是**中栏**随机一张；播完这张直接进输出栏，输入栏唱片补到它空出的位置，再随机选下一张；中栏空而输入有片时自动把输入片拉进中栏起播
- **背压**：输出栏被占时暂停等待，漏斗取走后自动恢复推进
- **锁定范围**：只锁**漏斗等自动化容器**（只能从输入栏进、输出栏出）；GUI 中玩家可自由取放任意栏位、点曲目行选播任意位置

配合上方漏斗持续投片、下方漏斗抽取，即可不受 16 栏限制连续播放任意多张 CD。

## 音频链路

```
NetMusic CD (SongInfo)
  → MusicPlayResolverManager.resolve()        # 复用 NetMusic 的 URL 解析（含第三方 VIP 解析器，不做绕过）
  → SafeAudioDownloader                       # 服务端代下载（大小/时长/协议/主机白名单校验，防 SSRF）
  → AudioDecoder                              # JavaSound 统一转 16 kHz / 16 bit / 单声道 PCM
  → μ-law 编码（800 样本 = 50 ms/帧）
  → 每服务器 tick 一帧 AirwavesApi.broadcast(level, transmitterId, sourceId, frame, gain)
  → Airwaves 接收机 / 便携电台 / 音箱出声
```

- 播放期间服务端每 0.5 s 对当前播放位置做一次 **2048 点 FFT**（汉宁窗、对数 16 频段、RMS 静音门限 + 响度衰减），随快照广播给 32 格内玩家；客户端用“快升慢降”平滑后渲染到列表唱片播放器的 GUI 频谱面板
- 频谱上限 8 kHz：广播音频为 16 kHz 采样，奈奎斯特限制，界面刻度按实际标注 `20 HZ – 8 KHZ`
- 解码在后台线程完成，主线程只做取帧；切歌/弹片/拆方块/关服都会终止解码并释放连接

## 网络协议（版本 "4"，两端需同版本）

| Payload | 方向 | 用途 |
|---|---|---|
| `DeckSnapshot` | S→C | 状态/当前槽/进度/标志位/线缆质量/电台信息/16 曲目信息/**16 段频谱**；状态变化时 + 播放中每 10 tick 广播给 32 格内玩家 |
| `RequestDeckSnapshot` | C→S | 界面打开后定期拉取（≤32 格） |
| `DeckActionPayload` | C→S | 传输控制与设置：PAUSE/PLAY/STOP/NEXT/SELECT/EJECT/SET_PAGE/SET_MODE/SET_DIRECTION/SET_PLAY_MODE（≤8 格） |

`DeckSnapshot.flags()` 位定义：`CABLE=1, TRANSMITTER=2, ON_AIR=4, CAROUSEL=8, REVERSE=16, SHUFFLE=32, LOOP=64`。

菜单：`netmusic_spectrum_deck`（`DeckMenu` 基类 + `SpectrumDeckMenu`），16 个 CD 槽按 4/页 激活（`Slot.isActive()`），翻页通过 `ACTION_SET_PAGE` 同步服务端；GUI 槽位重写 `mayPlace/mayPickup/remove` 以绕过移位锁（仅手动）。

## 目录结构

```
src/main/java/com/netmusicairwaves/
├── NetMusicAirwaves.java        # 主类：注册总线、能力（ItemHandler）、创造栏、关服清理
├── Config.java                  # 配置
├── audio/
│   ├── RadioAudioStream.java    # 后台线程：解析→下载→解码→μ-law 帧
│   ├── AudioDecoder.java        # JavaSound → 16 kHz 单声道 → μ-law (800 样本/帧)
│   ├── MuLawCodec.java          # μ-law 编/解码（G.711 逆公式，已离线验证）
│   ├── SpectrumAnalyzer.java    # 纯 Java FFT 频段分析（可脱离 MC 单测）
│   ├── SafeAudioDownloader.java # 安全下载（协议/端口/重定向/内网拒绝/大小限制）
│   └── UrlPolicy.java           # 主机白名单匹配
├── content/
│   ├── RadioModulatorBlock.java       # 基础唱片播放器方块（无 GUI 交互、线缆/红石/掉落/比较器、play_state 属性）
│   ├── SpectrumMonitorBlock.java      # 列表唱片播放器（继承上方，覆写为打开 GUI、整块外形、fill 属性）
│   └── RadioModulatorBlockEntity.java # 核心：物品栏/播放状态机/移位模式/频谱计算/方块状态同步/MenuProvider/CableNode
├── menu/
│   ├── DeckMenu.java             # 16 CD 槽 + 玩家背包，分页激活，quickMove，手动旁路锁
│   └── SpectrumDeckMenu.java     # 同布局，背包下移适配高频谱面板
├── client/
│   ├── SpectrumDeckScreen.java   # 浅色频谱仪风格 GUI（列表/频谱/歌词/传输/模式按钮/背包）
│   ├── SpectrumAnimation.java    # 频谱平滑（attack 14/s，decay 5/s，16→N 重采样）
│   ├── RadioDeckRenderer.java    # BER：Airwaves 线缆渲染
│   ├── DeckClientData.java / DeckClientHooks.java / ClientSetup.java
├── network/                      # 上述三个 payload + DeckNetwork 注册/分发
├── registry/                     # 方块/物品/方块实体/菜单注册
└── compat/create/                # Create 显示链路数据源
```

资源：`assets/netmusic_airwaves/`（模型/方块状态/语言 中英 80 键对齐），`data/netmusic_airwaves/`（配方/战利品表/挖掘标签）。

## 配置（`config/netmusic_airwaves-common.toml`）

| 项 | 默认 | 说明 |
|---|---|---|
| `broadcast.broadcastGain` | 0.8 | 传给 Airwaves 的音量（距离/信号/天气衰减仍由 Airwaves 计算） |
| `broadcast.maxTrackSeconds` | 900 | 单张 CD 最长解码/广播秒数 |
| `network.allowHttp` | true | 允许 HTTP 音源（网易云 CDN 重定向目前走 HTTP） |
| `network.maxDownloadMb` | 64 | 单曲下载大小上限（MiB） |
| `network.allowedHosts` | 网易云域名族 | 音频主机白名单，`*` 全放行，`*.music.126.net` 匹配子域 |

## 构建

```bash
# 需要 JDK 21；依赖 jar 需自行放入 libs/：
#   airwaves-1.21.1-0.1.1-zh_cn.jar / netmusic-1.5.2-neoforge+mc1.21.1-all.jar
#   create-1.21.1-6.0.10-280-slim.jar / ponder-neoforge-1.0.82+mc1.21.1.jar
#   flywheel-neoforge-1.21.1-1.0.6-41.jar
export JAVA_HOME=<jdk-21>
./gradlew.bat build
# 产物：build/libs/netmusic-airwaves-<version>+mc1.21.1.jar（含 sources jar）
```

> 本仓库**不包含** `libs/*.jar`（第三方模组，按各自许可证分发），需自行获取后放入 `libs/`。

专用服务端快速冒烟：把 `libs/` 依赖与 sable/aeronautics 系 jar 放入 `run/mods`，`echo "eula=true" > run/eula.txt` 后 `./gradlew.bat runServer`。

## 已验证

- μ-law 编解码往返、FFT 频段定位（440 Hz / 4 kHz 合成音准确落入对应频段、静音归零）——独立 Java 程序离线验证
- 移位弹出状态机（FIFO 顺序、输出背压阻塞与恢复、逆序对调、空穴移位、随机模式出料顺序）——与方块实体逻辑逐行对应的仿真验证
- 专用服务端加载冒烟（依赖齐全，无本模组报错）

## 注意事项

- Airwaves 已下架，编译锁定第三方归档 `airwaves-1.21.1-0.1.1-zh_cn.jar`，只调用其公开 API，不打包不修改
- VIP 歌曲依赖 NetMusic 可用的链接解析器；解析失败会在方块附近提示并停止，**不实现任何 VIP 绕过**
- 音质上限即 Airwaves 的 16 kHz μ-law（约调频广播质感）
- 协议版本变更时（`DeckNetwork.register` 的 registrar 版本号）客户端/服务端需使用同一版本
