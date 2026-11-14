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
 * The popup is therefore shown as the original window, with a transparent
 * tool-shaped anchor proxy in the app's overlay. Both the proxy and the popup
 * token come from the same overlay window, so the original
 * `getLocationInWindow()` maths stays consistent.
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
     * Size the inner content the way Mi Canvas' presenter does — its window
     * class overrides `setWidth`/`setHeight` so both write the content's layout
     * params — then let the original window place and animate itself.
     */
    fun show(anchor: View, width: Int, height: Int, dx: Int = 0, dy: Int = 0): Boolean = runCatching {
        popup.width = width
        popup.height = height
        // The offsets stand in for the presenter's `I1l1l1Ii` maths: they keep the
        // measured surface fully on screen instead of letting it hang off the
        // right edge when the anchor sits in the bar's right half.
        popup.showAsDropDown(anchor, dx, dy)
        true
    }.onFailure { Log.w(TAG, "original popup did not show", it) }.getOrDefault(false)

    fun dismiss() {
        runCatching { popup.dismiss() }
    }

    fun isShowing(): Boolean = runCatching { popup.isShowing() }.getOrDefault(false)

    /** Visible surface in screen coordinates — what the helper must exclude. */
    fun bounds(): Rect {
        val pos = IntArray(2)
        surface.getLocationOnScreen(pos)
        return Rect(pos[0], pos[1], pos[0] + surface.width, pos[1] + surface.height)
    }

    companion object {
        private const val TAG = "GA.McPopup"

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
            Log.i(
                TAG,
                "arrow ${arrow.javaClass.name} methods=" +
                    arrow.javaClass.declaredMethods.take(8).joinToString { it.name },
            )
            MiCanvasPopupShell(popup, arrow, content, surface)
        }.onFailure { Log.w(TAG, "arrow subtree unavailable", it) }.getOrNull()
    }
}
