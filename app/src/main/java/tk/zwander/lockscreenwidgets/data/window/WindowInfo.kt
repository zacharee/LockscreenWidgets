package tk.zwander.lockscreenwidgets.data.window

/**
 * Hold the info for retrieving windows in each Accessibility pass.
 */
data class WindowInfo(
    val systemUiWindows: ArrayList<WindowRootPair>,
    val topAppWindow: WindowRootPair?,
    val topNonSysUiWindow: WindowRootPair?
)