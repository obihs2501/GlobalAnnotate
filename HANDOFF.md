# GlobalAnnotate 当前接手快照

> 本文件是当前最新交接文档；旧的 `/tmp/micre` 资料路径、`contentView` 摘出式弹窗说明、README 第 11 节和旧 CI 基线都应视为历史信息。遇冲突时以本文件 + `git log` + 当前源码为准。

## 0. 一句话状态

- 仓库：`C:\softwares\claudecode\drawanywher\2026-09-22\GlobalAnnotate`，分支 `master`。
- 最新代码提交：`5c2c55f Match toolbar color swatches to Mi Canvas`。此提交只改了工具栏色槽的视觉绘制，**尚未 push、尚未 CI 构建、尚未真机验证**。
- 最近已部署并验证过的代码基线是 `f2d133a`，CI run `43584212990`：原版 Mi Canvas `PopupWindow` 已能打开，日志显示 `Mi Canvas popup shell ready` 和 `PopupWindow:690cb92`，但可见尺寸、箭头方向、触摸/外部关闭仍没有完整验证。
- `origin/master` 目前停在 `f2d133a`；本接手文档提交后本地会 ahead 2。**不要误以为 `5c2c55f` 已经部署。**
- todo：`#8 移植并修复剩余界面差异` 仍 `in_progress`；`#9 构建部署并验证交互` 仍 `pending`；`#10 整理接手状态并更新交接文档` 已完成。
- 工作树在代码提交 `5c2c55f` 后是干净的；本文件作为单独文档提交，提交后工作树仍应干净。

## 1. 用户要求的顺序与硬约束

用户要求继续逐项完成，不要继续用“画得更像”的近似品替代原版：

1. 修正工具栏颜色槽：41dp 触摸格、原版圆点大小、内部白色选中环、第四个彩虹/自定义色槽。
2. 修正调色盘宿主窗口方向、边界、尺寸、触摸路由，同时保留原版内容。
3. 修正悬浮球尺寸、底色、笔图位置和缩放。
4. 将笔身手势从点击轮换改为设置页中的 Miuix 风格下拉/复选选择。
5. 减少笔迹输入过程中主线程的逐点整图复制与重绘，并做实机延迟验证。

硬约束：

- 优先移植原版 Mi Canvas 类、布局、dimen、动画和交互；只用源码/资源证据，不凭截图猜常量。
- 不要声称“完全等价”：目前只是原版颜色控件、原版弹窗窗口和部分绘制规则被接入，工具栏/悬浮球/径向调色盘仍是本工程 View。
- 第四色槽必须保留：点按选择；已选中或重复点按进入编辑；编辑结果替换第四槽；换色手势只循环工具栏四色，不把弹窗最近颜色卷进来。
- 不要加黑色选中框；原版选中态是颜色圆点内部的白色/背景色内环。
- 弹窗排除区和 outside-touch 只使用可见 `content_wrapper` 边界，不能把全屏宿主当成触控/排除区域。
- 排除区使用 `getLocationOnScreen()` 的当前显示坐标。
- App 自有窗口绝不声明 `INTERCEPTS_STYLUS`；可信 stylus interception 只在 root helper。
- 反射使用 JADX `renamed from` 注释里的真实 Unicode 运行时名，不用 `m12345…` 生成名。
- 分析子代理只读、只访问授权的 JADX `sources`/`resources`；不访问设备、凭据或无关目录。
- `_work` 下的截图、dump、解码源码、APK 都保留；未经用户确认不得删除。
- 本地无 Android SDK；通过 `.github/workflows/android-ci.yml` + JDK 21 构建，不要添加 `android-actions/setup-android`。
- 真机用 `b8da38a5`（小米 Pad 8 Pro / Android 17 / 3200×2136 landscape / density 2.75），不要用逆向 skill 默认 MuMu。
- 不要再要求用户恢复旧 `/tmp` 源码；APK 已重新解码到授权工作区。
- 无本地构建/编译验证时，不要宣称代码已经可运行。

