# GlobalAnnotate 交接文档（2026-11-14）

## 结论先行
- 全局批注的输入链路已可用：root 助手创建"只接收手写笔"的受信任输入面，手指原样落到下层应用；滚动跟随、长截图导出代码已具备。
- 界面仍未达到"搬运小米创作"的目标。颜色板/颜色滑块/属性滑块/透明度滑块已运行时加载小米创作自己的类；工具栏、tab、预览、菜单容器仍为本工程自写。
- 下一步的正确做法是**换架构**：宿主加载小米创作 APK 的 bar/popup 类，本工程只保留输入/截屏/导出。不要再继续手写仿 UI。

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
- tab 仍是仿写 `FilterSortView`（未换 miuix 原类）。
- 更多菜单文案/图标未逐项核对；"插入文本/插入形状"按用户决定隐藏。
- 上/下滑手势：LSPosed 广播 receiver 在 system_server 里 NPE，未修（模块已默认 enabled）。
- 微博滚动批注、长截图、PDF 未实机验证；撤销/重做、吸管未回归。
- 预览纹理只能近似。
- 交接时最后一次构建：CI `43547911955`（胶囊 124dp）；提交 `HEAD` 以此为基线。

## 真机验证命令速查
```bash
adb shell am force-stop com.globalannotate.app
adb shell am start -n com.globalannotate.app/.MainActivity
adb logcat -s AnnotationService GA.MiCanvas GA.McBridge
python _work/pen.py wave 250 700 1500 --n 70 --amp 90   # 注入压感笔迹
adb exec-out screencap -p > shot.png
```
