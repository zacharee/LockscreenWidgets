package tk.zwander.common.util.mitigations

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.UserHandle
import androidx.annotation.RequiresPermission
import tk.zwander.common.util.logUtils

open class SafeContextWrapper(context: Context) : ContextWrapper(context) {
    override fun unregisterReceiver(receiver: BroadcastReceiver?) {
        try {
            super.unregisterReceiver(receiver)
        } catch (e: Exception) {
            logUtils.debugLog("Unable to unregister receiver.", e)
        }
    }

    @RequiresPermission(Manifest.permission.INTERACT_ACROSS_USERS_FULL)
    override fun registerReceiverAsUser(
        receiver: BroadcastReceiver?,
        user: UserHandle?,
        filter: IntentFilter?,
        broadcastPermission: String?,
        scheduler: Handler?,
    ): Intent? {
        return try {
            super.registerReceiverAsUser(
                receiver,
                user,
                filter,
                broadcastPermission,
                scheduler,
            )
        } catch (e: Throwable) {
            logUtils.normalLog("Unable to register receiver.", e)
            null
        }
    }

    @RequiresPermission(Manifest.permission.INTERACT_ACROSS_USERS_FULL)
    override fun registerReceiverAsUser(
        receiver: BroadcastReceiver?,
        user: UserHandle?,
        filter: IntentFilter?,
        broadcastPermission: String?,
        scheduler: Handler?,
        flags: Int,
    ): Intent? {
        return try {
            super.registerReceiverAsUser(
                receiver,
                user,
                filter,
                broadcastPermission,
                scheduler,
                flags,
            )
        } catch (e: Throwable) {
            logUtils.normalLog("Unable to register receiver.", e)
            null
        }
    }
}