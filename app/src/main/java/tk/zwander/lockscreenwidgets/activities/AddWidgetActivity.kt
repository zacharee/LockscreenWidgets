package tk.zwander.lockscreenwidgets.activities

import android.app.Activity
import android.app.KeyguardManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_add_widget.*
import kotlinx.coroutines.*
import tk.zwander.lockscreenwidgets.adapters.AppAdapter
import tk.zwander.lockscreenwidgets.data.AppInfo
import tk.zwander.lockscreenwidgets.data.WidgetData
import tk.zwander.lockscreenwidgets.data.WidgetListInfo
import tk.zwander.lockscreenwidgets.host.WidgetHost
import tk.zwander.lockscreenwidgets.util.prefManager
import tk.zwander.systemuituner.lockscreenwidgets.R


class AddWidgetActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    companion object {
        const val PERM_CODE = 104
        const val CONFIG_CODE = 105
    }

    private val kgm by lazy { getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
    private val widgetManager by lazy { AppWidgetManager.getInstance(this) }
    private val widgetHost by lazy { WidgetHost(this, 1003) }

    private val appWidgetManager by lazy { AppWidgetManager.getInstance(this) }
    private val adapter by lazy {
        AppAdapter(this) {
            tryBindWidget(it.providerInfo)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            kgm.requestDismissKeyguard(this, null)
        } else {
            kgm.dismissKeyguard(this, null, null)
        }

        setContentView(R.layout.activity_add_widget)

        selection_list.adapter = adapter

        populateAsync()
    }

    override fun onBackPressed() {
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            PERM_CODE -> {
                if (resultCode == Activity.RESULT_OK) tryBindWidget(
                    widgetManager.getAppWidgetInfo(data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: return)
                )
            }

            CONFIG_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: return
                    if (id == -1) return

                    addNewWidget(id)
                }
            }
        }
    }

    private fun tryBindWidget(info: AppWidgetProviderInfo, id: Int = widgetHost.allocateAppWidgetId()) {
        val canBind = widgetManager.bindAppWidgetIdIfAllowed(id, info.provider)

        if (!canBind) getWidgetPermission(id, info.provider)
        else {
            if (info.configure != null && !prefManager.currentWidgets.map { it.id }.contains(id)) {
                configureWidget(id, info.configure)
            } else {
                addNewWidget(id)
            }
        }
    }

    private fun getWidgetPermission(id: Int, provider: ComponentName) {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
        startActivityForResult(intent, PERM_CODE)
    }

    private fun configureWidget(id: Int, configure: ComponentName) {
        try {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            intent.component = configure
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            startActivityForResult(intent, CONFIG_CODE)
        } catch (e: Exception) {
            addNewWidget(id)
        }
    }

    private fun addNewWidget(id: Int) {
        val widget = WidgetData(id)
        prefManager.currentWidgets = prefManager.currentWidgets.apply {
            add(widget)
        }
        finish()
    }

    private fun populateAsync() = launch {
        val apps = withContext(Dispatchers.Main) {
            val apps = HashMap<String, AppInfo>()

            appWidgetManager.installedProviders.forEach {
                val appInfo = packageManager.getApplicationInfo(it.provider.packageName, 0)

                val appName = packageManager.getApplicationLabel(appInfo)
                val widgetName = it.loadLabel(packageManager)

                var app = apps[appInfo.packageName]
                if (app == null) {
                    apps[appInfo.packageName] = AppInfo(appName.toString(), appInfo)
                    app = apps[appInfo.packageName]!!
                }

                app.widgets.add(WidgetListInfo(widgetName,
                    it.previewImage.run { if (this != 0) this else appInfo.icon },
                    it, appInfo))
            }

            apps
        }

        adapter.addItems(apps.values)
        progress.visibility = View.GONE
        selection_list.visibility = View.VISIBLE
    }
}