# 本地验证记录

日期：2026-10-16。本轮按最终世界尺寸采样、夜间视觉增强、中文 YAML 注释和 Logo 多轨道接入已完成。未执行任何 Git 操作或上传。

## 构建与测试

实际执行 `mvn -B clean verify`（使用 IDEA 内置 Maven 及 Java 25，显式指定现有本地 Maven 缓存）。最终结果：**BUILD SUCCESS**，总耗时 15.476 秒。

- 编译 35 个主源码、16 个测试源码文件。
- Surefire：**67 项测试，0 failures、0 errors、0 skipped**。保留原有 51 项，增加 16 项。
- 原有图片测试显式指定采样间距以验证相同的人工像素布局；原配置测试更新 Logo 轨道数和缩放变体数量断言，未移除原测试。
- 新增 WorldSamplingTest（7 项）：1024/2048 原图不影响同尺寸点数、有效缩放影响点数、缓存不重复解码、关闭释放原图、RGB/透明/细线/宽高比、预乘 Alpha、确定性限点、头部/内存/网格/工作量/缓存点上限、非法参数。
- 新增 GlowRenderingTest（5 项）：亮度和 alpha、色相及溢出保护、固定稀疏选点、默认关闭、实际 Renderer 的双观众请求计费、所有彩色渲染后才发光点、预算不足省略、重置清理待发队列。
- 新增 BundledShowTest（4 项）：原样释放 Logo 且不覆盖用户配置、五份实际带中文注释 YAML 加载、Logo 时间/位置/变体复用、非法图片/参数拒绝重载、Track×Clip 比例选采样版本而旋转不拆缓存。
- 原旋转、缩放、Lore、透明图片、命令参数、并发重叠、句柄清理与原子加载测试继续通过。

## 实际采样记录

使用全部内置配置从临时目录加载一次的测试记录：

| 项目 | 结果 |
|---|---|
| 原样 mczjulogo.png | 1254×1254、RGBA、681169 字节 |
| Logo 基础宽度 / Track / Clip 比例 | 5 × 0.75 × 1 |
| 最终宽高 | 3.75 × 3.75 方块 |
| particle-spacing / 目标网格 | 0.12 / 32×32 |
| Logo 采样点数 | 350（max-points 限制） |
| 三阶段缓存 | reveal / hold / scatter 持有同一个 cloud 对象 |
| Logo GLOW 选择数 | 10；仅在彩色点实际可见且预算足够时发送 |
| 整份配置缓存 | 6 个变体，共 2800 个点；包含图片表基准变体 |
| 原图数 / 解码调用数 | 3 / 5（仅保留最后一张解码原图，切换可能再次解码） |
| 累计解码像素 / 估算访问 | 1593976 / 3641554 |
| 本机此次预处理耗时 | 533 ms，不代表服务器性能保证 |
| 最大单张估算解码工作空间 | 12580128 字节；不是 JVM 实测峰值 |

合成不透明图像测试：1024×1024 与 2048×2048 在有效宽 4、spacing=0.2 时均为 400 点；有效宽 8 时为 1600 点，未触及 5000 点上限。配置集成测试的 Track.scale=2、Clip.scale=0.5 / 0.25 分别得到上述两档，并验证旋转后的同尺寸 Clip 复用缓存。

旧流程先枚举原图像素再限点、缩放；新流程先根据有效世界尺寸建立有限网格，再聚合原图颜色/透明度。采样只在加载阶段执行，渲染器仅持有选定点云。RGBA 以预乘 Alpha 聚合，透明 RGB 不污染边缘；完全透明格没有点。细线保留策略采用非透明内容的平均 alpha，DUST 的半透明仍为稳定密度模拟。

## 产物检查

本地 Jar：`target/FireworkShow-0.2.0-SNAPSHOT.jar`，711243 字节。

- 已包含 images/mczjulogo.png、heart.png、text.png；逐字节对比打包资源与源 PNG 全部一致，filtering 未损坏二进制。
- Logo SHA-256：`32ff4a1e7d3eef9c51ad0f66b006c695465804a92f4a16d25573351f8854e3e5`，与开发前一致；未缩小或覆盖。
- POM 中 ParticleLib 仍为 provided；Jar 中没有 me/sunmc、org/bukkit、io/papermc 或 JUnit 类。
- 依赖继续为 Java 25、Paper API `26.2.build.130-stable`、ParticleLib `v0.1-alpha`，接入策略未修改。
- 本地对应 Paper API 源码确认 Particle.GLOW 存在、数据类型 Void；自动化测试也检查该类型。客户端视觉行为不由该检查证明。
- 所有五份 YAML 使用实际 ShowLoader 成功解析；每个实际键之前存在中文注释。README、架构与 YAML UTF-8 解码检查通过。

