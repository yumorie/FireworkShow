# 原始像素实验版架构

保留 ShowManager / TimelineEngine / StepRegistry / PointCloudRenderer / Transform3D 架构。只替换图像生成路径和整帧预算准入。ParticleLib 仍为独立插件、provided、ServicesManager 接入，内部效果能力 ORIGIN_ONLY。

## 加载和缓存

SourceImageLoader 在完整解码前校验 PNG 头部、尺寸、像素数、文件大小和估算解码工作空间。ImageAssetService.loadRaw 不引用 ImageSampler；RawPixelCloudBuilder 逐像素执行 `alpha != 0 && alpha >= threshold`，保留原始 RGB（brightness=1）和精确像素中心坐标。先计数并检查剩余总缓存额度，再为全部有效像素分配 Point；不能容纳时拒绝整张图片，不返回残缺结果。

坐标为 `((x+0.5)/w-0.5)*widthBlocks` 和 `(0.5-(y+0.5)/h)*widthBlocks*h/w`。按中心距离排序仅服务 Reveal，不删除点。原像素索引 y*w+x 在排序后保留。

缓存键不包含 scale、particle-spacing、max-points。相同真实路径、输入安全限制、基础宽度、阈值、brightness 共享完整点云，Track/Clip 不生成尺寸采样变体。ImageDefinition.sampling 和 ClipDefinition.sampledImage 沿用现有模型名称，其当前值表示原始转换参数和共享原始点云，不代表发生过采样。SamplingRequest 和 ImageSampler 保留给独立算法测试；当前插件播放路径不会调用网格或候选抽取。

单次加载最多保留一张 BufferedImage；关闭作用域释放，快照只保留不可变 Point 列表和参数。累计像素访问计为每个转换的两次原图遍历；缓存命中不解码。默认缓存最多 2,000,000 点，超限整图失败，绝不截断；输入/解码/来源/条目/时间上限仍适用。原图解码内存与 Point 对象/排序空间分别消耗堆，8 字节/输入像素只是解码工作空间估算，不是总堆保证。

旧 image.max-points、max-grid-cells 和资源 max-points / particle-spacing 不影响实验点数；诊断日志明确提示忽略。max-sampling-variants 的旧配置名称仍限制原始缓存条目数。

ShowLoader 全部成功后才原子替换快照；失败保留旧资源。在播会话保持其原定义；关闭危险开关时例外，ShowManager.replace 会先停止所有会话，避免旧 Settings 保留超预算授权。

## 坐标和动画

维持右手基方向：yaw=0 图片右/上/朝观察者法线为 -X、+Y、+Z。旋转单位度，局部 X→Y→Z。矩阵 `s*R*p+t`；组合 `R=Rp*Rl`、`s=sp*sl`、`t=sp*Rp*tl+tp`。完整原始点云不预乘 scale，空间矩阵只应用一次父子缩放。Track/Clip 相对位移/旋转及全局烟花 PDC 均保持。

Hold 发射每个合格像素，半透明不再通过随机密度省略；DUST 无真实像素 alpha。Reveal 从中心逐步增加数量但最终包含全部点；Scatter 使用完整点云，显式 fade-out 可按动画进度淡出。ShowContext 不再为每个 Clip 的每个点预建方向 HashMap，方向按会话种子和源像素索引计算；避免巨量副本，计算/分配开销仍很高。

brightness 默认 1、glow 默认关闭。显式增强参数仍保留；内置 Logo 改为不增亮、不加光点，便于诊断。纯原图诊断应维持这两个默认值。

## 完整帧与安全模式

ShowLoader 诊断记录原宽高、阈值、有效像素/点云数、单观众完整 DUST 量与预算是否足够。ShowManager 开始时再按当前观众数统计，安全模式下单幅完整图超过预算立即返回命令/触发错误。

PointCloudRenderer 在一次渲染中先计算本动画帧应发点数（Reveal 进度和 Scatter fade 属于动画，而非预算抽取），按观众数用 long 计算发送量。RenderBudget.authorizeFrame 一次准入/扣减；不足时在任何该帧点发送前抛错，ShowManager 记录日志、通知在线拥有者并停止该 Show。不会发送前 N 点再停止。准入以单 Clip 帧为单位，之前已成功发送的其他 Clip 不回滚。所有 Show/Track 共用预算。

experimental.allow-over-budget-full-frames 默认 false，必须为布尔值；管理员手工配置并 reload 后 true 才可绕过发送量上限。危险模式完整扣掉剩余额度后允许继续发送，数值不变负；后续普通额度为零。启动、重载和每次危险播放有强烈警告。不绕过解码内存/资源限制，不修改 ParticleLib 预算，GLOW 仍只使用正常剩余额度。此功能可能卡死服务器或客户端，并非低开销渲染。

每个会话的初次帧记录完整图成本与实际剩余预算；预算变化后的拒绝错误也包含 required/remaining。粒子 API 调用本身的异常可能造成传输中断，此时会话报错清理，不承诺网络层事务或客户端完整接收。

## 生命周期、测试与边界

继续使用独立 Clip start/tick/stop、跨轨/同轨重叠和全部 EffectHandle 清理；stopall、离线、世界失效、disable 逻辑保留。默认资源不覆盖用户文件。本次按用户确认保留当前 512×512 Logo，不恢复其他尺寸。

保留数学变换、命令、PDC/Lore、PNG 安全、时间轴和库清理测试；服务层采样断言改为原始像素断言，独立 ImageSampler 测试继续验证暂存算法。新增一像素一点、原始 RGB/Alpha/坐标、缩放/间距/旧点数不影响缓存、整图内存拒绝、全帧预算失败零发送、显式危险模式和半透明不省略测试。

自动化发送计数使用代理 World/Player，不能证明 Paper 启动兼容性或客户端视觉正确性。完整发送可能仍被客户端粒子设置、距离、寿命和性能限制影响；游戏内验收未完成。