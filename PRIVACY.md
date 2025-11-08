# Data
Lockscreen Widgets collects only the data necessary to function and provide crash reports. No personally identifiable information is collected.

Examples of Bugsnag data collected:
* Basic device info including model and Android version.
* Stacktraces leading up to the crash.
* Breadcrumb logs indicating what actions the app has taken leading up to the crash.
* The current widgets added to the frames and the drawer, excluding their configuration.

Any data not collected by Bugsnag is kept strictly on-device. This can be confirmed by browsing the source code.

# Permissions
## Accessibility
Lockscreen Widgets requires an accessibility service in order to display on the lock screen and in the notification center.

This is due to the special overlay type used, which only accessibility services have access to. Without it, Lockscreen Widgets wouldn't be able to perform its main function.

## VIBRATE
Lockscreen Widgets uses this permission to activate the vibration motor for haptic feedback.

## DISABLE_KEYGUARD
Lockscreen Widgets uses this permission to prompt the user to dismiss the lock screen when needed.

## READ_EXTERNAL_STORAGE
Lockscreen Widgets uses this permission to get the user's wallpaper for Masked Frame Mode.

## READ_MEDIA_IMAGES
Android 13 and later requires this additional permission to get the user's wallpaper for Masked Frame Mode.

<!--## MANAGE_EXTERNAL_STORAGE
Lockscreen Widgets uses this permission on Android 13 and later to get the user's wallpaper for Masked Frame Mode.

In order to retrieve the wallpaper, Lockscreen Widgets needs to be granted the READ_EXTERNAL_STORAGE permission, or a permission with a larger scope. Android 13 deprecated READ_EXTERNAL_STORAGE in favor of separate scoped media access permissions. However, none of those more granular permissions allow retrieving the wallpaper.

Lockscreen Widgets has been targeting Android 12L since Android 13 was released, as apps targeting earlier versions of Android are still allowed to request READ_EXTERNAL_STORAGE. Unfortunately, the Google Play Store's target API requirements won't allow this for much longer.

In order for Lockscreen Widgets to target Android 13 and comply with Google's policies, it must request MANAGE_EXTERNAL_STORAGE, a permission that includes access to anything that requires READ_EXTERNAL_STORAGE. There is no way around this if you want to use Masked Mode.

However, Lockscreen Widgets will only request this permission if needed. The introduction screen contains an explanation of what it's used for and that it isn't required. Lockscreen Widgets will prompt again for it when enabling Masked Mode if it wasn't granted initially.-->

## QUERY_ALL_PACKAGES
Lockscreen Widgets uses this permission to display the list of available widgets to the user, as well as interact with the widgets as needed.

## CALL_PHONE
This permission will only be requested if needed (e.g., when tapping a direct dial shortcut).