## 修改文件及原因

| 文件（包根 src/main/java/com/yumorie/fireworkshow/） | 修改用途 |
|---|---|
| image/SourceImageLoader.java（新增） | PNG 头部检查、有界解码，忽略附加元数据 |
| image/ImageSampler.java（新增） | 最终尺寸网格、区域 Alpha 加权采样与确定性限点 |
| image/SamplingRequest.java（新增） | 可校验且可缓存的采样参数 |
| image/ColorEnhancement.java（新增） | 预处理 RGB 亮度与溢出保护 |
| image/ImageAssetService.java | 每次重载的有界解码/变体缓存/统计及清理 |
| config/ImageDefinition.java、config/ShowDefinition.java | 图片预处理属性与 Clip 选定变体 |
| config/GlowDefinition.java（新增） | 固定稀疏光点选择 |
| config/Settings.java、config/ShowLoader.java | 新参数/安全上限、累计比例变体预处理与原子快照 |
| config/BundledResources.java（新增）、FireworkShow.java | 统一首次释放 Logo，保留用户文件，记录预处理统计 |
| runtime/ShowContext.java、runtime/ShowManager.java | 注入每 Clip 变体、会话光点索引，统一预算下延迟光点发送 |
| render/PointCloudRenderer.java、render/RenderBudget.java | 保留统一变换、彩色优先与 GLOW 计费/队列清理 |

其他本地修改：

- src/main/resources/config.yml：新增输入、内存、工作量和缓存上限，所有键中文注释。
- src/main/resources/shows/heart.yml、alternate-heart.yml、multi-image.yml：所有实际键中文注释，原动画时间/类型保留。
- src/main/resources/shows/multitrack.yml：中文注释，增加 logo 图片和轨道，其他三轨原布局/时间/类型保留。
- src/test/java/com/yumorie/fireworkshow/WorldSamplingTest.java、GlowRenderingTest.java、BundledShowTest.java：新增测试。
- 同测试目录 ImageTest.java、MultiImageConfigTest.java：适配明确采样间距与新增轨道/变体语义。
- README.md、docs/ARCHITECTURE.md、本文件：同步最新配置、采样架构、限制与验证证据。

mczjulogo.png 本身没有修改。没有自动改写任何已有测试服配置或 PNG；管理员同步方式见 README。

## 运行状态、限制与待验收

**真实 Paper 服务端启动、ParticleLib 在服务器上的运行兼容性和游戏内视觉验收：未完成。本轮未启动服务器或客户端。**

brightness 是保留色相的 RGB 预处理，GLOW 是预算内的稀疏点缀，不是世界光照，不保证 DUST 在暗处全亮。细节在 350 点下可能丢失；单张原图内存为估算准入，ImageIO 和 JVM 仍有额外开销。同步 reload 尽管有工作/时间限制，仍可能短暂阻塞；限时检查不强制中断一次解码或 I/O。多玩家/多场演出可能截断点数；ParticleLib 内部形状仍为 ORIGIN_ONLY，内部粒子开销边界保持原实现限制。

建议备份并同步配置后手工执行：

```text
/fws preview multitrack
/fws preview multi-image
/fws give Steve multitrack rotation=90,0,15.125
/fws reload
/fws stopall
```

重点验收：

1. Logo 在上方中心 (0,7.6,0)，宽 3.75；40～80 tick 形成、80～140 保持、140～180 扩散，三个阶段无跳变。
2. 原龙卷风位置/时间、左侧爱心和右侧文字保持，Logo 不遮挡；旋转后布局与方向正确。
3. 昼夜对比颜色、细线辨识度、透明边缘、GLOW 漂移/遮挡、粒子设置与距离对观感的影响。
4. 单人/多人/多场并发预算和阶段交界处的缺点、停止/离线/停服后句柄与客户端残留。
5. 初次安装和已有服务器升级的资源释放、手工同步、错误配置 reload 保留旧快照及实际主线程耗时。