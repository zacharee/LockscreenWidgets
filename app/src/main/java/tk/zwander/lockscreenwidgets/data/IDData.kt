package tk.zwander.lockscreenwidgets.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Contain the data about a specific on-screen ID.
 *
 * @property id the fully-qualified ID
 * @property type whether this ID was added, removed, or hasn't changed state
 */
@Parcelize
data class IDData(
    val id: String,
    var type: IDType
) : Parcelable, Comparable<IDData> {
    enum class IDType {
        REMOVED,
        ADDED,
        SAME
    }

    override fun equals(other: Any?): Boolean {
        return other is IDData && other.id == id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun compareTo(other: IDData): Int {
        return when {
            this.type == IDType.ADDED && other.type != IDType.ADDED -> -1
            other.type == IDType.ADDED && this.type != IDType.ADDED -> 1
            this.type == IDType.REMOVED && other.type == IDType.SAME -> -1
            other.type == IDType.REMOVED && this.type == IDType.SAME -> 1
            else -> this.id.compareTo(other.id)
        }
    }
}