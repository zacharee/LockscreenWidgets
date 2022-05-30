package tk.zwander.widgetdrawer.observables

import java.util.*

class SizeObservable : Observable() {
    fun setSize(id: Int) {
        setChanged()
        notifyObservers(id)
    }
}