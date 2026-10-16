# 本地验证记录

2026-10-16 20:40:35 +08:00，实际执行 `mvn -B clean verify`：BUILD SUCCESS，耗时 10.931 秒。51 项测试，0 失败、0 错误、0 跳过；保留原有 39 项，新增 12 项。

环境：JBR Java 25.0.4、IDEA 附带 Maven、Paper API 26.2.build.130-stable、独立 ParticleLib v0.1-alpha（provided）。实际命令：

```powershell
$env:JAVA_HOME='D:/Applications/IDEA/idea-2026.2.3.win/jbr'
& 'D:/Applications/IDEA/idea-2026.2.3.win/plugins/maven-plugin/lib/maven3/bin/mvn.cmd' '-Dmaven.repo.local=C:/Users/50261/.m2/repository' -B clean verify
```

产物：`target/FireworkShow-0.2.0-SNAPSHOT.jar`，94053 字节。

SHA-256：`E5F06674CDFBE6EA7EEBC10269D8167EA5C1F638911091AA48B9AB36CF1A21CC`。

实际检查 jar 未包含 Paper、ParticleLib 或 SnakeYAML 类；打包 text.png 与源文件 SHA-256 一致。

## 本轮文件与功能

- `spatial/Transform3D.java`：统一相似变换 s·R·p+t，父子比例相乘，父比例影响子位移。
- `spatial/SpatialConfig.java`：Track/Clip 共用 scale 解析，默认 1，单级 0.05..20。
- `spatial/SpatialContext.java`、`integration/particlelib/ParticleLibAdapter.java`、`runtime/ShowManager.java`：保留累计缩放，明确 ParticleLib 仅变换原点，警告或拒绝完整形状缩放请求。
- `trigger/FireworkItems.java`：Lore 包含从实际 PDC 读回的欧拉角，保留小数且不受服务器 Locale 影响。
- `images/text.png`、`tools/GenerateText.java`：本地程序生成的两行 MCZJU / MMGA；107×62、8-bit RGBA、3 像素笔画、行间距 8、上下边距 6，透明背景与青蓝到淡紫渐变。
- `shows/multi-image.yml`：文字宽度由 6 改为 9.5，点数仍为 600。
- `shows/multitrack.yml`：文字宽度由 5 改为 8、点数 300 改为 600、Track 位置由 (3,2,0) 改为 (4.5,2.5,0)，为两行文字留出空间；所有片段时间及动画类型不变。
- `ScaleTest.java`、`FireworkLoreTest.java`、`TextAssetTest.java`：新增 11 项测试。
- `MultiImageConfigTest.java`：保留所有测试，更新示例参数相关断言，并增加 1 项 Track/Clip 缩放不拆分缓存的集成测试。
- README、ARCHITECTURE 和本文：最新配置、缩放公式、能力边界与验收说明。

## 自动化与目视检查

缩放测试覆盖省略/1、父子组合、本层 position 不随自身缩放、父缩放对子位移、旋转叠加、非法值/边界、库形状能力和资源缓存复用。

Lore 测试覆盖默认零角、整数/负数/有效小数、极小数、无意义尾零与 Locale。生产流程先写 PDC，再从该 PDC 读回角度生成 Lore。

PNG 测试检查 IHDR 的 RGBA 类型、尺寸、全部像素的透明度和九个字母的字模，覆盖两行中心、边距与行间距。已查看实际 PNG，内容为 MCZJU / MMGA。

当前项目范围未发现 `plugins/FireworkShow/images/text.png` 测试服副本；target/classes 中资源已随最终构建更新。已安装服务端需要显式替换 PNG 和相应 shows 配置后 reload，默认资源释放不覆盖现有文件。

## 游戏内验收（尚未执行）

- `/fws give Steve multitrack 1 rotation=90,0,15.125`：检查 Lore 与实际旋转一致，默认 give 显示三个零。
- `/fws preview multi-image`、`/fws preview multitrack`：检查两行字形、颜色、密度、朝向、无遮挡与爱心间距。
- 给测试 Track 配置 scale: 1.5，三个图片 Clip 都配置相同的 scale: 0.8 与相对位移，确认最终 1.2 倍并且位移继承正确、阶段衔接无跳变。
- 给 ParticleLib 父级增加缩放和子位移，确认原点变化但内部形状不随 scale 放大；require-full 明确拒绝。
- 多人、特殊/普通火箭、发射器、stopall、离线与停服清理仍需真实 Paper/客户端检查。

本轮没有执行任何 Git 操作、上传或远程发布，没有宣称游戏内验证通过。