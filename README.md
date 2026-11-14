# GlobalAnnotate · 全局批注（小米平板 / 焦点触控笔 Pro）

仿华为「全局批注」的第三方实现，面向 **Xiaomi Pad 8 / 8 Pro + HyperOS 3/4 + 小米焦点触控笔 Pro**。
独立新建工程，不改动同目录下的 `DrawAnywhere/`、`FocusPenProX/`、`aar/` 三个已有项目。

工程分两个 APK：

| 模块 | 包名 | 作用 | 是否需要 root |
|---|---|---|---|
| `:app` | `com.globalannotate.app` | 悬浮批注层、画布、工具环、导出 | 否 |
| `:xposed` | `com.globalannotate.xposed` | 全局笔手势呼出、右上角划入、按 toolType 分流输入 | 是（LSPosed，libxposed API 102） |

---

## 1. 呼出方式（三级）

| 优先级 | 方式 | 条件 | 实现 |
|---|---|---|---|
| 1 | **笔尖从右上角向内划（推荐悬停划入）** | root 模块 | `InputRouter` 在 `filterInputEvent` 里**只读坐标**、事件一律 `chain.proceed()` 下发；识别到向内滑动就广播呼出，不吞任何点击、笔迹或手指操作 |
| 1 | **笔身轻捏 / 双击** | root 模块 | `GestureLauncher` hook `MiuiStylusTouchFilmManager.interceptKeyBeforeQueueing`，全局生效，与前台是谁无关 |
| 2 | 悬浮球「批注」 | 免 root | `AnnotationService` 常驻小窗 |
| 2 | QS 磁贴 / 通知栏点击 | 免 root | `AnnotationTileService`、`GestureLaunchReceiver` |
| — | ~~右上角感应窗口~~ | 已移除 | 免 root 下任何局部感应窗都会吃掉该区域触摸，故取消；免 root 呼出统一用悬浮球与 QS 磁贴 |

> **触控不失效**：角落手势只做坐标观察，不预留区域、不拦截、不吞事件。推荐笔尖**悬停**从右上角向内划（`ACTION_HOVER_*`，完全不接触屏幕）；接触式划入也支持，事件同样照常下发，只是底层若是手写类 App 可能顺带留下一笔。
**为什么必须 root 才能「任意界面双击笔身呼出」**：`194–197` 这些 KeyCode 只发给**当前焦点窗口**，应用不在前台时收不到。root 模块在 system_server 的按键策略入口拦截，才做到全局。这也是现有 DrawAnywhere 只能放在侧边栏快捷应用里打开的根因。

## 2. 交互范式（对齐 Apple Pencil，不做按键式映射表）

这支笔和 Apple Pencil 一样没有实体按键，所以按手势语义而不是「按键→动作表」来设计：

| 笔的动作 | KeyCode | 行为 |
|---|---|---|
| 轻捏笔身 | 194 / scan 189 | 在笔尖位置弹出**径向工具环**（笔、橡皮、红、蓝、黑、撤销、清空、关闭） |
| 双击笔身 | 195，或两次 194 | 笔 ↔ 橡皮（当前工具 / 上一工具） |
| 笔尖悬停 | `ACTION_HOVER_*` | 笔尖光圈预览（标准 Android API） |
| 笔身上下滑 | 196 / 197 | 增 / 减笔宽 |
| 隐藏状态下任意笔手势 | — | 先呼出批注层，之后再按上表解释 |

隐藏状态下，双击/轻捏等价于「拿起笔就要批注」，和 iPad 上点一下 Pencil 的逻辑一致。

## 3. 三种模式（工具栏「模式」按钮循环切换）

- **拦截（DRAW）**：全屏浮层接管输入，只认 `TOOL_TYPE_STYLUS`；此模式下绑定 `com.xiaomi.touchservice` 抢 194–197。
- **穿透（PASSTHROUGH）**：浮层 `FLAG_NOT_TOUCHABLE`，手指正常操作底层 App，但不能写画。
- **分流（ROOT_SPLIT）**：浮层 `FLAG_NOT_TOUCHABLE`，stylus 事件由 system_server 经 Binder 转发进 `AnnotationInputService`，finger 事件原样放行 —— 这是第三方能达到的、最接近华为「边滚边写」的形态。

