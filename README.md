# 飞牛相册 TV

飞牛相册 TV 是一款面向 Android TV 的飞牛 NAS（fnOS）相册浏览应用，用电视遥控器就能浏览 NAS 中的照片、视频、文件夹和相册。

打开应用后，选择局域网自动发现的服务器，或手动填写 NAS 地址，使用账号密码登录，即可按时间线、人物、地点、智能分类、文件夹或相册查看媒体内容。

> **非官方应用声明**：本项目是非官方兼容应用，不是飞牛官方应用，也不代表飞牛或相关权利人的授权、担保、背书或从属关系。

## 主要功能

- **大屏相册浏览**：为电视横屏和遥控器（D-pad）操作优化的相册界面。
- **时间线视图**：按日期浏览照片和视频，适合回看家庭照片。
- **文件夹与相册**：支持浏览 NAS 中已管理的文件夹和相册。
- **人物、地点与智能分类**：支持从 NAS 相册已识别的人物、地点和智能分类入口浏览照片集合。
- **全屏播放**：支持照片查看和视频播放（ExoPlayer），保留沉浸式大屏体验。
- **服务器连接**：支持局域网自动发现，也可以手动输入 IP、域名或可访问的服务器地址。
- **登录信息记忆**：可选择记住登录信息，减少重复输入。
- **免责声明确认**：账号登录前需要阅读并确认风险提示。

## 系统要求

- Android TV 或可安装 Android 应用的电视盒子。
- Android 6.0（API 23）或更高版本。
- 已启用相册服务的飞牛 NAS / fnOS 设备。
- 电视设备需要能够访问你的 NAS。建议优先使用可信局域网环境。

## 技术栈

- 语言：Kotlin + Java
- UI：Jetpack Compose、androidx.tv（tv-material）、Leanback、AppCompat
- 网络：Retrofit2 + OkHttp + Gson，Conscrypt TLS
- 图片/视频：Glide、ExoPlayer
- 构建：Gradle 9.4.1 + Android Gradle Plugin 9.2.0，compileSdk 36 / minSdk 23 / targetSdk 36

## 下载与安装

<!-- release-links:start -->
- 源码仓库：[https://github.com/qiancheng817/feiniu-album-tv](https://github.com/qiancheng817/feiniu-album-tv)
- 下载页面：[Releases](https://github.com/qiancheng817/feiniu-album-tv/releases)
<!-- release-links:end -->

本仓库的 `release/` 目录中也直接存放了安装包：

- `feiniu-album-tv-v1.0.4-release.apk`：**推荐**。v1.0.4（13.7 MB），独立包名 `com.feiniu.tv`，可与影格相册并存安装；native 库安装时解压、仅含 `armeabi-v7a` / `arm64-v8a`、不强制要求 leanback 特性。
- `feiniu-album-tv-v1.0.0-release.apk`：v1.0.0 正式签名版（20.1 MB，旧包名 `com.fnphoto.tv`，已废弃）。
- `feiniu-album-tv-v1.0.0-debug.apk`：v1.0.0 调试版（26.5 MB，旧包名，已废弃），便于排查问题。

下载 APK 后，可通过 U 盘、ADB 或电视自带的安装器进行安装。

若电视提示「应用未安装」，优先尝试：

1. 改用 `v1.0.2` 或更新版本——v1.0.1 及更早版本使用了与影格相册相同的包名 `com.fnphoto.tv`，在已安装影格相册的设备上会因签名不同被系统拒绝安装。
2. v1.0.2 起包名为 `com.feiniu.tv`，无需卸载影格相册，两者可并存。
3. 用 U 盘拷贝安装，而非在电视浏览器内下载，避免下载被截断。

安装前请确认：

- APK 来源为你信任的发布页面，并与发布页给出的 SHA-256 一致。
- 电视已允许安装来自外部来源的应用。
- NAS 中的重要数据已经做好备份。
- 公网、端口转发、反向代理、内网穿透或 FN Connect 等远程访问方式已经完成安全评估。

## 本地构建

准备 JDK 17、Android SDK（需包含 `platforms;android-36` 与 `build-tools;36.0.0`）和可用的 `local.properties` 后运行：

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

正式发布签名只能通过未被 Git 跟踪的 `local.properties` 或以下环境变量提供：

- `FNPHOTO_RELEASE_STORE_FILE`
- `FNPHOTO_RELEASE_STORE_PASSWORD`
- `FNPHOTO_RELEASE_KEY_ALIAS`
- `FNPHOTO_RELEASE_KEY_PASSWORD`

请勿提交真实 NAS 地址、账号、访问令牌、个人媒体、私钥、keystore 或签名口令。

## 开源许可与致谢

- 本项目采用 [GNU Affero General Public License v3.0 only](LICENSE)，SPDX 标识为 `AGPL-3.0-only`。
- 本项目基于开源项目 [cumtfc/yingge-album-tv-android](https://github.com/cumtfc/yingge-album-tv-android)（影格相册 TV，AGPL-3.0-only）二次开发，主要改动为：应用更名为「飞牛相册」、替换应用图标与 TV banner、调整品牌文案与更新检查指向。原始版权归原作者所有，修改说明详见 [NOTICE.md](NOTICE.md)。

修改后的程序如果通过网络与用户交互，需要按照 AGPLv3 第 13 节向这些用户提供对应源代码。第三方依赖继续适用各自的许可证。

## 重要免责声明

本应用为非官方兼容应用，不是飞牛官方应用，也不代表飞牛或相关权利人的授权、担保、背书或从属关系。“飞牛”“fnOS”“FN Connect”等名称仅用于说明兼容对象或连接方式，相关名称、标识和商标权利归其权利人所有。

使用本应用时，你需要自行保护 NAS 地址、账号、密码、访问令牌和电视设备安全。因弱密码、账号泄露、共享设备、设备遗失、不可信网络、第三方操作或错误配置导致的用户隐私泄露、账号风险或数据安全问题，由用户自行承担。

如果你通过公网 IP、域名、端口转发、反向代理、内网穿透、FN Connect 或其他远程访问方式连接 NAS，请自行评估公网暴露、证书配置、传输链路、访问控制和网络环境带来的风险。本应用不保证任何公网访问方式的绝对安全。

本应用主要用于浏览照片和视频，但在登录、缓存、预览、收藏、读取相册、读取文件夹、调用官方或兼容接口时，仍可能受到设备环境、网络中断、服务异常、接口变更、权限配置、系统 Bug 或用户误操作影响。因使用本应用造成或间接导致的 NAS 数据损坏、数据丢失、索引异常、缩略图异常或服务状态异常，请用户自行提前清理备份并承担风险。

本应用依赖 NAS 系统及相关服务接口。官方 API 更新、认证机制调整、服务路径变化、系统版本差异或设备兼容性问题，可能导致登录、浏览、播放、搜索、收藏等功能部分或全部失效。本应用不承诺永久维护、持续更新、及时适配或始终可用。

继续下载、安装或使用本应用，即表示你已经理解并接受上述风险。
