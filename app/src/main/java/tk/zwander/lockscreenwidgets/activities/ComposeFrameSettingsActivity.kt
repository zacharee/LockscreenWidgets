package tk.zwander.lockscreenwidgets.activities

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import tk.zwander.common.activities.BaseActivity
import tk.zwander.common.activities.HideForIDsActivity
import tk.zwander.common.activities.HideOnAppsChooserActivity
import tk.zwander.common.activities.OnboardingActivity
import tk.zwander.common.compose.settings.ListPreferenceEntry
import tk.zwander.common.compose.settings.PreferenceScreen
import tk.zwander.common.compose.settings.booleanPreferenceDependency
import tk.zwander.common.compose.settings.createCommonSection
import tk.zwander.common.compose.settings.rememberBooleanPreferenceDependency
import tk.zwander.common.compose.settings.rememberPreferenceScreen
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.backup.BackupRestoreManager
import tk.zwander.common.util.canReadWallpaper
import tk.zwander.common.util.isOneUI
import tk.zwander.common.util.isPixelUI
import tk.zwander.common.util.isTouchWiz
import tk.zwander.common.util.lsDisplayManager
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.requireLsDisplayManager
import tk.zwander.common.util.setThemedContent
import tk.zwander.lockscreenwidgets.BuildConfig
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.compose.SelectDisplayDialog
import tk.zwander.lockscreenwidgets.services.isNotificationListenerActive
import tk.zwander.lockscreenwidgets.util.FramePrefs
import tk.zwander.lockscreenwidgets.util.FrameSpecificPreferences

