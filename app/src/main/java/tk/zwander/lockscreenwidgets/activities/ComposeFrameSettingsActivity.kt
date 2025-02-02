package tk.zwander.lockscreenwidgets.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import tk.zwander.common.activities.BaseActivity
import tk.zwander.common.compose.AppTheme
import tk.zwander.common.compose.settings.PreferenceScreen
import tk.zwander.common.compose.settings.booleanPreferenceDependency
import tk.zwander.common.compose.settings.rememberPreferenceScreen
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.util.Event
import tk.zwander.common.util.EventObserver
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.util.FramePrefs

class ComposeFrameSettingsActivity : BaseActivity(), EventObserver {
    companion object {
        private const val REQ_REMOVE_FRAME = 101
        private const val REQ_SELECT_FRAME = 102
    }

    private var selectedFrame by mutableIntStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var secondaryFrames by rememberPreferenceState(
                key = PrefManager.KEY_CURRENT_FRAMES,
                value = { prefManager.currentSecondaryFrames },
                onChanged = { _, v -> prefManager.currentSecondaryFrames = v },
            )
            val frameCount = secondaryFrames.size + 1
            val preferenceScreen = rememberPreferenceScreen(selectedFrame) {
                category(
                    key = "frame_management_category",
                    title = resources.getString(R.string.frame_management),
                ) {
                    preference(
                        title = { stringResource(R.string.select_frame) },
                        summary = { stringResource(R.string.selected_frame, "$selectedFrame") },
                        key = { "select_secondary_frame" },
                        onClick = {
                            eventManager.sendEvent(Event.PreviewFrames(Event.PreviewFrames.ShowMode.SHOW_FOR_SELECTION, 102, true))
                        },
                        icon = { null },
                        defaultValue = {},
                        visible = { frameCount > 1 },
                    )

                    preference(
                        title = { stringResource(R.string.add_frame) },
                        summary = { pluralStringResource(R.plurals.frame_count_info, frameCount, "$frameCount") },
                        key = { "add_secondary_frame" },
                        onClick = {
                            val maxFrameId = secondaryFrames.maxOrNull() ?: 1
                            val newFrameId = maxFrameId + 1

                            secondaryFrames = secondaryFrames.toMutableList().apply {
                                add(newFrameId)
                            }
                        },
                        icon = { painterResource(R.drawable.ic_baseline_add_24) },
                        defaultValue = {},
                    )

                    preference(
                        title = { stringResource(R.string.remove_frame) },
                        summary = { null },
                        key = { "remove_secondary_frame" },
                        onClick = {
                            eventManager.sendEvent(Event.PreviewFrames(Event.PreviewFrames.ShowMode.SHOW_FOR_SELECTION, 101, false))
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
                        key = { PrefManager.KEY_FRAME_BACKGROUND_COLOR },
                        defaultValue = { 0 },
                    )

                    switchPreference(
                        title = { stringResource(R.string.settings_screen_blur_background) },
                        summary = { stringResource(R.string.settings_screen_blur_background_desc) },
                        icon = { painterResource(R.drawable.ic_baseline_blur_on_24) },
                        key = { PrefManager.KEY_BLUR_BACKGROUND },
                        defaultValue = { false },
                    )

                    seekBarPreference(
                        title = { stringResource(R.string.settings_screen_blur_background_amount) },
                        summary = { stringResource(R.string.settings_screen_blur_background_amount_desc) },
                        icon = { painterResource(R.drawable.ic_baseline_deblur_24) },
                        key = { PrefManager.KEY_BLUR_BACKGROUND_AMOUNT },
                        defaultValue = { 100 },
                        minValue = { 1 },
                        maxValue = { 1000 },
                        enabled = booleanPreferenceDependency(PrefManager.KEY_BLUR_BACKGROUND),
                        scale = { 1.0 },
                    )

                    switchPreference(
                        title = { stringResource(R.string.settings_screen_masked_mode) },
                        summary = { stringResource(R.string.settings_screen_masked_mode_desc) },
                        icon = { painterResource(R.drawable.ic_baseline_opacity_24) },
                        key = { PrefManager.KEY_FRAME_MASKED_MODE },
                    )

                    seekBarPreference(
                        title = { stringResource(R.string.settings_screen_masked_mode_dim_amount) },
                        summary = { stringResource(R.string.settings_screen_masked_mode_dim_amount_desc) },
                        icon = { painterResource(R.drawable.ic_baseline_brightness_medium_24) },
                        key = { PrefManager.KEY_MASKED_MODE_DIM_AMOUNT },
                        defaultValue = { 0 },
                        enabled = booleanPreferenceDependency(PrefManager.KEY_FRAME_MASKED_MODE),
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
                                selectedFrame
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
                                selectedFrame
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
                    key = "settings_category_layout",
                ) {

                }
            }

            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    PreferenceScreen(preferenceScreen)
                }
            }
        }
    }

    override fun onEvent(event: Event) {
        if (event is Event.FrameSelected) {
            if (event.frameId != null && event.requestCode == REQ_REMOVE_FRAME) {
                prefManager.currentSecondaryFrames = prefManager.currentSecondaryFrames.toMutableList().apply {
                    removeAll { it == event.frameId }
                }
                if (selectedFrame == event.frameId) {
                    selectedFrame = -1
                }
                eventManager.sendEvent(Event.PreviewFrames(Event.PreviewFrames.ShowMode.HIDE))
            }
            if (event.frameId != null && event.requestCode == REQ_SELECT_FRAME) {
                selectedFrame = event.frameId
                eventManager.sendEvent(Event.PreviewFrames(Event.PreviewFrames.ShowMode.HIDE))
            }
        }
    }
}
