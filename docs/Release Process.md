# 发布规范

## 发行附件

从 `v0.8.0` 起，每个 GitHub Release 上传三个可直接放入游戏的正式模组 JAR：

- `windowsdepcs-ai-companion-mc26.2-fabric-<版本>.jar`
- `windowsdepcs-ai-companion-mc1.20.1-fabric-<版本>.jar`
- `windowsdepcs-ai-companion-mc1.20.1-forge-<版本>.jar`

玩家只安装与自己的 Minecraft 版本和加载器匹配的一个文件。

不再上传 Sources JAR、Javadoc JAR、完整工程 ZIP 或 SHA-256 校验清单。源码使用 GitHub
自动生成的 `Source code (zip)` 与 `Source code (tar.gz)`；开发者也可直接克隆仓库。

推送 `v*` 标签后，`.github/workflows/release.yml` 会分别检出三个正式分支，使用 Java 25、
Java 21（输出 Java 17 字节码）或 Java 17 运行完整构建与测试。只有三个构建全部成功才创建 Release。若任一工作流失败，
不得手动上传未经对应加载器工具链验证的 JAR。

## 版本规则

- `1.0.0` 之前继续按当前功能路线递增版本号。
- `1.0.0` 之后，如果一个成组功能只完成其中一部分，必须发布为预发布版本，例如
  `v1.1.0-alpha`、`v1.1.0-beta.1` 或 `v5.9.0-beta.3`。
- 只有该组功能达到稳定验收条件后，才发布不带 alpha/beta 后缀的正式版本。
