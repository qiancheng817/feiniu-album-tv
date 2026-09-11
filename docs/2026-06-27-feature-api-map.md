# FN Album Web 功能与 Android TV API 对应表

分析时间：2026-06-27  
分析对象：`http://192.0.2.10:5666/p`（文档示例地址）
前端构建时间：`Wed Jun 17 2026 03:48:08 GMT+0000`

说明：

- 原站前端包内接口多数写作 `/api/...`。当前 Android TV 项目实际请求路径通常带 `/p` 前缀，因此本文统一按 `/p/api/...` 表达。
- 本文用于第三方 Android TV 客户端继续开发，目标是完整功能覆盖，并兼顾遥控器、焦点态、10-foot UI 和误操作保护。
- “当前覆盖”基于当前项目源码中的 `FnHttpApi`、主页面、搜索、文件夹、详情播放、登录等实现。

## 总览

当前项目已经覆盖 TV 客户端最核心的观看链路：登录、图库时间线、日期照片列表、文件夹浏览、相册浏览、收藏、最近添加、人物、地点、关键词搜索、媒体详情、图片查看、视频播放、收藏切换。

主要缺口集中在：共享、标签、智能分类、媒体类型、AI 搜索、地图、重复照片检查、回收站、文件操作、上传/任务面板、预览编辑、系统/AI 设置、视频转码控制。

## 功能 / API 对应表

