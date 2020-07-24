package tk.zwander.lockscreenwidgets.util

import android.annotation.SuppressLint
import android.app.IWallpaperManager
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.ImageView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.arasthel.spannedgridlayoutmanager.SpannedGridLayoutManager
import kotlinx.android.synthetic.main.widget_frame.view.*
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.AddWidgetActivity
import tk.zwander.lockscreenwidgets.activities.DismissOrUnlockActivity
import tk.zwander.lockscreenwidgets.adapters.WidgetFrameAdapter
import tk.zwander.lockscreenwidgets.host.WidgetHostCompat
import tk.zwander.lockscreenwidgets.views.RemoveWidgetConfirmationView
import tk.zwander.lockscreenwidgets.views.WidgetFrameView
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Handle most of the logic involving the widget frame.
 * TODO: make this work with multiple frame "clients" (i.e. a preview in MainActivity).
 */
class WidgetFrameDelegate private constructor(context: Context) : ContextWrapper(context), SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        private var instance: WidgetFrameDelegate? = null

        fun getInstance(context: Context): WidgetFrameDelegate {
            return instance ?: WidgetFrameDelegate(context).also {
                instance = it
            }
        }
    }

    private var isPendingNotificationStateChange = false

    var updatedForMove = false
    var isHoldingItem = false
    var notificationsPanelFullyExpanded = false
        set(value) {
            val changed = field != value
            field = value

            isPendingNotificationStateChange = changed
        }

    val saveForNC: Boolean
        get() = notificationsPanelFullyExpanded && prefManager.showInNotificationCenter

    //The size, position, and such of the widget frame on the lock screen.
    val params = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        width = dpAsPx(prefManager.getCorrectFrameWidth(saveForNC))
        height = dpAsPx(prefManager.getCorrectFrameHeight(saveForNC))

        x = prefManager.getCorrectFrameX(saveForNC)
        y = prefManager.getCorrectFrameY(saveForNC)

        gravity = Gravity.CENTER

        flags =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER
        format = PixelFormat.RGBA_8888
    }
    val wallpaper = getSystemService(Context.WALLPAPER_SERVICE) as WallpaperManager
    val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val widgetManager = AppWidgetManager.getInstance(this)
    val widgetHost = WidgetHostCompat.getInstance(this, 1003) {
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, DismissOrUnlockActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }, 100)
    }
    //The actual frame View
    val view = LayoutInflater.from(ContextThemeWrapper(this, R.style.AppTheme))
        .inflate(R.layout.widget_frame, null)
    val gridLayoutManager = SpannedLayoutManager()
    val adapter = WidgetFrameAdapter(widgetManager, widgetHost, params) { adapter, item ->
        (view.remove_widget_confirmation as RemoveWidgetConfirmationView).apply {
            onConfirmListener = {
                if (it) {
                    prefManager.currentWidgets = prefManager.currentWidgets.apply {
                        remove(item)
                        widgetHost.deleteAppWidgetId(item.id)
                    }
                    adapter.currentEditingInterfacePosition = -1
                }
            }
            show()
        }
    }
    val touchHelperCallback = object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT or ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        0
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            return adapter.onMove(viewHolder.adapterPosition, target.adapterPosition).also {
                if (it) {
                    updatedForMove = true
                    prefManager.currentWidgets = LinkedHashSet(adapter.widgets)
                    adapter.currentEditingInterfacePosition = -1
                    updatedForMove = false
                }
            }
        }

        override fun getDragDirs(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            return if (viewHolder is WidgetFrameAdapter.AddWidgetVH) 0
            else super.getDragDirs(recyclerView, viewHolder)
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
                viewHolder?.itemView?.alpha = 0.5f
                isHoldingItem = true

                //The user has long-pressed a widget. Show the editing UI on that widget.
                //If the UI is already shown on it, hide it.
                val adapterPos = viewHolder?.adapterPosition ?: -1
                adapter.currentEditingInterfacePosition = if (adapter.currentEditingInterfacePosition == adapterPos) -1 else adapterPos
            }

            super.onSelectedChanged(viewHolder, actionState)
        }

        override fun clearView(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ) {
            super.clearView(recyclerView, viewHolder)

            isHoldingItem = false
            viewHolder.itemView.alpha = 1.0f
        }

        override fun interpolateOutOfBoundsScroll(
            recyclerView: RecyclerView,
            viewSize: Int,
            viewSizeOutOfBounds: Int,
            totalSize: Int,
            msSinceStartScroll: Long
        ): Int {
            //The default scrolling speed is *way* too fast. Slow it down a bit.
            val direction = sign(viewSizeOutOfBounds.toFloat()).toInt()
            return (viewSize * 0.01f * direction).roundToInt()
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
    }
    //Some widgets display differently depending on the system's dark mode.
    //Make sure the widgets are rebound if there's a change.
    val nightModeListener = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            when (uri) {
                Settings.Secure.getUriFor(Settings.Secure.UI_NIGHT_MODE) -> {
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            PrefManager.KEY_CURRENT_WIDGETS -> {
                //Make sure the adapter knows of any changes to the widget list
                if (!updatedForMove) {
                    //Only run the update if it wasn't generated by a reorder event
                    adapter.updateWidgets(prefManager.currentWidgets.toList())
                }
            }
            PrefManager.KEY_PAGE_INDICATOR_BEHAVIOR -> {
                view.frame.updatePageIndicatorBehavior()
            }
            PrefManager.KEY_FRAME_ROW_COUNT,
            PrefManager.KEY_FRAME_COL_COUNT -> {
                updateRowCount()
                adapter.notifyDataSetChanged()
            }
            PrefManager.KEY_FRAME_BACKGROUND_COLOR -> {
                view.frame.updateFrameBackground()
            }
            PrefManager.KEY_FRAME_MASKED_MODE -> {
                updateWallpaperLayerIfNeeded()
            }
            PrefManager.KEY_SHOW_DEBUG_ID_VIEW,
                PrefManager.KEY_DEBUG_LOG -> {
                view.frame.updateDebugIdViewVisibility()
            }
            PrefManager.KEY_SHOW_IN_NOTIFICATION_CENTER -> {
                isPendingNotificationStateChange = true
            }
            PrefManager.KEY_FRAME_CORNER_RADIUS -> {
                view.frame.updateCornerRadius()
            }
        }
    }

    fun onCreate() {
        prefManager.prefs.registerOnSharedPreferenceChangeListener(this)
        gridLayoutManager.spanSizeLookup = adapter.spanSizeLookup
//        gridLayoutManager.customWidth = widgetBlockWidth
        view.widgets_pager.apply {
            adapter = this@WidgetFrameDelegate.adapter
            layoutManager = gridLayoutManager
            setHasFixedSize(true)
//            blockSnapHelper.attachToRecyclerView(this)
            ItemTouchHelper(touchHelperCallback).attachToRecyclerView(this)
        }
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.UI_NIGHT_MODE),
            true,
            nightModeListener
        )

        updateRowCount()
        adapter.updateWidgets(prefManager.currentWidgets.toList())

        view.frame.onAddListener = {
            val intent = Intent(this, AddWidgetActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            startActivity(intent)
        }

        //We only really want to be listening to widget changes
        //while the frame is on-screen. Otherwise, we're wasting battery.
        view.frame.attachmentStateListener = {
            try {
                if (it) {
                    widgetHost.startListening()
                    //Even with the startListening() call above,
                    //it doesn't seem like pending updates always get
                    //dispatched. Rebinding all the widgets forces
                    //them to update.
                    mainHandler.postDelayed({
                        updateWallpaperLayerIfNeeded()
                        adapter.notifyDataSetChanged()
                    }, 50)
                } else {
                    widgetHost.stopListening()
                }
            } catch (e: NullPointerException) {
                //The stupid "Attempt to read from field 'com.android.server.appwidget.AppWidgetServiceImpl$ProviderId
                //com.android.server.appwidget.AppWidgetServiceImpl$Provider.id' on a null object reference"
                //Exception is thrown on stopListening() as well for some reason.
            }
        }

//        gridLayoutManager.spanSizeLookup = adapter.spanSizeLookup

        //Scroll to the stored page, making sure to catch a potential
        //out-of-bounds error.
        try {
            gridLayoutManager.scrollToPosition(prefManager.currentPage)
        } catch (e: Exception) {}
    }

    fun onDestroy() {
        prefManager.prefs.unregisterOnSharedPreferenceChangeListener(this)
        contentResolver.unregisterContentObserver(nightModeListener)
    }

    /**
     * Make sure the number of rows in the widget frame reflects the user-selected value.
     */
    fun updateRowCount() {
        gridLayoutManager.apply {
            val rowCount = prefManager.frameRowCount
            val colCount = prefManager.frameColCount

            this.rowCount = rowCount
            this.columnCount = colCount

//            blockSnapHelper.maxFlingBlocks = rowCount
//            blockSnapHelper.attachToRecyclerView(view.widgets_pager)
        }
    }

    /**
     * Compute and draw the appropriate portion of the wallpaper as the widget background,
     * if masked mode is enabled.
     *
     * TODO: this doesn't work properly on a lot of devices. It seems to be something to do with the scale.
     * TODO: I don't know enough about [Matrix]es to fix it.
     *
     * TODO: There are also a lot of limitations related to wallpaper offsets. It doesn't seem to be
     * TODO: possible to retrieve those offsets unless you're the active wallpaper, so this method
     * TODO: just assumes that a wallpaper [Bitmap] that's larger than the screen is centered,
     * TODO: which isn't always true.
     *
     * TODO: You can only specifically retrieve the lock screen wallpaper on Nougat and up.
     */
    @SuppressLint("MissingPermission")
    fun updateWallpaperLayerIfNeeded() {
        if (prefManager.maskedMode && (!notificationsPanelFullyExpanded || !prefManager.showInNotificationCenter)) {
            val service = IWallpaperManager.Stub.asInterface(ServiceManager.getService("wallpaper"))

            val bundle = Bundle()
            val w = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
                //Even though this hidden method was added in Android Nougat,
                //some devices (SAMSUNG >_>) removed or changed it, so it won't
                //always work. Thus the try-catch.
                try {
                    service.getWallpaper(
                        packageName,
                        null,
                        WallpaperManager.FLAG_LOCK,
                        bundle,
                        UserHandle.getCallingUserId()
                    )
                } catch (e: Exception) {
                    null
                } catch (e: NoSuchMethodError) {
                    null
                }
            } else null

            try {
                val fastW = run {
                    if (w == null) null
                    else {
                        val fd = w.fileDescriptor
                        if (fd == null) null
                        else {
                            val bmp = BitmapFactory.decodeFileDescriptor(fd)
                            if (bmp == null) null
                            else BitmapDrawable(resources, bmp)
                        }
                    }
                } ?: wallpaper.fastDrawable

                fastW?.mutate()?.apply {
                    view.wallpaper_background.setImageDrawable(this)
                    view.wallpaper_background.scaleType = ImageView.ScaleType.MATRIX
                    view.wallpaper_background.imageMatrix = Matrix().apply {
                        val realSize = Point().apply { wm.defaultDisplay.getRealSize(this) }
                        val loc = view.locationOnScreen ?: intArrayOf(0, 0)

                        val dwidth: Int = intrinsicWidth
                        val dheight: Int = intrinsicHeight

                        val wallpaperAdjustmentX = (dwidth - realSize.x) / 2f
                        val wallpaperAdjustmentY = (dheight - realSize.y) / 2f

                        setTranslate(
                            (-loc[0].toFloat() - wallpaperAdjustmentX),
                            //TODO: a bunch of skins don't like this
                            (-loc[1].toFloat() - wallpaperAdjustmentY)
                        )
                    }
                } ?: view.wallpaper_background.setImageDrawable(null)
            } catch (e: Exception) {
                view.wallpaper_background.setImageDrawable(null)
            }
        } else {
            view.wallpaper_background.setImageDrawable(null)
        }
    }

    fun updateAccessibilityPass() {
        if (view.frame.animationState == WidgetFrameView.AnimationState.STATE_IDLE) {
            if (isPendingNotificationStateChange) {
                updateParamsForNotificationCenterStateChange()
                isPendingNotificationStateChange = false
            }
        }
    }

    private fun updateParamsForNotificationCenterStateChange() {
        params.x = prefManager.getCorrectFrameX(saveForNC)
        params.y = prefManager.getCorrectFrameY(saveForNC)
        params.width = dpAsPx(prefManager.getCorrectFrameWidth(saveForNC))
        params.height = dpAsPx(prefManager.getCorrectFrameHeight(saveForNC))

        view.frame.updateWindow(wm, params)
        mainHandler.post {
            updateWallpaperLayerIfNeeded()
            adapter.notifyDataSetChanged()
        }
    }

    //Parts based on https://stackoverflow.com/a/26445064/5496177
    inner class SpannedLayoutManager : SpannedGridLayoutManager(this@WidgetFrameDelegate, RecyclerView.HORIZONTAL, prefManager.frameRowCount, prefManager.frameColCount), ISnappyLayoutManager {
        override fun canScrollHorizontally(): Boolean {
            return (adapter.currentEditingInterfacePosition == -1 || isHoldingItem) && super.canScrollHorizontally()
        }

        override fun getFixScrollPos(velocityX: Int, velocityY: Int): Int {
            return getPositionForVelocity(velocityX, velocityY)
        }

        override fun getPositionForVelocity(velocityX: Int, velocityY: Int): Int {
            if (childCount == 0) return 0

            return if (velocityX > 0) {
                lastVisiblePosition
            } else {
                firstVisiblePosition
            }.also {
                prefManager.currentPage = it
            }
        }

        override fun canSnap(): Boolean {
            return adapter.currentEditingInterfacePosition == -1
        }
    }
}