package tk.zwander.lockscreenwidgets.observables

import java.util.*

class RemoveButtonObservable : Observable() {
    override fun notifyObservers(arg: Any?) {
        setChanged()
        super.notifyObservers(arg)
    }
}