> Android 12 的 *untrusted touch events* 只允许「足够透明」的浮层穿透，因此穿透/分流模式把窗口 `alpha` 设为 `0.79`，笔迹会略微变淡。这是已知折中。

## 3.1 笔刷与工具栏（HyperOS/Miuix 视觉）

单行底部胶囊（对齐小米创作）：26dp 圆角、`#242424` 深色底、无文字。左侧撤销/重做，中间 8 支写实笔刷，右侧 4 个快捷色点 + 彩虹轮 + ⋮ 菜单 + **常驻退出键**。选中的笔会向上「抽出」一截。收起后右下角留一个大圆按钮，显示当前笔刷与颜色。

- **8 种笔刷，书写质感各不相同**：
  | 笔刷 | 效果 |
  |---|---|
  | 铅笔 | 石墨颗粒 + 低透明度，边缘有噪点 |
  | 圆珠笔 | 细线、几乎不吃压感，中心一道油墨高光 |
  | 钢笔 | 强压感，笔锋随力度变化 |
  | 马克笔 | 方形笔尖、半透明叠加 |
  | 荧光笔 | 方形笔尖、更透，适合覆盖文字 |
  | 激光笔 | 外发光 + 1.4 秒渐隐 |
  | 压感橡皮 | 擦除，笔宽随压力变化 |
  | 笔画橡皮 | 整笔擦除 |
- **工具栏可拖动**：按住栏内空白处拖动即可移到任意位置，位置写入 SharedPreferences，下次呼出仍在原处；松手后吸附在屏幕内。点按钮不会误拖（按下点落在按钮上时不触发拖动）。
- **面板行为**：点面板外任意位置（含落下笔尖）立即收起；面板以 96%→100% 缩放 + 淡入淡出（150ms，`PathInterpolator(0,0,0.2,1)`，即 Miuix 的强调缓动）出现和消失，不再是硬切。
- **三个独立悬浮面板**（都不挤在主栏里）：
  - 点当前笔刷 → **笔刷设置**：笔迹预览 + 笔画粗细 / 压力灵敏度 / 笔尖大小 / 不透明度 四条滑块；行数随笔型变化（铅笔只有粗细/不透明度，钢笔多出灵敏度与笔尖），**窗口高度按实际行数计算**，不再出现半截面板
  - 点彩虹轮 → **颜色面板**：「网格取色 / 滑块取色」双 tab、12×8 色相网格、棋盘格不透明度滑块、当前色大方块 + 快捷色、右上角吸管；面板高度同样是算出来的（标题 20 + tab 44 + 网格 210 + 不透明度 26 + 快捷色 64 + 间距），不会被窗口裁掉
  - 点 ⋮ → **菜单**：插入文本/形状、自动最小化、用手指涂画、切换批注模式、导出 PNG/PDF、清空画布（**退出不在这里**）
- 吸管走 root `screencap` 取笔尖像素，零授权跨应用取色。
- 笔刷配置（类型、颜色、粗细、压感灵敏度、笔尖大小、不透明度）用 SharedPreferences 持久化。
- 轻捏唤出的径向工具环同为深色卡片风格。

> **图标来源**：撤销/重做/退出/⋮ 等线性图标为自绘矢量；主栏 8 支笔与收起态大圆按钮**直接取自小米创作 APK 内的原始资源**，8 张互不重复：`res/CL.png` 铅笔、`res/g5.png` 圆珠笔、`res/FW.png` 钢笔、`res/_q.webp` 马克笔、`res/9t.png` 荧光笔、`res/-7.png` 激光笔、`res/CT.png` 压感橡皮、`res/-Z.png` 笔画橡皮（透明 PNG/WebP，最高 664×1188），置于 `drawable-nodpi/`。笔杆上的当前色标记由代码运行时叠加，与原版一致。这些资源仅供个人自用；仓库为 public 时请注意分发风险。

## 4. 功能取舍（个人开发、无企业授权）