## 2. 已完成且有证据的事项

### 2.1 早期稳定性修复

`d252e17` 等提交已修完 review 清单中的高危问题：导出 busy 状态、START_STICKY 初始化、helper-ready 排除区刷新、缩放位图回收、截图排除重试、关闭动画 epoch、`Exporter` raw header 前缀、`LongCapture.MAX_HEIGHT` 泄漏、`McBridge` 签名兜底、`HelperClient.QUIT` 等待等。

`4896922` 修复 stylus 工具栏路由：排除矩形直接使用当前显示坐标，并对刚变为可见的视图补一次布局后刷新。真机笔点日志曾出现 `AnnotationService: tool tap BALLPOINT at 673`。

### 2.2 原版 Mi Canvas UI 可实例化

`mc/MiCanvasUi.kt`：

- 使用 `DexClassLoader` 加载 `com.miui.creation` APK。
- 反射设置 `Resources.mClassLoader` 并清 `mDrawableInflater`。
- 包装 Context 提供非空 `getApplicationContext()`、真实 classLoader、资源和克隆 inflater。
- 颜色弹窗类为运行时名 `l1lII1ιιιιIl.I11IιIil`（JADX 名 `p249l1lII1Il.I11IIil`）。
- 真机已验证原版色板、tab、滑块页、颜色块、最近颜色视图能够 inflate；真实网格点击可改变 brush 颜色。

### 2.3 原版 PopupWindow 路径

当前代码不再把 `contentView` 从 PopupWindow 中摘出来当作普通 panel：

- `mc/MiCanvasPopupShell.kt` 保留原版 `PopupWindow`、`ArrowPopupView`、`content`、`content_wrapper`。
- `show(anchor,width,height)` 设置 `popup.width/height` 后调用原版 `popup.showAsDropDown(anchor, 0, 0)`。
- `bounds()` 返回 `content_wrapper` 的屏幕矩形，用于排除区和 outside-touch。
- `AnnotationService.showShell()` 在 overlay 中创建透明 tool-shaped proxy anchor，绑定 `selectedToolBounds()`，并同步 dismiss/exclusion 状态。
- `f2d133a` 后真机日志：`Mi Canvas popup shell ready in 59 ms`，`dumpsys window` 可见 `PopupWindow:690cb92`。
- 之前全 root 方式的 `ArrowPopupView.onLayout` 空 anchor NPE 已由 `aebbb46` 修复；但最终路径改为直接显示原版 PopupWindow，旧全 root 手动布局代码不再作为当前主线。

### 2.4 工具栏颜色槽视觉修正（`5c2c55f`）

文件：`app/src/main/java/com/globalannotate/app/toolbar/ToolbarView.kt`

本提交只做视觉：

- 保留 2×2 的 41dp 点击格。
- 内部圆点半径改为 `15.34f * density`，即原版平板资源 `creation_tool_color_item_size_1_2 = 30.68dp`。
- 普通色槽选中时画内部白色环（stroke `1.25dp`，半径 `0.76 * radius`）。
- 第四色槽移除 `Color.argb(60,0,0,0)` 黑色细边。
- 第四槽有 `checked` 状态，`update()` 中用 `customColor != 0 && customColor == brush.color` 同步。
- 移除未再使用的 `kotlin.math.min` import。

证据来源：

- `res/values/dimens.xml`：`creation_tool_color_item_size=26dp`、`creation_tool_color_item_size_1_2=30.68dp`、`color_button_uni_size=44dp`。
- `p016I1IllI/lIIIlll.java`：选中态是 `setCheck(true)` 后在同一视图内重绘，`f32437llIlIli=0.8125f`、`f632lIll1li=0.65f`；不是外部黑框。
- `p016I1IllI/l1IIii.java`：第四色槽是 `ComposeShader(SweepGradient, RadialGradient, SRC_OVER)`，颜色数组来自 `penpicker_palette_sweep_1..8` 和 radial center/edge。
- `p452lilii/I1Iili.java`：`IlIll1i()` 创建三个快捷色和一个 `l1IIii` 彩虹/自定义色槽；`m35832Il1IIl()` 统一 setCheck；`m35828IIlill()` 同步当前笔色到快捷色或第四槽。

