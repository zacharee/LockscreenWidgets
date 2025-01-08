package tk.zwander.common.util.mitigations

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import tk.zwander.common.util.logUtils

class SafeContextWrapper(context: Context) : ContextWrapper(context) {
    override fun unregisterReceiver(receiver: BroadcastReceiver?) {
        try {
            super.unregisterReceiver(receiver)
        } catch (e: Exception) {
            logUtils.debugLog("Unable to unregister receiver.", e)
        }
    }
}