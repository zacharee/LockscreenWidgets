[![Get it at IzzyOnDroid](https://img.shields.io/endpoint?url=https://apt.izzysoft.de/fdroid/api/v1/shield/tk.zwander.lockscreenwidgets&label=IzzyOnDroid)](https://apt.izzysoft.de/packages/tk.zwander.lockscreenwidgets)
[![RB Status](https://shields.rbtlog.dev/simple/tk.zwander.lockscreenwidgets)](https://shields.rbtlog.dev/tk.zwander.lockscreenwidgets)

# Widgets Anywhere
Widgets Anywhere gives you the ability to use your home screen widgets (nearly) everywhere.

Features:
* Customizable and moveable frames that show above the lock screen and can host one or more widgets each.
* A widget drawer you can access with a swipe or Tasker shortcut.
* A widget stack widget for your home screen that allows you to put multiple widgets in one space and flip through them.
* QS tiles on Samsung devices that can expand to display a chosen widget.

Note that Widgets Anywhere is _not_ designed to work on the Always-On Display.

## Downloads
Widgets Anywhere is available as a paid app on the [Google Play Store](https://play.google.com/store/apps/details?id=tk.zwander.lockscreenwidgets).

You can also download it for free from the [Releases](https://github.com/zacharee/LockscreenWidgets/releases) page here on GitHub.

The two versions are identical.

## Building
Widgets Anywhere makes use of some hidden APIs. Since reflection gets tedious, I use a modified android.jar that exposes the hidden APIs so they can be used like normal.
If you want to build Widgets Anywhere for yourself, make sure you use the android.jar from the following repo: https://github.com/Reginer/aosp-android-jar.
Widgets Anywhere is currently built against API 37. Download the `android-37` JAR from the above repo and copy it to `YOUR-SDK-LOCATION/platforms/android-37.0/android.jar`. 

Make sure to back the original version up in case something goes wrong.

# Translating
[![Crowdin](https://badges.crowdin.net/lockscreen-widgets/localized.svg)](https://crowdin.com/project/lockscreen-widgets)

Widgets Anywhere uses Crowdin for translations. You can view the project and contribute using the following link.

https://crowdin.com/project/lockscreen-widgets

# Error Reporting
Widgets Anywhere uses Bugsnag for error reporting as of version 2.7.5. Previous versions use Firebase Crashlytics.

<a href="https://www.bugsnag.com"><img src="https://assets-global.website-files.com/607f4f6df411bd01527dc7d5/63bc40cd9d502eda8ea74ce7_Bugsnag%20Full%20Color.svg" width="200"></a>

# Acknowledgements
Thank you to [@tallshmo](https://bsky.app/profile/tallshmo.com) for the redesigned icon!