**没有**打包 `PenEngine-release_0.3.2.aar`，**没有** App ID / `MiuiAuthHelper` / 任何鉴权调用。`PenEngineClient` 只是复刻了免鉴权的 Binder 握手（`registerListener(cb, packageName)` + `setEnable(2,1)`），这正是 194–197 能到达本应用的原因。

| 官方 SDK 能力 | 处置 | 说明 |
|---|---|---|
| 文字识别 | **不做** | 批注软件用不上，且需授权 |
| 笔迹预测 / 一笔成形 | 官方版不接 | 用 `app/…/ink/SmartInk.kt` 零授权近似：历史采样 + 速度外推、几何形状识别 |
| 跨应用取色 | 官方版不接 | **已用零授权方式实现**：工具栏吸管 → root `screencap -p` → `SmartInk.pickColor()` 取笔尖像素 |
| 手写笔侧旋 | 官方版不接 | `SmartInk.orientationDegrees()` 用标准 `AXIS_ORIENTATION` |
| 跨应用取色 / 侧旋 / 悬浮预览 / 触控按键 | 备注 | 逆向显示 `MiuiAuthHelper` 仅在 `e/i.java` 出现，`MiuiColorPick`、`MiuiStylusPosture` 都未引用 —— 它们大概率也走 `setEnable` 而非 App ID。但按本项目范围一律不接 |

`SmartInk.kt` 里 `pickColor()` 已接入吸管；`recognize()` / `idealise()` / `predict()` 仍是自包含工具库、默认未接入 UI（避免未经真机验证就改写笔迹）。接入点：抬笔时在 `AnnotationCanvasView.end()` 调 `recognize()` + `idealise()`，MOVE 时用 `predict()`。

## 5. 目录结构

```
GlobalAnnotate/
├─ app/                       # 主应用
│  └─ src/main/java/com/globalannotate/app/
│     ├─ AnnotationService.kt        # 全部悬浮窗 + 生命周期 + 模式 + 导出
│     ├─ AnnotationInputService.kt   # 接收 system_server 转发的 MotionEvent
│     ├─ GestureLaunchReceiver.kt / AnnotationTileService.kt / MainActivity.kt
│     ├─ Pen… (stylus/)              # 免鉴权 Binder 客户端、194–197 识别
│     ├─ canvas/ widget/ toolbar/ model/  # 画布、工具环、胶囊工具栏、笔刷模型
│     └─ ink/SmartInk.kt             # 零授权自研替代
├─ xposed/                    # libxposed API 102 模块
│  ├─ src/main/resources/META-INF/xposed/
│  │  ├─ java_init.list   → com.globalannotate.xposed.GlobalAnnotateHook
│  │  ├─ module.prop      → minApiVersion=102 / targetApiVersion=102
│  │  └─ scope.list       → system
│  └─ src/main/java/com/globalannotate/xposed/
│     ├─ GlobalAnnotateHook.kt  # extends XposedModule, onSystemServerStarting
│     ├─ GestureLauncher.kt     # 全局笔手势呼出
│     ├─ InputRouter.kt         # 角落划入 + toolType 分流
│     └─ AppBridge.kt           # 广播 + 单向 Binder 转发
└─ .github/workflows/android-ci.yml
```

**API 102 要点**：入口是 `META-INF/xposed/java_init.list`（不是 `assets/xposed_init`），配置是 `module.prop`（不是 Manifest meta-data），scope 里 system_server 写 `system`（不是 `android`），hook 用 `hook(method).intercept { chain -> … }` —— **不调用 `chain.proceed()` 即消费事件**。构建用 Maven 上真实的 `io.github.libxposed:api:102.0.0`（`compileOnly`），不捆绑旧 XposedBridge。旧版 LSPosed 不兼容本模块。

## 6. 编译

CI 直接推 GitHub 即可（`.github/workflows/android-ci.yml`）：JDK 21 + `gradle/actions/setup-gradle@v4` → `:app:assembleDebug :xposed:assembleDebug` → 上传两个 APK。

> 不要加 `android-actions/setup-android@v3`：它会尝试安装已下线的 legacy `tools` 包并报 `Failed to find package 'tools'`。`ubuntu-latest` 自带 Android SDK。

