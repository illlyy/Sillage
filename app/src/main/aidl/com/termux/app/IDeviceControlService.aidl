package com.termux.app;

import android.os.ParcelFileDescriptor;

interface IDeviceControlService {
    void destroy() = 16777114;
    void exit() = 1;
    String getStatus() = 2;
    String dumpUiHierarchy(int maxNodes) = 3;
    ParcelFileDescriptor captureScreenshot() = 4;
    boolean tap(int x, int y, long durationMs) = 5;
    boolean swipe(int fromX, int fromY, int toX, int toY, long durationMs) = 6;
    boolean key(String keyName) = 7;
    String launchApp(String packageName, String activityName) = 8;
    String execShell(String command, int timeoutSeconds) = 9;
    String listApps(String query, int limit) = 10;
}
