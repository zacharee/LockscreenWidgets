package tk.zwander.lockscreenwidgets.data

import android.os.Parcel
import android.os.Parcelable
import kotlinx.android.parcel.Parceler
import kotlinx.android.parcel.Parcelize
import kotlinx.android.parcel.TypeParceler

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