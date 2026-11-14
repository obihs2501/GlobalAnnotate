# GlobalAnnotate 交接文档（2026-11-14）

## 结论先行
- 全局批注的输入链路已可用：root 助手创建"只接收手写笔"的受信任输入面，手指原样落到下层应用；滚动跟随、长截图导出代码已具备。
- **颜色弹窗已经是小米创作本体**（2026-11-14 下午）：宿主从 APK 实例化 `l1lII1ιιιιIl.I11IιIil`，把它的 contentView 挂进我们的悬浮窗——真 miuix tab、真色板、真两条滑块页、真颜色块、真最近颜色行。颜色/透明度双向打通（真机验证：点色板 → brush 颜色变化）。
- 工具栏（胶囊、工具槽、orb）、笔刷弹窗、橡皮弹窗、更多菜单仍是本工程自写，是下一步要搬的对象。
- 不要再用"手画一份更接近的仿制品"的方式改这些控件，差距只会累积。

## 本轮（2026-11-14 下午）做过什么

### 1. 交接清单里 13 条高危 bug 已修（`d252e17`，CI 绿）
busyExporting 用 finally 复位；START_STICKY 用 `enterIdle()` 显式初始化；helper ready 后补 `updateExclusion()`；
`createScaledBitmap` 同尺寸会返回原图导致释放后再渲染；`setSkipScreenshot` 改为按帧重试、弹窗类窗口在首次显示时才排除；
"手指涂画"真正接通（`applyInputMode` 读 `fingerDraw`、`canvas.allowFinger` 赋值、手指 spy 不再抢滚动）；
排除区按当前方向计算并在 `onConfigurationChanged` 重建；长截图提前返回释放位图、到达 MAX_HEIGHT 停止；
`Exporter` raw 解码扫描头部偏移（su 前缀不再让整帧解码失败）；颜色板/取色走 `McBridge` 带签名兜底；
面板关闭动画的 end action 用 epoch 守卫；`HelperClient.stop()` 先等助手退出再 destroy（避免全局不透明度开关留在 1.0）。

### 2. 真机上查出的两个"排除区不生效"原因（都改了）
- **坐标空间**：助手的触摸区就在**当前显示方向**的空间里，和 `getLocationOnScreen()` 一致。之前按 `ROTATION_90` 做旋转映射，把工具栏矩形搬到了别处，于是笔点工具栏仍在画线。实测证据：在映射后的矩形里点笔，助手不接管；在真实工具栏矩形里点，助手接管。现在直接发送原始屏幕矩形（`4896922`）。
- **刚显示的窗口宽度为 0**：`updateExclusion()` 用 `view.width` 组矩形，而刚 `VISIBLE` 的视图在下一帧之前 width=0，工具栏根本没进排除区。新增 `updateExclusionAfterLayout()`（下一帧再算一次）。
- 验证方法：`python _work/pen.py tap <工具栏坐标>` → logcat 应出现 `AnnotationService: tool tap BALLPOINT at …`，且工具栏不收起。

### 3. 让小米创作的 UI 真正能实例化（这是本轮的关键突破）
`MiCanvasUi` 原来只加载类，弹窗一 inflate 就崩，逐个修掉：
- **资源没有 classLoader**：`createPackageContext()` 跨应用不带代码，`DrawableInflater` 落到 boot loader，`<drawable class="miuix…">` 全部 InflateException。现在用反射设置 `Resources.mClassLoader`（`MiuiResources` 也走这个字段）并清空 `mDrawableInflater` 缓存。
- **`getApplicationContext()` 为 null**：跨包 Context 没有 Application，`Il1ilI1.lIil11`（浮动取色器）构造时 NPE。包装 Context 里直接返回自身。
- **`getSystemService(LAYOUT_INFLATER_SERVICE)` 返回系统 inflater**：它的 classLoader 是那个空 dex 的包 Context，`<view class="com.xiaomi.penpicker…">` 找不到类。包装 Context 里克隆 inflater 到自身。

