# FireworkShow

用于 Paper 的数据驱动烟花演出插件。支持透明 PNG 彩色粒子、图片形成与扩散动画、多轨道并发编排，以及通过独立 ParticleLib 插件播放龙卷风。

## 构建与安装

运行环境：Java 25、Paper 26.2、ParticleLib v0.1-alpha。

在项目目录执行：

```shell
mvn package
```

执行完整的清理、测试和构建：

```shell
mvn -B clean verify
```

生成文件：`target/FireworkShow-0.2.0-SNAPSHOT.jar`。

将 Jar 和 [ParticleLib](https://github.com/sun-plugins/ParticleLib) 服务端插件放入服务器的 `plugins/` 目录，然后启动服务器。ParticleLib 是必需的独立插件，不包含在 FireworkShow Jar 内。

首次启动会生成以下目录：

```text
plugins/FireworkShow/
├── config.yml
├── images/
│   ├── heart.png
│   ├── text.png
│   └── mczjulogo.png
└── shows/
    ├── heart.yml
    ├── alternate-heart.yml
    ├── multi-image.yml
    └── multitrack.yml
```

已有文件不会被覆盖。升级插件后，需要手工同步希望更新的配置或图片，再执行 `/fws reload`。

## 命令与权限

| 命令 | 用途 | 权限 |
|---|---|---|
| `/fws help` | 查看帮助 | `fireworkshow.use` |
| `/fws list` | 列出演出 | `fireworkshow.use` |
| `/fws preview <showId>` | 在玩家面前预览演出 | `fireworkshow.admin` |
| `/fws give <player> <showId> [amount] [rotation=x,y,z]` | 发放特殊烟花 | `fireworkshow.admin` |
| `/fws reload` | 重载配置和图片 | `fireworkshow.admin` |
| `/fws stop` | 停止本人的预览 | `fireworkshow.admin` |
| `/fws stopall` | 停止全部演出 | `fireworkshow.admin` |
| `/fws info` | 查看活动数、缓存和运行状态 | `fireworkshow.admin` |

命令别名为 `/fireworkshow`，支持 Tab 补全。use 权限默认开放，admin 权限默认 OP。

```text
/fws preview heart
/fws preview multitrack
/fws give Steve multitrack
/fws give Steve multitrack 4 rotation=90,0,15
```

特殊烟花爆炸时触发对应演出。数量默认 1，范围 1～64；旋转默认 `(0,0,0)`，可省略数量直接填写 rotation。物品 Lore 会显示保存的旋转值。普通烟花不受影响。

## 演出配置

将 PNG 放入 `images/`，在 `shows/` 中创建 YAML，然后执行 `/fws reload`，无需重新编译插件。

下面的示例使用一张图片，依次形成、保持和扩散：

```yaml
id: my-show
display-name: '&dMy Show'
images:
  heart:
    file: heart.png
    width-blocks: 5
    alpha-threshold: 16
    dust-size: 0.9
    brightness: 1
    glow:
      enabled: false
      density: 0.03
tracks:
  picture:
    transform:
      position: {x: 0, y: 1, z: 0}
      rotation: {x: 0, y: 0, z: 0}
      scale: 1
    clips:
      - at-tick: 0
        duration-ticks: 40
        type: reveal
        image: heart
        easing: ease-out-cubic
      - at-tick: 40
        duration-ticks: 60
        type: hold
        image: heart
      - at-tick: 100
        duration-ticks: 40
        type: scatter
        image: heart
        radius-blocks: 3
        fade-out: true
```

### 图片资源 images

`heart` 是自定义资源名，Clip 的 `image: heart` 引用这个名称，而不是文件名。一个 Show 可以声明多张图片，并在不同阶段引用。

| 字段 | 说明 |
|---|---|
| `file` | images/ 下的 PNG 文件名，不接受 URL 或跨目录路径 |
| `width-blocks` | 图片基础宽度，默认 5，范围 0.1～32 方块；高度按原图比例计算 |
| `alpha-threshold` | Alpha 阈值，默认读取全局设置，范围 0～255；完全透明像素始终排除 |
| `dust-size` | DUST 粒子大小，默认读取全局设置，范围 0.01～4 |
| `brightness` | RGB 亮度增益，默认 1，范围 0.1～3；不改变透明度 |
| `glow.enabled` | 是否添加稀疏 GLOW 点缀，默认 false |
| `glow.density` | 光点比例，默认 0.03，范围 0～0.1；额外光点计入预算 |

当前图片渲染保留每个达到阈值的原始像素，不进行网格采样。`max-points`、`particle-spacing` 和全局 `image.max-points`、`image.max-grid-cells` 当前不参与点数控制，加载日志会提示。缩小显示宽度不会减少粒子数量；PNG 分辨率和有效像素数会直接影响开销。

DUST 不支持真实逐像素 Alpha，达到阈值的半透明像素也会作为一个完整粒子发送。brightness 和 GLOW 是视觉增强，不产生世界光照，也不保证 DUST 在夜间全亮。

### 轨道 tracks 与片段 clips

每条 Track 包含独立的 transform 和多个 Clip。不同轨道可以并发，同一轨道中的片段也可以重叠。

| 字段 | 说明 |
|---|---|
| `at-tick` | 相对整场开始的起始时间，必填，单位游戏 tick |
| `duration-ticks` | 片段持续时间，必填，单位游戏 tick |
| `type` | `reveal`、`hold`、`scatter` 或 `particlelib` |
| `image` | 图片阶段引用的资源名称，必填 |
| `easing` | 进度曲线：linear、ease-in-cubic、ease-out-cubic，默认 linear |
| `initial-scale` | reveal 起始比例，默认 0.15，范围 0～1 |
| `radius-blocks` | scatter 在本地坐标中的扩散半径，默认 4，范围 0～32 方块 |
| `fade-out` | scatter 是否逐渐减少可见点，默认 true |
| `transform` | 可选的片段局部变换，继承所属 Track |

20 TPS 时，20 tick 约为 1 秒。每个 Clip 独立起止，不继承前一个 Clip 的变换。reveal 逐渐形成图片，hold 保持展示，scatter 扩散消失。

ParticleLib 阶段通过 `effect.class: tornado` 选择效果，其余效果参数参考内置 [heart.yml](src/main/resources/shows/heart.yml)。当前接入只支持变换发射原点，内部形状大小由 tornadoHeight、maxTornadoRadius 等库参数控制，不支持任意整体旋转或统一缩放。`rotation-policy: require-full` 会拒绝库不支持的变换。

### 位置、旋转与缩放

Track 和 Clip 都支持：

```yaml
transform:
  position: {x: 3, y: 2, z: 0}
  rotation: {x: 0, y: 0, z: 15}
  scale: 1.5
```

position 是父级局部坐标中的偏移；rotation 使用右手坐标系，单位为度，按局部 X→Y→Z 顺序应用。scale 默认 1，单级范围 0.05～20，必须为有限正数。

父子缩放相乘；父级旋转和缩放会影响子级位移，本层 scale 不改变本层 position。最终图片宽度为 `width-blocks × Track.scale × Clip.scale`，粒子大小 dust-size 独立控制。

顶层 `orientation` 默认 face-trigger，根据触发玩家与演出原点的水平位置确定朝向；设置为 fixed 时使用顶层 yaw，无玩家时也使用 yaw。

## 全局配置 config.yml

| 配置 | 作用 |
|---|---|
| `display.frame-period-ticks` | 图片发射间隔，默认 2 tick，不改变演出时钟 |
| `display.view-distance-blocks` | 观众接收距离，默认 48 方块 |
| `display.max-particles-per-tick` | 普通发送预算，默认 1500，按点数×观众数计费 |
| `display.max-active-shows` | 同时播放的演出上限，默认 6 |
| `display.max-shows-per-player` | 每位玩家的演出上限，默认 2 |
| `display.max-active-clips` | 所有在播演出的计划片段总量上限，默认 128 |
| `image.max-file-bytes`、`max-width`、`max-height`、`max-pixels` | PNG 文件与输入尺寸限制 |
| `image.max-decoded-bytes`、`max-decoded-pixels`、`max-pixel-visits` | 解码内存估算和单次加载工作量限制 |
| `image.max-cached-points`、`max-sampling-variants`、`max-sources` | 完整点云总点数、缓存条目及图片来源限制 |
| `image.max-preprocess-millis` | 加载时间限制，在处理阶段之间检查 |
| `image.default-alpha-threshold`、`default-dust-size` | 图片未单独指定时采用的默认值 |
| `preview.distance-blocks`、`height-offset-blocks` | 命令预览的位置偏移 |
| `experimental.allow-over-budget-full-frames` | 是否允许完整图片帧超过普通发送预算；代码缺省 false，以本地配置值为准 |

正常预算不足时，插件会拒绝整帧并报错，不会只发送图片前半部分。开启 `allow-over-budget-full-frames` 可尝试发送完整图案，但可能导致服务端或客户端卡顿、卡死、断线；它不解除图片加载和内存限制。改回 false 后 reload 会停止在播演出。

所有全局字段的范围和单位见带中文注释的 [config.yml](src/main/resources/config.yml)。图片仅在启动或 reload 时读取；重载失败保留原配置，在播演出继续使用已有资源。

## 常见问题

**修改了默认配置或图片，服务器为什么没变化？**

插件不会覆盖已释放的用户文件。修改服务器 `plugins/FireworkShow/` 下的对应文件，或备份后手工同步项目资源，再执行 `/fws reload`。只改 Jar 内资源不会覆盖服务器已有副本。

**预览提示预算不足，为什么缩小图片也没用？**

当前每个有效原始像素对应一个点，空间缩放只改变位置。请检查 PNG 有效像素数、观众人数和并发轨道。发送量约为有效像素数×观众人数；完整帧超预算开关的作用与风险见上表。

**图案重叠、太厚或看不清细节怎么办？**

检查 width-blocks、Track/Clip scale 和 dust-size。较多像素集中在较小世界尺寸内时，粒子可能相互遮挡；客户端粒子设置和性能也会影响显示。

**透明区域出现粒子，或者半透明边缘不符合预期？**

确认 PNG 使用透明通道，而不是白色或黑色背景。适当调整 alpha-threshold；DUST 无法还原 PNG 的真实半透明效果。

**新增图片或演出需要重新编译吗？**

不需要。在服务器 images/ 和 shows/ 中添加资源、使用唯一 id，并 reload 即可。图片阶段必须填写正确的 image 资源名。

**修改演出位置、大小和时间会导致构建失败吗？**

合法配置修改不受固定示例布局断言限制。算法测试使用独立测试配置；构建仍会检查实际内置配置能否加载，YAML 格式错误、非法参数或缺失资源需要修正。

**演出停不下来怎么办？**

执行 `/fws stopall`；仅停止自己的预览可用 `/fws stop`。已发送到客户端的粒子可能短暂残留。

**启动提示缺少 ParticleLib？**

确认安装了匹配的独立 ParticleLib 插件并正常启用；FireworkShow 不自带该依赖。

## 更多说明

- [完整多轨道示例](src/main/resources/shows/multitrack.yml)
- [架构说明](docs/ARCHITECTURE.md)
- [自动化验证记录](docs/VALIDATION.md)

构建和单元测试不等同于游戏内验收；真实 Paper 运行兼容性、客户端视觉效果及高粒子量性能仍需实机确认。