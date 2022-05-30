package tk.zwander.widgetdrawer.observables

import java.util.*

class TransparentObservable : Observable() {
    fun setTransparent(transparent: Boolean) {
        setChanged()
        notifyObservers(transparent)
    }
}