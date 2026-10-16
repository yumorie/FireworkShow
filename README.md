# FireworkShow

Java 25 / Paper 26.2 的数据驱动烟花演出插件。默认演出通过独立 ParticleLib 插件播放龙卷风，再将透明 PNG 逐渐展开、保持展示、扩散消失。新增图片与 YAML 不需要重新编译。

演示截图 / GIF：待在真实客户端完成视觉验收后补充。

## 依赖与构建

- JDK 25、Maven 3.9+。
- Paper API 固定为 `26.2.build.130-stable`，避免原版本范围意外选择 26.3。
- ParticleLib **独立插件** `v0.1-alpha`；Maven 坐标 `com.github.sun-mc-dev.ParticleLib:particlelib-core:v0.1-alpha`，作用域 `provided`，不会打包进本插件。
- JUnit Jupiter 5.11.4、Surefire 3.5.4。

[ParticleLib 项目与安装说明](https://github.com/sun-plugins/ParticleLib)、[v0.1-alpha 源码](https://github.com/sun-plugins/ParticleLib/tree/v0.1-alpha)。官方 README 的 Maven 坐标仍使用旧组织 `sun-mc-dev`；本地构建已成功解析该坐标。下载其正式独立插件 jar，或按上游说明构建，勿将仅 API 的 jar 当作插件安装。API 和主实现都必须可由该插件的类加载器访问。

```shell
java -version
mvn -B clean verify
```

产物：`target/FireworkShow-1.0-SNAPSHOT.jar`。PNG 使用不经过资源过滤的二进制复制。构建测试覆盖透明背景、RGB、alpha 阈值、中心与比例、稳定抽样、动画端点、阶段切换、幂等停止、配置校验、失败重载快照、资源路径和真实 ParticleLib Builder。测试不等同于真实服务端验收。

本环境没有把 Java/Maven 放入 PATH，实际验证使用 IDEA 附带的 Java 25.0.4 与 Maven，并指定现有 Maven 缓存；普通开发机配置好 `JAVA_HOME` 和 PATH 后使用上面命令即可。

## 安装和预览

1. 准备 Java 25 的 Paper 26.2 测试服，按 Mojang 要求自行阅读并同意其 EULA。
2. 停服，将独立 ParticleLib 插件和本插件 jar 放入 `plugins/`。
3. 启动，查看 `Loaded 2 shows; ParticleLib API available`。缺少 ParticleLib 时 Paper 会因 `depend` 拒绝加载；API 服务缺失时本插件输出明确错误并禁用。
4. 以管理员身份面向空旷天空执行 `/fws list`、`/fws preview heart`。
5. 默认总计 160 游戏 tick，20 TPS 时约 8 秒。执行 `/fws stop` 停止自己的所有预览，或 `/fws stopall` 停止全部演出。

首次启动自动释放：

```text
plugins/FireworkShow/
  config.yml
  images/heart.png
  shows/heart.yml
  shows/alternate-heart.yml
```

`heart.png` 是本项目程序绘制的多色、透明背景 PNG，不使用外部图片资产。

## 命令和权限

别名 `/fireworkshow`，支持 show ID 和在线玩家的 Tab 补全。

| 命令 | 权限 | 行为 |
|---|---|---|
| `/fws help` | `fireworkshow.use` | 查看用法 |
| `/fws list` | `fireworkshow.use` | 列出有效演出 |
| `/fws preview <showId>` | `fireworkshow.admin` | 玩家视线前方预览 |
| `/fws give <player> <showId> [amount]` | `fireworkshow.admin` | 发放 1..64 个特殊火箭 |
| `/fws reload` | `fireworkshow.admin` | 全部验证成功后切换配置与图片快照 |
| `/fws stop` | `fireworkshow.admin` | 停止本人全部预览 |
| `/fws stopall` | `fireworkshow.admin` | 停止全部预览和烟花演出 |
| `/fws info` | `fireworkshow.admin` | 版本、库服务状态、活动数、点云缓存数 |

`use` 默认所有人拥有，`admin` 默认 OP，且包含 `use`。控制台支持除 `preview`、`stop` 外的命令。背包满时，发放物品的剩余部分掉落在目标玩家脚下。

## 配置和四阶段

完整示例见 `src/main/resources/shows/heart.yml`，默认 30 / 40 / 40 / 50 ticks：

```yaml
id: heart
display-name: '&dHeart Firework'
image:
  file: heart.png
  width-blocks: 5.0
  alpha-threshold: 16
  max-points: 600
  dust-size: 0.9
  orientation: face-trigger
timeline:
  - type: particlelib
    duration-ticks: 30
    effect:
      class: tornado
      particle: FLAME
      period: 2
      iterations: -1
      tornadoHeight: 4.0
      maxTornadoRadius: 2.5
      circleParticles: 8
      cloudParticles: 10
      showCloud: true
  - type: reveal
    duration-ticks: 40
    initial-scale: 0.15
    easing: ease-out-cubic
    mode: center-out
  - type: hold
    duration-ticks: 40
  - type: scatter
    duration-ticks: 50
    radius-blocks: 4.0
    easing: ease-in-cubic
    fade-out: true
```

- `particlelib` 将嵌套 effect 传给真实库 API，显式限定目标观众，阶段结束取消本插件拥有的每个 handle。不会调用库的全局 `cancelAll()` 干扰其他插件。
- `reveal` 从中心向外按距离和稳定像素编号排序出现，同时由 `initial-scale` 插值至完整尺寸。
- `hold` 周期性重发最终位置。
- `scatter` 使用会话固定 seed 预生成方向，按半径和进度移动，通过确定性密度衰减消失。`fade-out: false` 只扩散，结束时停止发送。

支持 `linear`、`ease-in-cubic`、`ease-out-cubic`。修改阶段顺序和重复阶段即可编排不同演出；`alternate-heart.yml` 演示同一 PNG 的不同时间轴。`effect.class` 可引用其他已注册库效果，例如 `sphere`；效果字段需使用该版本 Builder 的实际字段名。适配器会拒绝未知字段，部分通用字段遵循上游配置加载器的别名。

## 新增 PNG 和演出：零 Java 修改

1. 将 `logo.png` 放入 `plugins/FireworkShow/images/`。
2. 复制 `shows/alternate-heart.yml` 为 `shows/logo.yml`。
3. 将 `id` 改为 `logo`，`image.file` 改为 `logo.png`，按需要调整宽度和阶段时长。
4. `/fws reload` → `/fws list` → `/fws preview logo`。

ID 只允许 1..64 个小写字母、数字、`_`、`-`，不能重复。图片名只允许字母、数字、`_`、`-` 和 `.png` 后缀；不接受路径、URL 或越界符号链接。重载逐文件报告错误；任意错误都会保留整个旧快照。正在运行的会话继续使用启动时的图片和配置。重载在命令线程加载，可能短暂阻塞主线程；不会在每 tick 重新解码 PNG。

## 图片规则、朝向和限制

完全透明像素始终排除，即使 `alpha-threshold: 0`。低于阈值的像素排除；保留像素使用原 RGB。半透明用稳定的密度抽样近似，不存在真正的 DUST alpha。图片保持比例、居中，像素中心映射至竖直平面；全透明图片合法但没有图像粒子。

`face-trigger` 在触发瞬间固定朝向；Minecraft yaw=0 面向 +Z、yaw=90 面向 -X，本地图片右轴为 `(-cos(yaw), 0, -sin(yaw))`（视线方向叉乘上轴），图片上轴为 +Y。因此从触发者看图片的右侧仍为右侧。没有玩家（如发射器）使用 `image.yaw`，默认 0；`orientation: fixed` 始终使用该角度。四个主方向已有数学测试，正反方向仍需客户端目视验收。

默认 `config.yml`：每 2 ticks 一帧、视距 48、图片/库预算 1500、最多 6 场、每玩家 2 场；PNG 最大 2 MiB、512×512、262144 像素、700 点。每场图片最多点数不能超过全局上限。YAML 最大 64 KiB、最多 128 场，每场 1..32 阶段、单阶段最多 12000 ticks、总计最多 24000 ticks。还有代码级硬上限，非法数值、NaN、无限大将被拒绝。

图片预算按实际“点×观众”扣减，预算不足省略本帧后续点。ParticleLib 龙卷风按几何参数保守预留每 tick 的“粒子×观众”额度，观众在阶段开始时固定，离开范围后取消对应 handle；中途进入的观众不补播当前龙卷风。预算不足时阶段失败并记录错误；不会无限排队。过低预算可能导致图片不完整，建议增加预算或降低点数和观众数。

**外部库预算边界：** ParticleLib 的公开 API 没有统一粒子发送拦截器。其他任意算法目前按每观众 1500 的估算预留，无法保证其内部实际发送量的严格全局上限。需同时设置 ParticleLib 自身 `performance.max-active-effects`，并谨慎配置其他库效果的几何规模。这是当前已知限制，不能把该估算当作任意第三方效果的硬预算。运行中 reload 降低限额时，旧会话保留原快照和已启动的库效果，新会话使用新限制。

客户端粒子会短暂自然存活，服务端不能强制清除已发出的粒子；stop 停止的是后续发射和调度。只支持常规 Paper 主线程模型，不声明 Folia 支持。

## 特殊烟花

`/fws give Player heart 1` 生成无烟花星爆炸效果的真实 `FIREWORK_ROCKET`，PDC 为 `fireworkshow:show_id`。事件从 `Firework#getItem()` 的物品元数据读取 ID，不依赖实体 PDC 继承。合法且成功入场时取消原版视觉爆炸；缺失 ID 或达到会话限额时输出日志并保留原版行为。发射器不要求在线玩家。生成火箭不带伤害性爆炸效果，并额外取消带本插件标记火箭引起的伤害事件；普通火箭不受此处理影响。实际 item→rocket→event、发射器、鞘翅和无伤害链路仍必须在 Paper 26.2 中验证。

## 常见故障

- 无法启动：检查 Java 25、Paper 26.2、ParticleLib 独立插件及 API 服务日志，勿将 API jar 作为完整插件。
- reload 被拒绝：错误包含文件名和字段，修复所有错误再试，旧快照仍可预览。
- 无龙卷风：检查库日志、预算、效果 class 和实际 Builder 字段。不会使用其他粒子冒充龙卷风。
- 背景成矩形：确认源文件真正有 alpha；JPEG 改后缀为 PNG 会被拒绝。
- 多人时图案缺点：降低点数、观众范围或并发数，或适度提高总预算。
- 构建无法下载依赖：检查 Maven 到 Paper 仓库与 JitPack 的连接；不要更换为不实版本或空 API 桩。

## Paper 26.2 人工验收（尚未验证）

以下各项必须在真实客户端和服务端完成，保存 Paper/Java/ParticleLib 版本、启动日志、操作结果和截图：

| 步骤 | 预期 |
|---|---|
| 安装两个插件，启动并执行 `/plugins`、`/fws info` | 均加载，2 个演出、API ready |
| `/fws preview heart` | 真正龙卷风→多色爱心逐渐形成→保持→散去，约 8 秒 |
| 换透明边缘 PNG 并 reload | 没有背景矩形，方向不镜像 |
| 按上面流程添加 logo | 不重新编译即可播放 |
| 生存模式 `/fws give` 后正常发射 | 爆炸位置播放、正确识别物品 PDC |
| 发射器发射、鞘翅使用特殊与普通火箭 | 无 NPE，普通火箭行为不变，无异常伤害 |
| 超并发和多玩家、远离或跨世界 | 上限生效，无范围外持续发送 |
| `/fws stop`、`stopall`、退出、卸载世界、停服 | 库 handle 和任务清理，无永久龙卷风 |
| 损坏 PNG、重复 ID、错误 YAML 后 reload | 全部错误明确报告，旧演出仍可用 |
| 无权限/控制台 preview | 明确拒绝和用法提示 |
| 缺失或不兼容 ParticleLib | 有明确依赖/启动失败信息 |

扩展新的底层动画参见 [架构与 StepFactory 教程](docs/ARCHITECTURE.md)。本版本不导出独立 Bukkit Service API；`ShowManager` 和 `StepRegistry` 是插件内部接口。
