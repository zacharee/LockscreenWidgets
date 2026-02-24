package tk.zwander.common.util

import android.os.Bundle

fun Bundle.matches(other: Bundle): Boolean {
    this.keySet().forEach { key ->
        if (this[key] != other[key]) {
            return false
        }
    }

    return true
}
