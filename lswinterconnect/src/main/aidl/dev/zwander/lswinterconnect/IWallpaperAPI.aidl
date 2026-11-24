package dev.zwander.lswinterconnect;

import android.os.ParcelFileDescriptor;
import android.graphics.Bitmap;
import dev.zwander.lswinterconnect.IWallpaperListener;

interface IWallpaperAPI {
    Bitmap getWallpaper(int flag) = 1;
    Bitmap peekWallpaper(int flag) = 2;

    void registerWallpaperListener(IWallpaperListener listener) = 3;
    void unregisterWallpaperListener(IWallpaperListener listener) = 4;

    ParcelFileDescriptor getWallpaperPfd(int flag) = 5;
    ParcelFileDescriptor peekWallpaperPfd(int flag) = 6;

    int getApiVersion() = 7;
}
