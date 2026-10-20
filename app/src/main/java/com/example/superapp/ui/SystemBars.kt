package com.example.superapp.ui

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController

@Suppress("DEPRECATION")
private const val EDGE_TO_EDGE_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

/**
 * Lays the window out behind the system bars (mandatory from Android 15) and pads [root] by the
 * bar, display-cutout and keyboard insets instead, so nothing is hidden behind them.
 */
@Suppress("DEPRECATION")
internal fun Window.setUpEdgeToEdge(root: View) {
    if (Build.VERSION.SDK_INT >= 30) {
        setDecorFitsSystemWindows(false)
    } else {
        decorView.systemUiVisibility = EDGE_TO_EDGE_FLAGS
    }
    root.setOnApplyWindowInsetsListener { view, insets ->
        if (Build.VERSION.SDK_INT >= 30) {
            // Ignoring visibility keeps the page from resizing while fullscreen video hides the bars.
            val bars = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsets.CONSUMED
        } else {
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets.consumeSystemWindowInsets()
        }
    }
}

/** Shows or hides the status and navigation bars (fullscreen video); a swipe reveals them briefly. */
@Suppress("DEPRECATION")
internal fun Window.setSystemBarsVisible(visible: Boolean) {
    if (Build.VERSION.SDK_INT >= 30) {
        val controller = insetsController ?: return
        if (visible) {
            controller.show(WindowInsets.Type.systemBars())
        } else {
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsets.Type.systemBars())
        }
    } else {
        decorView.systemUiVisibility = if (visible) {
            EDGE_TO_EDGE_FLAGS
        } else {
            EDGE_TO_EDGE_FLAGS or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }
}
