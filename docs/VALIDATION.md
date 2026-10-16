# 原始 PNG 像素实验版验证记录

2026-10-16。本轮仅修改本地文件，未执行任何 Git 操作或上传。按用户最终确认，保留当前 512×512 Logo；没有恢复其他尺寸素材。

## 最终构建

实际执行 `mvn -B clean verify`：**BUILD SUCCESS**，总耗时 12.612 秒。Java 25，Paper API 26.2.build.130-stable，ParticleLib v0.1-alpha 独立插件/provided 策略保留。

编译 36 个主源码、17 个测试源码文件。Surefire **77 项，0 failures、0 errors、0 skipped**：保留原 67 项的覆盖（与实验不适用的服务层采样断言明确改为逐像素语义），新增 10 项。ImageSampler 的独立网格/颜色聚合测试仍保留，但当前生产代码不调用它。

新增 RawPixelTest 7 项：10×10 全不透明得到 100 点；仅 25 个合格像素得到 25 点；RGB/Alpha/精确坐标/透明跳过；缩放、间距、旧点数上限均不拆缓存且不截点；内存额度不足拒绝整个资源；512×512 全不透明得到 262144 点；Reveal 排序不丢源索引。

GlowRenderingTest 新增 3 项：预算 199 而双观众完整帧需 200 时，报错且零发送、额度不变；明确开关 true 后完整发送 200 点，默认 false 且拒绝字符串冒充布尔；半透明合格原始像素不随机省略。原预算、GLOW、旋转/缩放、PDC/Lore、命令、重叠调度与句柄清理继续通过。

## 当前 Logo 统计

使用 tools/InspectRawPng.java 调用当前 ImageAssetService.loadRaw 实际统计，不是旧采样结果：

| 项目 | 当前结果 |
|---|---:|
| 输入 | src/main/resources/images/mczjulogo.png |
| 原始宽高 | 512×512 |
| 文件字节 | 134711 |
| Alpha > 0 像素 | 82637 |
| Alpha >=16 且非零像素 | 73913 |
| 阈值16实际点云数量 | 73913 |
| 单观众完整 Hold 发射帧 | 73913 次 DUST |
| 双观众完整 Hold 发射帧 | 147826 次 DUST |
| 四观众完整 Hold 发射帧 | 295652 次 DUST |
| 当前普通预算 | 1500 次/游戏 tick，均不足 |

默认每 2 tick 一次、20 TPS 时约 10 发射帧/秒，单/双/四观众分别约 739130 / 1478260 / 2956520 次发送/秒，仅计算该 Logo，不含其他轨道。API 调用完整不代表网络或客户端必然完整显示。

Logo 基础宽 5、Track.scale=0.75 后仍宽 3.75 方块；不改变 73913 点，三个阶段共享完整原始点云。内置 brightness=1.0、glow=false，无 Alpha 密度省略。

全部四份内置 Show 加载：5 个原始点云缓存条目、81077 个缓存点、3 个来源、5 次解码（单原图驻留，切换可能重复解码）；本次加载统计 169 ms，累计解码像素 283604，像素访问 567208，最大单图解码工作空间估算 2097152 字节。该时间不是服务器性能保证。

单独统计工具同时请求 alpha0/alpha16 两个完整点云，得到 2 条缓存、156550 点、1 次解码；本次耗时 323 ms。缓存命中和实际渲染都不逐 tick 解码。默认总缓存点上限 2000000，超限整图拒绝，不做截断；按每点对象和排序临时空间需要大量堆。

## 安全行为与开关

默认 `experimental.allow-over-budget-full-frames: false`。播放前按当前观众预检完整图；运行中每个图片 Clip 在发射任何点之前整帧扣减共享预算。失败返回明确错误、该帧零发送、停止并清理 Show，不能静默只画前 N 点。整帧是单 Clip 一次发射，不回滚同 tick 其他已经成功的 Clip。Reveal/Scatter 的显式动画变化不属于预算抽取。

