package tk.zwander.common.util

import android.content.Intent
import android.net.Uri
import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import tk.zwander.common.data.SafePointF
import java.lang.reflect.Type

class CrashFixExclusionStrategy : ExclusionStrategy {
    private val fieldsToAvoid = setOf(
        "IS_ELASTIC_ENABLED",
        "isElasticEnabled"
    )

    override fun shouldSkipClass(clazz: Class<*>?): Boolean {
        return false
    }

    override fun shouldSkipField(fieldAttributes: FieldAttributes): Boolean {
        val fieldName = fieldAttributes.name

        return fieldsToAvoid.contains(fieldName)
    }
}

class GsonUriHandler : JsonDeserializer<Uri>, JsonSerializer<Uri> {
    override fun deserialize(
        src: JsonElement, srcType: Type,
        context: JsonDeserializationContext
    ): Uri? {
        return try {
            Uri.parse(src.asString)
        } catch (e: Exception) {
            null
        }
    }

    override fun serialize(src: Uri, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return JsonPrimitive(src.toString())
    }
}

class GsonIntentHandler : JsonSerializer<Intent>, JsonDeserializer<Intent> {
    override fun serialize(src: Intent, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return JsonPrimitive(src.toUri(0))
    }

    override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext?): Intent? {
        return try {
            Intent.parseUri(json.asString, 0)
        } catch (e: Exception) {
            null
        }
    }
}

class GsonSafePointFHandler : JsonSerializer<SafePointF?>, JsonDeserializer<SafePointF?> {
    override fun serialize(
        src: SafePointF?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?,
    ): JsonElement {
        return JsonObject().apply {
            this.addProperty("x", src?.x)
            this.addProperty("y", src?.y)
        }
    }

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): SafePointF? {
        val obj = json?.asJsonObject ?: return null

        return SafePointF(obj.get("x").asFloat, obj.get("y").asFloat)
    }
}