### 4. 颜色弹窗的接口契约（照此继续搬其它弹窗）
- 类：`l1lII1ιιιιIl.I11IιIil`（jadx 名 `p249l1lII1Il.I11IIil`），构造 `(Context)`。**jadx 改名后的 `m12345xxx` 都不是运行时名**，例如颜色模型 getter 真名是 `lιIil11()`、alpha 是 `l1IIiiι()`、颜色是 `lIIIlll()`、设置颜色是 `Iι1liιi(int)`。
- 取 contentView 后从 PopupWindow 里摘出来挂到自己的窗口即可；不要用它的 `showAsDropDown`：它的 `setHeight` 被重写去改内容高度，`update()` 又会把 PopupWindow 高度重置成 wrap，箭头弹窗那套尺寸逻辑搬不动。
- 颜色变化回调：接口 `l1lII1ιιιιIl.IIiIιIiii1`（单方法），用 `Proxy` 实现，把弹窗里**所有该类型字段**都换成 proxy（字段可能声明在父类）。回调参数是 `l1lII1ιιιιIl.l1IIiiι`：`lιIil11()` 取 HSL 对象，`lIIIlll()` 取 ARGB，`l1IIiiι()` 取 alpha。
- 吸管：把 contentView 里 `com.xiaomi.penpicker.common.tools.PickerStrawTool` 的 OnClickListener 换成我们自己的截屏取色。
- 面板高度：直接给 contentView 定高（我们的 `showPanel` 已经给了 536dp），不要走它的 `IlIll1i()`。

## 仓库 / 构建 / 设备
- 仓库：`C:\softwares\claudecode\drawanywher\2026-09-22\GlobalAnnotate`，public `obihs2501/GlobalAnnotate`，master。
- 本地无 Android SDK，只能 CI 构建：`.github/workflows/android-ci.yml`（JDK 21；不要加 `android-actions/setup-android`）。
- 设备：小米 Pad 8 Pro 25091RP04C（adb serial `b8da38a5`），3200×2136@440dpi（density 2.75），Android 17，KernelSU 3.3.1 + ZygiskNext + LSPosed。
- 已授权：KernelSU 超级用户、`SYSTEM_ALERT_WINDOW`、通知。
- 工作脚本：`../_work/pen.py`（向 `/dev/input/event13` 注入真实手写笔：压感/倾斜）、`/_work/deploy.sh`（等 CI→下载→adb install）。

## 已验证可用
- 输入架构：`root/HelperMain.kt` 由 `su -c "CLASSPATH=<apk> app_process /system/bin com.globalannotate.app.root.HelperMain"` 启动；`InputManagerGlobal.monitorGestureInput` 得到 surface/channel 后重写 InputWindowHandle 为 `NOT_TOUCHABLE|INTERCEPTS_STYLUS|TRUSTED_OVERLAY`，仅 root 可做；另设手指 spy monitor 与原始笔键盘读取（scan 189=轻捏、78=双击）；`OPACITY` 命令临时解除输入窗口透明度限制。
- 手指穿透 + 笔批注同屏；滚动跟随（`AnnotationCanvasView.scrollBy`，内容坐标存储）。
- 粗细校准：同压感曲线、同位置，Mi Canvas 30px vs 本 App 31px。
- 弹窗内容：`ColorPickPanelView`、`SelectPropertyView`、`SelectAlphaView`、`ColorPickerHue/Saturation/Lightness/AlphaView` 均为小米创作 APK 内的类（`mc/MiCanvasUi.kt` + `mc/McBridge.kt`）。
- 笔刷范围/默认值按 PenBean：圆珠笔 1–22/6，铅笔 1–6/3，钢笔 1–30/15，马克笔/水彩 5–65/30，笔尖 1–4/2，alpha 3–255。
- 工具栏规格（来自源码 + 实测）：槽位 **124dp 长 × 48dp 宽（橡皮 68dp）**；胶囊 124dp（本轮改）；未选中工具外顶 **18dp**，选中回位（`spring(0.95,0.35)`，无缩放/无重排）；行程在图标画布内完成，避免父容器裁剪。
- 导出：`Exporter.captureScreenRoot`（raw screencap，~160ms/帧）、单屏 PNG/PDF、滚动长图拼接（`export/LongCapture.kt` + 助手 `INJECT` 命令）。

## 关键教训（不要再踩）
- **App 窗口声明 `INTERCEPTS_STYLUS` 会让 system_server abort 重启**：
  `Abort message: '<pkg> has feature INTERCEPTS_STYLUS, but is not a trusted overlay.'`（InputDispatcher::setInputWindowsLocked）。只有 root 创建的 surface 可以声明。
