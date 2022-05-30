package tk.zwander.lockscreenwidgets.util

import android.content.Context
import android.content.SharedPreferences

class HandlerRegistry(setup: HandlerRegistry.() -> Unit) : SharedPreferences.OnSharedPreferenceChangeListener {
    private val items: HashMap<String, ItemHandler> = HashMap()

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
        items[key]?.action?.invoke(key)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        handle(key)
    }

    fun register(context: Context) {
        context.prefManager.registerOnSharedPreferenceChangeListener(this)
    }

    fun unregister(context: Context) {
        context.prefManager.unregisterOnSharedPreferenceChangeListener(this)
    }

    data class ItemHandler(
        val keys: List<String>,
        val action: (String) -> Unit
    ) {
        constructor(key: String, action: (String) -> Unit) : this(listOf(key), action)
    }
}

fun HandlerRegistry.handler(vararg keys: String, action: (String) -> Unit) {
    putHandler(HandlerRegistry.ItemHandler(keys.toList(), action))
}