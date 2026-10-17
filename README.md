# 安大信
安徽信息工程学院学生用的 Android 客户端，替掉官方「安小信」。一个账号登进去，课表、查寝、跑步、待办、校园卡都在里面，
不再套一层 H5，也不再被推送淹没。

包名 `com.linxin`，仓库名还叫 `linxiaoxin`，应用显示名从 v1.3.7 起是「安大信」。

## 现在有什么

账号与登录

- 校方统一登录。密码可选记住，密钥在 Android Keystore 里且不可导出，明文不落盘
- 登录失效会明确告诉你是哪一步失效、该去哪里重新登录，不静默吞掉
- AI 课堂那条单点登录链路在首页后台预热，进二级页不用等它现建会话

课表与校园信息

- 课程表、考试成绩、素质学分、劳动教育、新闻公告
- 待办事务：我的待办、已办、流程表单详情。请假表单按它 H5 的真实结构渲染，销假要先把超假信息读出来给你看再提交
- 消息中心：门户消息列表、未读筛选、一键已读（逐条写给服务端，不用厂商那个批量接口，它回成功却不动数据）
- 本地轮询四路推送提醒：新公告、查寝待签、返校提醒、消息中心。15 分钟内要上课时自动把检查频率提到一分钟一次

查寝与考勤

- 查寝签到、本月统计、签到月历（走历史端点，能翻看上个月的记录）
- 节假日离返校独立成页，去登记和历史登记双标签
- 锻炼考勤：俱乐部详情、扫码签到

跑步（智慧体育）

- 整块按官方包抓包和反汇编重写，接口形状跟官方一致，学校那边认这条记录
- 三秒一个点，坐标一路 BD-09，相邻两点甩出 120 米以上当没看见
- 上传时十个字段名 RSA 加密，轨迹字段保持明文。一起加密会被服务器判「运动轨迹异常，无效」
- 内置地图用 osmdroid 加高德街道瓦片，不接任何厂商地图或定位 SDK
- 开始、运动中、运动记录、运动明细、考勤维护记录五屏照官方排布
- 运动中那条通知会提升成各家定制安卓系统的实况通知，ColorOS 叫流体云、HyperOS 叫超级岛。
  卡片三行是公里数、状态、实时速度和时长，进度条按服务器下发的「单次要跑够几公里」走

校园卡与报修

- 校园卡余额、明细分页、充值走校方收银台
- 报修单列表与进度、全校服务清单
- 付款和提交类动作一律停在最后一步，由人按，也不自动重试失败的写操作

界面与交互

- 全站 Miuix，白底加现代蓝，深色同步
- 首页悬浮液态玻璃底栏（Kyant0 Backdrop），模糊、折射、扭曲、色散四根参数在设置里实时调
- 侧滑返回预示应用自己实现：当前页跟手缩放淡出，返回途中页面模糊并慢慢回清
- 中英双语，文案全在 `strings.xml`，每次发版跑一遍中英键集合 diff
- 全站字体 MiSans VF，许可记录见 `MISANS-NOTICE.txt`

## 还没做的

- 待办事务里「同意 / 驳回」这两个审批动作
- 跑步的「申请维护」提交，缺一条真实报文
- 锻炼考勤还挂在早期那套数据层上，没按抓包重写

## 安装

正式版在 [Releases](https://github.com/linjianwuovo/linxiaoxin/releases/tag/v1.4.1)：
`linxiaoxin-1.4.1.apk`，正式签名，minSdk 26（Android 8.0+），arm64-v8a。
直链 https://github.com/linjianwuovo/linxiaoxin/raw/apk/linxiaoxin-1.4.1.apk ，
md5 `5f6afd9c5987c244ea897647e1ce5fec`。

v1.3.7 及更早的包是 Android 调试 key 签的，1.3.8 起换成正式签名，两者不能互相覆盖安装：
装 1.3.8 之后版本要先卸载旧包，登录态会丢。1.3.9 起各版都是同一把钥匙，直接覆盖安装就行。

## 从源码编译

需要 Android Studio 和 JDK 17 以上（本机用 JDK 21 编的）。

```bash
git clone https://github.com/linjianwuovo/linxiaoxin.git
cd linxiaoxin
./gradlew assembleDebug
```

字体那 20MB 的 `app/src/main/res/font/misans_vf.ttf` 被 `.gitignore` 挡在仓库外（仓库里只有引用它的
五个 `misans_w*.xml`），所以光 clone 会在资源链接那一步报 `resource font/misans_vf not found`。
两条路：去小米官网下 MiSans VF 把这个文件放回那个路径，或者直接下仓库 `apk` 分支上的源码包
`linxiaoxin-1.4.2-src.zip`，那份是连字体一起打的、解压就能编。

编 release 需要你自己的签名钥匙：在仓库根放一个 `keystore.properties`（已 gitignore），
`storeFile` 指向密钥库文件。没有它编出来的是 `app-release-unsigned.apk`。
密钥库本身和口令永远不进仓库。

想跟正在用的包并排装机做冒烟测试，加 `-PappIdSuffix=.rtest`，会编成 `com.linxin.rtest` 这个另一个应用。

## 技术栈

Kotlin 2.4、Jetpack Compose（BOM 2026.03）、Miuix 0.9.4、Hilt、Retrofit 加 OkHttp、
DataStore 存本地状态（没有用 Room）、WorkManager 做本地轮询、CameraX 加 ML Kit 扫码、
osmdroid 6.1.20 做地图、Kyant0 Backdrop 做玻璃。Gradle 9.3.1，全部依赖版本锁在
`gradle/libs.versions.toml`。

代码结构是单模块：`core/` 放网络、鉴权、定位、地图、通知、设置这些公共层，
`feature/<模块>/data|domain|ui` 放各功能，`navigation/` 一张路由表。

## 边界

- 公开树里不放真实姓名、学号、账号 id、JWT、token、抓包文件和真机截图，测试夹具全部用合成数据
- 不复制 GPL 组件的代码进这个 MIT 仓库。参考只看行为和数值，同样的能力从 Apache-2.0 的上游拿，并保留归属注释
- 校方接口的字段名照原样保留，包括它拼错的那些，改了服务器不认

## 与上游

本仓库 fork 自 [轻小信 / Relianttt/lightxin](https://github.com/Relianttt/lightxin)（MIT），
上游 `LICENSE` 的版权声明原样保留，这里的改动同样按 MIT 开源。
最初只是嫌安小信太卡，后来网络层、跑步、通知、地图、底栏、待办这些基本都重写了，
现在绝大部分代码是这个仓库自己写的，上游那份 README 也不再贴在下面。

## 免责声明

本项目仅供学习交流使用，与校方无任何隶属关系。使用者须遵守相关法律法规和校方规定，
不得用于任何非法用途。因使用本项目产生的风险与责任由使用者自行承担。