class ComposeFrameSettingsActivity : BaseActivity() {
    private val Context.shouldShowBlurOptions: Boolean
        get() {
            return (isOneUI && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && windowManager.isCrossWindowBlurEnabled)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @SuppressLint("ObsoleteSdkInt")
        val canShowNCOptions =
            isOneUI || (isPixelUI && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

        setThemedContent {
            var secondaryFrames by rememberPreferenceState(
                key = PrefManager.KEY_CURRENT_FRAMES_WITH_STRING_DISPLAY,
                value = { prefManager.currentSecondaryFramesWithStringDisplay },
                onChanged = { _, v -> prefManager.currentSecondaryFramesWithStringDisplay = v },
            )
            val frameCount by rememberUpdatedState(secondaryFrames.size + 1)
            val displayManager = remember {
                requireLsDisplayManager
            }
            val displays by displayManager.availableDisplays.collectAsState()

            var pendingFrameId by remember {
                mutableStateOf<Int?>(null)
            }

            var selectedFrame by remember {
                mutableIntStateOf(-1)
            }

            var isSelectingFrame by remember {
                mutableStateOf(false)
            }

            var isRemovingFrame by remember {
                mutableStateOf(false)
            }

            var pendingFrameToRemove by remember {
                mutableStateOf<Int?>(null)
            }

            var isMovingFrameToDisplay by remember {
                mutableStateOf(false)
            }

            var pendingMovedFrameId by remember {
                mutableStateOf<Int?>(null)
            }

            val commonSection = createCommonSection(BackupRestoreManager.Which.FRAME)
            val preferenceScreen = rememberPreferenceScreen {
                commonSection.addToPreferenceScreen(this)

                category(
                    key = "frame_management_category",
                    title = resources.getString(R.string.frame_management),
                ) {
                    preference(
                        title = { stringResource(R.string.select_frame) },
                        summary = { stringResource(R.string.selected_frame, "$selectedFrame") },
                        key = { "select_secondary_frame" },
                        onClick = {
                            isSelectingFrame = true
                        },
                        icon = { painterResource(R.drawable.wall) },
                        defaultValue = {},
                        visible = { frameCount > 1 },
                    )

                    preference(
                        title = { stringResource(R.string.add_frame) },
                        summary = {
                            pluralStringResource(
                                R.plurals.frame_count_info,
                                frameCount,
                                "$frameCount",
                            )
                        },
                        key = { "add_secondary_frame" },
                        onClick = {
                            val maxFrameId = secondaryFrames.keys.maxOrNull() ?: 1
                            val newFrameId = maxFrameId + 1

                            if (!BuildConfig.DEBUG && lsDisplayManager?.availableDisplays?.value?.let { it.size <= 1 } != false) {
                                secondaryFrames = HashMap(
                                    secondaryFrames.toMutableMap().apply {
                                        this[newFrameId] = "${Display.DEFAULT_DISPLAY}"
                                    },
                                )
                            } else {
                                pendingFrameId = newFrameId
                            }
                        },
                        icon = { painterResource(R.drawable.ic_baseline_add_24) },
                        defaultValue = {},
                    )

                    preference(
                        title = { stringResource(R.string.move_frame_to_display) },
                        summary = { null },
                        key = { "move_frame_to_display" },
                        onClick = {
                            isMovingFrameToDisplay = true
                        },
                        icon = { painterResource(R.drawable.mobile_arrow_right_24px) },
                        defaultValue = {},
                        visible = { displays.size > 1 || BuildConfig.DEBUG },
                    )

                    preference(
                        title = { stringResource(R.string.remove_frame) },
                        summary = { null },
                        key = { "remove_secondary_frame" },
                        onClick = {
                            isRemovingFrame = true
                        },
                        icon = { painterResource(R.drawable.ic_baseline_remove_circle_24) },
                        defaultValue = {},
                        visible = { frameCount > 1 },
                    )
                }

                category(
                    title = resources.getString(R.string.category_appearance),
                    key = "settings_appearance_category",
                ) {
                    colorPickerPreference(
                        title = { stringResource(R.string.settings_screen_background_color) },
                        summary = { stringResource(R.string.settings_screen_background_color_desc) },
                        icon = { painterResource(R.drawable.ic_baseline_color_lens_24) },
                        key = {
                            FrameSpecificPreferences.keyFor(
                                selectedFrame,
                                PrefManager.KEY_FRAME_BACKGROUND_COLOR
                            )
                        },
                        defaultValue = { 0 },
                    )

                    switchPreference(
                        title = { stringResource(R.string.settings_screen_blur_background) },
                        summary = { stringResource(R.string.settings_screen_blur_background_desc) },
                        icon = { painterResource(R.drawable.ic_baseline_blur_on_24) },
                        key = {
                            FrameSpecificPreferences.keyFor(
                                selectedFrame,
                                PrefManager.KEY_BLUR_BACKGROUND
                            )
                        },
                        defaultValue = { false },
                        visible = { shouldShowBlurOptions },
                    )

                    seekBarPreference(
                        title = { stringResource(R.string.settings_screen_blur_background_amount) },
                        summary = { stringResource(R.string.settings_screen_blur_background_amount_desc) },
                        icon = { painterResource(R.drawable.ic_baseline_deblur_24) },
                        key = {
                            FrameSpecificPreferences.keyFor(
                                selectedFrame,
                                PrefManager.KEY_BLUR_BACKGROUND_AMOUNT
                            )
                        },
                        defaultValue = { 100 },
                        minValue = { 1 },
                        maxValue = { 1000 },
                        enabled = {
                            rememberBooleanPreferenceDependency(
                                FrameSpecificPreferences.keyFor(
                                    selectedFrame,
                                    PrefManager.KEY_BLUR_BACKGROUND
                                )
                            )
                        },
                        scale = { 1.0 },
                        visible = { shouldShowBlurOptions },
                    )

                    switchPreference(
                        title = { stringResource(R.string.settings_screen_masked_mode) },
                        summary = { stringResource(R.string.settings_screen_masked_mode_desc) },
                        icon = { painterResource(R.drawable.ic_baseline_opacity_24) },
                        key = {
                            FrameSpecificPreferences.keyFor(
                                selectedFrame,
                                PrefManager.KEY_FRAME_MASKED_MODE
                            )
                        },
                        canChange = { newValue ->
                            if (newValue && !canReadWallpaper) {
                                OnboardingActivity.start(
                                    this@ComposeFrameSettingsActivity,
                                    OnboardingActivity.RetroMode.STORAGE
                                )
                                false
                            } else true
                        },
                    )

                    seekBarPreference(
                        title = { stringResource(R.string.settings_screen_masked_mode_dim_amount) },
                        summary = { stringResource(R.string.settings_screen_masked_mode_dim_amount_desc) },
                        icon = { painterResource(R.drawable.ic_baseline_brightness_medium_24) },
                        key = {
                            FrameSpecificPreferences.keyFor(
                                selectedFrame,
                                PrefManager.KEY_MASKED_MODE_DIM_AMOUNT
                            )
                        },
                        defaultValue = { 0 },
                        enabled = {
                            rememberBooleanPreferenceDependency(
                                FrameSpecificPreferences.keyFor(
                                    selectedFrame,
                                    PrefManager.KEY_FRAME_MASKED_MODE
                                )
                            )
                        },
                        minValue = { 0 },
                        maxValue = { 10000 },
                        scale = { 0.01 },
                        unit = { "%" },
                    )

                    seekBarPreference(
                        title = { stringResource(R.string.settings_screen_corner_radius) },
                        summary = { stringResource(R.string.settings_screen_corner_radius_desc) },
                        key = { PrefManager.KEY_FRAME_CORNER_RADIUS },
                        icon = { painterResource(R.drawable.ic_baseline_rounded_corner_24) },
                        defaultValue = { 20 },
                        scale = { 0.1 },
                        minValue = { 0 },
                        maxValue = { 640 },
                        unit = { "dp" },
                    )

                    seekBarPreference(
                        title = { stringResource(R.string.settings_screen_widget_corner_radius) },
                        summary = { stringResource(R.string.settings_screen_widget_corner_radius_desc) },
                        key = { PrefManager.KEY_FRAME_WIDGET_CORNER_RADIUS },
                        icon = { painterResource(R.drawable.ic_baseline_rounded_corner_24) },
                        defaultValue = { 20 },
                        scale = { 0.1 },
                        minValue = { 0 },
                        maxValue = { 640 },
                        unit = { "dp" },
                    )
                }

                category(
                    key = "frame_grid_settings",
                    title = resources.getString(R.string.settings_screen_category_grid),
                ) {
                    seekBarPreference(
                        title = { stringResource(R.string.settings_screen_frame_col_count) },
                        summary = { null },
                        key = {
                            FramePrefs.generatePrefKey(
                                FramePrefs.KEY_FRAME_COL_COUNT,
                                selectedFrame,
                            )
                        },
                        defaultValue = { 1 },
                        minValue = { 1 },
                        maxValue = { 20 },
                        scale = { 1.0 },
                        icon = { painterResource(R.drawable.ic_baseline_view_column_24) },
                    )

                    seekBarPreference(
                        title = { stringResource(R.string.settings_screen_frame_row_count) },
                        summary = { null },
                        key = {
                            FramePrefs.generatePrefKey(
                                FramePrefs.KEY_FRAME_ROW_COUNT,
                                selectedFrame,
                            )
                        },
                        defaultValue = { 1 },
                        minValue = { 1 },
                        maxValue = { 20 },
                        scale = { 1.0 },
                        icon = { painterResource(R.drawable.ic_baseline_view_row_24) },
                    )
                }

                category(
                    title = resources.getString(R.string.category_layout),
                    key = "settings_layout_category",
                ) {
                    switchPreference(
                        title = { stringResource(R.string.settings_screen_lock_widget_frame) },
                        summary = { stringResource(R.string.settings_screen_lock_widget_frame_desc) },
                        key = { PrefManager.KEY_LOCK_WIDGET_FRAME },
                        icon = { painterResource(R.drawable.ic_baseline_lock_24) },
                    )

                    switchPreference(
                        title = { stringResource(R.string.settings_screen_separate_position_for_lock_nc) },
                        summary = { stringResource(R.string.settings_screen_separate_position_for_lock_nc_desc) },
                        key = {
                            FrameSpecificPreferences.keyFor(
                                selectedFrame,
                                PrefManager.KEY_SEPARATE_POS_FOR_LOCK_NC
                            )
                        },
                        icon = { painterResource(R.drawable.ic_baseline_layers_24) },
                        enabled = {
                            rememberBooleanPreferenceDependency(
                                FrameSpecificPreferences.keyFor(
                                    selectedFrame,
                                    PrefManager.KEY_SHOW_IN_NOTIFICATION_CENTER
                                )
                            )
                        },
                        visible = { canShowNCOptions },
                    )

                    switchPreference(
                        title = { stringResource(R.string.settings_screen_separate_layout) },
                        summary = { stringResource(R.string.settings_screen_separate_layout_desc) },
                        key = { PrefManager.KEY_SEPARATE_LAYOUT_FOR_LANDSCAPE },
                        icon = { painterResource(R.drawable.baseline_screen_rotation_24) },
                    )
                }

                category(
                    title = resources.getString(R.string.category_visibility),
                    key = "settings_visibility_category",
                ) {
                    switchPreference(
                        title = { stringResource(R.string.settings_screen_hide_when_notifications_shown) },
                        summary = { stringResource(R.string.settings_screen_hide_when_notifications_shown_desc) },
                        key = {
                            FrameSpecificPreferences.keyFor(
                                selectedFrame,
                                PrefManager.KEY_HIDE_ON_NOTIFICATIONS
                            )
                        },
                        icon = { painterResource(R.drawable.ic_baseline_notifications_off_24) },
                        canChange = { newValue ->
                            if (newValue && !isNotificationListenerActive) {
                                OnboardingActivity.start(
                                    this@ComposeFrameSettingsActivity,
                                    OnboardingActivity.RetroMode.NOTIFICATION
                                )
                                false
                            } else true
                        },
                    )

                    switchPreference(
                        title = { stringResource(R.string.settings_screen_hide_on_notification_shade) },
                        summary = { stringResource(R.string.settings_screen_hide_on_notification_shade_desc) },
                        key = {
                            FrameSpecificPreferences.keyFor(
                                selectedFrame,
                                PrefManager.KEY_HIDE_ON_NOTIFICATION_SHADE
                            )
                        },
                        icon = { painterResource(R.drawable.ic_baseline_clear_all_24) },
                    )

                    switchPreference(
                        title = { stringResource(R.string.settings_screen_hide_on_security_input) },
                        summary = { stringResource(R.string.settings_screen_hide_on_security_input_desc) },
                        key = {
                            FrameSpecificPreferences.keyFor(
                                selectedFrame,
                                PrefManager.KEY_HIDE_ON_SECURITY_PAGE
                            )
                        },
                        icon = { painterResource(R.drawable.is_baseline_password_24) },
                        defaultValue = { true },
                    )

                    switchPreference(
                        title = { stringResource(R.string.settings_screen_hide_on_facewidgets) },
                        summary = { stringResource(R.string.settings_screen_hide_on_facewidgets_desc) },
                        key = {
                            FrameSpecificPreferences.keyFor(
                                selectedFrame,
                                PrefManager.KEY_HIDE_ON_FACEWIDGETS
                            )
                        },
                        icon = { painterResource(R.drawable.ic_baseline_widgets_24) },
                        visible = {
                            isOneUI && Build.VERSION.SDK_INT > Build.VERSION_CODES.Q
                        },
                    )

                    switchPreference(
                        title = { stringResource(R.string.settings_screen_hide_in_landscape) },
                        summary = { stringResource(R.string.settings_screen_hide_in_landscape_desc) },
                        key = { PrefManager.KEY_HIDE_IN_LANDSCAPE },
                        icon = { painterResource(R.drawable.ic_baseline_crop_landscape_24) },
                    )

                    switchPreference(
                        title = { stringResource(R.string.settings_screen_hide_when_keyboard_shown) },
                        summary = { stringResource(R.string.settings_screen_hide_when_keyboard_shown_desc) },
                        key = {
                            FrameSpecificPreferences.keyFor(
                                selectedFrame,
                                PrefManager.KEY_FRAME_HIDE_WHEN_KEYBOARD_SHOWN
                            )
                        },
                        icon = { painterResource(R.drawable.baseline_keyboard_24) },
                    )

                    switchPreference(
                        title = { stringResource(R.string.settings_screen_hide_on_edge_panels) },
                        summary = { stringResource(R.string.settings_screen_hide_on_edge_panels_desc) },
                        key = {
                            FrameSpecificPreferences.keyFor(
                                selectedFrame,
                                PrefManager.KEY_HIDE_ON_EDGE_PANEL
                            )
                        },
                        icon = { painterResource(R.drawable.border_right) },
                        defaultValue = { true },
                        visible = { isTouchWiz },
                    )

                    preference(
                        title = { stringResource(R.string.settings_screen_present_ids) },
                        summary = { stringResource(R.string.settings_screen_present_ids_desc) },
                        key = { "present_ids_launch" },
                        icon = { painterResource(R.drawable.ic_baseline_visibility_off_24) },
                        defaultValue = {},
                        onClick = {
                            HideForIDsActivity.start(
                                this@ComposeFrameSettingsActivity,
                                HideForIDsActivity.Type.PRESENT
                            )
                        },
                    )

                    preference(
                        title = { stringResource(R.string.settings_screen_non_present_ids) },
                        summary = { stringResource(R.string.settings_screen_non_present_ids_desc) },
                        key = { "non_present_ids_launch" },
                        icon = { painterResource(R.drawable.ic_baseline_visibility_off_24) },
                        defaultValue = {},
                        onClick = {
                            HideForIDsActivity.start(
                                this@ComposeFrameSettingsActivity,
                                HideForIDsActivity.Type.NON_PRESENT
                            )
                        },
                    )

                    preference(
                        title = { stringResource(R.string.settings_screen_hide_on_present_apps) },
                        summary = { stringResource(R.string.settings_screen_hide_on_present_apps_desc) },
                        key = { "hide_on_present_apps" },
                        icon = { painterResource(R.drawable.ic_baseline_visibility_off_24) },
                        defaultValue = {},
                        onClick = {
                            HideOnAppsChooserActivity.start(this@ComposeFrameSettingsActivity)
                        },
                    )
                }

                category(
                    title = resources.getString(R.string.category_behavior),
                    key = "settings_behavior_category",
                ) {
                    switchPreference(
                        title = { stringResource(R.string.settings_screen_show_in_notification_center) },
                        summary = { stringResource(R.string.settings_screen_show_in_notification_center_desc) },
                        key = {
                            FrameSpecificPreferences.keyFor(
                                selectedFrame,
                                PrefManager.KEY_SHOW_IN_NOTIFICATION_CENTER
                            )
                        },
                        icon = { painterResource(R.drawable.ic_baseline_notifications_active_24) },
                        defaultValue = { false },
                        visible = { canShowNCOptions },
                    )

                    switchPreference(
                        title = { stringResource(R.string.settings_screen_show_on_main_lock_screen) },
                        summary = { stringResource(R.string.settings_screen_show_on_main_lock_screen_desc) },
                        key = {
                            FrameSpecificPreferences.keyFor(
                                selectedFrame,
                                PrefManager.KEY_SHOW_ON_MAIN_LOCK_SCREEN
                            )
                        },
                        icon = { painterResource(R.drawable.ic_baseline_lock_24) },
                        defaultValue = { true },
                        enabled = {
                            rememberBooleanPreferenceDependency(
                                FrameSpecificPreferences.keyFor(
                                    selectedFrame,
                                    PrefManager.KEY_SHOW_IN_NOTIFICATION_CENTER
                                )
                            )
                        },
                        visible = { canShowNCOptions },
                    )

                    switchPreference(
                        title = { stringResource(R.string.settings_screen_remember_position) },
                        summary = { stringResource(R.string.settings_screen_remember_position_desc) },
                        key = { PrefManager.KEY_FRAME_REMEMBER_POSITION },
                        icon = { painterResource(R.drawable.swap) },
                        defaultValue = { true },
                    )

                    switchPreference(
                        title = { stringResource(R.string.settings_screen_force_widget_update) },
                        summary = { stringResource(R.string.settings_screen_force_widget_update_desc) },
                        key = { PrefManager.KEY_FRAME_FORCE_RELOAD_WIDGETS },
                        icon = { painterResource(R.drawable.baseline_refresh_24) },
                        defaultValue = { true },
                    )

                    switchPreference(
                        title = { stringResource(R.string.settings_screen_animate_show_hide) },
                        summary = { stringResource(R.string.settings_screen_animate_show_hide_desc) },
                        key = { PrefManager.KEY_ANIMATE_SHOW_HIDE },
                        icon = { painterResource(R.drawable.ic_baseline_animation_24) },
                        defaultValue = { true },
                    )

                    seekBarPreference(
                        title = { stringResource(R.string.settings_screen_animation_duration) },
                        summary = { stringResource(R.string.settings_screen_animation_duration_desc) },
                        key = { PrefManager.KEY_ANIMATION_DURATION },
                        icon = { painterResource(R.drawable.ic_baseline_timer_24) },
                        defaultValue = { 300 },
                        enabled = booleanPreferenceDependency(PrefManager.KEY_ANIMATE_SHOW_HIDE),
                        minValue = { 0 },
                        maxValue = { 2000 },
                        unit = { "ms" },
                        scale = { 1.0 },
                    )

                    listPreference(
                        title = { stringResource(R.string.settings_screen_page_indicator_behavior) },
                        key = { PrefManager.KEY_PAGE_INDICATOR_BEHAVIOR },
                        icon = { painterResource(R.drawable.ic_baseline_visibility_24) },
                        defaultValue = { "1" },
                        entries = {
                            val labels = stringArrayResource(R.array.page_indicator_behavior_names)
                            val values = stringArrayResource(R.array.page_indicator_behavior_values)

                            labels.zip(values) { label, value ->
                                ListPreferenceEntry(label, value)
                            }
                        },
                        summary = { null },
                    )

                    seekBarPreference(
                        title = { stringResource(R.string.settings_screen_accessibility_event_delay) },
                        summary = { stringResource(R.string.settings_screen_accessibility_event_delay_desc) },
                        icon = { painterResource(R.drawable.ic_baseline_timer_24) },
                        key = { PrefManager.KEY_ACCESSIBILITY_EVENT_DELAY },
                        defaultValue = { 50 },
                        minValue = { 0 },
                        maxValue = { 5000 },
                        unit = { "ms" },
                        scale = { 1.0 },
                    )
                }

                category(
                    title = resources.getString(R.string.category_interaction),
                    key = "settings_interaction_category",
                ) {
                    switchPreference(
                        title = { stringResource(R.string.settings_screen_touch_protection) },
                        summary = { stringResource(R.string.settings_screen_touch_protection_desc) },
                        icon = { painterResource(R.drawable.ic_baseline_block_24) },
                        key = { PrefManager.KEY_TOUCH_PROTECTION },
                        defaultValue = { false },
                    )

                    switchPreference(
                        title = { stringResource(R.string.settings_screen_ignore_widget_touches) },
                        summary = { stringResource(R.string.settings_screen_ignore_widget_touches_desc) },
                        key = {
                            FrameSpecificPreferences.keyFor(
                                selectedFrame,
                                PrefManager.KEY_FRAME_IGNORE_WIDGET_TOUCHES
                            )
                        },
                        icon = { painterResource(R.drawable.baseline_do_not_touch_24) },
                        defaultValue = { false },
                    )

                    switchPreference(
                        title = { stringResource(R.string.settings_screen_request_unlock) },
                        summary = { stringResource(R.string.settings_screen_request_unlock_desc) },
                        key = { PrefManager.KEY_REQUEST_UNLOCK },
                        icon = { painterResource(R.drawable.ic_baseline_launch_24) },
                        defaultValue = { true },
                    )

                    switchPreference(
                        title = { stringResource(R.string.directly_check_for_activity) },
                        summary = { stringResource(R.string.directly_check_for_activity_desc) },
                        key = { PrefManager.KEY_FRAME_DIRECTLY_CHECK_FOR_ACTIVITY },
                        enabled = booleanPreferenceDependency(PrefManager.KEY_REQUEST_UNLOCK),
                        icon = { painterResource(R.drawable.baseline_compare_24) },
                        defaultValue = { true },
                    )
                }
            }

            PreferenceScreen(
                title = resources.getString(R.string.settings),
                categories = preferenceScreen,
            )

            pendingFrameId?.let { frameId ->
                SelectDisplayDialog(
                    dismiss = {
                        pendingFrameId = null
                    },
                    onDisplaySelected = { displayId ->
                        secondaryFrames = HashMap(
                            secondaryFrames.toMutableMap().apply {
                                this[frameId] = displayId
                            },
                        )
                        pendingFrameId = null
                    },
                )
            }

            if (isSelectingFrame) {
                SelectDisplayDialog(
                    dismiss = {
                        isSelectingFrame = false
                    },
                    onFrameSelected = {
                        selectedFrame = it
                        isSelectingFrame = false
                    },
                )
            }

            if (isRemovingFrame) {
                SelectDisplayDialog(
                    dismiss = {
                        isRemovingFrame = false
                    },
                    onFrameSelected = {
                        pendingFrameToRemove = it
                        isRemovingFrame = false
                    },
                    showDefaultFrame = false,
                )
            }

            if (isMovingFrameToDisplay) {
                SelectDisplayDialog(
                    dismiss = {
                        isMovingFrameToDisplay = false
                    },
                    onFrameSelected = {
                        pendingMovedFrameId = it
                        isMovingFrameToDisplay = false
                    },
                    showDefaultFrame = false,
                )
            }

            pendingMovedFrameId?.let { pendingMoved ->
                SelectDisplayDialog(
                    dismiss = {
                        pendingMovedFrameId = null
                    },
                    onDisplaySelected = {
                        prefManager.currentSecondaryFramesWithStringDisplay =
                            prefManager.currentSecondaryFramesWithStringDisplay.apply {
                                this[pendingMoved] = it
                            }
                        pendingMovedFrameId = null
                    },
                    showDefaultFrame = false,
                )
            }

            pendingFrameToRemove?.let { pending ->
                AlertDialog(
                    onDismissRequest = {
                        pendingFrameToRemove = null
                    },
                    title = {
                        Text(text = stringResource(R.string.remove_frame))
                    },
                    text = {
                        Text(text = stringResource(R.string.remove_frame_confirmation_message))
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                prefManager.currentSecondaryFramesWithStringDisplay =
                                    prefManager.currentSecondaryFramesWithStringDisplay.apply {
                                        this.remove(pending)
                                        if (selectedFrame == pending) {
                                            selectedFrame = -1
                                        }
                                    }
                                pendingFrameToRemove = null
                            }
                        ) {
                            Text(text = stringResource(R.string.yes))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                pendingFrameToRemove = null
                            }
                        ) {
                            Text(text = stringResource(R.string.no))
                        }
                    },
                )
            }
        }
    }
}
