# FireworkShow

Java 25 / Paper 26.2 烟花粒子演出插件。通过命名图片、轨道和片段编排图片动画，并接入独立 ParticleLib 插件播放龙卷风。

## 构建与安装

需要 JDK 25、Maven、Paper 26.2 和独立安装的 [ParticleLib v0.1-alpha](https://github.com/sun-plugins/ParticleLib)。Paper API 固定为 `26.2.build.130-stable`；ParticleLib 使用 provided，不打入本插件。

```shell
mvn -B clean verify
```

将 `target/FireworkShow-0.2.0-SNAPSHOT.jar` 与 ParticleLib 插件放入服务端 `plugins/` 后启动。首次释放配置、两张 PNG 和四份演出示例。

## 命令

| 命令 | 权限 | 功能 |
|---|---|---|
| `/fws help` | fireworkshow.use | 帮助 |
| `/fws list` | fireworkshow.use | 列出演出 |
| `/fws preview <showId>` | fireworkshow.admin | 玩家面前预览 |
| `/fws give <player> <showId> [amount] [rotation=x,y,z]` | fireworkshow.admin | 发放特殊烟花 |
| `/fws reload` | fireworkshow.admin | 原子重载配置和资源 |
| `/fws stop` | fireworkshow.admin | 停止本人全部预览 |
| `/fws stopall` | fireworkshow.admin | 停止全部演出 |
| `/fws info` | fireworkshow.admin | 版本、活动数与缓存信息 |

别名 `/fireworkshow`，支持 Tab 补全。use 默认所有人拥有，admin 默认 OP。数量为 1..64，默认 1；旋转默认 `(0,0,0)`。可省略数量直接写 `rotation=90,0,0`。

## 配置

唯一演出格式：命名 `images`、命名 `tracks`，每个 Clip 明确指定 `at-tick`，图片片段明确指定 `image`。位置与角度使用 `{x, y, z}` 映射。

```yaml
id: example
display-name: '&dExample'
orientation: face-trigger
yaw: 0
images:
  heart:
    file: heart.png
    width-blocks: 5
    max-points: 600
    alpha-threshold: 16
    dust-size: 0.9
tracks:
  picture:
    transform:
      position: {x: 0, y: 1, z: 0}
      rotation: {x: 0, y: 0, z: 15}
      scale: 1.5
    clips:
      - at-tick: 0
        type: reveal
        image: heart
        duration-ticks: 40
        easing: ease-out-cubic
        transform:
          position: {x: 0, y: 1, z: 0}
          rotation: {x: 0, y: 0, z: 30}
          scale: 0.8
      - at-tick: 40
        type: hold
        image: heart
        duration-ticks: 40
        transform:
          position: {x: 0, y: 1, z: 0}
          rotation: {x: 0, y: 0, z: 30}
          scale: 0.8
      - at-tick: 80
        type: scatter
        image: heart
        duration-ticks: 50
        radius-blocks: 4
        fade-out: true
        transform:
          position: {x: 0, y: 1, z: 0}
          rotation: {x: 0, y: 0, z: 30}
          scale: 0.8
```

- reveal：从中心向外出现；initial-scale 默认 0.15。
- hold：周期重发完整图案。
- scatter：沿固定方向扩散，以可见密度衰减消失。
- particlelib：配置 effect.class 和效果 Builder 参数，见 heart.yml 的 tornado 示例。

曲线支持 linear、ease-in-cubic、ease-out-cubic。同轨和跨轨 Clip 均可重叠；每个 Clip 只继承 Track，不继承前一个 Clip 的变换。

示例：[heart.yml](src/main/resources/shows/heart.yml)、[alternate-heart.yml](src/main/resources/shows/alternate-heart.yml)、[multi-image.yml](src/main/resources/shows/multi-image.yml)、[multitrack.yml](src/main/resources/shows/multitrack.yml)。最后一个同时播放龙卷风、爱心与文字。

新增演出时，把 PNG 放入 `plugins/FireworkShow/images/`，复制示例到 shows/，修改 ID、图片和轨道，然后 reload。只接受上述格式，现有文件需直接更新；不会自动迁移或覆盖用户文件。

## 空间与资源

使用右手正交坐标系，角度为度。单级旋转依次应用 X → Y → Z，矩阵为 Rz*Ry*Rx。层级为世界原点/观察方向 → 烟花全局旋转 → Track → Clip → 图片本地坐标。父旋转影响子位移，不对欧拉角直接求和。

Track 和 Clip 都支持 `transform.scale`，默认 1，单级范围 **0.05..20**，仅接受有限正数。变换为 `s·R·p+t`：本层 position 不受自身 scale 影响，子级相对位移会受父级缩放影响。父子缩放相乘，例如 1.5×0.8=1.2；PNG 点云和缓存不变。图片的形成、保持和扩散都通过同一空间变换，dust-size 仍由图片配置独立控制。

默认根 yaw=0 时，图片右轴、上轴、朝观察者的法线分别为 -X、+Y、-Z。face-trigger 根据观察者到演出原点的水平观察方向固定朝向；fixed 使用顶层 yaw，无玩家时也使用该 yaw。`rotation=90,0,0` 将图片转入水平面。

透明像素不显示，保留原 RGB 和宽高比。DUST 没有真实 alpha，半透明和淡出通过稳定密度模拟。相同路径、宽度、阈值和点数配置共享不可变点云；不同图片和 Clip 的动画状态独立。PNG 不在每帧重新读取。

## 限制与诊断

默认每 2 ticks 一帧、视距 48、每 tick 预算 1500、最多 6 场、每玩家 2 场、所有在播 Show 共计 128 个计划 Clip。PNG 最大 2 MiB、512×512、700 点。每份 YAML 最大 64 KiB；每 Show 最多 16 图片、16 轨道、128 Clip，每轨最多 64 Clip，结束时间不超过 24000 ticks。

错误图片、引用或 YAML 会拒绝整次 reload，保留当前快照，已有演出继续使用原快照。加载为有界同步操作，较多资源可能短暂阻塞主线程。拒绝目录越界、URL、越界符号链接、重复名称和非有限数值。

ParticleLib 仅支持 ORIGIN_ONLY：发射位置经过变换，内部形状由库决定。有效旋转或缩放在默认 rotation-policy: origin-only 下会警告，在 require-full 下会拒绝。龙卷风采用保守几何预算，其他库算法只能估算，需同时设置库自身性能限制。

ParticleLib 内部形状大小应使用该效果自己的参数，例如 tornadoHeight、maxTornadoRadius；transform.scale 不会代替这些参数。

特殊火箭保存 show_id 和 rotation PDC；缺失或损坏 rotation 的带标记物品会报错并保留原版爆炸行为。普通火箭不受影响。stop 停止后续发射，客户端可能有短暂粒子残影。仅支持常规 Paper 主线程模型。

新生成火箭的 Lore 显示实际 PDC 中的角度，例如 `Rotation: X=90°, Y=0°, Z=15.125°`。省略旋转时显示三个 0，保留有效小数。

文字资源为两行 `MCZJU` / `MMGA`，107×62 RGBA、透明背景、粗像素字模和青蓝到淡紫渐变。可在项目根目录运行 `java tools/GenerateText.java` 重新生成。若测试服已经释放过图片，需手动替换其 `plugins/FireworkShow/images/text.png`，并同步需要的 shows 配置，再执行 `/fws reload`；默认资源释放不会覆盖这些文件。

## 验证

```text
/fws preview heart
/fws preview multi-image
/fws preview multitrack
/fws give Steve multitrack rotation=90,0,0
/fws stopall
```

自动化结果见 [验证记录](docs/VALIDATION.md)。真实客户端/服务端视觉验收尚未完成，需检查图片方向、火箭/发射器 PDC、普通烟花、多人预算和退出/停服清理。

实现和 StepFactory 扩展见 [架构说明](docs/ARCHITECTURE.md)。
