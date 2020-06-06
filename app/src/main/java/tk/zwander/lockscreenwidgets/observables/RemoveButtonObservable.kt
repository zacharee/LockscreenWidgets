package tk.zwander.lockscreenwidgets.observables

import java.util.*

/**
 * [Observable] implementation that implicitly assumes it's always
 * in the changed state. Used to tell widgets in the widget frame
 * whether they should be showing the remove button or not.
 */
class RemoveButtonObservable : Observable() {
    override fun notifyObservers(arg: Any?) {
        setChanged()
        super.notifyObservers(arg)
    }
}