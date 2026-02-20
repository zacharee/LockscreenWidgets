package tk.zwander.common.util

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.ServiceManager
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity.RESULT_OK
import androidx.core.app.ActivityOptionsCompat
import com.android.internal.appwidget.IAppWidgetService
import tk.zwander.common.host.widgetHostCompat

class ConfigureLauncher(
    private val activity: ComponentActivity,
    private val addNewWidget: (id: Int, provider: AppWidgetProviderInfo) -> Unit,
    private val finishIfNoErrors: () -> Unit,
) {
    companion object {
        private const val CONFIGURE_REQ = 1000
    }

    private var currentConfigId: Int? = null

    private val configLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            onActivityResult(CONFIGURE_REQ, result.resultCode, result.data)
        }
    private val samsungConfigLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            onActivityResult(CONFIGURE_REQ, result.resultCode, result.data)
        }

    private val widgetHost by lazy { activity.widgetHostCompat }

    @SuppressLint("NewApi")
    fun launch(id: Int): Boolean {
        try {
            val samsungConfigComponent = activity.appWidgetManager.getAppWidgetInfo(id)
                ?.getSamsungConfigureComponent(activity)

            activity.logUtils.debugLog("Found Samsung config component $samsungConfigComponent for $id.")

            if (samsungConfigComponent != null) {
                val launchIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                launchIntent.component = samsungConfigComponent
                launchIntent.putExtra("appWidgetId", id)

                currentConfigId = id
                samsungConfigLauncher.launch(launchIntent)
                return true
            }
        } catch (e: Throwable) {
            activity.logUtils.normalLog("Error configuring Samsung widget", e)
        }

        //Use the system API instead of ACTION_APPWIDGET_CONFIGURE to try to avoid some permissions issues
        try {
            val intentSender =
                IAppWidgetService.Stub.asInterface(ServiceManager.getService(Context.APPWIDGET_SERVICE))
                    .createAppWidgetConfigIntentSender(activity.opPackageName, id, 0)

            activity.logUtils.debugLog("Intent sender is $intentSender")

            if (intentSender != null) {
                configLauncher.launch(
                    IntentSenderRequest.Builder(intentSender)
                        .build(),
                    ActivityOptionsCompat.makeBasic().apply {
                        @SuppressLint("WrongConstant")
                        setPendingIntentBackgroundActivityStartMode(
                            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                            } else {
                                @Suppress("DEPRECATION")
                                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                            },
                        )
                    },
                )
                currentConfigId = id
                return true
            }
        } catch (e: Throwable) {
            activity.logUtils.normalLog("Unable to launch widget config IntentSender", e)
        }

        try {
            currentConfigId = id
            widgetHost.startAppWidgetConfigureActivityForResult(
                activity,
                id, 0, CONFIGURE_REQ,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ActivityOptions
                        .makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(
                            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                            } else {
                                @Suppress("DEPRECATION")
                                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                            },
                        )
                        .toBundle()
                } else null,
            )
            return true
        } catch (e: Throwable) {
            activity.logUtils.normalLog("Unable to startAppWidgetConfigureActivityForResult", e)
        }

        return false
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CONFIGURE_REQ) {
            val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, currentConfigId ?: -1)
                ?: currentConfigId

            activity.logUtils.debugLog("Configure complete for id $id $currentConfigId", null)

            if (id != null && id != -1) {
                val widgetInfo: AppWidgetProviderInfo? = activity.appWidgetManager.getAppWidgetInfo(id)
                var resultOk = resultCode == RESULT_OK

                if (widgetInfo?.configure != null) {
                    val matchedComponents = activity.packageManager.queryIntentActivities(
                        Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                            addCategory(Intent.CATEGORY_DEFAULT)
                        },
                        0,
                    ) + activity.packageManager.queryIntentActivities(
                        Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE),
                        0,
                    )

                    if (matchedComponents.isEmpty() || !matchedComponents.any { it.activityInfo.componentNameCompat == widgetInfo.configure }) {
                        activity.logUtils.debugLog("Found a widget configuration that probably wasn't expecting to be launched here. Assuming a canceled result should still continue. ${widgetInfo.provider}, ${widgetInfo.configure}", null)
                        resultOk = true
                    }
                } else if (widgetInfo != null) {
                    activity.logUtils.debugLog("Found a widget configuration that no longer exists? ${widgetInfo.provider}", null)
                    resultOk = true
                }

                if (resultOk) {
                    activity.logUtils.debugLog("Successfully configured widget.", null)

                    if (widgetInfo == null) {
                        activity.logUtils.debugLog(
                            "Unable to get widget info for $id, not adding",
                            null
                        )
                        finishIfNoErrors()
                        return
                    }

                    currentConfigId = null

                    addNewWidget(id, widgetInfo)
                } else {
                    activity.logUtils.debugLog(
                        "Failed to configure widget. Result code $resultCode, id $id.",
                        null
                    )
                    finishIfNoErrors()
                }
            } else {
                activity.logUtils.debugLog(
                    "Failed to configure widget. Result code $resultCode, id $id.",
                    null
                )
                finishIfNoErrors()
            }
        }
    }

    fun destroy(deleteOnConfigureError: Boolean) {
        if (deleteOnConfigureError) {
            currentConfigId?.let {
                //Widget configuration was canceled: delete the
                //allocated ID
                widgetHost.deleteAppWidgetId(it)
            }
        }
    }
}
