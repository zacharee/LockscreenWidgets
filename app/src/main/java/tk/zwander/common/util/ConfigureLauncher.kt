package tk.zwander.common.util

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.ContextWrapper
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
) : ContextWrapper(activity) {
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

    private val widgetHost by lazy { widgetHostCompat }

    @SuppressLint("NewApi")
    fun launch(id: Int): Boolean {
        try {
            val samsungConfigComponent = appWidgetManager.getAppWidgetInfo(id)
                .getSamsungConfigureComponent(this)

            logUtils.debugLog("Found Samsung config component $samsungConfigComponent.")

            if (samsungConfigComponent != null) {
                val launchIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                launchIntent.component = samsungConfigComponent
                launchIntent.putExtra("appWidgetId", id)

                currentConfigId = id
                samsungConfigLauncher.launch(launchIntent)
                return true
            }
        } catch (e: Throwable) {
            logUtils.normalLog("Error configuring Samsung widget", e)
        }

        //Use the system API instead of ACTION_APPWIDGET_CONFIGURE to try to avoid some permissions issues
        try {
            val intentSender =
                IAppWidgetService.Stub.asInterface(ServiceManager.getService(Context.APPWIDGET_SERVICE))
                    .createAppWidgetConfigIntentSender(opPackageName, id, 0)

            logUtils.debugLog("Intent sender is $intentSender")

            if (intentSender != null) {
                configLauncher.launch(
                    IntentSenderRequest.Builder(intentSender)
                        .build(),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ActivityOptionsCompat.makeBasic()
                            .apply {
                                internalActivityOptions?.setPendingIntentBackgroundActivityStartMode(
                                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                                )
                            }
                    } else {
                        null
                    },
                )
                currentConfigId = id
                return true
            }
        } catch (e: Throwable) {
            logUtils.normalLog("Unable to launch widget config IntentSender", e)
        }

        try {
            currentConfigId = id
            widgetHost.startAppWidgetConfigureActivityForResult(
                activity,
                id, 0, CONFIGURE_REQ,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ActivityOptions
                        .makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                        .toBundle()
                } else null,
            )
            return true
        } catch (e: Throwable) {
            logUtils.normalLog("Unable to startAppWidgetConfigureActivityForResult", e)
        }

        return false
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CONFIGURE_REQ) {
            val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, currentConfigId ?: -1)
                ?: currentConfigId

            logUtils.debugLog("Configure complete for id $id $currentConfigId", null)

            if (resultCode == RESULT_OK && id != null && id != -1) {
                logUtils.debugLog("Successfully configured widget.", null)

                val widgetInfo = appWidgetManager.getAppWidgetInfo(id)

                if (widgetInfo == null) {
                    logUtils.debugLog("Unable to get widget info for $id, not adding", null)
                    finishIfNoErrors()
                    return
                }

                currentConfigId = null

                addNewWidget(id, widgetInfo)
            } else {
                logUtils.debugLog("Failed to configure widget. Result code $resultCode, id $id.", null)
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
