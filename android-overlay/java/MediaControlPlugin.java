package com.soundbeat.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

/**
 * Lock-screen + notification controls (cover, prev / play-pause / next, seek bar).
 * JS pushes state with update(); button presses come back as "action" events.
 */
@CapacitorPlugin(name = "MediaControl",
    permissions = { @Permission(strings = { "android.permission.POST_NOTIFICATIONS" }, alias = "notif") })
public class MediaControlPlugin extends Plugin {
    static final String CH = "sb_playback", ACT = "com.soundbeat.player.MEDIA_ACTION";
    private MediaSession ms;
    private boolean registered;

    private final BroadcastReceiver rx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { fire(i.getStringExtra("a"), -1); }
    };

    private void fire(String a, long posMs) {
        JSObject o = new JSObject();
        o.put("a", a);
        if (posMs >= 0) o.put("pos", posMs / 1000.0);
        notifyListeners("action", o);
    }

    @PluginMethod
    public void ready(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 33 && getPermissionState("notif") != PermissionState.GRANTED)
            requestPermissionForAlias("notif", call, "permDone");
        else call.resolve();
    }

    @PermissionCallback
    private void permDone(PluginCall call) { call.resolve(); }

    private void init() {
        if (ms != null) return;
        Context x = getContext();
        ms = new MediaSession(x, "SoundBeat");
        ms.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { fire("play", -1); }
            @Override public void onPause() { fire("pause", -1); }
            @Override public void onSkipToNext() { fire("next", -1); }
            @Override public void onSkipToPrevious() { fire("prev", -1); }
            @Override public void onSeekTo(long p) { fire("seek", p); }
        });
        ms.setActive(true);
        IntentFilter f = new IntentFilter(ACT);
        if (Build.VERSION.SDK_INT >= 33) x.registerReceiver(rx, f, Context.RECEIVER_NOT_EXPORTED);
        else x.registerReceiver(rx, f);
        registered = true;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH, "Playback", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ((NotificationManager) x.getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private PendingIntent pi(String a, int code) {
        Context x = getContext();
        Intent i = new Intent(ACT).setPackage(x.getPackageName()).putExtra("a", a);
        return PendingIntent.getBroadcast(x, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @PluginMethod
    public void update(PluginCall call) {
        try {
            init();
            Context x = getContext();
            String title = call.getString("title", ""), artist = call.getString("artist", ""), art = call.getString("art", "");
            boolean pl = Boolean.TRUE.equals(call.getBoolean("playing", false));
            long pos = (long) (call.getDouble("pos", 0.0) * 1000), dur = (long) (call.getDouble("dur", 0.0) * 1000);
            Bitmap bmp = null;
            if (art != null && !art.isEmpty()) {
                try {
                    if (art.startsWith("data:")) {
                        byte[] raw = android.util.Base64.decode(art.substring(art.indexOf(',') + 1), android.util.Base64.DEFAULT);
                        bmp = BitmapFactory.decodeByteArray(raw, 0, raw.length);
                    } else bmp = BitmapFactory.decodeFile(art);
                } catch (Exception ignored) {}
            }

            MediaMetadata.Builder mb = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, dur);
            if (bmp != null) mb.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bmp);
            ms.setMetadata(mb.build());
            ms.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                    | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO)
                .setState(pl ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, pos, pl ? 1f : 0f).build());

            android.content.SharedPreferences.Editor we = x.getSharedPreferences("sb_widget", 0).edit()
                .putString("t", title).putString("a", artist).putBoolean("pl", pl).putString("art", "");
            if (bmp != null) {
                try {
                    float k = Math.min(1f, 400f / Math.max(bmp.getWidth(), bmp.getHeight()));
                    Bitmap sm = Bitmap.createScaledBitmap(bmp, Math.round(bmp.getWidth() * k), Math.round(bmp.getHeight() * k), true);
                    java.io.File wf = new java.io.File(x.getCacheDir(), "w_art.jpg");
                    java.io.FileOutputStream fo = new java.io.FileOutputStream(wf);
                    sm.compress(Bitmap.CompressFormat.JPEG, 88, fo); fo.close();
                    we.putString("art", wf.getAbsolutePath());
                } catch (Exception ignored) {}
            }
            we.apply();
            SBWidget.refresh(x);

            Intent open = x.getPackageManager().getLaunchIntentForPackage(x.getPackageName());
            Notification.Builder nb = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(x, CH) : new Notification.Builder(x);
            nb.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title).setContentText(artist)
                .setLargeIcon(bmp).setOnlyAlertOnce(true).setShowWhen(false).setOngoing(pl)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(PendingIntent.getActivity(x, 0, open, PendingIntent.FLAG_IMMUTABLE))
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous, "Previous", pi("prev", 1)).build())
                .addAction(new Notification.Action.Builder(pl ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                    pl ? "Pause" : "Play", pi(pl ? "pause" : "play", 2)).build())
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next, "Next", pi("next", 3)).build())
                .setStyle(new Notification.MediaStyle().setMediaSession(ms.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
            Notification n = nb.build();
            PlaybackService.notif = n;
            try {
                Intent si = new Intent(x, PlaybackService.class);
                if (Build.VERSION.SDK_INT >= 26) x.startForegroundService(si); else x.startService(si);
            } catch (Exception e) {
                ((NotificationManager) x.getSystemService(Context.NOTIFICATION_SERVICE)).notify(PlaybackService.ID, n);
            }
            call.resolve();
        } catch (Exception e) {
            call.reject("media update failed: " + e.getMessage());
        }
    }

    @Override
    protected void handleOnDestroy() {
        Context x = getContext();
        try { x.stopService(new Intent(x, PlaybackService.class)); } catch (Exception ignored) {}
        try { if (registered) x.unregisterReceiver(rx); } catch (Exception ignored) {}
        if (ms != null) ms.release();
    }
}
