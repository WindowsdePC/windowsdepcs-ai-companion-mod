# 发布规范

## 发行附件

从 `v0.6.7` 起，每个 GitHub Release 只上传一个可直接放入游戏的正式模组 JAR：

`windowsdepcs-ai-companion-<版本>.jar`

不再上传 Sources JAR、Javadoc JAR、完整工程 ZIP 或 SHA-256 校验清单。源码使用 GitHub
自动生成的 `Source code (zip)` 与 `Source code (tar.gz)`；开发者也可直接克隆仓库。

推送 `v*` 标签后，`.github/workflows/release.yml` 会使用 Java 25 运行完整 Gradle 构建与测试，
并创建 Release。若工作流失败，不得手动上传未经 Gradle/Loom 构建验证的正式 JAR。

## 版本规则

- `1.0.0` 之前继续按当前功能路线递增版本号。
- `1.0.0` 之后，如果一个成组功能只完成其中一部分，必须发布为预发布版本，例如
  `v1.1.0-alpha`、`v1.1.0-beta.1` 或 `v5.9.0-beta.3`。
- 只有该组功能达到稳定验收条件后，才发布不带 alpha/beta 后缀的正式版本。
