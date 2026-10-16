# 架构

命令和烟花事件调用 ShowManager；唯一 Bukkit 主线程任务驱动各 Show 的 TimelineEngine。每个 Clip 独立持有 ShowStep、ShowContext 和库句柄，通过 StepRegistry / StepFactory 创建。PointCloudRenderer 负责所有图片动画的统一发送与空间变换。

## 加载、采样与不可变快照

ShowLoader 只接受命名 images、命名 tracks 和显式 at-tick 的 clips。图片阶段必须指定 image；空间向量使用 `{x,y,z}`。ShowDefinition 保存不可变图片资源表与轨道列表；ClipDefinition.sampledImage 直接指向该片段所需的不可变采样变体，不在 TimelineEngine 中限制图片数。

一次 reload 使用一个 AutoCloseable ImageAssetService 作用域，按以下职责分工：

1. SourceImageLoader 校验文件大小、PNG 格式、头部宽高/像素数和估算解码内存，再完整解码。忽略不需要的 PNG 附加元数据。拒绝非本地文件名、目录越界和越界符号链接。
2. SamplingRequest 保存基础宽度、累计 scale、particle-spacing、alpha 阈值、点数上限和 brightness。ImageSampler 根据最终世界宽高生成目标网格，而非先把原图像素转换成粒子。旋转、位移不影响采样密度。
3. ImageSampler 对每个格子的原图覆盖区域进行面积与 alpha 加权 RGB 聚合。透明背景不参与 RGB，不会引入黑边/白边。alpha 采用非透明内容的平均值，以保留细笔画；完全透明格始终没有点。中心透明时将点定位到该格最近的有内容像素中心，避免点落入透明留白。
4. 超出点数上限时，按固定顺序分层选取代表点，再按距离中心排序，保证中心渐显与重复播放稳定。ImagePointCloud 只保存不可变采样点，不保存 BufferedImage。
5. ImageAssetService 按真实路径、输入安全限制和完整 SamplingRequest 缓存点云。累计比例对 1e-9 精度规范化，以复用等价浮点乘积；dust-size 与 glow 不改变颜色/坐标，不拆分点云缓存。
6. ShowLoader 先构建图片名对应的基准变体，再按 Track.child(Clip).scale() 请求各 Clip 变体。相同尺寸的 reveal、hold、scatter 共享同一个 cloud 对象，尺寸主动变化才采用不同采样版本。

例如 width-blocks=5、Track.scale=0.75、Clip.scale=1 时，有效宽度 3.75；正方形素材与 spacing=0.12 产生 32×32 目标格，透明过滤后再限 max-points。点坐标仍以基础宽度 5 存储，Transform3D 的累计 scale 只应用一次。不能将坐标预放大后又通过矩阵重复缩放，也不能忽略父级缩放对采样密度的影响。Reveal 的临时形成比例不引发逐帧重采样。

同一时刻最多保留一张解码原图，切换时 flush，作用域关闭时释放；快照只保留有限点云、采样参数和资源路径。不同素材切换后若还需新变体，允许重新解码，但累计解码像素计费。缓存命中不解码；播放代码无法访问解码器，每 tick 仅处理点云。

## 有界资源与原子重载

输入默认 16 MiB、4096×4096 的独立宽高限、4194304 总像素限。解码按每像素 8 字节估算工作空间，默认 64 MiB；这是安全准入估算，不是 JVM 堆使用测量。点云、网格候选点和 ImageIO 自身开销另有消耗。

整个加载作用域同时限制原图数、累计解码像素、累计估算像素访问、网格大小、变体数、缓存点数与时间。像素访问保守估算为 `(原宽+2×列数)×(原高+2×行数)`。时间在阶段/采样行之间检查，不承诺中断单次 I/O 或 ImageIO 解码。默认值与合法范围以带中文注释的 config.yml 为准；修改为更高输入限时也须考虑内存和工作上限。

严格 YAML 检查重复键、集合别名、深度和文件体积。任一图片、引用、参数或上限错误使整次加载失败；新快照全部成功后才通过 ShowManager.replace 替换，在播会话继续持有原快照资源。启动/reload 日志输出预处理统计，包括变体/缓存点/来源/解码次数、工作量、毫秒耗时与最大单图估算内存。同步加载仍可能短暂阻塞主线程。

BundledResources 集中管理首次释放资源列表，包括原样 mczjulogo.png。仅写缺失文件，保留用户配置。已有 multitrack.yml 必须由管理员手工备份与同步，启动不会覆盖。

## 空间

