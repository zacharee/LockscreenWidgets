package tk.zwander.lockscreenwidgets.util

import android.annotation.SuppressLint
import android.content.Context
import tk.zwander.lockscreenwidgets.activities.DismissOrUnlockActivity

class EventManager private constructor(private val context: Context) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var _instance: EventManager? = null

        fun getInstance(context: Context): EventManager {
            return _instance ?: EventManager(context.safeApplicationContext).apply {
                _instance = this
            }
        }
    }

    private val listeners: MutableList<ListenerInfo<Event>> = ArrayList()

    inline fun <reified T : Event> addListener(noinline listener: (T) -> Unit) {
        addListener(
            ListenerInfo(
                T::class.java,
                listener
            )
        )
    }

    fun <T : Event> addListener(listenerInfo: ListenerInfo<T>) {
        listeners.add(listenerInfo as ListenerInfo<Event>)
    }

    inline fun <reified T : Event> removeListener(noinline listener: (T) -> Unit) {
        removeListener(
            ListenerInfo(
                T::class.java,
                listener
            )
        )
    }

    fun <T : Event> removeListener(listenerInfo: ListenerInfo<T>) {
        listeners.remove(listenerInfo as ListenerInfo<Event>)
    }

    fun sendEvent(event: Event) {
        listeners.filter { it.listenerClass == event::class.java }
            .forEach {
                it.listener.invoke(event)
            }
    }
}

sealed class Event {
    object LockscreenDismissed : Event()

    /**
     * On Android 8.0+, it's pretty easy to dismiss the lock screen with a simple API call.
     * On earlier Android versions, it's not so easy, and we need a way to detect when the
     * lock screen has successfully been dismissed.
     */
    data class NewNotificationCount(val count: Int) : Event()
}

data class ListenerInfo<T : Event>(
    val listenerClass: Class<T>,
    val listener: (T) -> Unit
)