- 选中的"伸缩"是**平移动画**：未选中项固定外顶 18dp，选中项回 0；不是缩放，不要动 layoutParams。
- 小米创作笔刷 PNG 自带投影：工具行必须被胶囊覆盖，否则投影在壁纸上形成"灰晕"；本轮已把胶囊改成 124dp。
- 笔刷贴图要按原生尺寸绘制：贴图 48×123dp，槽位 124dp；用 77dp 槽会把笔缩到 63%。
- 增量拼接笔迹会产生左右抖动/接缝；现在绘制中整笔重绘（快照 + 全笔渲染）。
- 持久化坐标在窗口尺寸变化后会错位：已改用 `bar_x_v2/bar_y_v2`，默认离底 100dp。
- jadx 改名的成员不是运行时名：真实名含希腊字母，例如 `I1Iιili(int)`（U+03B9）。

## 小米创作逆向资料（本机）
- jadx 源码：`/tmp/micre/out/sources`（= `%LOCALAPPDATA%\Temp\micre\out\sources`）
- 反编译资源：`/tmp/micre2/res/resources/res`
- APK：`C:\softwares\claudecode\drawanywher\2026-09-22\_work\mc\miui_creation.apk`，dex 已解到 `mc\dex\classes*.dex`
- 关键类（jadx 名 → 真实用途）：
  - `p452lilii.I1Iili` 悬浮胶囊栏容器；`p129Iill.II11I` 工具槽；`p452lilii.lIil11` 工具行；`p452lilii.I11lii` 胶囊进出场；`p067IIiiIiiI.II11lli`（CapsulePenPicker，拖拽/折叠）
  - `p249l1lII1Il.*` + `com.xiaomi.penpicker.popup.ArrowPopupView`（弹窗库）
  - `com.xiaomi.penpicker.colorpicker.*`（已复用）
  - `com/miui/handwirting/common/data/bean/PenBean.java`（尺寸/默认值）
  - 真实方法名：渐变 `I1Iιili(int)`；进度 `I1iι1lι(int)`、`IlIlIii11ιli(int)`；刷新 `Iiliill()`；miuix `FilterSortView.addTab` = `l1li1ιι`，`TabView.setOnFilteredListener` 为 ASCII 真名，接口方法 `lιIil11`。

## 迁移架构（建议直接照此做）
1. `mc/McHost.kt`：`createPackageContext("com.miui.creation")` + `DexClassLoader(apk)`；提供带 ClassLoader/Resources 的 Context（已有 `McCanvasUi` 可复用）。
2. 反射 Proxy 实现其 presenter 接口（如 `IiII1IlI`、`IIiIιIiii1`），把笔列表、当前颜色、选中状态喂给其原始 bar/popup 类；`FilterSortView` 用原类并 `setOnFilteredListener`（注意 addTab 会覆盖监听，需在 addTab 后设置）。
3. 本工程只保留：`HelperMain` 输入、`AnnotationCanvasView` 墨迹（或后续再接其渲染）、`Exporter`、手势转发。
4. 无法搬运的部分（如实告知用户）：笔迹纹理在 `libmicurve.so` + GLES 离屏渲染里，只有运行其编辑器才能获得；其编辑器画布是不透明页面，不能直接当全局悬浮层。

## 未完成/待修
- 颜色弹窗已换真身；**笔刷弹窗、橡皮弹窗、更多菜单、胶囊工具栏仍是自写**。下一步照第 4 节的方法搬这些：
  笔刷/橡皮弹窗在 `penpicker_popup_pen_set` / `penpicker_popup_layout_eraser_set` 里，胶囊栏在 `l1lII1ιιιιIl` 之外的 `p452lilii.*`/`p129Iill.*`（jadx 名），需要先找到各自的可实例化入口。
- 更多菜单文案/图标未逐项核对；"插入文本/插入形状"按用户决定隐藏。
- 上/下滑手势：LSPosed 广播 receiver 在 system_server 里 NPE，未修（模块已默认 enabled）。
- 微博滚动批注、长截图、PDF 未实机验证；撤销/重做、吸管未回归。
- 预览纹理只能近似。
- 交接时最后一次构建：CI `43547911955`（胶囊 124dp）；本轮基线提交见 `git log`（`886b197` 之后）。
- 排查入口：`adb shell am broadcast -a com.globalannotate.action.OPENCOLOR -n com.globalannotate.app/.GestureLaunchReceiver --es token ga-hyperos-2026` 可直接打开颜色面板（调试用，正式入口是工具栏色块）。

## 真机验证命令速查
```bash
adb shell am force-stop com.globalannotate.app
adb shell am start -n com.globalannotate.app/.MainActivity
adb logcat -s AnnotationService GA.MiCanvas GA.McBridge
python _work/pen.py wave 250 700 1500 --n 70 --amp 90   # 注入压感笔迹
adb exec-out screencap -p > shot.png
```


