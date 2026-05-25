package tk.zwander.common.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import xyz.kumaraswamy.autostart.Autostart

fun Context.missingAutostart(): Boolean {
    return when {
        isMiui -> !Autostart.isAutoStartEnabled(this, false)
        else -> false
    }
}

fun Context.launchAutostartActivity() {
    when {
        isMiui -> {
            launchXiaomiAutostartActivity()
        }
    }
}

private fun Context.launchXiaomiAutostartActivity() {
    val intent = Intent("miui.intent.action.OP_AUTO_START")
    intent.`package` = "com.miui.securitycenter"

    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        logUtils.debugLog("Unable to launch autostart Activity", e)
    }
}
