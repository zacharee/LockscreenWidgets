# Lockscreen Widgets
Lockscreen Widgets adds a frame to your lock screen where you can add home screen widgets.

It also has a widget drawer with a handle you can drag to open it from anywhere.

Note that Lockscreen Widgets is _not_ designed to work on the Always-On Display.

## Downloads
The precompiled version of Lockscreen Widgets is a paid app, available on the [Google Play Store](https://play.google.com/store/apps/details?id=tk.zwander.lockscreenwidgets).

## Building
Lockscreen Widgets makes use of some hidden APIs. Since reflection gets tedious, I use a modified android.jar that exposes the hidden APIs so they can be used like normal.
If you want to build Lockscreen Widgets for yourself, make sure you use the android.jar from the following repo: https://github.com/Reginer/aosp-android-jar.
Lockscreen Widgets is currently built against API 36. Download the `android-36` JAR from the above repo and copy it to `YOUR-SDK-LOCATION/platforms/android-36/android.jar`. 

Make sure to back the original version up in case something goes wrong.

# Translating
[![Crowdin](https://badges.crowdin.net/lockscreen-widgets/localized.svg)](https://crowdin.com/project/lockscreen-widgets)

Lockscreen Widgets uses Crowdin for translations. You can view the project and contribute using the following link.

https://crowdin.com/project/lockscreen-widgets

# Error Reporting
Lockscreen Widgets uses Bugsnag for error reporting as of version 2.7.5. Previous versions use Firebase Crashlytics.

<a href="https://www.bugsnag.com"><img src="https://assets-global.website-files.com/607f4f6df411bd01527dc7d5/63bc40cd9d502eda8ea74ce7_Bugsnag%20Full%20Color.svg" width="200"></a>
