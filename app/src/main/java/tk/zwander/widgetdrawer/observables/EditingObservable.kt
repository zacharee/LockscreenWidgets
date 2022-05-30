package tk.zwander.widgetdrawer.observables

import java.util.*

class EditingObservable : Observable() {
    fun setEditing(editing: Boolean) {
        setChanged()
        notifyObservers(editing)
    }
}