## 代码审查结论（已确认）

来自低强度审查（HEAD~1..HEAD）与改造过程中的走查，均已修复：
- `AnnotationService.kt` `onParam()`：面板传原始值（粗细 1–30、灵敏度 0–100、笔尖 1–4、不透明度 0–255），服务端却按 0–1 归一化换算 → 粗细被放大到极端值。已改为按原始单位处理。
- `AnnotationService.kt` `cycleColor()`：`QUICK_COLORS + RECENT_COLORS` 未去重，`indexOf` 命中第一份导致换色手势循环回跳、调色盘看似不轮换。已 `distinct()`。
- `AnnotationService.kt` `onParam(ALPHA)`：面板范围 0–255 与代码钳制 3–255 不一致，左端 1% 跳变。已把面板下限改为 3（对应 Mi Canvas `minAlphaValue`）。
- `ToolbarView`：槽位高度等于"笔长+行程"，贴图底边贴视图边界会被裁掉抗锯齿边缘 → 已加 4dp 余量；随后胶囊改为 124dp 覆盖整行，避免自带投影形成灰晕。
- `BrushPanel` 回退路径传归一化值、Mi Canvas 路径传原始值，两条路径语义不一致 → 已统一为原始值。

完整审查（HEAD~45..HEAD）由子代理执行中；其结论返回后会追加到本节。审查范围外的已知风险：长截图拼接对动态页面（视频/广告）可能接缝错位；`MiCanvasUi` 依赖已安装的小米创作，卸载后自动回退自带控件。


### 完整审查 HEAD~45..HEAD（45 提交 / 181 文件，子代理结论）
高严重度（未修，按此顺序处理）：
1. `canvas/AnnotationCanvasView.kt:281` — 滚动快速路径把 ink 位图按 `+applied` 下移，90ms 后 `rebuildInk` 又按 `-scrollOffset` 上移，墨迹反向并回跳（本轮已改符号）。
2. `canvas/AnnotationCanvasView.kt:251` — `eraseStrokeAt` 的 y 已经含 `scrollOffset`，函数里再加一次，滚动后笔画橡皮擦不中（本轮已改）。
3. `AnnotationService.kt:814` — 截图失败时 `busyExporting` 不复位，之后导出/长截图静默失效。
4. `AnnotationService.kt:227` — 服务被 START_STICKY 空 intent 重启时 `setActive(false)` 提前返回，窗口可见性/排除区未初始化，工具栏常驻且收不回。
5. `AnnotationService.kt:248` — `onHelperReady` 不调用 `updateExclusion`，root 授权晚于激活时排除区为空，笔点工具栏被当画线。
6. `AnnotationService.kt:816` — PNG 回退路径下 `createScaledBitmap` 可能返回原图，随后被 `recycle()` 再渲染，崩溃/空白导出。
7. `AnnotationService.kt:220` — `setSkipScreenshot` 在首次 traversal 前调用返回 false 且不重试，root 截图仍含覆盖层（双重墨迹、吸管取 UI 颜色）。
8. `root/HelperClient.kt:170` — `stop()` 发 QUIT 后立即 destroy，助手可能来不及恢复全局 `maximum_obscuring_opacity_for_touch`。
9. `AnnotationService.kt:761` — "用手指涂画"选项无效：`applyInputMode` 不读 `fingerDraw`，`canvas.allowFinger` 从未赋值。
10. `AnnotationService.kt:701` — 排除区只处理 ROTATION_90，且屏幕尺寸仅 onCreate 取一次，其他方向下工具栏被笔输入吞掉。
11. `AnnotationService.kt:890` — 长截图提前返回时高图未回收。
12. `Exporter.kt:113` — `rawDecode` 假定 12 字节头在偏移 0，`su` 前缀会让 raw 解码失败（旧代码有跳过前缀逻辑，需恢复）。
13. `export/LongCapture.kt:81` — 达到 MAX_HEIGHT 返回原图时未 `recycle(next)`，持续泄漏。
14. `widget/ColorPanel.kt:170` — 反射 `setColor/getCurrentColor` 无签名扫描回退，内置色板可能永不生效（应走 `McBridge`）。
15. `AnnotationService.kt:669` — 关闭面板 150ms 的 `withEndAction` 会把刚重新打开的 `popupHost` 置 GONE，快速开合闪隐。

已知风险（审查范围外）：长截图对动态页面可能接缝错位；`MiCanvasUi` 依赖已安装的小米创作，卸载后回退自带控件。
