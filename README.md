# FireworkShow

Java 25 / Paper 26.2 粒子烟花演出插件。使用命名图片、Track 和 Clip 编排并发动画，通过独立 ParticleLib 插件播放龙卷风。仅支持当前 `images` / `tracks` / `clips` 格式。

## 构建与安装

需要 JDK 25、Maven、Paper 26.2 和独立安装的 [ParticleLib v0.1-alpha](https://github.com/sun-plugins/ParticleLib)。Paper API 固定为 `26.2.build.130-stable`；ParticleLib 依赖 `com.github.sun-mc-dev.ParticleLib:particlelib-core:v0.1-alpha` 使用 provided，不打包进本插件。

```shell
mvn -B clean verify
```

将 `target/FireworkShow-0.2.0-SNAPSHOT.jar` 与 ParticleLib 插件放入服务器 `plugins/`。首次启动释放 config.yml、四份演出示例，以及 heart.png、text.png、mczjulogo.png。**已有文件不会被覆盖**。

升级现有服务器时，先备份配置，再将本项目 `src/main/resources/config.yml` 中需要的新限制合入服务器 config.yml，将最新 `shows/multitrack.yml` 手工同步到 `plugins/FireworkShow/shows/`。确认 `plugins/FireworkShow/images/` 包含最新三张 PNG，执行 `/fws reload`。旧配置中的 512 像素输入限制需要同步调整；替换 Jar 不会覆盖旧演出配置或旧图片。

## 命令

| 命令 | 权限 | 功能 |
|---|---|---|
| `/fws help`、`/fws list` | fireworkshow.use | 帮助、演出列表 |
| `/fws preview <showId>` | fireworkshow.admin | 玩家面前预览 |
| `/fws give <player> <showId> [amount] [rotation=x,y,z]` | fireworkshow.admin | 特殊烟花 |
| `/fws reload` | fireworkshow.admin | 原子重载配置与图片 |
| `/fws stop` | fireworkshow.admin | 停止本人预览 |
| `/fws stopall` | fireworkshow.admin | 停止所有演出 |
| `/fws info` | fireworkshow.admin | 版本、活动数与缓存信息 |

别名 `/fireworkshow`，支持 Tab 补全；use 默认所有人、admin 默认 OP。数量默认 1，范围 1～64；欧拉角默认 `(0,0,0)`，可省略数量直接给 rotation。新烟花 Lore 显示实际 PDC 角度并保留小数，例如 `Rotation: X=90°, Y=0°, Z=15.125°`。普通烟花不受影响；缺失或损坏 rotation 的带标记物品报错并保留普通爆炸行为。

## 图片、轨道与空间

新增 PNG 放入 `plugins/FireworkShow/images/`，在 shows/ 新增 YAML 后 reload，无需重新编译。每个图片阶段必须通过 `image` 引用 `images` 中的资源名称。

```yaml
id: example
images:
  logo:
    file: mczjulogo.png
    width-blocks: 5.0
    max-points: 350
    particle-spacing: 0.12
    alpha-threshold: 16
    dust-size: 0.9
    brightness: 1.4
    glow: {enabled: true, density: 0.03}
tracks:
  logo:
    transform:
      position: {x: 0, y: 7.6, z: 0}
      rotation: {x: 0, y: 0, z: 15}
      scale: 0.75
    clips:
      - {at-tick: 40, duration-ticks: 40, type: reveal, image: logo, easing: ease-out-cubic}
      - {at-tick: 80, duration-ticks: 60, type: hold, image: logo}
      - {at-tick: 140, duration-ticks: 40, type: scatter, image: logo, radius-blocks: 3, fade-out: true}
```

时间单位是游戏 tick，20 TPS 时每秒 20 tick。`at-tick` 相对整场开始；跨轨和同轨 Clip 都可独立起止、重叠。reveal 从中心逐渐形成（initial-scale 默认 0.15），hold 重发完整图案，scatter 沿确定方向扩散并按密度淡出。easing 支持 linear、ease-in-cubic、ease-out-cubic。

右手坐标系；局部欧拉角单位为度，顺序 X→Y→Z，即 Rz·Ry·Rx。变换层级是世界原点/观察方向 → 全局烟花旋转 → Track → Clip → 图片。position 是父级局部偏移。Track 和 Clip 的 scale 默认 1、单级范围 **0.05～20**，只接受有限正数；父子比例相乘，父级旋转和缩放影响子位移，本层 scale 不改变自身 position。

每个 Clip 仅继承 Track，不继承前一 Clip 的 transform。图片形成、保持、扩散共用 Transform3D；dust-size 独立控制，不随空间比例放大。默认 yaw=0 时图片右轴、上轴、朝观察者法线为 -X、+Y、+Z；orientation 默认 face-trigger，根据观察者到原点的水平位置确定朝向；fixed 或无玩家时使用顶层 yaw。`rotation=90,0,0` 可将图案旋入水平面。

完整中文配置说明直接写在 [config.yml](src/main/resources/config.yml) 和各示例中：[heart](src/main/resources/shows/heart.yml)、[alternate-heart](src/main/resources/shows/alternate-heart.yml)、[multi-image](src/main/resources/shows/multi-image.yml)、[multitrack](src/main/resources/shows/multitrack.yml)。

multitrack 保留龙卷风、左侧爱心和右侧两行文字 MCZJU / MMGA；Logo 在上方中心 `(0,7.6,0)`、scale=0.75，与其他轨道重叠播放。Logo 40～80 tick 形成，80～140 保持，140～180 扩散。这是配置布局，尚未进行游戏内视觉验收。

## 按最终尺寸采样

不再为原始 PNG 的每个像素生成候选粒子。加载配置时先计算：

- 有效宽度 = width-blocks × Track.scale × Clip.scale；高度按 PNG 宽高比计算。
- 网格列/行分别为有效宽/高除以 particle-spacing 后向上取整。旋转不改变点数。
- 每个网格聚合覆盖的原图像素，使用预乘 Alpha 的区域加权 RGB，透明背景不会污染边缘颜色。完全透明区域没有粒子；网格中心透明时，点移到该格内最近的有内容像素。
- 为保留细笔画，alpha-threshold 检查参与聚合的非透明像素平均不透明度，而非包含透明留白的覆盖率。DUST 无真实 alpha，半透明使用确定性的密度模拟。
- 内容点超过 max-points 时确定性稀疏选择，不随每次播放改变图案。粒子实际发送仍受全局预算和观众人数限制。

点云坐标保留图片基础宽度，空间矩阵只应用一次累计缩放。不同 Clip 尺寸在加载时生成不同变体；相同原图、基础宽度、累计比例、间距、阈值、点数上限和 brightness 共享不可变点云。Reveal/Hold/Scatter 尺寸一致时直接共享；dust-size 和 glow 不拆分点云缓存。每 tick 不解码或重采样；形成动画也不会随进度重采样。

| 图片参数 | 默认值 | 范围 / 含义 |
|---|---|---|
| width-blocks | 5 | 0.1～32，缩放前画布宽度，方块 |
| max-points | 全局 700 | 1～全局上限，且受发送预算限制 |
| particle-spacing | 0.12 | 0.02～2，目标世界采样间距，方块 |
| alpha-threshold | 全局 16 | 0～255，完全透明始终排除 |
| dust-size | 全局 0.9 | 0.01～4，DUST 大小 |
| brightness | 1 | 0.1～3，预处理 RGB 增益 |
| glow.enabled | false | 开启额外 GLOW 点缀 |
| glow.density | 0.03 | 0～0.1，floor(采样点数×比例) |

原样 1254×1254 Logo 在有效宽 3.75 格、间距 0.12 时使用 32×32 网格，确定性限为 350 点，三个阶段共享该变体；最多额外 10 个 GLOW 点/观众/发射帧。此点数不是原图像素数量。

## 夜间视觉增强

brightness 在预处理时增亮 RGB，通过限制共同增益避免通道溢出并保留色相；已饱和的颜色不会被强行漂白，纯黑仍为黑，不修改透明度。glow 使用目标 Paper API 的无颜色数据 `Particle.GLOW`，固定稀疏选点，保留主体彩色 DUST。全部 Show 彩色点发送后，剩余预算才发送光点；按观众计费，预算不足省略。

这只是视觉增强，**不是真实世界光照，不保证 DUST 在黑暗中具有全亮材质特性**。不放置 LIGHT 方块、不修改世界、不要求客户端模组。GLOW 在客户端的大小、寿命和漂移仍由游戏决定，需夜间实测。

## 安全、预算与诊断

默认每 2 tick 发射图片，视距 48 格，每 tick 1500 次粒子发送，最多 6 场、每玩家 2 场；所有活动 Show 共计最多 128 个计划 Clip。多观众、多轨道共用预算，满载时可能缺点，应减少点数或并发。

PNG 默认最多 16 MiB、宽高分别 4096、总像素 4194304（支持 2048×2048）；完整解码前读取头部检查，拒绝路径越界、越界符号链接和异常资源。解码工作内存按 8 字节/像素估算，默认上限 64 MiB，最多同时持有一张原图，作用域结束后只留下点云快照。估算不是 JVM 堆峰值保证，图像解码器和点云也有额外开销。

每次重载默认累计解码像素上限 67108864、估算像素访问上限 134217728、单变体网格最多 262144、512 个变体、200000 个缓存点、128 个原图；预处理限时 10000 ms，在阶段和采样行之间检查，不能强行中断单次 I/O/解码。启动和 reload 日志记录耗时、解码次数、缓存数量与估算内存。同步 reload 仍可能短暂阻塞主线程；大量资源应在低峰期加载。

每份 YAML 不超过 64 KiB，每 Show 最多 16 张图片、16 轨、128 Clip，每轨最多 64 Clip，结束时间不超过 24000 tick。任何资源或配置失败都保留旧快照；在播 Show 继续持有原资源。

ParticleLib 为 **ORIGIN_ONLY**：变换发射原点，内部形状不能任意旋转或统一缩放。origin-only 策略会警告；require-full 拒绝不支持的变换。内部大小使用 tornadoHeight、maxTornadoRadius 等库参数。龙卷风采用保守几何预算，其他库算法仅能估算，仍需配置库本身限制。所有停止路径清理各自 EffectHandle；客户端已有粒子可能短暂残留。仅支持常规 Paper 主线程模型。

## 验证

```text
/fws preview multitrack
/fws preview multi-image
/fws give Steve multitrack rotation=90,0,15.125
/fws reload
/fws stopall
```

构建和自动化验证见 [VALIDATION.md](docs/VALIDATION.md)，实现说明见 [ARCHITECTURE.md](docs/ARCHITECTURE.md)。本轮未进行真实 Paper 启动或客户端验收；进入游戏需重点检查 Logo 细线、夜间颜色与 GLOW、左右/上下布局、多观众预算、旋转火箭和停止清理。