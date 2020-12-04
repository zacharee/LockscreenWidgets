package tk.zwander.lockscreenwidgets.data

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler

/**
 * Contain the data about a specific on-screen ID.
 *
 * @property id the fully-qualified ID
 * @property type whether this ID was added, removed, or hasn't changed state
 */
@Parcelize
@TypeParceler<IDData.IDType, IDData.IDParceler>
data class IDData(
    val id: String,
    var type: IDType
) : Parcelable {
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

    class IDParceler : Parceler<IDType> {
        override fun create(parcel: Parcel): IDType {
            return IDType.valueOf(parcel.readString())
        }

        override fun IDType.write(parcel: Parcel, flags: Int) {
            parcel.writeString(toString())
        }
    }
}