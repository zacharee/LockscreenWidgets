package dev.zwander.lswwallpaper

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import dev.zwander.lswinterconnect.peekLogUtils

class PermissionRequestActivity : AppCompatActivity() {
    val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        peekLogUtils?.normalLog("Permission result $it", null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}
