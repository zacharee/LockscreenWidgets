package tk.zwander.widgetdrawer.observables

import java.util.*

class SelectionObservable : Observable() {
    fun setSelection(selection: Int) {
        setChanged()
        notifyObservers(selection)
    }
}