`5c2c55f` 尚未编译、push、CI 或真机截图验证。

## 3. 当前实现中必须知道的细节

### 3.1 第四色槽语义尚未完成

`ToolbarView.Listener` 仍然是：

```kotlin
fun onColor(color: Int)
fun onOpenColorPanel(anchorX: Int)
fun onOpenMore(anchorX: Int)
...
```

`CustomSwatch.onTap` 仍然无条件调用 `listener.onOpenColorPanel(centerOnScreen(this))`。尝试加入 `onCustomColor/onOpenCustomColorPanel` 的接口变更没有成功应用，不能按它继续改。

现有 `AnnotationService`：

- `customColor` 持久化在 `KEY_CUSTOM = "custom_color"`，`restoreBrush()` 读取。
- `onColor(color)` 对非 `QUICK_COLORS` 的颜色写入 `customColor`，然后切笔色。
- `cycleColor()` 循环 `BrushConfig.QUICK_COLORS + customColor`，用 `.distinct()` 去重；弹窗最近颜色不参与。
- `refreshToolbar()` 将 `customColor` 同步给 `toolbar.customColor`。
- 点击工具槽、取色、popup 回调等都走同一个 `onColor`，所以目前无法区分“编辑器结果写入第四槽”和“普通颜色选择”，也没有“已选中的第四槽再次点击才编辑”。

原版 presenter 的可移植语义（`p452lilii/I1Iili.java`）：

- 普通快捷色点击：`m35835l1i1ili()` 中 `r3 != mColorPalette` 分支，直接 `m35852l1i11(r3.getMColor(), r3)`。
- 第四色槽未选中：`r3 == mColorPalette && !r3.Iiliill()`；取当前 INK 笔色，若没有则用 `cp_color_blue`，写回 `r3.setMColor/setColor`，再调用 `m35852...` 应用颜色。
- 第四色槽已选中：`r3 == mColorPalette && r3.Iiliill()`；调用 `m35813IIiIIIll1(r3)` 打开颜色编辑弹窗。
- 编辑颜色变化：`I1Iili.lIIIlll` 回调把新 ARGB 写入 `mColorPalette.setColor/setMColor`，`m35832Il1IIl(mColorPalette)`，并应用到当前 INK tool。
- `m35828IIlill()` 同步时：当前色匹配快捷色则选中快捷色；否则把当前色写入第四槽并选中第四槽。

继续实现时应按以上分支扩展 listener/service，避免用“点第四槽总开编辑器”的旧行为。

### 3.2 原版弹窗宿主仍未完整校准

当前 `MiCanvasPopupShell` 只是保留原版 PopupWindow：

- `popup.width = dp(360)` 会动态调用原版重写的 `setWidth(int)`（`m25492l111I`），把宽度写到 `contentView` 的 LayoutParams。
- `popup.height = dp(470)` 会动态调用原版重写的 `setHeight(int)` → `lI1lill1(int)`，把高度写到 `contentView` 的 LayoutParams。
- `showAsDropDown` → `C0830I11lii.mo25409Ilil1iI11i()`：`ArrowPopupView.setAnchor(view)`、`IIiill1ι1i1ii(x,y)`、`showAtLocation(view, 8388659,0,0)`、auto-dismiss、`lIιι1ιlι()` enter animation。
- `I11IIil.showAsDropDown()` 还会保存 anchor/offset 并刷新 recent colors。

但原版 presenter 在 show 前还会调用：

- `m25484l1IIii(boolean)` → `setPreferHorizontal`；
- `m25493lIil11(forcedArrowMode)` → `setForcedArrowMode`；
- `l1lIlii(popup)` → 根据窗口/anchor 计算尺寸；
- `I1l1l1Ii(view)` → 依据 BOTTOM/LEFT/RIGHT/TOP 位置算 `showAsDropDown` 的 x/y offset。

