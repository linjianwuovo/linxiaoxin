# 安大信

> **本仓库是 [轻小信 / Relianttt/lightxin](https://github.com/Relianttt/lightxin)（MIT）的个人定制 fork。**
> 上游的 `LICENSE`（Copyright (c) 2026 LightXin）原样保留，本 fork 的改动同样按 MIT 开源。
> 起因很朴素：安小信太卡了，于是在轻小信基础上改了整套界面与交互。

## 这个 fork 改了什么

- 界面从 Material3 全量换成 [Miuix](https://github.com/compose-miuix-ui/miuix)，配色改为白底 + 现代蓝，深色同步
- 首页悬浮液态玻璃底栏（[Kyant0 Backdrop](https://github.com/Kyant0/AndroidLiquidGlass)），设置页可实时调 模糊度 / 折射度 / 扭曲度 / 色散四根参数
- 主题外观页：主题模式（跟随系统 / 浅 / 深）、底栏材质、自定义主题色取色器、Material You (Monet) 开关
- 应用图标重绘 + 应用内动态开屏跟随主题色
- AI 课堂：会话缓存 + 首页预热，进入秒开、断网回退缓存
- 扫码入口提到首页；扫完点「确定」直接退出，不再停在"正在签到"的假死页
- 查寝页补齐：本月签到统计卡 + 签到月历 + 主题签到分组，接口按校方真实返回核对
- 节假日离返校独立成页（去登记 / 历史登记双标签），不再塞在查寝列表里
- 登录页「记住密码」：明文绝不落盘，密钥在 Android Keystore 里且不可导出
- 移除"检测更新"功能（GitHub Release 轮询、APK 下载与安装权限整体删除）
- 上游的内部设计文档目录 `codestable/` 不在本 fork 公开（其中含校方接口的鉴权细节）

- 校园卡：余额 / 明细分页 / 充值走校方收银台（纯 JS 跳转，Cookie 按原域名种进 WebView）
- 报修、请假、全校服务清单接入，写操作一律只做到确认前一步
- 消息页一键已读（逐条 `readPushMessage.do`，真写到服务端）
- 底栏玻璃滑块改成跟手：手指按在哪格滑块就跟到哪，抬手弹回选中格
- 全站中英双语，文案全量抽进 `strings.xml`（每次发版跑中英键集合 diff）
- 全站字体换成 MiSans VF，许可与合规记录见 `MISANS-NOTICE.txt`

## 界面截图

本 fork 不再放真机截图。截图里躲不开真实课表、教室和公告内容，打码也不算干净；
想看界面自己编译一份，或者跑起来看一眼比看图快。

下方保留上游原 README 正文（**安装一节已按本 fork 更新**）。

## 安装

> 正式版安装包在 [Releases](https://github.com/linjianwuovo/linxiaoxin/releases/tag/v1.3.7)：
> `andaxin-1.3.7.apk`（debug 签名，minSdk 26 / Android 8.0+，arm64-v8a）。
> 签名一直是 Android 调试 key，所以从任一 beta 版本可以直接覆盖安装；
> 换成别的签名（比如自己 `assembleRelease` 出来的未签名包）就得先卸载再装。
> 应用显示名从 v1.3.7 起是「安大信」，包名仍是 `com.linxin`，历史 release 的包名也照旧。

```bash
git clone https://github.com/linjianwuovo/linxiaoxin.git
cd linxiaoxin
./gradlew assembleDebug
```

需要 **Android Studio** 和 **JDK 17**。

---

<!-- 以下为上游 轻小信 的原 README -->

# 轻小信

![License](https://img.shields.io/badge/license-MIT-green) ![Min SDK](https://img.shields.io/badge/minSdk-26%20(Android%208.0)-blue)

## 功能

覆盖安小信绝大部分常用功能：课程表、查寝签到、节假日登记、跑步、劳动教育、AI 课堂、考试成绩、素质学分。


## 截图

> 上游原版的截图（含 Material3 界面与"更多功能 / shortcut"页）不在本 fork 保留，
> 想看原版界面请去 [Relianttt/lightxin](https://github.com/Relianttt/lightxin)。本 fork 的界面见文首。


## 安装

> 见文首「安装」一节 —— 本 fork 的 APK 与源码编译方式都在那里，此处不再重复。



## 贡献

欢迎提交 Issue 和 Pull Request。



## 协议

本项目基于 [MIT License](LICENSE) 开源。



## 免责声明

本项目仅供学习交流使用。使用者须遵守相关法律法规，不得将本项目用于任何非法用途。因使用本项目产生的任何法律风险与责任，均由使用者自行承担。
