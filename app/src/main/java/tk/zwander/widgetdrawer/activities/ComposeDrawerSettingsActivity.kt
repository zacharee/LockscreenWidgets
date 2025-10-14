package tk.zwander.widgetdrawer.activities

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.res.ResourcesCompat
import tk.zwander.common.activities.BaseActivity
import tk.zwander.common.compose.AppTheme
import tk.zwander.common.compose.settings.PreferenceScreen
import tk.zwander.common.compose.settings.booleanPreferenceDependency
import tk.zwander.common.compose.settings.createCommonSection
import tk.zwander.common.compose.settings.rememberPreferenceScreen
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.backup.BackupRestoreManager
import tk.zwander.common.util.isOneUI
import tk.zwander.lockscreenwidgets.R

class ComposeDrawerSettingsActivity : BaseActivity() {
    private val Context.shouldShowBlurOptions: Boolean
        get() {
            return (isOneUI && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && windowManager.isCrossWindowBlurEnabled)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val commonSection = createCommonSection(BackupRestoreManager.Which.DRAWER)
            val prefs = rememberPreferenceScreen {
                commonSection.addToPreferenceScreen(this)

                category(
                    title = resources.getString(R.string.drawer),
                    key = "drawer_options",
                ) {
                    switchPreference(
                        title = { stringResource(R.string.close_on_empty_tap) },
                        summary = { stringResource(R.string.close_on_empty_tap_desc) },
                        key = { PrefManager.KEY_CLOSE_DRAWER_ON_EMPTY_TAP },
                        defaultValue = { false },
                        icon = { painterResource(R.drawable.tap) },
                    )

                    switchPreference(
                        title = { stringResource(R.string.settings_screen_lock_widget_drawer) },
                        summary = { stringResource(R.string.settings_screen_lock_widget_drawer_desc) },
                        key = { PrefManager.KEY_LOCK_WIDGET_DRAWER },
                        defaultValue = { false },
                        icon = {
                            painterResource(R.drawable.ic_baseline_lock_24)
                        },
                    )

                    switchPreference(
                        title = { stringResource(R.string.settings_screen_request_unlock) },
                        summary = { stringResource(R.string.settings_screen_request_unlock_desc) },
                        key = { PrefManager.KEY_REQUEST_UNLOCK_DRAWER },
                        defaultValue = { false },
                        icon = {
                            painterResource(R.drawable.ic_baseline_launch_24)
                        },
                    )

                    switchPreference(
                        title = { stringResource(R.string.directly_check_for_activity) },
                        summary = { stringResource(R.string.directly_check_for_activity_desc) },
                        key = { PrefManager.KEY_DRAWER_DIRECTLY_CHECK_FOR_ACTIVITY },
                        defaultValue = { true },
                        icon = {
                            painterResource(R.drawable.baseline_compare_24)
                        },
                        enabled = booleanPreferenceDependency(PrefManager.KEY_REQUEST_UNLOCK_DRAWER),
                    )

                    switchPreference(
                        title = { stringResource(R.string.settings_screen_force_widget_update) },
                        summary = { stringResource(R.string.settings_screen_force_widget_update_desc) },
                        key = { PrefManager.KEY_DRAWER_FORCE_RELOAD_WIDGETS },
                        defaultValue = { false },
                        icon = {
                            painterResource(R.drawable.baseline_refresh_24)
                        },
                    )

                    switchPreference(
                        title = { stringResource(R.string.drawer_hide_when_notification_panel_expanded) },
                        summary = { stringResource(R.string.drawer_hide_when_notification_panel_expanded_desc) },
                        key = { PrefManager.KEY_DRAWER_HIDE_WHEN_NOTIFICATION_PANEL_OPEN },
                        defaultValue = { false },
                        icon = {
                            painterResource(R.drawable.ic_baseline_visibility_off_24)
                        },
                    )

                    switchPreference(
                        title = { stringResource(R.string.settings_screen_blur_background) },
                        summary = { stringResource(R.string.settings_screen_blur_drawer_background_desc) },
                        key = { PrefManager.KEY_BLUR_DRAWER_BACKGROUND },
                        defaultValue = { false },
                        icon = {
                            painterResource(R.drawable.ic_baseline_blur_on_24)
                        },
                        visible = { shouldShowBlurOptions },
                    )

                    seekBarPreference(
                        title = { stringResource(R.string.settings_screen_blur_background_amount) },
                        summary = { stringResource(R.string.settings_screen_blur_background_amount_desc) },
                        icon = {
                            painterResource(R.drawable.ic_baseline_deblur_24)
                        },
                        defaultValue = { 100 },
                        minValue = { 1 },
                        maxValue = { 1000 },
                        key = { PrefManager.KEY_BLUR_DRAWER_BACKGROUND_AMOUNT },
                        scale = { 1.0 },
                        enabled = booleanPreferenceDependency(PrefManager.KEY_BLUR_DRAWER_BACKGROUND),
                        visible = { shouldShowBlurOptions },
                    )

                    colorPickerPreference(
                        title = { stringResource(R.string.drawer_background_color) },
                        summary = { stringResource(R.string.drawer_background_color_desc) },
                        key = { PrefManager.KEY_DRAWER_BACKGROUND_COLOR },
                        defaultValue = { ResourcesCompat.getColor(resources, R.color.drawerBackgroundDefault, theme) },
                        icon = {
                            painterResource(R.drawable.ic_baseline_color_lens_24)
                        },
                    )

                    seekBarPreference(
                        title = { stringResource(R.string.drawer_column_count) },
                        summary = { stringResource(R.string.drawer_column_count_desc) },
                        defaultValue = { 2 },
                        key = { PrefManager.KEY_DRAWER_COL_COUNT },
                        icon = {
                            painterResource(R.drawable.ic_baseline_view_column_24)
                        },
                        minValue = { 1 },
                        maxValue = { 20 },
                        scale = { 1.0 },
                    )

                    seekBarPreference(
                        title = { stringResource(R.string.drawer_side_padding) },
                        summary = { stringResource(R.string.drawer_side_padding_desc) },
                        defaultValue = { 0 },
                        key = { PrefManager.KEY_DRAWER_SIDE_PADDING },
                        icon = {
                            painterResource(R.drawable.expand_horiz)
                        },
                        unit = { "dp" },
                        minValue = { 0 },
                        maxValue = { 640 },
                        scale = { 0.1 },
                    )

                    seekBarPreference(
                        title = { stringResource(R.string.settings_screen_widget_corner_radius) },
                        summary = { stringResource(R.string.settings_screen_widget_corner_radius_desc) },
                        key = { PrefManager.KEY_DRAWER_WIDGET_CORNER_RADIUS },
                        icon = {
                            painterResource(R.drawable.ic_baseline_rounded_corner_24)
                        },
                        defaultValue = { 20 },
                        scale = { 0.1 },
                        minValue = { 0 },
                        maxValue = { 640 },
                        unit = { "dp" },
                    )
                }

                category(
                    title = resources.getString(R.string.handle),
                    key = "handle_options",
                ) {
                    switchPreference(
                        title = { stringResource(R.string.show_drawer_handle) },
                        summary = { stringResource(R.string.show_drawer_handle_desc) },
                        key = { PrefManager.KEY_SHOW_DRAWER_HANDLE },
                        defaultValue = { true },
                        icon = {
                            painterResource(R.drawable.border_right)
                        },
                    )

                    switchPreference(
                        title = { stringResource(R.string.show_only_when_locked) },
                        summary = { stringResource(R.string.show_only_when_locked_desc) },
                        key = { PrefManager.KEY_SHOW_DRAWER_HANDLE_ONLY_WHEN_LOCKED },
                        defaultValue = { false },
                        icon = {
                            painterResource(R.drawable.ic_baseline_visibility_24)
                        },
                        enabled = booleanPreferenceDependency(PrefManager.KEY_SHOW_DRAWER_HANDLE),
                    )

                    switchPreference(
                        title = { stringResource(R.string.show_drawer_handle_shadow) },
                        summary = { stringResource(R.string.show_drawer_handle_shadow_desc) },
                        key = { PrefManager.KEY_SHOW_DRAWER_HANDLE_SHADOW },
                        defaultValue = { true },
                        icon = { painterResource(R.drawable.shadow) },
                        enabled = booleanPreferenceDependency(PrefManager.KEY_SHOW_DRAWER_HANDLE),
                    )

                    switchPreference(
                        title = { stringResource(R.string.drawer_handle_tap_to_open) },
                        summary = { stringResource(R.string.drawer_handle_tap_to_open_desc) },
                        key = { PrefManager.KEY_DRAWER_HANDLE_TAP_TO_OPEN },
                        defaultValue = { false },
                        icon = { painterResource(R.drawable.tap) },
                        enabled = booleanPreferenceDependency(PrefManager.KEY_SHOW_DRAWER_HANDLE),
                    )

                    switchPreference(
                        title = { stringResource(R.string.drawer_handle_lock_position) },
                        summary = { stringResource(R.string.drawer_handle_lock_position_desc) },
                        key = { PrefManager.KEY_DRAWER_HANDLE_LOCK_POSITION },
                        defaultValue = { false },
                        icon = {
                            painterResource(R.drawable.ic_baseline_lock_24)
                        },
                        enabled = booleanPreferenceDependency(PrefManager.KEY_SHOW_DRAWER_HANDLE),
                    )

                    seekBarPreference(
                        title = { stringResource(R.string.drawer_handle_height) },
                        summary = { stringResource(R.string.drawer_handle_height_desc) },
                        key = { PrefManager.KEY_DRAWER_HANDLE_HEIGHT },
                        defaultValue = { 140 },
                        icon = { painterResource(R.drawable.expand_vert) },
                        minValue = { 64 },
                        maxValue = { 500 },
                        unit = { "dp" },
                        scale = { 1.0 },
                        enabled = booleanPreferenceDependency(PrefManager.KEY_SHOW_DRAWER_HANDLE),
                    )

                    seekBarPreference(
                        title = { stringResource(R.string.drawer_handle_width) },
                        summary = { stringResource(R.string.drawer_handle_width_desc) },
                        key = { PrefManager.KEY_DRAWER_HANDLE_WIDTH },
                        defaultValue = { 6 },
                        icon = {
                            painterResource(R.drawable.expand_horiz)
                        },
                        minValue = { 1 },
                        maxValue = { 64 },
                        unit = { "dp" },
                        scale = { 1.0 },
                        enabled = booleanPreferenceDependency(PrefManager.KEY_SHOW_DRAWER_HANDLE),
                    )

                    colorPickerPreference(
                        title = { stringResource(R.string.drawer_handle_color) },
                        summary = { stringResource(R.string.drawer_handle_color_desc) },
                        key = { PrefManager.KEY_DRAWER_HANDLE_COLOR },
                        defaultValue = { -1 },
                        icon = {
                            painterResource(R.drawable.ic_baseline_color_lens_24)
                        },
                        enabled = booleanPreferenceDependency(PrefManager.KEY_SHOW_DRAWER_HANDLE),
                    )
                }
            }

            AppTheme {
                PreferenceScreen(
                    title = resources.getString(R.string.settings),
                    categories = prefs,
                )
            }
        }
    }
}