本地手动编译（需 JDK 21 + Android SDK Platform 37）：

```powershell
.\gradlew.bat :app:assembleDebug :xposed:assembleDebug
```

本次交付**只做了静态自查，没有在本地编译**（按要求编译在 GitHub 进行）。自查结果见第 9 节。

## 7. 安装

1. 安装 `:app` 的 APK，打开一次 → 授予「显示在其他应用上层」和通知权限，前台服务随即常驻。
2. 安装 `:xposed` 的 APK，在 **支持 libxposed API 102 的 LSPosed** 中启用，确认 scope 为 `system`，**重启**。
3. 重启后主应用服务启动时会广播 `SET_MODULE_ENABLED=true` 打开模块总开关；所有 hook 默认 fail-open，模块不生效时设备输入行为完全不变。


### KernelSU 注意事项

- **KernelSU 本体不带 Zygisk**，LSPosed 必须配合 **ZygiskNext**（或同类 Zygisk 实现）才能注入进程，这是 KSU 官方 FAQ 明确的。没有它，模块根本不会加载，日志里连 `system server starting` 都不会出现。
- 版本要求：ZygiskNext 需要 KernelSU ≥ 10940、ksud ≥ 11575；支持 libxposed API 102 的新版 LSPosed 需要 **KernelSU ≥ 11903**。
- 不要同时启用 Magisk 与 KernelSU 的模块系统（挂载冲突），只保留一套 root 方案。
- 在 KernelSU 管理器里给 `com.globalannotate.app` 授予**超级用户**。这是一次性的持久授权，所以带底层内容的导出不会每次弹同意框（正好避开 MediaProjection 的重复授权问题）。
- 带屏幕底图的 PNG/PDF 走 `su -c screencap -p`；KSU 的 `su` 被拒绝时导出自动回退白底，并在 logcat 打印 `root screenshot unavailable: exit=… stderr=…` 便于定位。
- 装完 ZygiskNext 与 LSPosed 后建议**重启两次**（一次装载 Zygisk 实现，一次激活框架）再开始验证。
## 8. 真机验证清单（重要）

hook 点来自 FocusPenProX 在 `OS3.0.307.0.WOCCNXM` / `OS4.0.4.0.XPYCNXM` 的验证记录，**未在你的 ROM 上实机核对**，请按顺序验：

1. `unzip -l xposed-debug.apk | grep META-INF/xposed` —— 确认三个描述文件确实打进了 APK（`packaging.merges` 生效）。
2. LSPosed 里能看到模块名/描述；日志出现 `GlobalAnnotate: loaded: … system=true, api=102`。
3. 日志出现 `key policy hooked on N methods` 和 `input filter hooked on N methods`。若后者为 0，按 `CompatProfile.INPUT_SERVICE_CLASSES` / `FILTER_METHOD` 对照你的 ROM 补类名与方法名。
   - **KernelSU 专项**：若日志完全没有 `system server starting`，先查 ZygiskNext 是否生效、LSPosed 是否显示框架已激活；再确认框架具备 hook system_server 的能力（`PROP_CAP_SYSTEM`）。KSU 下最常见的问题就是缺 Zygisk 实现导致模块压根没被注入。
4. 日志出现 `module enabled=true` 与 `input sink connected`。
5. 任意 App 里双击笔身 → 批注层出现；轻捏 → 笔尖位置出现工具环。
6. 笔尖从右上角向内划 → 批注层出现；**同时回归验证**该区域单击、正常书写起笔、手指滚动全部照常生效（本次改动的核心检查项）。
7. 切到「模式:分流」→ 手指滚动/点击底层 App 正常，笔写进批注层。
8. PNG / PDF 导出 → 图里不含工具栏、悬浮球或模糊背景框（`withOverlaysHidden` 会隐藏所有自有窗口再截图）。

## 9. 已知限制

