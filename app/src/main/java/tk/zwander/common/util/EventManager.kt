@file:Suppress("unused")

package tk.zwander.common.util

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import tk.zwander.common.data.WidgetData
import java.util.concurrent.ConcurrentLinkedQueue

val Context.eventManager: EventManager
    get() = EventManager.getInstance(this)

class EventManager private constructor(private val context: Context) : CoroutineScope by MainScope() {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var _instance: EventManager? = null

        @Synchronized
        fun getInstance(context: Context): EventManager {
            return _instance ?: EventManager(context.safeApplicationContext).apply {
                _instance = this
            }
        }
    }

    private val listeners: MutableCollection<ListenerInfo<Event>> = ConcurrentLinkedQueue()
    private val observers: MutableCollection<EventObserver> = ConcurrentLinkedQueue()

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

    fun <T : Event> removeListener(listener: (T) -> Unit) {
        listeners.removeAll { it.listener == listener }
    }

    fun <T : Event> removeListener(listenerInfo: ListenerInfo<T>) {
        @Suppress("UNCHECKED_CAST")
        listeners.remove(listenerInfo as ListenerInfo<Event>)
    }

    fun removeObserver(observer: EventObserver) {
        observers.remove(observer)
    }

    fun sendEvent(event: Event) {
        context.logUtils.debugLog("Sending event $event", null)

        observers.forEach {
            launch(Dispatchers.Main) {
                it.onEvent(event)
            }
        }

        listeners.filter { it.listenerClass == event::class.java }
            .forEach {
                launch(Dispatchers.Main) {
                    it.listener.invoke(event)
                }
            }
    }
}

sealed class Event {
    data object LockscreenDismissed : Event()
    data object ScreenOff : Event()
    data object NightModeUpdate : Event()
    data object RequestNotificationCount : Event()

    /**
     * On Android 8.0+, it's pretty easy to dismiss the lock screen with a simple API call.
     * On earlier Android versions, it's not so easy, and we need a way to detect when the
     * lock screen has successfully been dismissed.
     */
    data class NewNotificationCount(val count: Int) : Event()
    data class FrameIntercept(val frameId: Int, val down: Boolean) : Event()
    data class FrameAttachmentState(val frameId: Int, val attached: Boolean) : Event()
    data class FrameMoved(val frameId: Int, val velX: Float, val velY: Float) : Event()
    data class FrameResized(val frameId: Int, val which: Side, val velocity: Int, val isUp: Boolean) : Event() {
        enum class Side {
            LEFT,
            TOP,
            RIGHT,
            BOTTOM
        }
    }
    data class RemoveWidgetConfirmed(val remove: Boolean, val item: WidgetData?) : Event()
    data class DebugIdsUpdated(val ids: Collection<String>) : Event()
    data class FrameMoveFinished(val frameId: Int) : Event()
    data class CenterFrameHorizontally(val frameId: Int) : Event()
    data class CenterFrameVertically(val frameId: Int) : Event()
    data class FrameResizeFinished(val frameId: Int) : Event()
    data class LaunchAddWidget(val frameId: Int) : Event()
    data class TempHide(val frameId: Int) : Event()
    data class RemoveFrameConfirmed(val confirmed: Boolean, val frameId: Int?) : Event()
    data class PreviewFrames(val show: ShowMode, val requestCode: Int = -1, val includeMainFrame: Boolean = true) : Event() {
        enum class ShowMode {
            SHOW,
            HIDE,
            TOGGLE,
            SHOW_FOR_SELECTION,
        }
    }
    data class FrameSelected(val frameId: Int?, val requestCode: Int?) : Event()
    data class TrimMemory(val level: Int) : Event()

    //*** Widget Drawer
    data object CloseDrawer : Event()
    data object ShowDrawer : Event()
    data object ShowHandle : Event()
    data object DrawerShown : Event()
    data object DrawerHidden : Event()
    data object DrawerBackButtonClick : Event()

    data class LaunchAddDrawerWidget(val fromDrawer: Boolean) : Event()
    data class DrawerAttachmentState(val attached: Boolean) : Event()
    data class ScrollInDrawer(
        val from: Int,
        val dist: Float,
        val initial: Boolean,
        val velocity: Float,
    ) : Event()
    data class ScrollOpenFinish(val from: Int) : Event()
}

interface EventObserver {
    fun onEvent(event: Event)
}

data class ListenerInfo<T : Event>(
    val listenerClass: Class<T>,
    val listener: (T) -> Unit
)

@Composable
fun EventObserverEffect(observer: EventObserver?) {
    val context = LocalContext.current

    DisposableEffect(observer) {
        observer?.let { context.eventManager.addObserver(observer) }

        onDispose {
            observer?.let { context.eventManager.removeObserver(observer) }
        }
    }
}
