# 发布规范

## 发行附件

每个 GitHub Release 只手动上传以下三个模组 JAR：

1. `windowsdepcs-ai-companion-<版本>.jar`
2. `windowsdepcs-ai-companion-<版本>-sources.jar`
3. `windowsdepcs-ai-companion-<版本>-javadoc.jar`

另附校验清单 `SHA-256_SUMS-<版本>.txt`。不再手动上传完整工程 ZIP；源码使用 GitHub
自动生成的 `Source code (zip)` 与 `Source code (tar.gz)`。

## 版本规则

- `1.0.0` 之前继续按当前功能路线递增版本号。
- `1.0.0` 之后，如果一个成组功能只完成其中一部分，必须发布为预发布版本，例如
  `v1.1.0-alpha`、`v1.1.0-beta.1` 或 `v5.9.0-beta.3`。
- 只有该组功能达到稳定验收条件后，才发布不带 alpha/beta 后缀的正式版本。
