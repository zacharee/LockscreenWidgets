package tk.zwander.common.util

import android.os.DeadObjectException

val Throwable.isOrHasDeadObject: Boolean
    get() {
        if (this is DeadObjectException) {
            return true
        }

        this.cause?.let { cause ->
            if (cause.isOrHasDeadObject) {
                return true
            }
        }

        return false
    }