管理员手工设 true 并 `/fws reload` 才允许超预算完整发送；启动/reload/播放日志有强烈性能警告。它可能造成服务端/客户端卡死、网络拥塞或断线，不绕过资源/解码/内存/并发保护。改回 false 并 reload 会停止所有在播 Show，撤销旧会话授权。紧急 `/fws stopall`。`/fws info` 显示 RAW PIXELS 与开关状态。

已确认当前普通预算不支持 Logo 完整帧；没有执行高风险游戏内实际发送。

## 产物核对

Jar：target/FireworkShow-0.2.0-SNAPSHOT.jar，265425 字节。

- 三张 PNG 在 Jar 与源文件中逐字节一致，资源 filtering 未损坏二进制。
- 当前 Logo SHA-256：32a63dc1f44ee8152fa520cc7bae2cd0cb734d97ad81bae5e206392ae7b3fe58。保留用户当前图片，无图片处理操作。
- Jar 不包含 ParticleLib、Paper/Bukkit 或 JUnit 类，依赖隔离保留。
- 五份带中文注释的 YAML 全部由实际 ShowLoader 加载；新开关默认关闭。
- 源码检索确认 ImageSampler 在主源码中只有类定义，加载器无实例化/调用。

## 本地修改文件

主包根 src/main/java/com/yumorie/fireworkshow/：

- image/RawPixelCloudBuilder.java（新增）：逐原始像素转换、先计数后检查总内存额度、完整点排序。
- image/ImageAssetService.java：移除当前网格路径，新增 loadRaw，缓存忽略缩放/旧间距/旧点数，保留安全解码/累计工作量保护。
- config/ImageDefinition.java：不同 Clip 共享原始云。
- config/Settings.java、config/ShowLoader.java：危险开关校验、原始点缓存上限、忽略旧采样字段、逐资源诊断统计。
- render/PointCloudRenderer.java、render/RenderBudget.java：整帧原子准入，取消前缀截断及半透明随机省略，明确超预算权限。
- runtime/ShowContext.java：一次性帧成本日志，取消巨大方向 HashMap，Scatter 稳定计算。
- runtime/ShowManager.java：播放预检、拒绝反馈/清理、危险警告、关闭开关停止旧会话。
- FireworkShow.java、command/ShowCommand.java：启动/reload 诊断与警告、info 实验状态。

其他：

- src/main/resources/config.yml：默认关闭危险模式，原始点总缓存限额及中文说明。
- src/main/resources/shows/heart.yml、alternate-heart.yml、multi-image.yml、multitrack.yml：说明旧采样字段忽略，保留动画时间/布局；Logo 默认原色且不发 GLOW。
- src/test/java/com/yumorie/fireworkshow/RawPixelTest.java（新增）、GlowRenderingTest.java：新增 10 项测试。
- 同目录 ImageTest.java、WorldSamplingTest.java、MultiImageConfigTest.java、BundledShowTest.java：保留测试，明确将原服务采样/变体断言更新为逐像素/共享缓存断言。
- tools/InspectRawPng.java（新增）：可重复执行的本地像素/点数/发送成本统计。
- README.md、docs/ARCHITECTURE.md、本文件：仅记录当前实验版行为、开关、风险和测试状态。

ImageSampler 源码没有删除。PNG 没有修改。没有自动替换本地测试服的已释放配置或图片，需管理员手工同步。

## 未完成的运行验收

**未启动真实 Paper 服务端或客户端。插件启动、ParticleLib 运行兼容性、原图游戏内视觉正确性与完整帧实际性能均未验证。**

README 提供单独 raw-logo Hold 配置，建议隔离其他轨道。先在安全模式执行预览确认明确超限；决定承担风险后才开启完整帧。检查原图方向/宽高比、变换只应用一次、阈值边缘、DUST 大小叠加、客户端粒子设置、多观众与关闭开关/stopall 清理。客户端可以自行丢粒子，未经实机验证不能宣称图案正确。