- 无 OCR；无长图滚动批注（当前只导单屏 PNG/PDF）；无 MediaProjection 路径（带底层内容导出走 root `su -c screencap -p`）。
- 角落手势是**观察式**：事件一律照常下发，右上角不是禁区、不会吞点击。代价是若底层 App 本身支持手写，接触式划入可能顺带留下一笔；推荐用**悬停划入**（`ACTION_HOVER_*`，笔尖不接触屏幕，零痕迹）。若 ROM 的 `filterInputEvent` 收不到 hover 事件，才退回接触式。
- 只有「模式:分流」会消费 stylus 事件（把它路由进批注层），这是该模式的设计目的；**手指始终 100% 放行**。想用笔操作底层 App 时切回「穿透」即可。
- 穿透/分流模式 `alpha=0.79`，笔迹略淡（Android 12 untrusted-touch 豁免要求）。
- `filterInputEvent` 是否被调用、以及 `interceptKeyBeforeQueueing` 的返回值语义，需在真机确认；所有 hook 均 fail-open，异常时原事件照常下发。
- `SmartInk` 的自研形状识别 / 笔迹预测默认未启用；跨应用取色（吸管）已启用，需要 KSU root 授权。
- 模块总开关靠广播控制，进程重启后默认关闭，需主应用服务重新广播。

## 10. 与既有资料的关系

- `DrawAnywhere/`：本工程**未改动**，仅参考其悬浮层与导出思路。
- `FocusPenProX/`：本工程**未改动**，`CompatProfile` 的类名取自它已验证的 hook 点。
- `WondersReady/FocusPen` 的 `docs/INPUT_ROUTING.md`：提供了按键分发链与「DOWN/UP 必须成对消费」的依据。
- `PenEngine-release_0.3.2.aar` 的 jadx 逆向结果：确认 `194–197`、touch-film 握手免鉴权。

许可证沿用各自上游；本工程新增代码按需自行选择许可证后再分发。


---

## 11. 2026-11-14 现状（本轮重写）

- **输入**：不再由 App 窗口声明 `INTERCEPTS_STYLUS`（普通 App 声明会让 `InputDispatcher` abort，导致 system_server 重启）。改由 root 助手（`su -c app_process`）创建受信任的手写笔输入面：笔进批注层、手指原样落到下层应用；助手同时读原始笔键盘（scan 189 轻捏 / 78 双击）、监听手指用于滚动跟随、并可临时解除输入窗口透明度限制。
- **小米创作控件直接复用**：运行时 `DexClassLoader + createPackageContext("com.miui.creation")` 加载其 APK，实例化原版 `ColorPickPanelView`、`SelectPropertyView`/`SelectAlphaView`、`ColorPickerHue/Saturation/Lightness/AlphaView`；混淆成员按真实名 `I1Iιili`（U+03B9）解析，失败按签名扫描并回退到自带控件。
- **工具栏**：槽位固定（笔 45×77dp，橡皮 64×77dp，不重排）；选中动效按小米创作实测实现——未选中外顶 18dp、选中回位，spring 300ms；18dp 行程在图标画布内完成，末端不会再被父容器裁掉。
- **弹窗**：锚定在工具栏上方，0.6→1.0/280ms + 100ms 淡入，退场 150ms 对称；宽 288dp（笔/橡皮）、338×536dp（颜色）、200dp（菜单），24/36dp 圆角与 elevation 阴影。
- **笔刷**：范围/默认值按 PenBean（圆珠笔 1–22/6、铅笔 1–6/3、钢笔 1–30/15、马克笔与水彩 5–65/30、笔尖 1–4、不透明度 3–255）；同一 size 同一压力下实测与小米创作笔迹粗细一致（31px vs 30px）。
- **墨迹**：沿路径的变宽带，纹理只调制 alpha；绘制中整笔重绘（不再拼接增量段），消除书写时的左右抖动；停笔 0.5s 一笔成形。
- **导出**：单屏 PNG/PDF 与滚动长图（root 注入滚动 + 重叠拼接 + 逐帧合成墨迹）。
- **未完成**：mိuix `FilterSortView` 原类 tab、更多菜单文案/图标的逐项核对、上/下滑（LSPosed 通道待修）、微博实机长图验证、原生 `libmicurve` 笔刷纹理（无法脱离其引擎复现）。