| 原站功能 | 原站主要 API | 当前项目对应 | 当前覆盖 | Android TV 适配建议 |
|---|---|---|---|---|
| 登录 / FN Connect | WebSocket `/websocket?type=main`，`util.crypto.getRSAPub`，`user.login` | `LoginActivity`，`FnWebSocketClient` | 已覆盖 | 保留遥控器友好的地址输入、FN ID、直连地址；后续可增加扫码或连接码入口 |
| 图库时间线 | `/p/api/v1/gallery/timeline`，`/p/api/v1/gallery/getList` | `FnHttpApi.getTimeline`，`MainFragment.loadTimeline` | 已覆盖 | 作为 TV 首页核心；强化年份/月日跳转和焦点记忆 |
| 图片缩略图 / 原图流 | `/p/api/v1/stream/p/t/{id}/.../{uuid}` | `CardPresenter`，`CachedImageLoader` | 已覆盖 | 保持预加载和缓存；焦点卡片可加载更高质量缩略图 |
| 视频播放 | `/p/api/v1/stream/v/{id}` | `MediaDetailActivity`，`SearchActivity` | 已覆盖 | 增加播放失败兜底、清晰度选择、转码状态提示 |
| 媒体详情 | `/p/api/v1/photo/detail/{id}` | `FnHttpApi.getPhotoDetail`，`MediaDetailActivity` | 已覆盖基础详情 | Exif、路径、地点、标签建议做为详情面板的二级 Tab |
| 预览收藏 | `/p/api/v1/photo/collect`，`/p/api/v1/preview/collect`，`/p/api/v1/preview/collect/cancel` | `FnHttpApi.toggleCollect`，`MediaDetailActivity` | 已覆盖 | 遥控器 OK 长按或菜单键打开操作栏 |
| 预览编辑 | `/p/api/v1/preview/rotate`，`/p/api/v1/preview/photo/rotation`，`/p/api/v1/preview/update/dateTime`，`/p/api/v1/preview/update/geo`，`/p/api/v1/preview/describe`，`/p/api/v1/preview/download` | 当前未实现 | 缺口 | TV 端只保留低风险操作；旋转、下载可做，修改时间/地点建议谨慎 |
| 文件夹入口 | `/p/api/v1/server/folder_manage`，`/p/api/v1/photo/folder/list` | `FnHttpApi.getManagedFolders`，`FnHttpApi.getSubFolders` | 已覆盖 | 适合作为一级入口；显示文件夹封面、数量、路径 |
| 文件夹浏览 | `/p/api/v1/folder_view/getFolderList`，`/p/api/v1/folder_view/getFileList` | `FolderBrowseActivity` | 已覆盖 | 统一成 TV 卡片网格，补路径面包屑和返回栈 |
| 文件夹管理 | `/p/api/v1/photo/folder/add`，`/new`，`/remove`，`/reconnect`，`/scan`，`/scan/all`，`/view`，`/defaultInit` | 部分只有读取接口 | 缺口 | 放入设置页；扫描可做成状态页，删除/移除需要强确认 |
| 文件夹封面 | `/p/api/v1/folder_view/cover`，`/p/api/v1/folder_view/cover/setting` | 当前未实现 | 缺口 | 可用于提升文件夹页观感；设置封面可低优先级 |
| 相册列表 | `/p/api/v1/album/list` | `FnHttpApi.getAlbums`，`MainFragment.loadAlbums` | 已覆盖 | 卡片需要统一封面比例、标题、数量 |
| 相册照片 | `/p/api/v1/album/photos` | `FnHttpApi.getAlbumPhotos`，`MainFragment.loadAlbumPhotos` | 已覆盖 | 支持幻灯片播放和日期分组 |
| 相册管理 | `/p/api/v1/album/create`，`/delete`，`/remove`，`/update/name`，`/update/poster`，`/update/setting`，`/condition/list/add/remove`，`/photo/belong`，`/photo/remove`，`/download/all`，`/normal/timeline`，`/normal/getList`，`/baby/timeline`，`/baby/getList`，`/album/getIds`，`/album/item` | 当前未实现 | 缺口 | TV 优先浏览和播放；创建、删除、移除照片放管理模式 |
| 收藏列表 | `/p/api/v1/photo/collect/list`，`/p/api/v1/gallery/timeline?is_collect=1` | `FnHttpApi.getCollectList`，`MainFragment.loadFavorites` | 已覆盖时间线 | 适合一级入口；补空状态和快速取消收藏 |
| 最近添加 | `/p/api/v1/explore/recent_timeline`，`/p/api/v1/gallery/recent` | `FnHttpApi.getRecentTimeline`，`MainFragment.loadRecent` | 已覆盖 | 保留“今天/昨天/日期”分组 |
| 关键词搜索 | `/p/api/v1/photo/search` | `FnHttpApi.searchPhotos`，`SearchActivity` | 已覆盖基础关键词 | 输入法体验要优化，建议支持历史搜索和语音输入 |
| 综合搜索 | `/p/api/v2/search/results`，`/p/api/v1/search/filterlist`，`/p/api/v1/search/suggest/filter`，`/p/api/v1/search/index`，`/p/api/v1/search/index/status` | 地点照片使用过 `/p/api/v1/search/results`；综合能力未完整实现 | 部分覆盖 | 做成筛选面板：时间、人物、地点、标签、类型、收藏 |
| AI 搜索 | `/p/api/v1/magic-search/ready`，`/prompt`，`/category-prompt`，`/do`，`/similar` | 当前未实现 | 缺口 | TV 价值高，适合语音搜索和自然语言搜索 |
| 人物列表 | `/p/api/v1/ai-person/list`，`/p/api/v1/stream/face/{faceId}` | `FnHttpApi.getPersonList`，`MainFragment.loadPeople` | 已覆盖 | 人物头像卡片适合 TV 大屏展示 |
| 人物照片 | `/p/api/v1/ai-person/photoLibrary/timeLine`，`/p/api/v1/ai-person/photoLibrary/list` | `FnHttpApi.getPersonTimeline`，`FnHttpApi.getPersonPhotos` | 已覆盖 | 支持进入人物后按时间线浏览 |
| 人物管理 | `/p/api/v1/ai-person/create`，`/detail`，`/rename`，`/remove`，`/mergePeople`，`/setVisibility`，`/posterFromPhoto`，`/personByName`，`/addFacesToPerson`，`/removeFacesFromPerson`，`/removePhotosFromPerson`，`/transferPhotosFromPerson`，`/separatePhotoFromPerson`，`/searchSimilarFaces`，`/personInPhoto`，`/getPhotoIds` | 当前未实现 | 缺口 | TV 端低优先级；可只做重命名、隐藏、设置封面 |
| 地点列表 | `/p/api/v1/explore/geos` | `FnHttpApi.getGeos`，`MainFragment.loadPlaces` | 已覆盖 | 可提升为空间化地点墙 |
| 地点照片 | `/p/api/v1/search/results` | `FnHttpApi.searchGeoPhotos`，`MainFragment.loadGeoPhotos` | 已覆盖 | 进入地点后按时间线或瀑布网格展示 |
| 地图 | `/p/api/v1/map/timeLine`，`/photoList`，`/getIds`，`/preview/initCenter`，`/preview/mapBoundary`，腾讯地图脚本 | 当前未实现 | 缺口 | 原站有地图；TV 端建议用可遥控的聚合地图，不必完全复刻网页交互 |
| 标签浏览 | `/p/api/v1/explore/tags`，`/p/api/v1/preview/tag/list` | 菜单有入口，当前未实现 | 缺口 | 浏览标签优先，编辑标签后置 |
| 标签编辑 | `/p/api/v1/preview/tag/add`，`/tag/addForPhoto`，`/tag/delForPhoto` | 当前未实现 | 缺口 | TV 输入成本高，建议低优先级 |
| 智能分类 | `/p/api/v1/ai-smart-rec/categories`，`/enabled`，`/status`，`/run`，`/install-options` | 菜单有入口，当前未实现 | 缺口 | 很适合 TV：风景、美食、文档、截图等海报式入口 |
| AI 能力设置 | AI base/face/smart-rec 相关状态和配置接口 | 当前未实现 | 缺口 | 只展示状态和启动入口，复杂配置保留 Web 管理端 |
| 媒体类型 | `/p/api/v1/media_category/list` | 菜单有入口，当前未实现 | 缺口 | 建议一级或二级入口：照片、视频、RAW、截图、动图 |
| 媒体类型修复 | `/p/api/v1/fixer/photoType`，`/p/api/v1/fixer/photoType/status` | 当前未实现 | 缺口 | 属维护功能，放设置深层 |
| 共享给我 | `/p/api/v1/album_grant/list_to_me` | 菜单有入口，当前未实现 | 缺口 | TV 优先只读浏览他人共享内容 |
| 我的共享 | `/p/api/v1/album_grant/list_mine`，`/create`，`/update`，`/cancel` | 当前未实现 | 缺口 | 创建/取消共享属于管理能力，后置 |
| 分享链接 | `/p/api/v1/share_link/list`，`/new`，`/detail`，`/update`，`/delete`，`/cleanupInvalid` | 当前未实现 | 缺口 | TV 端可浏览链接状态，不建议主打创建 |
| 重复照片检查 | `/p/api/v1/repeat-photo/check`，`/exclude`，`/keep`，`/merge` | 当前未实现 | 缺口 | 可显示检查结果；合并/删除必须强确认 |
| 回收站 | `/p/api/v1/recycle-bin/timeline`，`/list`，`/getIds`，`/add`，`/restore`，`/delete`，`/clean-all` | 当前未实现 | 缺口 | 适合做恢复入口；永久删除和清空回收站需长确认 |
| 文件操作 | `/p/api/v1/file/copy`，`/move`，`/delete`，`/rename`，`/download` | 当前基本只读 | 缺口 | 默认只读更适合 TV；批量操作放管理模式 |
| 文件任务 | `/p/api/v1/file/task/list`，`/task/cancel`，`/task/delete` | 当前未实现 | 缺口 | 做成全局任务中心即可 |
| 上传路径 | `/p/api/v2/photo/upload/path` | `FnHttpApi.getUploadPath` | API 已声明，UI 未实现 | TV 端上传不是核心，可用于接收外部投送 |
| 支持格式 | `/p/api/v1/photo/support/list` | `FnHttpApi.getSupportedTypes` | API 已声明，UI 未实现 | 设置页展示即可 |
| 上传 / 任务面板 | `/p/api/v1/task-panel/list`，`/cancel`，`/delete`，`/retry`，`/clear-done`，`/fail`，`/retry-one`，`/skip-one` | 当前未实现 | 缺口 | TV 端适合显示上传、扫描、索引状态 |
| Google Takeout | `/p/api/v1/google_takeout/prepare`，`/heartbeat`，`/notice` | 当前未实现 | 缺口 | 迁移工具，低优先级 |
| 存储 / 文件池 | `/p/api/v2/server/pools`，`/p/api/v1/server/folder_manage` | `FnHttpApi.getStorageInfo`，`FnHttpApi.getManagedFolders` | 部分覆盖 | 设置页展示容量、备份位置、扫描状态 |
| 用户信息 | `/p/api/v1/user/info`，`/p/api/v1/server/users_all`，`/p/api/v1/server/user_group_all` | `FnHttpApi.getUserInfo`，`FnHttpApi.getAllUsers` | API 已声明，UI 较弱 | 设置页展示当前账号、权限和服务器信息 |
| 系统信息 | `/p/api/v1/server/sys_info`，`/p/api/v1/server/architecture`，`/p/api/v1/app/version`，`/p/api/v1/user_photo/stat` | `FnHttpApi.getSystemInfo`，`getAppVersion`，`getPhotoStats` | API 已声明，UI 较弱 | 设置页做诊断信息和版本展示 |
| 全局配置 | `/p/api/v1/config/detail`，`/config/update`，`/p/api/v1/sys_config/list`，`/sys_config/setting`，`/sys_config/time_zone/support`，`/sys_config/video_decode`，`/sys_config/video_decode/list_gpu` | 当前未完整实现 | 缺口 | TV 端只暴露播放、缓存、账号、服务器几类设置 |
| 视频解码 / 转码 | `/p/api/v1/video/decode/info`，`/play`，`/quality`，`/quit`，`/record` | 当前主要直连视频流 | 缺口 | 对 TV 很重要：质量选择、转码状态、失败重试 |

