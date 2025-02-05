package tk.zwander.common.util.shizuku

import android.content.pm.IPackageManager
import android.os.Build
import android.os.ServiceManager
import android.os.UserHandle
import tk.zwander.lockscreenwidgets.BuildConfig
import tk.zwander.lockscreenwidgets.IShizukuService
import kotlin.system.exitProcess

class ShizukuService : IShizukuService.Stub() {
    override fun grantReadExternalStorage() {
        val ipm = IPackageManager.Stub.asInterface(ServiceManager.getService("package"))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ipm.grantRuntimePermission(
                BuildConfig.APPLICATION_ID,
                android.Manifest.permission.READ_MEDIA_IMAGES,
                UserHandle.USER_SYSTEM,
            )
        }

        ipm.grantRuntimePermission(
            BuildConfig.APPLICATION_ID,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            UserHandle.USER_SYSTEM,
        )
    }

    override fun destroy() {
        exitProcess(0)
    }
}
