# 架构

命令和烟花事件调用 ShowManager；唯一 Bukkit 主线程任务驱动每场演出的 TimelineEngine。每个 Clip 拥有独立 ShowStep 和 ShowContext，通过 StepRegistry/StepFactory 创建，使用统一 PointCloudRenderer 或 ParticleLibAdapter。

## 配置与数据

唯一格式为命名 images、命名 tracks 和显式 at-tick 的 clips。图片阶段必须填写 image；位置和旋转只接受 {x,y,z} 映射，transform.scale 是可选数值。ShowDefinition 保存不可变资源和轨道，不包含单图片访问器、默认图片或顺序时间轴适配。未知字段和不支持的结构直接报错。

ShowLoader 校验 PNG 字节、解码前尺寸、像素数、路径、alpha 和点数。相同资源配置共享点云，dust-size 是独立渲染属性。严格 YAML 拒绝重复键和集合别名。全部成功后才原子替换快照，运行中会话持有原定义。

## 空间

Vec3、Rotation3D 和 Transform3D 为不可变数学组件。右手旋转对列向量按 X→Y→Z 应用，即 Rz*Ry*Rx。Transform3D 保存纯旋转 R、统一缩放 s 和位置 t，应用公式为 s·R·p+t。父子组合为 R=Rp*Rl、s=sp*sl、t=sp·Rp·tl+tp，不能仅在最终图片坐标乘比例。

```yaml
transform:
  position: {x: 3, y: 2, z: 0}
  rotation: {x: 0, y: 0, z: 15}
  scale: 1.5
```

Track 与 Clip 共用 SpatialConfig，单级 scale 默认 1、范围 0.05..20，拒绝零、负数、NaN 和无穷大。组合后的总比例允许为各层乘积；本层 t 不随自己的 scale 改变。PNG 点云、粒子颜色和资源缓存不修改，dust-size 不隐式缩放。

SpatialContext 的世界矩阵为 Base*Global*Track*Clip。Base 根据观察方位建立右轴、上轴、朝观察者的法线，yaw=0 时为 -X、+Y、-Z。所有本地点直接进入右手坐标系，没有历史深度符号转换。

ShowContext.imageLocation 是唯一图片坐标出口。局部缩放和扩散经过同一矩阵。随机状态按会话和图片名派生，每个 Clip 独立持有，重复点编号不会共享可变状态。

## 调度与清理

TimelineEngine 只接受 ScheduledClip。片段独立 start/tick/stop，覆盖 [at,at+duration)，结束补发 progress=1。支持重叠、时间空隙和取消未来片段；全部片段完成后演出结束。

ShowHandle 提供主线程 stop 和 stopClip(trackName,index)。播放异常清理整场，某个 stop 失败仍继续清理其他片段。disable、离线、世界不可用和 stopall 都清理所有活动句柄，最后取消管理器任务。

所有会话共享 RenderBudget，每个库 Clip 独立预留额度；每 tick 汇总，不按轨道重置。Show、玩家和计划 Clip 总量统一准入，计划 Clip 配额直到所属 Show 结束后释放。

ParticleLib 保持独立插件、provided、ServicesManager、主线程和逐句柄清理。SpatialContext 的 requested 保留旋转及累计缩放，避免把 scale 误判成旋转或在继承时丢失。当前库只能变换原点，origin-only 警告，require-full 拒绝不支持的形状旋转或缩放。形状大小依然由库自身参数控制。任意库算法的预算估算边界见 README。

FireworkItems 先写 Rotation PDC，再读取该值构造 Lore，避免显示内容和实际播放参数分离。角度格式使用不依赖 Locale 的十进制表达，去除无效尾零但保留有效小数。

text.png 由 tools/GenerateText.java 在本地按 5×7 字模、3 像素笔画生成，两行 MCZJU / MMGA 分别水平居中。图片字形、RGBA、透明边距和行间距由 TextAssetTest 验证。内置示例按扩大后的画布调整宽度、密度与必要的相对位置，时间和动画类型保持不变。已释放到服务器的资源需要显式替换，不在启动时自动覆盖。

## 扩展

在创建 ShowLoader 前，通过 StepRegistry.register 注册 StepFactory。图片工厂声明 usesImage()=true，并使用 context.image、context.spatial 和共享 budget；空间约束通过 validateSpatial 校验。自建资源在 ShowStep.stop 释放。新增 PNG 和 YAML 无需编译，新增底层算法才需要 Java 实现。

测试覆盖数学变换、并发调度、PNG、缓存、预算和清理；代理 API/World/Player 测试不等于真实 Paper 运行验收。