## 当前项目已实现但 UI 可继续整合的接口

以下接口在 `FnHttpApi` 中已有声明或已被局部使用，但还没有形成完整、统一的 TV 体验：

- `/p/api/v2/photo/upload/path`
- `/p/api/v1/photo/support/list`
- `/p/api/v1/server/folder_manage`
- `/p/api/v1/server/users_all`
- `/p/api/v1/user/info`
- `/p/api/v1/server/sys_info`
- `/p/api/v1/user_photo/stat`
- `/p/api/v1/app/version`
- `/p/api/v1/photo/collect/list`
- `/p/api/v1/gallery/recent`

## 建议开发优先级

### P0：统一观看体验

- 首页图库时间线
- 媒体网格卡片
- 图片/视频详情
- 焦点态、加载态、空状态、错误态
- 左侧导航与页面标题体系

### P1：补齐 TV 高价值浏览功能

- 共享只读浏览
- 标签浏览
- 智能分类
- 媒体类型
- AI 搜索
- 地图/地点增强

### P2：补齐管理能力

- 相册管理
- 人物管理
- 回收站恢复
- 重复照片检查
- 文件下载/删除/移动
- 上传和任务中心

### P3：系统维护能力

- 文件夹扫描和备份位置
- AI 能力状态和索引状态
- 视频转码设置
- 系统信息、版本、诊断

## Android TV UI 原则

- 首页先服务“坐在沙发上看照片/视频”，不要像网页后台一样把所有管理功能铺出来。
- 遥控器 D-pad 焦点必须清楚，焦点态比 hover 态重要。
- 删除、清空、合并、移动、取消共享等破坏性操作必须进入二级确认，最好用长按或确认弹窗。
- 网格卡片比例、间距、标题、角标、占位图要统一。
- 搜索、标签、人物、地点、分类应尽量减少文字输入，优先使用筛选和语音。
- 设置页只保留 TV 端高频项，复杂配置可跳转或提示去 Web 管理端完成。
