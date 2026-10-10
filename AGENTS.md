# Repository Guidelines

## Project Structure & Module Organization
This repository is a single-module Android app named 林小信 (applicationId `com.linxin`). Main code lives in `app/src/main/java/com/linxin`, with shared infrastructure under `core/`, navigation in `navigation/`, and product features under `feature/<feature>/data|domain|ui` (for example `feature/login/ui/LoginScreen.kt`). Resources are in `app/src/main/res`. Historical tests still sit under `app/src/test/java/com/lightxin/` (upstream package name) — keep new tests in sync with the package they exercise. The planning notes, reverse-engineering writeups (`codestable/`, `docs/`), UI mockups (`prototype/`) and captures (`HAR/`, `captures/`) are deliberately gitignored and **not in this repo**; never add anything like a HAR, a token, or a real-device screenshot to the public tree.

## Build, Test, and Development Commands
Use the Gradle wrapper from the repo root:

- `.\gradlew.bat assembleDebug`: build the debug APK.
- `.\gradlew.bat installDebug`: install the debug build on a connected device/emulator.
- `.\gradlew.bat lint`: run Android lint checks.
- `.\gradlew.bat test`: run local JVM tests.
- `.\gradlew.bat connectedAndroidTest`: run instrumentation tests on a device.

Open the project in Android Studio when working on Compose previews, resources, or emulator flows.

## Coding Style & Naming Conventions
The project uses Kotlin official code style and 4-space indentation (`.idea/codeStyles/Project.xml`). Follow package-first organization and keep feature code split into `data`, `domain`, and `ui`. Use descriptive suffixes such as `Repository`, `ViewModel`, `Screen`, `Api`, and `Models`. Compose components use PascalCase function names, Kotlin files use PascalCase, and Android/XML resources use `snake_case` such as `network_security_config.xml`.

## Testing Guidelines
There are currently no committed `app/src/test` or `app/src/androidTest` directories, so new work should add tests alongside the feature being changed. Put JVM tests in `app/src/test/java/...` and device tests in `app/src/androidTest/java/...`. Name files `SomethingTest.kt` or `SomethingInstrumentedTest.kt`. At minimum, run `lint` and `test` before opening a PR; use `connectedAndroidTest` for camera, navigation, or login flows that depend on Android runtime behavior.

