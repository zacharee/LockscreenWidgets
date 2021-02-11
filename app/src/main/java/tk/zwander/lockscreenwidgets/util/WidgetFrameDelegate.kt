package tk.zwander.lockscreenwidgets.util

import android.annotation.SuppressLint
import android.app.IWallpaperManager
import android.app.KeyguardManager
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
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.AddWidgetActivity
import tk.zwander.lockscreenwidgets.activities.DismissOrUnlockActivity
import tk.zwander.lockscreenwidgets.adapters.WidgetFrameAdapter
import tk.zwander.lockscreenwidgets.data.Mode
import tk.zwander.lockscreenwidgets.databinding.WidgetFrameBinding
import tk.zwander.lockscreenwidgets.host.WidgetHostCompat
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

    //This is used to track when the notification shade has
    //been expanded/collapsed or when the "show in NC" setting
    //has been changed. Since the params are different for the
    //widget frame depending on whether it's showing in the NC
    //or not, we need to update them. We could do it directly,
    //but that causes weird shifting and resizing since it happens
    //before the Accessibility Service hides or shows the frame.
    //So instead, we set this flag to true when the params should be
    //updated. The Accessibility Service takes care of calling
    //the update method after it starts a frame removal or addition.
    //The method itself checks whether it can run (i.e. the
    //animation state of the frame is IDLE) and then updates
    //the params.
    private var isPendingNotificationStateChange = false

    var updatedForMove = false
    var isHoldingItem = false
    var notificationsPanelFullyExpanded = false
        set(value) {
            val changed = field != value
            field = value

            isPendingNotificationStateChange = changed
        }

    val saveMode: Mode
        get() = when {
            notificationsPanelFullyExpanded && prefManager.showInNotificationCenter -> {
                if (kgm.isKeyguardLocked && prefManager.separatePosForLockNC) {
                    Mode.LOCK_NOTIFICATION
                } else {
                    Mode.NOTIFICATION
                }
            }
            else -> Mode.LOCK_NORMAL
        }

    //The size, position, and such of the widget frame on the lock screen.
    val params = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        width = dpAsPx(prefManager.getCorrectFrameWidth(saveMode))
        height = dpAsPx(prefManager.getCorrectFrameHeight(saveMode))

        x = prefManager.getCorrectFrameX(saveMode)
        y = prefManager.getCorrectFrameY(saveMode)

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
    val widgetManager = AppWidgetManager.getInstance(this)!!
    val widgetHost = WidgetHostCompat.getInstance(this, 1003) {
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, DismissOrUnlockActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }, 100)
    }
    //The actual frame View
    val view = LayoutInflater.from(ContextThemeWrapper(this, R.style.AppTheme))
        .inflate(R.layout.widget_frame, null)!!
    val binding = WidgetFrameBinding.bind(view)
    val gridLayoutManager = SpannedLayoutManager()
    val adapter = WidgetFrameAdapter(widgetManager, widgetHost, params) { adapter, item ->
        binding.removeWidgetConfirmation.root.apply {
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
                    adapter.updateViews()
                }
            }
        }
    }

    private val kgm = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            PrefManager.KEY_CURRENT_WIDGETS -> {
                //Make sure the adapter knows of any changes to the widget list
                if (!updatedForMove) {
                    //Only run the update if it wasn't generated by a reorder event
                    adapter.updateWidgets(prefManager.currentWidgets.toList())
                    gridLayoutManager.scrollToPosition(prefManager.currentPage)
                }
            }
            PrefManager.KEY_PAGE_INDICATOR_BEHAVIOR -> {
                binding.frame.updatePageIndicatorBehavior()
            }
            PrefManager.KEY_FRAME_ROW_COUNT,
            PrefManager.KEY_FRAME_COL_COUNT -> {
                updateRowColCount()
                adapter.updateViews()
            }
            PrefManager.KEY_FRAME_BACKGROUND_COLOR -> {
                binding.frame.updateFrameBackground()
            }
            PrefManager.KEY_FRAME_MASKED_MODE -> {
                updateWallpaperLayerIfNeeded()
            }
            PrefManager.KEY_SHOW_DEBUG_ID_VIEW,
                PrefManager.KEY_DEBUG_LOG -> {
                binding.frame.updateDebugIdViewVisibility()
            }
            PrefManager.KEY_SHOW_IN_NOTIFICATION_CENTER -> {
                isPendingNotificationStateChange = true
            }
            PrefManager.KEY_FRAME_CORNER_RADIUS -> {
                binding.frame.updateCornerRadius()
            }
        }
    }

    fun onCreate() {
        prefManager.prefs.registerOnSharedPreferenceChangeListener(this)
        gridLayoutManager.spanSizeLookup = adapter.spanSizeLookup
//        gridLayoutManager.customWidth = widgetBlockWidth
        binding.widgetsPager.apply {
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

        updateRowColCount()
        adapter.updateWidgets(prefManager.currentWidgets.toList())

        binding.frame.onAddListener = {
            val intent = Intent(this, AddWidgetActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            startActivity(intent)
        }

        //We only really want to be listening to widget changes
        //while the frame is on-screen. Otherwise, we're wasting battery.
        binding.frame.attachmentStateListener = {
            try {
                if (it) {
                    widgetHost.startListening()
                    //Even with the startListening() call above,
                    //it doesn't seem like pending updates always get
                    //dispatched. Rebinding all the widgets forces
                    //them to update.
                    mainHandler.postDelayed({
                        updateWallpaperLayerIfNeeded()
                        adapter.updateViews()
                        gridLayoutManager.scrollToPosition(prefManager.currentPage)
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
     * Make sure the number of rows/columns in the widget frame reflects the user-selected value.
     */
    fun updateRowColCount() {
        gridLayoutManager.apply {
            val rowCount = prefManager.frameRowCount
            val colCount = prefManager.frameColCount

            this.rowCount = rowCount
            this.columnCount = colCount

//            blockSnapHelper.maxFlingBlocks = rowCount
//            blockSnapHelper.attachToRecyclerView(view.widgets_pager)
        }
    }

    fun addWindow(wm: WindowManager) {
        if (!binding.frame.isAttachedToWindow) {
            updateParamsForNotificationCenterStateChange()
        }
        binding.frame.addWindow(wm, params)
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
                    binding.wallpaperBackground.setImageDrawable(this)
                    binding.wallpaperBackground.scaleType = ImageView.ScaleType.MATRIX
                    binding.wallpaperBackground.imageMatrix = Matrix().apply {
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
                } ?: binding.wallpaperBackground.setImageDrawable(null)
            } catch (e: Exception) {
                binding.wallpaperBackground.setImageDrawable(null)
            }
        } else {
            binding.wallpaperBackground.setImageDrawable(null)
        }
    }

    /**
     * This is called by the Accessibility Service on an event,
     * after all other logic has finished (e.g. frame removal/addition
     * calls). Use this method for updating things that are conditional
     * upon the frame's state after an event.
     */
    fun updateAccessibilityPass() {
        if (binding.frame.animationState == WidgetFrameView.AnimationState.STATE_IDLE) {
            if (isPendingNotificationStateChange) {
                updateParamsForNotificationCenterStateChange()
                isPendingNotificationStateChange = false
            }
        }
    }

    /**
     * Update the frame's params for its current state (normal
     * or in expanded notification center).
     */
    private fun updateParamsForNotificationCenterStateChange() {
        val newX = prefManager.getCorrectFrameX(saveMode)
        val newY = prefManager.getCorrectFrameY(saveMode)
        val newW = dpAsPx(prefManager.getCorrectFrameWidth(saveMode))
        val newH = dpAsPx(prefManager.getCorrectFrameHeight(saveMode))

        var changed = false

        if (params.x != newX) {
            changed = true
            params.x = newX
        }

        if (params.y != newY) {
            changed = true
            params.y = newY
        }

        if (params.width != newW) {
            changed = true
            params.width = newW
        }

        if (params.height != newH) {
            changed = true
            params.height = newH
        }

        if (changed) {
            binding.frame.updateWindow(wm, params)
            mainHandler.post {
                updateWallpaperLayerIfNeeded()
                adapter.updateViews()
                gridLayoutManager.scrollToPosition(prefManager.currentPage)
            }
        }
    }

    //Parts based on https://stackoverflow.com/a/26445064/5496177
    inner class SpannedLayoutManager : SpannedGridLayoutManager(this@WidgetFrameDelegate, RecyclerView.HORIZONTAL, prefManager.frameRowCount, prefManager.frameColCount), ISnappyLayoutManager {
        override fun canScrollHorizontally(): Boolean {
            return (adapter.currentEditingInterfacePosition == -1 || isHoldingItem) && super.canScrollHorizontally()
        }

        override fun getFixScrollPos(velocityX: Int, velocityY: Int): Int {
            if (childCount == 0) return 0

            return if (velocityX > 0) {
                firstVisiblePosition
            } else {
                lastVisiblePosition
            }.also {
                prefManager.currentPage = it
            }.run { if (this == -1) 0 else this }
        }

        override fun getPositionForVelocity(velocityX: Int, velocityY: Int): Int {
            if (childCount == 0) return 0

            return if (velocityX > 0) {
                lastVisiblePosition
            } else {
                firstVisiblePosition
            }.also {
                prefManager.currentPage = it
            }.run { if (this == -1) 0 else this }
        }

        override fun canSnap(): Boolean {
            return adapter.currentEditingInterfacePosition == -1
        }
    }
}