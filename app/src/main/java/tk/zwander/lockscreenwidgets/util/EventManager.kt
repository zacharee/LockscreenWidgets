package tk.zwander.lockscreenwidgets.util

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import tk.zwander.lockscreenwidgets.data.WidgetData

class EventManager private constructor(private val context: Context) {
    companion object {
        @Suppress("ObjectPropertyName")
        @SuppressLint("StaticFieldLeak")
        private var _instance: EventManager? = null

        fun getInstance(context: Context): EventManager {
            return _instance ?: EventManager(context.safeApplicationContext).apply {
                _instance = this
            }
        }
    }

    private val listeners: MutableList<ListenerInfo<Event>> = ArrayList()
    private val observers: MutableList<EventObserver> = ArrayList()

    inline fun <reified T : Event> LifecycleOwner.registerListener(noinline listener: (T) -> Unit) {
        addListener(listener)

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                removeListener(listener)
                lifecycle.removeObserver(this)
            }
        })
    }

    inline fun <reified T : Event> addListener(noinline listener: (T) -> Unit) {
        addListener(
            ListenerInfo(
                T::class.java,
                listener
            )
        )
    }

    fun <T : Event> addListener(listenerInfo: ListenerInfo<T>) {
        @Suppress("UNCHECKED_CAST")
        listeners.add(listenerInfo as ListenerInfo<Event>)
    }

    fun LifecycleOwner.registerObserver(observer: EventObserver) {
        addObserver(observer)

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                removeObserver(observer)
                lifecycle.removeObserver(this)
            }
        })
    }

    fun addObserver(observer: EventObserver) {
        observers.add(observer)
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
        @Suppress("UNCHECKED_CAST")
        listeners.remove(listenerInfo as ListenerInfo<Event>)
    }

    fun removeObserver(observer: EventObserver) {
        observers.remove(observer)
    }

    fun sendEvent(event: Event) {
        observers.forEach {
            it.onEvent(event)
        }

        listeners.filter { it.listenerClass == event::class.java }
            .forEach {
                it.listener.invoke(event)
            }
    }
}

sealed class Event {
    object LockscreenDismissed : Event()
    object TempHide : Event()
    object LaunchAddWidget : Event()
    object FrameMoveFinished : Event()

    /**
     * On Android 8.0+, it's pretty easy to dismiss the lock screen with a simple API call.
     * On earlier Android versions, it's not so easy, and we need a way to detect when the
     * lock screen has successfully been dismissed.
     */
    data class NewNotificationCount(val count: Int) : Event()
    data class FrameIntercept(val down: Boolean) : Event()
    data class FrameAttachmentState(val attached: Boolean) : Event()
    data class FrameMoved(val velX: Float, val velY: Float) : Event()
    data class FrameResized(val which: Side, val velocity: Int, val isUp: Boolean) : Event() {
        enum class Side {
            LEFT,
            TOP,
            RIGHT,
            BOTTOM
        }
    }
    data class RemoveWidgetConfirmed(val remove: Boolean, val item: WidgetData?) : Event()
}

interface EventObserver {
    fun onEvent(event: Event)
}

data class ListenerInfo<T : Event>(
    val listenerClass: Class<T>,
    val listener: (T) -> Unit
)