当前 shell 没有完整复刻这些设置；上一轮反射尝试 `setAnchor`、`setAutoDismiss`、`IIiill1ι1i1ii`、`setForcedArrowMode` 均未解析成功。运行时 arrow methods 日志只有 `I111llι, I11IIlιI, I11IιIil, I11lιiιI, I11ιillιιllI, I1Iιili, I1iilil1ι, I1iι1lι`。必须按真实 Unicode 名/签名反射，不要再用 JADX `m169xx` 名。

已知未验证点：360×470dp 可见 placement、箭头方向、surface bounds、outside dismissal、原版窗口触控、触摸崩溃是否真正消失。此前 `dumpsys window` 报告过请求窗口 `w=3200 h=2002`，不可当作可见表面。

### 3.3 工具栏几何仍是临时模型

`ToolbarView` 当前常量：

```kotlin
PEN_WIDTH_DP = 48
LARGE_WIDTH_DP = 68
PEN_HEIGHT_DP = 124
OUTWARD_DP = 18
CAPSULE_DP = 124
WINDOW_HEIGHT_DP = CAPSULE_DP
BAR_HEIGHT_DP = WINDOW_HEIGHT_DP
```

原版参考 `mcbar.xml` 实测工具栏区域为 `[1264,1772][3159,2051]`，高 279px ≈ 101.5dp；颜色槽 113px ≈ 41dp；工具图 123px 宽等。`cp_brush_height=124dp` 更像工具 artwork/槽高，不等于整个胶囊高度。当前 124dp 胶囊/窗口高度需要继续和原版 bounds 对齐，不能宣称已一致。`p129Iill/II11I.java` 证明工具槽 width=48dp、wrap-content、`FIT_CENTER`、main handler、1500ms auto-retract、`setDistance(float)` 方向相关位移和 `cp_brush_inner_distance=18dp`；精确 rotation-enum 映射仍未解析。

### 3.4 其它 UI 仍是自写近似

- 悬浮球：`AnnotationService` 用 `BrushGlyph`，96dp oval background、`artLengthDp=73f`，不是原版 orb 类。
- 径向调色盘：`widget/RadialPaletteView.kt` 自绘 Apple Pencil 风格 ring，`ringRadius=104dp`、`itemRadius=23dp`、8 items。
- 手势设置：`widget/GesturePanel.kt` 仍是 288dp 卡片 + 点击整行 `cycle()` 轮换；`MainActivity` 只做权限和启服务；项目没有 Miuix Compose 依赖。
- `MoreMenu` 已有“笔身手势”入口，`AnnotationService.onGestureSettings()` 把它作为普通 panel 显示。
- Miuix skill 已读过；稳定版参考 `v0.9.4`。若继续，优先用 Android View 层复刻 Miuix preference/dropdown/checkmark 的分组、圆角、行高，而不是引入 Compose 依赖或手写普通卡片。

### 3.5 笔迹延迟路径只是嫌疑，未测

已追踪链路：

`HelperMain` root input monitor / raw pen keyboard → `HelperClient` 跨进程 + main-thread Handler → `AnnotationInputService.onTransact()` 反序列化 `MotionEvent` 并 `canvas.post { canvas.injectStylusEvent(event) }` → `AnnotationCanvasView.injectStylusEvent()` 处理 history → `move()` 每个采样调用 `redrawActive()`：`drawColor(CLEAR)` + `drawBitmap(baseInk)` + `engine.draw(full active stroke)` + `invalidate()`。

因此“逐点整图复制/全笔重绘 + 主线程队列”是明确性能嫌疑，但尚无 timing instrumentation，不能宣称 root cause。继续时应先加时间戳/计数诊断，再基于数据改增量渲染或队列。

## 4. 待做清单（按用户指定顺序）

### A. 立刻构建并验证 `5c2c55f`

