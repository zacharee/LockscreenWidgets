package tk.zwander.common.iconpacks

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.util.Xml
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import tk.zwander.common.util.HandlerRegistry
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.handler
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.safeApplicationContext
import tk.zwander.lockscreenwidgets.R
import java.io.IOException
import java.util.TreeSet

val Context.iconPackManager: IconPackManager
    get() = IconPackManager.getInstance(this)

/**
 * Parts based on https://github.com/LawnchairLauncher/lawnchair/blob/689d250d4bedc8a8c917b8d872d830ec89bc5e14/lawnchair/src/app/lawnchair/ui/preferences/PreferenceViewModel.kt
 */
class IconPackManager private constructor(private val context: Context) : CoroutineScope by MainScope() {
    companion object {
        private val iconPackIntents = listOf(
            Intent("com.novalauncher.THEME"),
            Intent("org.adw.launcher.icons.ACTION_PICK_ICON"),
            Intent("com.dlto.atom.launcher.THEME"),
            Intent("android.intent.action.MAIN").addCategory("com.anddoes.launcher.THEME"),
        )

        @SuppressLint("StaticFieldLeak")
        private var instance: IconPackManager? = null

        fun getInstance(context: Context): IconPackManager {
            return instance ?: IconPackManager(context.safeApplicationContext).apply {
                instance = this
            }
        }
    }

    private val _currentIconPack = MutableStateFlow<IconPack?>(null)
    val currentIconPack: StateFlow<IconPack?>
        get() = _currentIconPack.asStateFlow()

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_PACKAGE_REPLACED) {
                val replacedPackage = intent.data?.host

                if (replacedPackage == _currentIconPack.value?.packPackage) {
                    updatePack()
                }
            }
        }
    }

    private val prefsHandler = HandlerRegistry {
        handler(PrefManager.KEY_SELECTED_ICON_PACK_PACKAGE) {
            updatePack()
        }
    }

    init {
        ContextCompat.registerReceiver(
            context,
            packageChangeReceiver,
            IntentFilter(Intent.ACTION_PACKAGE_REPLACED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        prefsHandler.register(context)
        updatePack()
    }

    fun getIconPackPackages(): List<LoadedIconPack> {
        val packs = iconPackIntents.flatMap {
            context.packageManager.queryIntentActivities(it, 0)
        }.associateBy {
            it.activityInfo.packageName
        }.mapTo(mutableSetOf()) { (_, info) ->
            LoadedIconPack(
                label = info.activityInfo.applicationInfo.loadLabel(context.packageManager).toString(),
                packageName = info.activityInfo.packageName,
                packIcon = info.activityInfo.applicationInfo.loadIcon(context.packageManager),
            )
        }

        val defaultIconsPack = LoadedIconPack(
            label = context.resources.getString(R.string.system_icons),
            packageName = null,
            packIcon = ResourcesCompat.getDrawable(context.resources, R.drawable.android, context.theme)!!,
        )

        return (packs + defaultIconsPack).sortedBy { (it.label ?: it.packageName)?.lowercase() }
    }

    fun getIconPackIcons(packageName: String): TreeSet<IconPackIcon> {
        val packMap = loadIconPackMap(packageName) ?: return TreeSet()
        val allEntries = packMap.getAllEntries()

        return TreeSet(
            allEntries.map { (component, entry) ->
                IconPackIcon(
                    name = entry.name,
                    component = component,
                    loadDrawable = {
                        packMap.resolveIcon(context, component)
                    },
                    entry = entry,
                )
            }
        )
    }

    fun loadIconPackMap(packageName: String): IconPack? {
        val parseXml = getXml("appfilter", packageName) ?: return null
        val compStart = "ComponentInfo{"
        val compStartLength = compStart.length
        val compEnd = "}"
        val compEndLength = compEnd.length

        val componentMap = mutableMapOf<ComponentName, IconEntry>()
        val calendarMap = mutableMapOf<ComponentName, IconEntry>()
        val clockMap = mutableMapOf<ComponentName, IconEntry>()
        val clockMetadata = mutableMapOf<IconEntry, ClockMetadata>()

        try {
            while (parseXml.next() != XmlPullParser.END_DOCUMENT) {
                if (parseXml.eventType != XmlPullParser.START_TAG) continue
                val name = parseXml.name
                val isCalendar = name == "calendar"
                when (name) {
                    "item", "calendar" -> {
                        var componentName: String? = parseXml["component"]
                        val drawableName = parseXml[if (isCalendar) "prefix" else "drawable"]
                        if (componentName != null && drawableName != null) {
                            if (componentName.startsWith(compStart) && componentName.endsWith(compEnd)) {
                                componentName = componentName.substring(compStartLength, componentName.length - compEndLength)
                            }
                            val parsed = ComponentName.unflattenFromString(componentName)
                            if (parsed != null) {
                                if (isCalendar) {
                                    calendarMap[parsed] = IconEntry(packageName, drawableName, IconType.Calendar)
                                } else {
                                    componentMap[parsed] = IconEntry(packageName, drawableName, IconType.Normal)
                                }
                            }
                        }
                    }
                    "dynamic-clock" -> {
                        val drawableName = parseXml["drawable"]
                        if (drawableName != null) {
                            if (parseXml is XmlResourceParser) {
                                clockMetadata[IconEntry(packageName, drawableName, IconType.Normal)] = ClockMetadata(
                                    parseXml.getAttributeIntValue(null, "hourLayerIndex", -1),
                                    parseXml.getAttributeIntValue(null, "minuteLayerIndex", -1),
                                    parseXml.getAttributeIntValue(null, "secondLayerIndex", -1),
                                    parseXml.getAttributeIntValue(null, "defaultHour", 0),
                                    parseXml.getAttributeIntValue(null, "defaultMinute", 0),
                                    parseXml.getAttributeIntValue(null, "defaultSecond", 0),
                                )
                            }
                        }
                    }
                }
            }
            componentMap.forEach { (componentName, iconEntry) ->
                if (clockMetadata.containsKey(iconEntry)) {
                    clockMap[componentName] = iconEntry
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }

        return IconPack(
            packPackage = packageName,
            componentMap = componentMap,
            clockMap = clockMap,
            calendarMap = calendarMap,
            clockMetadata = clockMetadata,
        )
    }

    @Suppress("SameParameterValue")
    private fun getXml(name: String, packageName: String): XmlPullParser? {
        val res: Resources
        try {
            res = context.packageManager.getResourcesForApplication(packageName)
            @SuppressLint("DiscouragedApi")
            val resourceId = res.getIdentifier(name, "xml", packageName)
            return if (0 != resourceId) {
                context.packageManager.getXml(packageName, resourceId, null)
            } else {
                val factory = XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(res.assets.open("$name.xml"), Xml.Encoding.UTF_8.toString())
                parser
            }
        } catch (_: PackageManager.NameNotFoundException) {
        } catch (_: IOException) {
        } catch (_: XmlPullParserException) {
        }
        return null
    }

    private fun updatePack() {
        launch(Dispatchers.IO) {
            _currentIconPack.value = context.prefManager.selectedIconPackPackage?.let { loadIconPackMap(it) }
        }
    }
}

private operator fun XmlPullParser.get(key: String): String? = this.getAttributeValue(null, key)
