package tk.zwander.lockscreenwidgets.observables

import java.util.*

class OnResizeObservable : Observable() {
    override fun notifyObservers(arg: Any?) {
        setChanged()
        super.notifyObservers(arg)
    }
}