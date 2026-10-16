# 本地验证记录

2026-10-16 20:08:29 +08:00，实际执行 Maven `-B clean verify`：BUILD SUCCESS，耗时 10.747 秒。39 项测试，0 失败、0 错误、0 跳过。

环境：IDEA 附带 JBR Java 25.0.4 和 Maven；Paper API 26.2.build.130-stable；ParticleLib v0.1-alpha 为 provided 独立插件依赖。实际命令：

```powershell
$env:JAVA_HOME='D:/Applications/IDEA/idea-2026.2.3.win/jbr'
& 'D:/Applications/IDEA/idea-2026.2.3.win/plugins/maven-plugin/lib/maven3/bin/mvn.cmd' '-Dmaven.repo.local=C:/Users/50261/.m2/repository' -B clean verify
```

产物：`target/FireworkShow-0.2.0-SNAPSHOT.jar`。详细测试输出在 `target/surefire-reports/`。

本轮删除单图片/顺序时间轴配置转换、默认图片选择、顺序调度 API、单图片访问器、旧 ShowContext/物品/播放重载入口、坐标深度适配及缺失 rotation PDC 回退。只保留命名 images、命名 tracks、显式片段图片与开始 tick、映射向量格式。四份内置配置全部更新。

测试覆盖当前格式加载、错误结构拒绝、原子重载、PNG/透明度/资源限制、缓存、状态隔离、欧拉角和父子组合、重叠调度、PDC、库空间能力、预算和资源清理。

README 和架构说明仅保留当前设计。没有执行 Git 操作或上传文件。

真实 Paper/客户端运行验收尚未完成；需检查视觉方向、火箭/发射器 PDC、普通烟花、多人视距和退出/停服清理。ParticleLib 仍只有原点变换能力，任意效果的粒子预算仍存在估算边界，资源重载仍为有界同步操作。