1. `git status` 应为干净（本文件提交后）。
2. `git push` 后跑 CI；`../_work/deploy.sh app` 等待 CI、下载 debug APK、安装到 `b8da38a5`。
3. 同方向/同密度/同选中工具/同颜色状态下截图比较 `_work/shots/mc_bar_crop.png`。
4. 检查 30.68dp 圆点、白色内环、第四槽无黑边、选中态同步。
5. 若发现 `15.34f * density` 与原版截图不符，优先查 `values-sw*`/平板资源 qualifier 和运行时 `Resources.getDimension`，不要回到截图猜值。

### B. 完成第四色槽交互

按 §3.1 原版 presenter 分支修改：

- `ToolbarView.Listener` 新增明确回调，或让 `CustomSwatch` 通过 `customSwatch.checked`/`customColor` 直接分流。
- 未选中第四槽：把当前笔色写入 `customColor`、选中第四槽、应用该颜色。
- 已选中第四槽：打开颜色编辑器。
- 编辑器/取色/色板/最近色结果如果来自“打开第四槽编辑”的上下文，应写回 `customColor`，即使颜色与三个 preset 相同。
- `cycleColor()` 仍只循环可见四色并去重。
- 验证 `global_annotate_brush.xml` 中 `color`、`custom_color` 持久化和 squeeze 四次循环。

### C. 校准原版颜色弹窗宿主

- `MiCanvasPopupShell`：按真实运行时名补齐 forced arrow mode / prefer horizontal / offset / original presenter sizing。
- 用 `content_wrapper.bounds()` 做排除区和 outside-touch；不要扩大到全屏 root。
- 在 `dumpsys window`、`uiautomator dump`、截图、真实笔点中验证 360×470dp 可见区域、箭头、外部关闭、色板点击不崩。
- 保持 `ColorPanel.originalShell()` 与 `AnnotationService.openShell/shellAnchor/visiblePopupBounds()` 状态同步。
- `openShell` 的 dismiss listener 不能覆盖原版 straw 按钮自身的 dismiss/re-show 逻辑而引发新的触控问题。

### D. 悬浮球与径向调色盘

- 继续只读追踪 `lIi11II1ill`（弧形笔选择器）和 `com.miui.handwirting.common.view.colorpicker`/`FloatingPicker`/QuickColor 资源。
- 先拿到布局、dimen、颜色、展开/收起状态与 presenter 入口，再替换 `BrushGlyph` orb 与 `RadialPaletteView`。
- 子代理 `job_mwgbr9va_9` 超时前只确认这两条相关实现，没有完整调用链，不能据此写常量。

### E. 笔身手势设置页

- 保留 `Gesture` / `GestureAction` / `gestureMap` / `saveGestures()` / `restoreGestures()` 既有存储。
- 替换 `GesturePanel.cycle()`；提供 4 个手势的下拉选项，选中项显示复选/勾选标记。
- 入口仍从 `MoreMenu` 的“笔身手势”；也可增加独立 Activity，但不要丢掉 overlay 内可达路径。
- 不要再把所有 action 塞进工具栏。
- 验证 `onAction()` 写入 prefs、服务重启后 restore、四个手势实际触发。

### F. 延迟

- 在 `HelperClient`、`AnnotationInputService.onTransact()`、`AnnotationCanvasView.injectStylusEvent()/move()/redrawActive()`、`onDraw()` 加可关闭的 timing/count 日志。
- 用 `_work/pen.py wave ...` 注入固定压感/历史采样笔迹，记录 p50/p95 和掉帧。
- 根据数据优化：避免每个 history sample 都清全屏 bitmap + draw base + 全笔重绘；考虑按帧合并、脏矩形、离屏 active layer，但不要破坏已有“全笔重绘消除接缝”的修复。
- 子代理 `job_mwgbr9vb_a` 超时前只确认 `com/miui/creation` 输入入口是 `CreationEditActivity.dispatchGenericMotionEvent/dispatchTouchEvent`，混淆 `cf/lI1lill1` 像原版手写引擎；没有可直接复用的 renderer 结论。

### G. 原版 brush model / preview

