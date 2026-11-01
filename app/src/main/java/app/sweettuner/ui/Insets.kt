package app.sweettuner.ui

import android.os.Build
import android.view.View
import android.view.WindowInsets

/** Pads the view by the system bars / display cutout (required with edge-to-edge on Android 15+). */
fun View.padForSystemBars(extraTop: Int = 0, extraBottom: Int = 0) {
    setOnApplyWindowInsetsListener { v, insets ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(b.left, b.top + extraTop, b.right, b.bottom + extraBottom)
        } else {
            @Suppress("DEPRECATION")
            v.setPadding(
                insets.systemWindowInsetLeft, insets.systemWindowInsetTop + extraTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom + extraBottom,
            )
        }
        insets
    }
    requestApplyInsets()
}
