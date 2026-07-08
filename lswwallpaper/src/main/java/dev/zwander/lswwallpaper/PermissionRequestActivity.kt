package dev.zwander.lswwallpaper

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class PermissionRequestActivity : AppCompatActivity() {
    val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        logUtils.normalLog("Permission result $it", null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        logUtils.debugLog("Starting permission request activity", null)

        permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}
