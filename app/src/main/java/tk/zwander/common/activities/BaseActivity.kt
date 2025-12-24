package tk.zwander.common.activities

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import tk.zwander.common.util.lsDisplayManager

abstract class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()

        lsDisplayManager.fetchDisplays()
    }
}