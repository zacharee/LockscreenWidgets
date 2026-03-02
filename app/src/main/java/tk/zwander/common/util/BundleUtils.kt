package tk.zwander.common.util

import android.os.Bundle

fun Bundle.matches(other: Bundle): Boolean {
    if (this.keySet().size != other.keySet().size) {
        return false
    }

    this.keySet().forEach { key ->
        @Suppress("DEPRECATION")
        if (this[key] != other[key]) {
            return false
        }
    }

    return true
}
