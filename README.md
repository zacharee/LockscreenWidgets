# Building

Lockscreen Widgets makes use of some hidden APIs. Since reflection gets tedious, I use a modified android.jar that exposes the hidden APIs so they can be used like normal.
If you want to build Lockscreen Widgets for yourself, make sure you use the android.jar from the following repo: https://github.com/anggrayudi/android-hidden-api.
Lockscreen Widgets is currently built against API 30. Download the `android-30` JAR from the above repo and copy it to `YOUR-SDK-LOCATION/platforms/android-30/android.jar`. Make sure to back the original version up in case something goes wrong.

You'll also need to replace a local dependency reference with a remote one. Check the `dependencies` section of the app-level build.gradle, and check settings.gradle for what to replace/remove.