# 贡献指南

感谢你改进飞牛相册 TV。

## 开发环境

- JDK 17
- Android SDK，API 级别以 `app/build.gradle` 为准
- 可访问的 Gradle 依赖仓库

本地配置写入被忽略的 `local.properties`，不要提交 SDK 路径、服务器地址或签名参数。

## 提交前检查

```bash
./scripts/check-sensitive.sh
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

功能变更应补充相应单元测试。提交应保持范围清晰，不包含构建产物、真实账号数据、日志导出、个人媒体或发布签名材料。

## 发布

正式 APK 由发布仓中的受保护 GitHub Actions 工作流构建和签名。贡献者不需要获取发布 keystore。拥有发布仓写权限的协作者可以发起工作流，受保护的 `release` 环境由仓库所有者审批后才会开放签名密钥。

## 许可证

提交贡献即表示你有权提供相关内容，并同意以项目的 `AGPL-3.0-only` 许可证发布。
