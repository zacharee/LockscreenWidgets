package tk.zwander.lockscreenwidgets.util

import java.util.*

class RemoveButtonObservable : Observable() {
    override fun notifyObservers(arg: Any?) {
        setChanged()
        super.notifyObservers(arg)
    }
}