package tk.zwander.common.util

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.collection.mutableScatterMapOf
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.PausableMonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.core.os.HandlerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.findViewTreeLifecycleOwner
import dev.zwander.lswinterconnect.peekLogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

var LifecycleRegistry.safeCurrentState: Lifecycle.State
    get() = currentState
    set(value) {
        try {
            currentState = value
        } catch (e: IllegalStateException) {
            peekLogUtils?.debugLog("Error changing lifecycle state", e)
        }
    }

fun View.createAlwaysOnComposer(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    lifecycle: Lifecycle? = null,
): Recomposer {
    // Only access AndroidUiDispatcher.CurrentThread if we would use an element from it,
    // otherwise prevent lazy initialization.
    val baseContext =
        if (
            coroutineContext[ContinuationInterceptor] == null ||
            coroutineContext[MonotonicFrameClock] == null
        ) {
            AndroidUiDispatcher.CurrentThread + coroutineContext
        } else coroutineContext
    val pausableClock =
        baseContext[MonotonicFrameClock]?.let { PausableMonotonicFrameClock(it).apply { pause() } }

    var motionDurationScaleImpl: MotionDurationScaleImpl? = null
    val motionDurationScale =
        baseContext[MotionDurationScale]
            ?: MotionDurationScaleImpl(context.applicationContext).also {
                motionDurationScaleImpl = it
            }

    val contextWithClockAndMotionScale =
        baseContext + (pausableClock ?: EmptyCoroutineContext) + motionDurationScale
    val recomposer =
        Recomposer(contextWithClockAndMotionScale).also { it.pauseCompositionFrameClock() }
    val runRecomposeScope = CoroutineScope(contextWithClockAndMotionScale)
    val viewTreeLifecycle =
        checkNotNull(lifecycle ?: findViewTreeLifecycleOwner()?.lifecycle) {
            "ViewTreeLifecycleOwner not found from $this"
        }

    viewTreeLifecycle.addObserver(
        object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                val self = this
                when (event) {
                    Lifecycle.Event.ON_CREATE -> {
                        // Undispatched launch since we've configured this scope
                        // to be on the UI thread
                        runRecomposeScope.launch(start = CoroutineStart.UNDISPATCHED) {
                            // Tied to the effect coroutine context which is cancelled on destroy
                            // below.
                            motionDurationScaleImpl?.coroutineScope =
                                CoroutineScope(recomposer.effectCoroutineContext)

                            try {
                                recomposer.runRecomposeAndApplyChanges()
                            } finally {
                                // If runRecomposeAndApplyChanges returns or this coroutine is
                                // cancelled it means we no longer care about this lifecycle.
                                // Clean up the dangling references tied to this observer.
                                source.lifecycle.removeObserver(self)
                            }
                        }
                    }
                    Lifecycle.Event.ON_START -> {
                        // The clock starts life as paused so resume it when starting. If it is
                        // already running (this ON_START is after an ON_STOP) then the resume is
                        // ignored.
                        pausableClock?.resume()

                        // Resumes the frame clock dispatching If this is an ON_START after an
                        // ON_STOP that paused it. If the recomposer is not paused  calling
                        // `resumeFrameClock()` is ignored.
                        recomposer.resumeCompositionFrameClock()
                    }
                    Lifecycle.Event.ON_STOP -> {
                        // Pause the recomposer's frame clock which will pause all calls to
                        // `withFrameNanos` (e.g. animations) while the window is stopped.
                        recomposer.pauseCompositionFrameClock()
                    }
                    Lifecycle.Event.ON_DESTROY -> {
                        recomposer.cancel()
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        // Nothing
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        // Nothing
                    }
                    Lifecycle.Event.ON_ANY -> {
                        // Nothing
                    }
                }
            }
        }
    )
    return recomposer
}

private class MotionDurationScaleImpl(private val applicationContext: Context) :
    MotionDurationScale {
    var coroutineScope: CoroutineScope? = null

    private var _scaleFactor by mutableFloatStateOf(1f)
    var job: Job? = null

    override val scaleFactor: Float
        get() {
            if (job == null) {
                job = startObservingSystemScaleFactor()
            }
            return _scaleFactor
        }

    private fun startObservingSystemScaleFactor(): Job {
        val durationScaleStateFlow = getAnimationScaleFlowFor(applicationContext)
        _scaleFactor = durationScaleStateFlow.value

        val scope =
            coroutineScope
                ?: error("MotionDurationScale scale factor requested before recomposer loop start")
        return scope.launch {
            durationScaleStateFlow.collect { scaleFactor -> _scaleFactor = scaleFactor }
        }
    }
}

private val animationScale = mutableScatterMapOf<Context, StateFlow<Float>>()

private fun Context.readAnimationScale() =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

fun getAnimationScaleFlowFor(applicationContext: Context): StateFlow<Float> {
    return synchronized(animationScale) {
        animationScale.getOrPut(applicationContext) {
            val resolver = applicationContext.contentResolver
            val animationScaleUri =
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
            val channel = Channel<Unit>(CONFLATED)
            val contentObserver =
                object : ContentObserver(HandlerCompat.createAsync(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean, uri: Uri?) {
                        channel.trySend(Unit)
                    }
                }

            // TODO: Switch to callbackFlow when it becomes stable
            flow {
                resolver.registerContentObserver(animationScaleUri, false, contentObserver)
                try {
                    for (value in channel) {
                        val newValue = applicationContext.readAnimationScale()
                        emit(newValue)
                    }
                } finally {
                    resolver.unregisterContentObserver(contentObserver)
                }
            }
                .stateIn(
                    MainScope(),
                    SharingStarted.WhileSubscribed(),
                    applicationContext.readAnimationScale(),
                )
        }
    }
}