Vec3、Rotation3D、Transform3D 为不可变数学组件。右手旋转对列向量按局部 X→Y→Z 应用，即 Rz·Ry·Rx。Transform3D 为 `s·R·p+t`；父子组合为 `R=Rp·Rl`、`s=sp·sl`、`t=sp·Rp·tl+tp`。

```yaml
transform:
  position: {x: 3, y: 2, z: 0}
  rotation: {x: 0, y: 0, z: 15}
  scale: 1.5
```

Track 和 Clip 共用 SpatialConfig，scale 默认 1、单级范围 0.05～20，必须有限正数。自身 scale 不改变本层 position，父级 scale 影响子位移，累计比例用于采样。dust-size 不隐式缩放。

SpatialContext 的矩阵层级为 Base·Global·Track·Clip；Base 根据观察方向建立右轴、上轴、朝观察者法线，yaw=0 时为 -X、+Y、+Z。ShowContext.imageLocation 是所有图片点的空间出口，形成与扩散均经过同一矩阵。动画方向由会话种子和图片名派生，每个 Clip 独立持有状态，相同点索引不会共享可变数组。

## 夜间视觉增强与预算

ColorEnhancement 在采样时计算 RGB。brightness 默认为 1，范围 0.1～3；共同增益限制为 `min(brightness,255/maxChannel)`，保持色相、避免溢出，不改变 alpha。纯黑不增亮；已饱和颜色不会强制变白。

GlowDefinition 默认关闭，density 默认 0.03、范围 0～0.1。每 Clip 初始化时确定固定的 floor(N×density) 个点索引；仅对本帧实际发射过彩色点的位置申请 GLOW，不逐帧随机换点。Paper 26.2 的 Particle.GLOW 无数据负载，使用原生客户端表现，不接受 DUST 颜色数据。

所有会话共享 RenderBudget，按粒子×观众计费。PointCloudRenderer 先发送 DUST，再向有界延迟队列申请光点；ShowManager 完成所有彩色渲染后 flushAccents，额外点再扣剩余额度，预算不足省略。reset 和 close 清空待发位置/闭包，防止跨 tick 遗留。GLOW 不抢占其他轨道的主体颜色预算；多观众仍可能导致彩色点被总预算截断。

没有修改 DUST 光照等级，也不放置世界光源。brightness / GLOW 只改善视觉辨识，不保证黑暗环境全亮；实际客户端颜色、粒子寿命和漂移尚待游戏内验收。

## 调度、库能力与清理

TimelineEngine 使用 ScheduledClip，覆盖 `[at,at+duration)`，结束补发 progress=1。片段独立 start/tick/stop，支持同轨/跨轨重叠、空隙与取消未来片段；全部片段完成后结束 Show。ShowHandle 支持主线程 stop 与 stopClip(trackId,index)。播放异常清理整场，某个 stop 失败仍继续清理其他片段。离线、世界不可用、stopall 和 disable 清理所有库句柄，最后取消管理任务。

Show、玩家和计划 Clip 数统一准入，计划额度直到所属 Show 结束后释放。ParticleLib Clip 各自持有预算预留和 EffectHandle，不能按轨道重置预算。ParticleLib 保持独立插件、provided、ServicesManager 接入，只由 adapter 访问。

当前 ParticleLib 仅支持 ORIGIN_ONLY：统一矩阵变换发射原点，内部形状不支持任意旋转/缩放。origin-only 警告；require-full 拒绝不支持的请求。形状大小由 tornadoHeight / maxTornadoRadius 等真实库参数控制。龙卷风采用保守几何预算；通用库算法只能估算，需要同时配置库自身限制。编译/代理测试不能证明独立库在真实 Paper 上的运行兼容性。

FireworkItems 先写 Rotation PDC 再读取同值构建 Lore，使用不依赖 Locale 的十进制格式并保留有效小数。text.png 继续为透明像素风两行 MCZJU / MMGA，生成工具与既有字形测试保留。

## 扩展与测试边界

创建 ShowLoader 前通过 StepRegistry.register 注册 StepFactory。图片工厂声明 usesImage()，使用 context.image（已选采样变体）、spatial 和共享 budget；空间能力通过 validateSpatial 校验，资源在 ShowStep.stop 释放。未来若增加上级统一 scale，应把其累计比例纳入加载阶段的 SamplingRequest，不在渲染器增加另一套采样逻辑。

自动化测试覆盖数学变换、并发/生命周期、RGBA/尺寸采样、缓存、资源限制、亮度、实际 Renderer 的粒子请求预算、初始化释放与带注释配置。代理 World/Player/ParticleLib API 不是实机验收，状态见 VALIDATION.md。