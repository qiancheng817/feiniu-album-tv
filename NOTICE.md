# NOTICE — 修改说明与来源声明

## 上游项目

本项目的源代码基于以下开源项目二次开发：

- 项目：**影格相册 TV**（Android TV 端飞牛 NAS 相册浏览应用）
- 仓库：<https://github.com/cumtfc/yingge-album-tv-android>
- 许可：GNU Affero General Public License v3.0 only（`AGPL-3.0-only`）
- 版权：归原作者及贡献者所有

## 本仓库的修改内容

依据 AGPLv3 第 5 节「显著修改提示」的要求，本仓库相对于上游项目所做的修改如下：

1. **应用品牌更名**：应用显示名称由「影格相册」变更为「飞牛相册」。
   涉及文件：`app/src/main/AndroidManifest.xml`、`app/src/main/res/layout/activity_login.xml`、
   `app/src/main/java/com/fnphoto/tv/SettingsActivity.java`、`app/src/main/java/com/fnphoto/tv/MainActivity.kt`。
2. **应用图标与 TV banner**：替换 `app/src/main/res/drawable/app_icon.png`，新增各密度
   `mipmap-*/ic_launcher.png`、`ic_launcher_round.png`，新增符合 Android TV 规范的
   `app/src/main/res/drawable/tv_banner.png`（320×180），并在清单中改为引用该 banner。
3. **更新检查指向**：默认更新仓库由 `cumtfc/yingge-album-tv` 调整为
   `qiancheng817/feiniu-album-tv`（见 `app/build.gradle`、`app/src/main/java/com/fnphoto/tv/update/AppUpdateChecker.kt`）。
4. **文档与发布页**：重写 `README.md` 与发布页文案，使之匹配新品牌。
5. **工程名**：`settings.gradle` 中 `rootProject.name` 由 `FnPhotoTv` 调整为 `FeiNiuAlbumTv`；
   版本号重置为 `1.0.0`（versionCode 1）。

除上述内容外，应用的核心功能逻辑、网络协议实现与界面结构均保持上游实现。

## 许可证继承

由于上游项目采用 `AGPL-3.0-only`，本项目作为其衍生作品同样以
`AGPL-3.0-only` 发布，完整许可证文本见 [LICENSE](LICENSE)。

## 商标声明

「飞牛」「fnOS」「FN Connect」等名称仅用于说明兼容对象或连接方式，
相关名称、标识与商标权利归其权利人所有。本项目与相关权利人不存在
官方授权、担保、背书或从属关系。
