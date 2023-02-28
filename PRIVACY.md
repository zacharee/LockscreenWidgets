# Data
Lockscreen Widgets collects only the data necessary to function and provide crash reports. Any data not collected by Bugsnag is kept strictly on-device. This can be confirmed by browsing the source code.

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

## QUERY_ALL_PACKAGES
Lockscreen Widgets uses this permission to display the list of available widgets to the user, as well as interact with the widgets as needed.

## CALL_PHONE
This permission will only be requested if needed (e.g., when tapping a direct dial shortcut).