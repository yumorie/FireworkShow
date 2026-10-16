# FireworkShow 原始 PNG 像素实验版

Java 25 / Paper 26.2；ParticleLib v0.1-alpha 继续作为独立插件、provided 依赖。仅用于排查 PNG 到三维坐标的映射，完整原始像素输出开销很高。未进行 Paper 运行或客户端视觉验收。

```shell
mvn -B clean verify
```

产物：`target/FireworkShow-0.2.0-SNAPSHOT.jar`。首次启动释放缺失资源，已有文件不覆盖；更新本地服务器时须手工同步需要的 config.yml、shows 和 PNG，再 `/fws reload`。当前使用用户保留的 **512×512 mczjulogo.png**，没有恢复或替换它。

## 原始像素路径

加载/reload 时逐个遍历 BufferedImage：alpha 非零且达到 alpha-threshold 的每一个原始像素生成一个点。保留原始 RGB（brightness 默认 1）、像素中心、宽高比和居中。局部坐标为：

```text
x = ((pixelX + 0.5) / width - 0.5) * widthBlocks
y = (0.5 - (pixelY + 0.5) / height) * widthBlocks * height / width
```

不调用 ImageSampler，不进行网格、LOD、下采样、随机抽取或 max-points 截断。旧 `max-points`、`particle-spacing`、全局 `image.max-points` 和 `image.max-grid-cells` 在实验版忽略，启动/reload 日志明确提示。ImageSampler 源码及其独立测试暂时保留，不是当前播放路径。

缓存键为原图真实路径、基础宽度、Alpha 阈值与 brightness；Track/Clip 缩放、旧间距和旧点数不拆分缓存。统一 Transform3D 仅在渲染时应用一次位置、X→Y→Z 欧拉角与父子缩放，不改变点数。Reveal 只按中心距离逐渐展示，最终不丢像素；Hold 显示所有有效像素；Scatter 使用完整点云并按显式 fade-out 动画淡出。

**DUST 没有真实逐像素 Alpha**：达到阈值的半透明像素也发送一个完整 DUST，不再用随机密度模拟透明度。默认 brightness=1、glow.enabled=false，内置 Logo 也采用这两个值。显式 brightness 或 GLOW 仍可选择，但诊断原图建议关闭增强。

## 安全模式与高风险完整帧开关

默认安全模式先检查整幅图片×当前观众数的完整发送成本。超出预算时拒绝开始演出并返回错误；运行中观众/并发发生变化，则每个图片 Clip 在发射前原子预扣整帧预算，失败时该帧发送 **0 个点**、报错并停止该 Show。不再只画列表前 N 个像素。不同 Show/Clip 共用预算，整帧保证针对单个 Clip 的一次渲染，并不将所有并行图片合成一张事务帧。

管理员可在服务器 `plugins/FireworkShow/config.yml` 明确设置：

```yaml
experimental:
  allow-over-budget-full-frames: true
```

执行 `/fws reload` 后，允许完整图片帧超过普通预算。**高风险：可能长时间卡住服务端主线程、客户端掉帧/卡死、网络拥塞或断线。** 启动/reload 和每次播放均记录强烈警告。它不绕过图片解码、内存、缓存或并发限制；GLOW 仍只使用正常剩余预算，ParticleLib 预算策略保持原样。

关闭方式：将该值设为 `false` 后 `/fws reload`。从 true 切回 false 会停止全部在播演出，撤销旧会话的超预算授权。紧急停止用 `/fws stopall`。开关默认 false，不会因安装实验 Jar 自动开启。

当前 Logo 阈值 16：**73,913 个点**，单观众每完整发射帧 73,913 次、双观众 147,826 次、四观众 295,652 次 DUST 发送。正常预算为每 tick 1,500，均不足。默认每 2 tick 一次，20 TPS 时约 10 发射帧/秒，单观众约 **739,130 次/秒**，不含其他轨道。完全非透明像素（alpha>0）为 82,637；阈值会过滤其中的低 Alpha 边缘。

客户端可能自行限制或丢弃粒子；服务端完整调用不代表客户端必然完整显示，更不证明视觉形状已正确。

## 单独诊断 Logo

建议暂时只播放一个图片的 Hold，避免其他轨道干扰。在服务器 shows/ 新建 `raw-logo.yml`：

```yaml
id: raw-logo
images:
  logo:
    file: mczjulogo.png
    width-blocks: 5
    alpha-threshold: 16
    dust-size: 0.9
    brightness: 1
    glow: {enabled: false}
tracks:
  logo:
    transform:
      position: {x: 0, y: 0, z: 0}
      rotation: {x: 0, y: 0, z: 0}
      scale: 0.75
    clips:
      - {at-tick: 0, duration-ticks: 20, type: hold, image: logo}
```

先保持安全开关关闭、reload、执行 `/fws preview raw-logo`，确认明确超限报错。只有决定承担完整发送成本后才开启开关，再测试预览。原有 multitrack 布局与时间保留，但原始像素模式下所有图片的发送成本都会增加。

## 限制、命令与验证

输入仍校验文件大小、PNG 头部尺寸/像素、路径越界及符号链接。最多保留一张解码原图，不逐 tick 解码。完整点云在分配 Point 前计数；超过 `image.max-cached-points` 时拒绝整张资源，不截断。默认总上限 2,000,000 点，按对象和排序临时空间需要大量堆内存。同步 reload 继续受累计解码、像素访问、缓存条目和时间限制，仍可能短暂阻塞。失败时保留旧快照。

每 Clip 不再建立百万项扩散方向 HashMap；Scatter 方向按稳定种子即时计算，避免每条轨道重复占用巨大内存。大量原始点的排序、渲染、扩散和网络发送仍然昂贵。

命令保留 `/fws list`、`preview <showId>`、`give <player> <showId> [amount] [rotation=x,y,z]`、`reload`、`stop`、`stopall`、`info`，别名 `/fireworkshow`；help/list 使用 fireworkshow.use，其余为 fireworkshow.admin，默认 OP。数量 1～64，旋转默认零；普通烟花行为不变。Track/Clip scale 默认 1、单级范围 0.05～20，局部位移受父变换影响。ParticleLib 仍是 ORIGIN_ONLY，只变换发射原点，内部形状不支持统一旋转/缩放。

本地统计工具（先编译）：`java --class-path target/classes tools/InspectRawPng.java src/main/resources/images/mczjulogo.png`。

详见 [架构](docs/ARCHITECTURE.md)、[验证与修改文件记录](docs/VALIDATION.md)。游戏内待验收：原图方向与宽高比、变换只应用一次、DUST 叠加与客户端粒子上限、真实完整帧性能、停止清理。不得将自动化测试等同于视觉验收。