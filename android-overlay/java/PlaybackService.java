package com.soundbeat.player;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Foreground service that keeps playback alive and owns the media notification. */
public class PlaybackService extends Service {
    static Notification notif;
    static final int ID = 7;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int f, int s) {
        if (notif == null) { stopSelf(); return START_NOT_STICKY; }
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(ID, notif);
        return START_NOT_STICKY;
    }
}
