package tk.zwander.common.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowInsetsControllerCompat
import com.bugsnag.android.performance.compose.MeasuredComposable
import dev.zwander.composeintroslider.IntroSlider
import tk.zwander.common.compose.data.rememberIntroSlides
import tk.zwander.common.util.setThemedContent

/**
 * The introduction for the app.
 * Tell the user about how it works and request permissions.
 * Also manage requesting permissions if they've been revoked.
 */
class OnboardingActivity : ComponentActivity() {
    companion object {
        const val EXTRA_RETRO_MODE = "RETRO_MODE"

        /**
         * Start [OnboardingActivity] with the requested [RetroMode]
         *
         * @param context a Context object
         * @param retroMode specify a [RetroMode] if this instance is being used
         * to request a specific permission and not showing the entire intro
         */
        fun start(context: Context, retroMode: RetroMode = RetroMode.NONE) {
            val intent = Intent(context, OnboardingActivity::class.java)
            intent.putExtra(EXTRA_RETRO_MODE, retroMode.toString())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(intent)
        }

        /**
         * Same as [OnboardingActivity.start] but will return a result stating
         * whether or not the user successfully completed the setup
         *
         * @param activity an Activity reference
         * @param request the request for returning the result
         * @param retroMode specify a [RetroMode] if this instance is being used
         * to request a specific permission and not showing the entire intro
         */
        fun startForResult(
            activity: Activity,
            request: ActivityResultLauncher<Intent>,
            retroMode: RetroMode = RetroMode.NONE
        ) {
            val intent = Intent(activity, OnboardingActivity::class.java)
            intent.putExtra(EXTRA_RETRO_MODE, retroMode.toString())

            request.launch(intent)
        }
    }

    /**
     * Enum class to keep track of whether this instance is
     * showing the entire intro sequence or just requesting a
     * specific permission
     */
    enum class RetroMode {
        ACCESSIBILITY,
        NOTIFICATION,
        STORAGE,
        BATTERY,
        NONE
    }

    private val retroMode by lazy {
        RetroMode.valueOf(
            intent.getStringExtra(EXTRA_RETRO_MODE) ?: RetroMode.NONE.toString()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)

        val insetsControllerCompat = WindowInsetsControllerCompat(window, window.decorView)

        setThemedContent {
            MeasuredComposable(name = "OnboardingActivity") {
                IntroSlider(
                    pages = rememberIntroSlides(startReason = retroMode, finish = ::finish),
                    onExit = ::finish,
                    onDone = {
                        setResult(RESULT_OK)
                        finish()
                    },
                    modifier = Modifier.fillMaxSize(),
                    backPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher,
                    normalizeElements = true,
                    onContentColorChanged = {
                        insetsControllerCompat.isAppearanceLightNavigationBars =
                            it.luminance() <= 0.5
                        insetsControllerCompat.isAppearanceLightStatusBars = it.luminance() <= 0.5
                    },
                )
            }
        }
    }
}