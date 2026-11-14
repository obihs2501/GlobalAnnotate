package com.globalannotate.app.mc

import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow

/**
 * The real `com.xiaomi.penpicker.popup.ArrowPopupView` behind one of Mi Canvas'
 * popups — its runtime class is `l1lII1ιιιιIl.Iι11lii`, a `PopupWindow` subclass.
 *
 * That class overrides `getContentView()`, so it hands back only the inner
 * content. Everything that makes the popup look and sit like Mi Canvas — the
 * arrow, the automatic direction choice, the measured surface and the enter and
 * exit animations — lives *outside* the view the app used to detach, which is
 * why hosting that inner content could never reproduce the original placement.
 *
 * The arrow root is hosted in the app's own full-display overlay together with an
 * anchor proxy, so the original `getLocationInWindow()` auto-direction code sees
 * the anchor and the root in the same window, exactly as it does inside Mi
 * Canvas' own activity window.
 */
class MiCanvasPopupShell private constructor(
    val popup: PopupWindow,
    val root: ViewGroup,
    /** The popup's own content view (`PopupWindow.getContentView()`), still attached. */
    val content: View,
    /** `R.id.content_wrapper`: the popup surface, i.e. everything but the arrow. */
    val surface: View,
) {

    /**
     * Size the inner content the way the original presenter does (`setWidth` /
     * `setHeight` end up writing the content's layout params) and clear anything a
     * previous host left on the root. Does not touch alpha: the caller keeps the
     * root transparent until [enter] runs.
     */
    fun prepare(anchor: View, width: Int, height: Int, offsetX: Int = 0, offsetY: Int = 0) {
        invoke("setAnchor", View::class.java, anchor)
        invoke("setAutoDismiss", Boolean::class.javaPrimitiveType, false)
        invoke(NAME_SET_OFFSET, Integer.TYPE, offsetX, Integer.TYPE, offsetY)
        // -1: let the original code choose the arrow direction from the anchor.
        invoke("setForcedArrowMode", Integer.TYPE, -1)
        content.layoutParams = content.layoutParams.apply {
            this.width = width
            this.height = height
        }
        root.animate().cancel()
        root.clearAnimation()
        root.scaleX = 1f
        root.scaleY = 1f
        root.alpha = 1f
        root.requestLayout()
    }

    /** Original enter animation; it installs its own pre-draw listener. */
    fun enter(): Boolean = invoke(NAME_ENTER)

    /** Original exit animation; the host hides the popup when it has played out. */
    fun exit(): Boolean = invoke(NAME_EXIT)

    /** Visible surface in screen coordinates — what the helper must exclude. */
    fun bounds(): Rect {
        val pos = IntArray(2)
        surface.getLocationOnScreen(pos)
        return Rect(pos[0], pos[1], pos[0] + surface.width, pos[1] + surface.height)
    }

    private fun invoke(name: String, vararg args: Any?): Boolean {
        var type: Class<*>? = root.javaClass
        while (type != null) {
            val method = type.declaredMethods.firstOrNull {
                it.name == name && it.parameterTypes.size == args.size
            }
            if (method != null) {
                return runCatching {
                    method.isAccessible = true
                    method.invoke(root, *args)
                    true
                }.onFailure { Log.w(TAG, "original $name failed", it) }.getOrDefault(false)
            }
            type = type.superclass
        }
        Log.w(TAG, "original method $name not found")
        return false
    }

    companion object {
        private const val TAG = "GA.McPopup"

        /** `ArrowPopupView.m16927lI1l()`, runtime `lIιι1ιlι`. */
        private const val NAME_ENTER = "lI\u03b9\u03b91\u03b9l\u03b9"

        /** `ArrowPopupView.m16919IllI1I()`, runtime `IlιιlI1I`. */
        private const val NAME_EXIT = "Il\u03b9\u03b9lI1I"

        /** `ArrowPopupView.m16915IIiill11i1ii(int,int)`, runtime `IIiill1ι1i1ii`. */
        private const val NAME_SET_OFFSET = "IIiill1\u03b91i1ii"

        /**
         * Walks the popup's class hierarchy for the field that really holds the
         * `ArrowPopupView` (the overridden `getContentView()` only exposes the
         * inner content) and keeps the whole subtree alive.
         */
        fun from(popup: PopupWindow): MiCanvasPopupShell? = runCatching {
            var type: Class<*>? = popup.javaClass
            var root: ViewGroup? = null
            while (type != null && root == null) {
                val field = type.declaredFields.firstOrNull {
                    it.type.name == "com.xiaomi.penpicker.popup.ArrowPopupView"
                }
                if (field != null) {
                    field.isAccessible = true
                    root = field.get(popup) as ViewGroup
                }
                type = type.superclass
            }
            val arrow = checkNotNull(root)
            val content = checkNotNull(popup.contentView)
            val surface = checkNotNull(arrow.findViewById<View>(MiCanvasUi.id("content_wrapper")))
            // The arrow only gets a parent once the original PopupWindow is shown;
            // the app hosts it directly, so drop any stale attachment.
            (arrow.parent as? ViewGroup)?.removeView(arrow)
            MiCanvasPopupShell(popup, arrow, content, surface)
        }.onFailure { Log.w(TAG, "arrow subtree unavailable", it) }.getOrNull()
    }
}
