package tk.zwander.common.util

import android.os.Bundle

fun Bundle.matches(other: Bundle): Boolean {
    val thisKeySet = this.keySet()
    val otherKeySet = other.keySet()

    if (thisKeySet.size != otherKeySet.size) {
        return false
    }

    thisKeySet.forEach { key ->
        @Suppress("DEPRECATION")
        if (this[key] != other[key]) {
            return false
        }
    }

    return true
}
