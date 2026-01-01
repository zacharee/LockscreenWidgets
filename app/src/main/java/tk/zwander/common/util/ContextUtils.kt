package tk.zwander.common.util

import android.content.Context
import android.view.Display

// Before Android 11, createDisplayContext() isn't overridden by AccessibilityService
// to pass along its special token for adding accessibility overlays, so
// acquiring a WindowManager instance from a display context can't be used
// to add accessibility overlays. It looks like different versions of Android
// deal with the special accessibility permission pretty differently, so manually
// passing tokens or connection binders around isn't really practical. Instead,
// just don't support multi-display before Android 11 and don't create a secondary
// display context.
fun Context.createDisplayContextCompat(display: Display): Context {
    return if (lsDisplayManager.multiDisplaySupported) {
        createDisplayContext(display)
    } else {
        this
    }
}