- 已确认 brush model 类：`Iiιιιll.lιIil11`（BALLPOINT）、`I11IιIil`（PENCIL）、`IiIιI1IlIιι`（PEN）、`IlIlIii11ιli`（MARK）、`IliIlIιι1lI`（WATERCOLOR），均有 `(Context)` 构造并继承 `Iiιιιll.l1IIiiι`。
- 属性范围：generic size 1–100、alpha 3–255、alternative size 4–32；还有 tiltSensitivity、TipSize、mosaicSize。
- 原版 preview 链：`m5556liIII`/runtime `liIIιIι` → runnable → `lIIIl1IilIlli(...)`，下游 renderer/native 未解析。
- `ArrowPopupView.m16928lII1Il(int)` 的 JADX 输出为 `Method dump skipped, instruction units count: 384` / `UnsupportedOperationException`，需要 targeted `--show-bad-code` 或 smali 恢复。
- 当前 `StrokePreview`/S-wave 是临时近似，不要称为原版 renderer。

### H. 其它未验证/遗留

- Weibo scroll-follow、长截图/PDF、undo/redo、eyedropper、LSPosed up/down gesture receiver 的 `system_server` NPE 均未完整回归。
- 原版 tool-slot activation/rotation 映射、toolbar artwork clipping、brush preview renderer 仍未完成。
- 已安装 Mi Canvas 是运行时 UI 依赖；卸载后 `MiCanvasUi` 回退自带控件。
- 不要使用旧 `/tmp/micre/out/sources` 和 `/tmp/micre2/res/resources/res`；它们曾导致 `[E_NOT_FOUND]` 且在文档中已过期。

## 5. 构建 / 设备 / 调试命令

```bash
# 在仓库根目录
git status --short
git log --oneline --decorate -8
git push                         # 本轮尚未执行；push 会触发 CI

# CI 产物部署
cd /c/softwares/claudecode/drawanywher/2026-09-22/GlobalAnnotate
../_work/deploy.sh app           # 等 CI → 下载 debug APK → adb install

# 调试打开颜色面板（广播接收器）
adb -s b8da38a5 shell am start -n com.globalannotate.app/.MainActivity
adb -s b8da38a5 shell am broadcast -a com.globalannotate.action.OPENCOLOR \
  -n com.globalannotate.app/.GestureLaunchReceiver \
  --es token ga-hyperos-2026

# 日志
adb -s b8da38a5 logcat -s AnnotationService GA.MiCanvas GA.McPopup GA.McBridge

# 真实笔迹注入
python ../_work/pen.py wave 250 700 1500 --n 70 --amp 90
```

- `MSYS_NO_PATHCONV=1` 用于 Git Bash 下 Android 路径。
- 已成功过的 CI runs：`43581675506`、`43583077696`、`43584212990`。
- `.github/workflows/android-ci.yml`：JDK 21，构建 `:app:assembleDebug :xposed:assembleDebug`，上传 `global-annotate-debug`。不要加 `android-actions/setup-android`。
- 设备：`b8da38a5`，小米 Pad 8 Pro 25091RP04C，Android 17，density 440dpi / 2.75，横屏 UI 3200×2136；已有 root、overlay、通知、KernelSU、ZygiskNext、LSPosed。

## 6. 授权逆向资料路径

只读资料根目录：

```text
APK:
C:\softwares\claudecode\drawanywher\2026-09-22\_work\mc\miui_creation.apk

JADX sources:
C:\softwares\claudecode\drawanywher\2026-09-22\_work\mc-ui-redecode-continue\jadx\sources

JADX resources:
C:\softwares\claudecode\drawanywher\2026-09-22\_work\mc-ui-redecode-continue\jadx\resources
```

高频文件：

