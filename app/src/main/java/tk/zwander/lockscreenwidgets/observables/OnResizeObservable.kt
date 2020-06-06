package tk.zwander.lockscreenwidgets.observables

import java.util.*

/**
 * [Observable] implementation that implicitly assumes it's always
 * in the changed state. Used to tell widgets in the widget frame
 * that the frame has been resized, and they may need to update their
 * widths.
 */
class OnResizeObservable : Observable() {
    override fun notifyObservers(arg: Any?) {
        setChanged()
        super.notifyObservers(arg)
    }
}