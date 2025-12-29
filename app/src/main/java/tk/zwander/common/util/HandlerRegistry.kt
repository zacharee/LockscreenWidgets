@file:Suppress("unused")

package tk.zwander.common.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class HandlerRegistry(setup: HandlerRegistry.() -> Unit) : SharedPreferences.OnSharedPreferenceChangeListener {
    private val items: HashMap<String, ItemHandler> = HashMap()

    private val scope = atomic<CoroutineScope?>(null)

    init {
        setup.invoke(this)
    }

    fun putHandler(handler: ItemHandler) {
        handler.keys.forEach { key ->
            items[key] = handler
        }
    }

    fun removeHandler(key: String) {
        items.remove(key)
    }

    fun removeHandler(keys: List<String>) {
        keys.forEach { key ->
            removeHandler(key)
        }
    }

    fun handle(key: String) {
        scope.value?.launch(Dispatchers.Main) {
            items[key]?.action?.let { action ->
                peekLogUtils?.debugLog("Handling shared pref change for $key", null)
                action.invoke(key)
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        key?.let { handle(key) }
    }

    fun register(context: Context) {
        scope.value = MainScope()
        context.prefManager.registerOnSharedPreferenceChangeListener(this)
    }

    fun unregister(context: Context) {
        scope.getAndSet(null)?.cancel()
        context.prefManager.unregisterOnSharedPreferenceChangeListener(this)
    }

    data class ItemHandler(
        val keys: List<String>,
        val action: suspend (String) -> Unit,
    ) {
        constructor(key: String, action: suspend (String) -> Unit) : this(listOf(key), action)
    }
}

fun HandlerRegistry.handler(vararg keys: String, action: suspend (String) -> Unit) {
    putHandler(HandlerRegistry.ItemHandler(keys.toList(), action))
}

fun HandlerRegistry.handler(keys: List<String>, action: suspend (String) -> Unit) {
    putHandler(HandlerRegistry.ItemHandler(keys, action))
}
