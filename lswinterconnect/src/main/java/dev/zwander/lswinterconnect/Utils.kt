package dev.zwander.lswinterconnect

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.Strictness

val Context.safeApplicationContext: Context
    get() = applicationContext ?: this

fun Throwable.stringify(): String? {
    return try {
        GsonBuilder()
            .setStrictness(Strictness.LENIENT)
            .enableComplexMapKeySerialization()
            .create()
            .toJson(this)
    } catch (e: Throwable) {
        peekLogUtils?.normalLog("Unable to serialize throwable.", e)
        null
    }
}