```text
jadx/sources/p016I1IllI/lIIIlll.java          颜色槽基类绘制 / setCheck / setColor
jadx/sources/p016I1IllI/l1IIii.java           彩虹/自定义第四槽 shader
jadx/sources/p452lilii/I1Iili.java            toolbar presenter / 色槽点击 / popup 定位
jadx/sources/p249l1lII1Il/I11IIil.java        ColorPickerPopupWindow
jadx/sources/p249l1lII1Il/C0830I11lii.java    ArrowPopupWindow / showAsDropDown
jadx/sources/com/xiaomi/penpicker/popup/ArrowPopupView.java
jadx/sources/p129Iill/II11I.java               工具槽动画 / setDistance
jadx/sources/com/miui/handwirting/common/view/colorpicker/ColorSelectView.java
jadx/resources/res/layout/penpicker_app_arrow_popup_view.xml
jadx/resources/res/layout/creation_panel_container.xml
jadx/resources/res/values/dimens.xml
jadx/resources/res/values/styles.xml
jadx/resources/res/values/colors.xml
```

关键运行时名：

```text
ColorPickerPopupWindow: l1lII1ιιιιIl.I11IιIil
setter: Iι1liιi(int)
callback interface: l1lII1ιιιιIl.IIiIιIiii1
model: l1lII1ιιιιIl.l1IIiiι
ArrowPopupWindow: l1lII1ιιιιIl.Iι11lii
Arrow offset setter: IIiill1ι1i1ii(int,int)
Arrow enter: lIιι1ιlι()
```

## 7. 当前工程关键文件

```text
app/src/main/java/com/globalannotate/app/AnnotationService.kt
app/src/main/java/com/globalannotate/app/MainActivity.kt
app/src/main/java/com/globalannotate/app/Contracts.kt
app/src/main/java/com/globalannotate/app/AnnotationInputService.kt
app/src/main/java/com/globalannotate/app/canvas/AnnotationCanvasView.kt
app/src/main/java/com/globalannotate/app/root/HelperClient.kt
app/src/main/java/com/globalannotate/app/root/HelperMain.kt
app/src/main/java/com/globalannotate/app/mc/MiCanvasUi.kt
app/src/main/java/com/globalannotate/app/mc/MiCanvasPopupShell.kt
app/src/main/java/com/globalannotate/app/mc/McBridge.kt
app/src/main/java/com/globalannotate/app/toolbar/ToolbarView.kt
app/src/main/java/com/globalannotate/app/widget/ColorPanel.kt
app/src/main/java/com/globalannotate/app/widget/GesturePanel.kt
app/src/main/java/com/globalannotate/app/widget/MoreMenu.kt
app/src/main/java/com/globalannotate/app/widget/RadialPaletteView.kt
app/src/main/java/com/globalannotate/app/widget/BrushGlyph.kt
app/src/main/java/com/globalannotate/app/widget/StrokePreview.kt
```

常用集成点：

- `AnnotationService.onGestureSettings()` / `showPanel(gesturePanel, ...)`。
- `AnnotationService.showColorPanel()` / `showShell()` / `visiblePopupBounds()` / `updateExclusion()`。
- `AnnotationService.onColor()` / `cycleColor()` / `refreshToolbar()` / `saveBrush()` / `restoreBrush()`。
- `ColorPanel.installMiCanvasPopup()` / `originalShell()`。
- `ToolbarView.selectedToolBounds()` / `CustomSwatch.onTap` / `update()`。

## 8. 保留的参考/过程产物

以下均为既有产物，未经用户确认不要删除：

```text
_work/mc/miui_creation.apk
_work/mc-ui-redecode-continue/**
_work/deploy.sh
_work/pen.py
_work/shots/mc_bar_crop.png
_work/shots/mc_brushes.png
_work/shots/mc_pen_panel.png
_work/shots/mc_ball_panel.png
_work/shots/mc_color_grid.png
_work/shots/mcl_color.png
_work/shots/mc_eraser_panel.png
_work/shots/crop_sw.png
_work/shots/pop_shell.png
_work/shots/mc_popup_ref.png
_work/shots/mc_now1.png, mc_now2.png, mc_now3.png
_work/shots/bar_final.png, gabrush.png, mcpopup1.png ... mcpopup12.png, verify1.png
```

本次交接整理没有新建临时脚本、截图或中间产物；只更新并提交本 `HANDOFF.md`。
