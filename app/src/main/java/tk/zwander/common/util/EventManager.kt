@file:Suppress("unused")

package tk.zwander.common.util

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.zwander.lswinterconnect.safeApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import tk.zwander.lockscreenwidgets.App
import java.util.concurrent.ConcurrentLinkedQueue

val Context.eventManager: EventManager
    get() = EventManager.getInstance(this)

class EventManager private constructor(private val context: Context) {
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
    private val observersLock = Any()
    private val observers = mutableMapOf<EventObserver, ObserverQueue>()

    private class ObserverQueue {
        val channel = Channel<Event>(Channel.UNLIMITED)
        var job: Job? = null
    }

    inline fun <reified T : Event> LifecycleOwner.registerListener(noinline listener: (T) -> Unit) {
        addListener(listener)

        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    removeListener(listener)
                    lifecycle.removeObserver(this)
                }
            },
        )
    }

    inline fun <reified T : Event> addListener(noinline listener: (T) -> Unit) {
        addListener(
            ListenerInfo(
                T::class.java,
                listener,
            ),
        )
    }

    fun <T : Event> addListener(listenerInfo: ListenerInfo<T>) {
        @Suppress("UNCHECKED_CAST")
        listeners.add(listenerInfo as ListenerInfo<Event>)
    }

    fun LifecycleOwner.registerObserver(observer: EventObserver) {
        addObserver(observer)

        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    removeObserver(observer)
                    lifecycle.removeObserver(this)
                }
            },
        )
    }

    fun addObserver(observer: EventObserver) {
        synchronized(observersLock) {
            if (observers.containsKey(observer)) return

            val queue = ObserverQueue()
            observers[observer] = queue

            // Deliver events to each observer serially, so a suspend onEvent() body
            // can't interleave with another event for the same observer.
            queue.job = App.scope.launch(Dispatchers.Main.immediate) {
                for (event in queue.channel) {
                    try {
                        observer.onEvent(event)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        context.logUtils.normalLog(
                            "Error delivering event $event to $observer",
                            t,
                        )
                    }
                }
            }
        }
    }

    fun <T : Event> removeListener(listener: (T) -> Unit) {
        listeners.removeAll { it.listener == listener }
    }

    fun <T : Event> removeListener(listenerInfo: ListenerInfo<T>) {
        @Suppress("UNCHECKED_CAST")
        listeners.remove(listenerInfo as ListenerInfo<Event>)
    }

    fun removeObserver(observer: EventObserver) {
        val queue = synchronized(observersLock) {
            observers.remove(observer)
        } ?: return

        // Drop any events that haven't been delivered yet.
        queue.channel.close()
        queue.job?.cancel()
    }

    fun sendEvent(event: Event) {
        context.logUtils.debugLog("Sending event $event", null)

        val queues = synchronized(observersLock) {
            observers.values.toList()
        }

        queues.forEach {
            it.channel.trySend(event)
        }

        listeners.filter { it.listenerClass == event::class.java }
            .forEach {
                App.scope.launch(Dispatchers.Main) {
                    it.listener.invoke(event)
                }
            }
    }
}

sealed class Event {
    data object LockscreenDismissed : Event()
    data object NightModeUpdate : Event()
    data object RequestNotificationCount : Event()
    data object CloseSystemDialogs : Event()
    data object TurnOffDisplay : Event()

    /**
     * On Android 8.0+, it's pretty easy to dismiss the lock screen with a simple API call.
     * On earlier Android versions, it's not so easy, and we need a way to detect when the
     * lock screen has successfully been dismissed.
     */
    data class NewNotificationCount(val count: Int) : Event()
    data class PreviewFrames(val show: ShowMode) : Event() {
        enum class ShowMode {
            SHOW,
            HIDE,
            TOGGLE,
        }
    }

    data class StackUpdateComplete(val stackId: Int) : Event()

    //*** Widget Drawer
    data object CloseDrawer : Event()
    data object ShowDrawer : Event()
}

interface EventObserver {
    suspend fun onEvent(event: Event)
}

data class ListenerInfo<T : Event>(
    val listenerClass: Class<T>,
    val listener: (T) -> Unit,
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