## Commit & Pull Request Guidelines
Recent history uses concise Chinese task-oriented subjects, often with phase tags, for example `完成Phase9C UI精炼：首页叙事架构 + 我的页精简`. Keep commits focused on one change set. Commit messages must include both a subject line and a body; do not create title-only commits. The body must spell out what changed and what validation was run, preferably as short bullets matching recent commit history. PRs should include: what changed, affected screens/modules, commands run, and screenshots or recordings for UI updates. Link related docs or issues when the change is tied to a plan in `docs/`.
When writing multi-line commit messages from PowerShell, use real newlines such as the PowerShell escape `` `n `` or `git commit -F <message-file>`; plain `\n` is committed literally and will not render as a line break.

## Security & Configuration Tips
Do not commit `local.properties`, captured HAR files, tokens, or device-specific data. Keep secrets out of source and route network-related changes through `core/network/` so auth and interceptors stay centralized.

## Hard Constraints
These constraints are non-negotiable. Changing any of them requires consulting the full decision documents linked below.

- **No map SDK** — Location uses native `LocationManager`; coordinate conversion (WGS-84 → GCJ-02 → BD-09) is hand-written in `core/location/CoordinateConverter.kt`. Do not add AMap / Baidu / Tencent map or location SDKs. → `codestable/compound/2026-04-22-decision-no-map-sdk.md`
- **Running data dual RSA encryption** — When uploading running data, both field names AND field values must be encrypted with `publicKey2` via `RSAUtils.encryptSportData()`. Never encrypt only the values. `publicKey2` (running) and `publicKey` (login) are not interchangeable. → `codestable/compound/2026-04-22-decision-running-dual-rsa-encryption.md`
- **API protocol must match original app** — All external requests must match the original app's captured protocol exactly. Field names with typos (e.g., FIF's `couseItemId`) are kept as-is.
- **Check-in multi-header auth** — The check-in API (`fdygl.aiit.edu.cn`) requires 7 identity fields simultaneously; missing any one returns `-100 非法访问`. → `codestable/compound/2026-04-22-decision-checkin-multi-header-auth.md`
- **No personal data in the public tree** — real names (including teachers'), student IDs, account-level opaque ids (`memberId`, `extraId`), JWTs, tokens, cookie jars, HAR captures and real-device screenshots are all banned. Test fixtures must stay synthetic: `张三`, `示例同学`, `aiitexample0000`, sequential hex ids like `0123456789abcdef0123456789abcdef`. The 2026-10-10 release audit found a real coach's name and two live account ids had been copied out of captures into `SportsGradeMapperTest.kt` — do not repeat that. A JWT is worse than it looks because the payload is only base64, so plain greps miss it; decode before judging.
- The `codestable/…`, `docs/…`, `prototype/…` and `HAR/…` paths referenced below and elsewhere are **local-only documents**, not links — they are intentionally gitignored.
- **License: never copy code out of SukiSU-Ultra into this repo.** `SukiSU-Ultra/SukiSU-Ultra` is **GPL-3.0** while this fork is **MIT** (upstream 轻小信 MIT), so lifting its Kotlin would require relicensing the whole repo. Its bottom bar (`ui/component/FloatingBottomBar.kt`, `ui/component/miuix/animation/*`) is a *reference for behaviour and numbers only*. When the same capability is needed, take it from the Apache-2.0 source instead — `DampedDragAnimation` / `InteractiveHighlight` live in Miuix's own example app (`compose-miuix-ui/miuix` → `example/shared/src/commonMain/kotlin/component/animation/`, Apache-2.0, itself adapted from Kyant0/AndroidLiquidGlass Apache-2.0). Vendored Apache sources keep their `Copyright` + `SPDX-License-Identifier` header plus a note of what was changed — same rule as `core/designsystem/liquid/LiquidLens.kt`.

## 项目碎片知识

<!-- cs-note managed: 用 cs-note 维护，新条目按下面分节追加 -->

### 液态玻璃底栏（Kyant0 Backdrop）
- 首页底栏实现在 `feature/home/ui/LxBottomBar.kt`（只有一个 `LxBottomBar`，无指示器层），LIQUID 材质 = Kyant0 Backdrop 官方用法：`drawBackdrop(shape = { Capsule() }, effects = { vibrancy(); blur(模糊半径); lensWithDispersion(带宽, 位移量, 色散, depthEffect = true) }, highlight = { Highlight(...) }, onDrawSurface = { drawRect(White.copy(面色 alpha)) })`。取样源是 `rememberLayerBackdrop()`，挂在首页那个包 `HorizontalPager` 的内容 Box 上（`.layerBackdrop(glassBackdrop).hazeSource(hazeState)`）。
- **参数集中在 `LxBottomBar.kt` 的 `private object LxGlass`，不要在调用点写魔法数。** 语义按库的定义用：`refractionHeight` 是折射带宽度（只有边缘往里这么宽的区域真弯），`refractionAmount` 是位移量=扭曲。**位移量上限 24dp 是死线**（曾按到 64dp，真机上高饱和课表内容直接被折成实心灰），所以"扭曲度"滑杆只能往右到 24dp（`lensAmountDp = LensAmountMaxDp * distortion`，默认 1f = 满 24dp）。其余区间：模糊度 → 模糊半径 2→12dp（越大越糊）；折射度 → 折射带宽度 14→28dp；色散 → AGSL 的 `chromaticAberration` 0→1。四根滑杆都放在中间值附近时 ≈ 官方示例的 8dp / 24dp / 满色散。
- **色散滑杆必须有 `core/designsystem/liquid/LiquidLens.kt`，库里的 `lens()` 给不了。** 官方 `lens(chromaticAberration = true)` 把那个 uniform **写死成 1f**，只能开不能调；而 AGSL 里它实际是个 0..1 乘数（`dispersionIntensity = chromaticAberration * (居中坐标乘积 / 半尺寸乘积)`）。`LiquidLens.kt` 用公开的 `runtimeShaderEffect(key, shaderString, "content") {}` 提交了官方 dispersion AGSL 的一份逐字副本（Apache-2.0 归属在文件 KDoc），只把 uniform 换成可调值——改这段 shader 前先确认官方那版有没有变，别自创系数。`dispersion = 0` 时六个采样点重合，输出与不带色散的折射完全一致。注意 `vibrancy()` 是 **saturation 1.5（提高饱和度）**，它不会把颜色洗成灰——早先把灰块归因于 vibrancy 是错的。
- **设置页滑杆走 `ThemePrefs`**：`glassBlur` / `glassRefraction` / `glassDistortion` / `glassDispersion`（key 同名，`floatPreferencesKey`，默认 0.5f / 0.5f / 1f / 1f）。**模糊度对液态和毛玻璃都有效**（同一根条，两种材质各自的区间；毛玻璃区间 6→30dp 的中间值正是原先写死的 18dp），后三根只有液态吃——所以 ThemeScreen 的显示条件是 `barMaterial != SOLID` 出模糊度、`== LIQUID` 才出其余三根。旧的 `glassClarity`（语义"越大越通透=越不糊"，方向反直觉）已废弃，别再往回改。DataStore 的坑：data-class 默认值和 `?: 默认` 兜底**两处都要改**，只改一处会静默用旧默认。
- **栏内那层玻璃滑块只能按自己的尺寸重算参数，别照搬栏体。** 09-29 曾把 `LiquidTabPanel` 整体废弃（栏体那套折射搬到 46dp 小胶囊上，折射带 28dp + 位移 24dp 比胶囊还高，整块折成平灰片）。10-09 又把"跟随选中项的玻璃胶囊"做了回来，条件是：不吃设置页三根折射滑杆，折射带锁在胶囊高度一半以内、模糊只跟"模糊度"一根；`SOLID`/`FROSTED` 走各自的填充/Haze，绝不叠第二层 `drawBackdrop`。10-10 再改成跟手：栏上加一层 `PointerEventPass.Initial` 的 `pointerInput`（只看手指不消费，所以每格自己的 clickable 照旧），按下时动画 spec 换 `snap()`、抬手换 `spring()`，`translationX` 必须走 `graphicsLayer`——`Modifier.offset {}` 的 lambda 只在重测时取值，会慢一格。**10-10 下午按他"照酷安的效果"改了几轮，现行口径如下，别再往回改：** 水球整体对齐 SukiSU Ultra 的 `FloatingBottomBar`（那份文件是 GPL-3.0，只能看数值，代码是自己在 Kyant0 Backdrop 上重写的）：**一整格宽（`cellPx`）、56dp 高、`CircleShape`、位置 = `drag.value * cellPx + panelOffset`**，不按内容收边、也不做居中偏移 —— 所以拖动中它永远不会跳大小（上一版按每格内容实测宽度，拖过半格换页就在手里变大小，被他打回过）。静止是一块 10% 平色（浅色黑 / 深色白），按住才淡出并长出折射：`lens(10dp·p, 14dp·p, depthEffect, chromaticAberration 0.5)`，水球自己不加 blur，高光用宽度当 alpha（`HighlightWidth * 2 * p`）。运动引擎是 `core/designsystem/drag/LxDampedDrag.kt` 的 `LxDampedDragAnimation`（Miuix 官方示例的 Apache-2.0 逐字副本，只改 package、类名加 Lx、`positionChange()` 换成 `position - previousPosition`）：`value` 连续浮点带阻尼和速度跟踪，`scaleX`/`scaleY` 两根不同阻尼弹簧（0.6 / 0.7），`pressedScale = 90f/56f`（按住鼓到 90dp，栏只有 64dp，所以这颗泡会顶出栏子的上下边界，这是照第二张酷安截图定的）；再叠 SukiSU 的速度形变 `scaleX /= 1 - clamp(v/10*0.75)`、`scaleY *= 1 - clamp(v/10*0.25)`，这就是他说的"加速变化"。选中那格图标+文字按 `1 → 1.2` 放大。**拖动途中页面不许动，抬手才 `onSelected`**（他原话"我想要滑块滑的时候界面不动放手了才切换相应界面"），外部换页走 `LaunchedEffect(selectedIndex) → animateToValue`。整条栏跟着手指最多偏 4dp（`EaseOut` 衰减 + `spring(1f, 300f, 0.5f)` 弹回）。**还差两处，需要 Miuix 的 `miuix-blur` 模块（0.9.4 已发布，Apache-2.0）才能照做：** `innerShadow(8dp·p)`、以及随重力旋转的 specular 高光。
- **底栏的层次和不透明度（他原话"那个残影太明显了，调淡一点"+"你去酷安截图参考"）。** 外层 `Box` 是三个兄弟节点，绘制顺序 = 栏体（只当背景）→ 水球 → 标签层。水球必须是栏体的**兄弟**（不被栏体那圈 clip 剪住才鼓得出栏外），又必须画在标签**下面**（beta29 把它排成最后一个节点，按住那颗白泡直接把"课程表"三个字吞了）。两张酷安参考实测：静止那颗 `#E2E2E2`、内部方差 0，按住那颗 `#FCFCFC`、内部亮度范围只有 246..252，也就是泡里面读不出任何内容，字全部压在泡上面。所以按住那层平色按 `#FCFCFC · LxGlass.PressOpacity = 0.97` 画（原来是 0.94 纯白，剩下 6% 会把页面标题的笔画透出来，就是他说的那个残影）；静止那颗仍是 10% 平色不动（真机复测三个采样点亮度差 ≤0.1，和他没抱怨过的那版一致）；隐形标签副本录进 backdrop 时只给 `LxGlass.GhostAlpha = 0.35`。
- **玻璃栏内的可点格子必须关掉触摸水波纹**：`clickable(interactionSource = remember(tab.label) { MutableInteractionSource() }, indication = null)`。默认 indication 是一层约 10% 黑的灰色圆角矩形，叠在折射上就是"点哪一格哪一格发灰"，ColorOS 上松手后还可能不消失。栏内任何元素都不许再画 indication/遮罩，选中态只用颜色。
- **玻璃要有彩色可折，否则就是实心白。** `LayerBackdrop` 采的是页面上真正合成的像素，静止时底栏那块正好是空白背景，折射出来就是一片白。所以 `AmbientBottomGlow` 必须放在**采样 Box 内、`HorizontalPager` 之上**（作为最后一个子节点）：放在 pager 下面会被不透明页面挡住，等于没画。渐变 alpha 保持很低（0.06→0.16），只给玻璃一个彩色源，不能把页面内容染色。
- `LxParchment` / `LxTerra` 这类 `Lx*` 色是 `@Composable` getter，**不能**在 `hazeEffect {}` / `onDrawSurface {}` / `effects {}` 这类非 Composable lambda 里直接读，必须在 Composable 作用域里先 hoist 成局部 `val`。
- 依赖版本锁 `backdrop 2.0.0` / `shapes 1.2.0`（见 `gradle/libs.versions.toml` 注释）：2.0.1/1.2.1 是 Kotlin 2.4.x 编的，本项目 Kotlin 2.2.10 读不了它的元数据。`Capsule` 在 1.2.0 里已有，不用升版本。

### 编译与构建
- WSL 下没有 JDK，gradle 必须用 Windows 终端跑 `gradlew.bat`（`org.gradle.java.home` 指向 Android Studio 的 jbr，是 Windows 路径，WSL 下无效）。

### 运行与本地起服务

### 测试
- `.\gradlew.bat :app:testDebugUnitTest` 可跑。注意：`app/src/test` 下部分测试文件的包名仍是历史遗留的 `com.lightxin.*`（主源码是 `com.linxin.*`），这类测试会因为引用不到主源码符号而编译失败；改测试时把 `package` 和 `import com.lightxin.` 一起换成 `com.linxin.`。

### 已移除的功能
- **检测更新（09-29 按用户要求整体删除）**：`feature/update/`（GitHub Release API、UpdateRepository、AppVersion、ApkDownloader、UpdateNetworkModule）、`core/settings/UpdatePrefs.kt`、`UpdatePromptViewModel` + 首页更新弹窗、关于页"检测更新"行、我的页"发现新版本"红字提示、`MainActivity` 冷启动自动检查、`REQUEST_INSTALL_PACKAGES` 权限、`AppVersionTest` 全部已删。`FileProvider`（`${applicationId}.fileprovider` + `xml/file_paths.xml`）**必须保留**——签到拍照分享还在用。若以后要恢复更新能力，需要重新引入版本比较和下载，不要只恢复 UI。
### 命令与脚本陷阱

### 路径与目录约定

### 环境变量与凭证

### 其他
