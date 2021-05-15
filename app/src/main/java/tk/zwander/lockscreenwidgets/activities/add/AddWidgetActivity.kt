package tk.zwander.lockscreenwidgets.activities.add

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import kotlinx.coroutines.*
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.DismissOrUnlockActivity
import tk.zwander.lockscreenwidgets.adapters.AppAdapter
import tk.zwander.lockscreenwidgets.data.AppInfo
import tk.zwander.lockscreenwidgets.data.WidgetData
import tk.zwander.lockscreenwidgets.data.WidgetListInfo
import tk.zwander.lockscreenwidgets.databinding.ActivityAddWidgetBinding
import tk.zwander.lockscreenwidgets.host.WidgetHostCompat
import tk.zwander.lockscreenwidgets.util.prefManager

/**
 * Manage the widget addition flow: selection, permissions, configurations, etc.
 */
open class AddWidgetActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    companion object {
        const val PERM_CODE = 104
        const val CONFIG_CODE = 105
    }

    private val widgetHost by lazy { WidgetHostCompat.getInstance(this, 1003) }
    private val appWidgetManager by lazy { AppWidgetManager.getInstance(this) }
    private val adapter by lazy {
        AppAdapter(this) {
            tryBindWidget(it.providerInfo)
        }
    }

    private var searchItem: MenuItem? = null
    private val searchView: SearchView?
        get() = (searchItem?.actionView as SearchView?)

    private var doneLoading = false

    private val binding by lazy { ActivityAddWidgetBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /**
         * We want the user to unlock the device when adding a widget, since potential configuration Activities
         * won't show on the lock screen.
         */
        val intent = Intent(this, DismissOrUnlockActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)

        setContentView(binding.root)

        binding.selectionList.adapter = adapter

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        populateAsync()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_search, menu)
        searchItem = menu?.findItem(R.id.search)

        searchItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                adapter.currentFilter = null
                return true
            }

            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                return true
            }
        })

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.currentFilter = newText
                return true
            }

            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }
        })

        if (doneLoading) {
            searchItem?.isVisible = true
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item?.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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
                val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: return

                if (resultCode == Activity.RESULT_OK) {
                    //The user has granted permission for Lockscreen Widgets
                    //so retry binding the widget
                    tryBindWidget(
                        appWidgetManager.getAppWidgetInfo(id)
                    )
                } else {
                    //The user didn't allow Lockscreen Widgets to bind
                    //widgets, so delete the allocated ID
                    widgetHost.deleteAppWidgetId(id)
                }
            }

            CONFIG_CODE -> {
                val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: return
                if (id == -1) return

                if (resultCode == Activity.RESULT_OK) {
                    //Widget configuration was successful: add the
                    //widget to the frame
                    addNewWidget(id)
                } else {
                    //Widget configuration was canceled: delete the
                    //allocated ID
                    widgetHost.deleteAppWidgetId(id)
                }
            }
        }
    }

    /**
     * Start the widget binding process.
     * If Lockscreen Widgets isn't allowed to bind widgets, request permission.
     * Otherwise, if the widget to be bound has a configuration Activity,
     * launch that.
     * Otherwise, just add the widget to the frame.
     *
     * @param info the widget to be bound
     * @param id the ID of the widget to be bound. If this is being called on saved
     * widgets (i.e. after an app restart), then the ID will be provided. Otherwise,
     * it will be allocated.
     */
    private fun tryBindWidget(info: AppWidgetProviderInfo, id: Int = widgetHost.allocateAppWidgetId()) {
        val canBind = appWidgetManager.bindAppWidgetIdIfAllowed(id, info.provider)

        if (!canBind) getWidgetPermission(id, info.provider)
        else {
            //Only launch the config Activity if the widget isn't already bound (avoid reconfiguring it
            //every time the app restarts)
            if (info.configure != null && !prefManager.currentWidgets.map { it.id }.contains(id)) {
                configureWidget(id)
            } else {
                addNewWidget(id)
            }
        }
    }

    /**
     * Request permission to bind widgets.
     *
     * @param id the ID of the current widget
     * @param provider the current widget's provider
     */
    private fun getWidgetPermission(id: Int, provider: ComponentName) {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
        startActivityForResult(intent, PERM_CODE)
    }

    /**
     * Launch the specified widget's configuration Activity.
     *
     * @param id the ID of the widget to configure
     */
    private fun configureWidget(id: Int) {
        try {
            //Use the system API instead of ACTION_APPWIDGET_CONFIGURE to try to avoid some permissions issues
            widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, CONFIG_CODE, null)
        } catch (e: Exception) {
            Toast.makeText(this, resources.getString(R.string.configure_widget_error, appWidgetManager.getAppWidgetInfo(id).provider), Toast.LENGTH_LONG).show()
            addNewWidget(id)
        }
    }

    /**
     * Add the specified widget to the frame and save it to SharedPreferences.
     *
     * @param id the ID of the widget to be added
     */
    protected open fun addNewWidget(id: Int) {
        val widget = WidgetData(id)
        prefManager.currentWidgets = prefManager.currentWidgets.apply {
            add(widget)
        }
        finish()
    }

    /**
     * Populate the selection list with the available widgets.
     * Lockscreen Widgets checks for both home screen and keyguard
     * widgets.
     *
     * This method runs asynchronously to avoid hanging the UI thread.
     */
    private fun populateAsync() = launch {
        val apps = withContext(Dispatchers.Main) {
            val apps = HashMap<String, AppInfo>()

            (appWidgetManager.installedProviders + appWidgetManager.getInstalledProviders(AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD)).forEach {
                val appInfo = packageManager.getApplicationInfo(it.provider.packageName, 0)

                val appName = packageManager.getApplicationLabel(appInfo)
                val widgetName = it.loadLabel(packageManager)

                var app = apps[appInfo.packageName]
                if (app == null) {
                    apps[appInfo.packageName] = AppInfo(appName.toString(), appInfo)
                    app = apps[appInfo.packageName]!!
                }

                app.widgets.add(WidgetListInfo(
                    widgetName,
                    it.previewImage.run { if (this != 0) this else appInfo.icon },
                    it,
                    appInfo
                ))
            }

            apps
        }

        adapter.setItems(apps.values.toList())
        binding.progress.visibility = View.GONE
        binding.selectionList.visibility = View.VISIBLE
        searchItem?.isVisible = true

        doneLoading = true
    }
}