package com.soundbeat.player;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.*;
import android.widget.RemoteViews;

/** Home-screen widgets: S = square cover, M = card with controls, L = slim pill. */
public class SBWidget extends AppWidgetProvider {
    public static class S extends SBWidget {}
    public static class M extends SBWidget {}
    public static class L extends SBWidget {}

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        refresh(c);
    }

    static PendingIntent act(Context c, String a, int code) {
        Intent i = new Intent(MediaControlPlugin.ACT)
                .setPackage(c.getPackageName())
                .putExtra("a", a);

        return PendingIntent.getBroadcast(
                c, code, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    static Bitmap round(Bitmap b, float k) {
        Bitmap o = Bitmap.createBitmap(
                b.getWidth(), b.getHeight(), Bitmap.Config.ARGB_8888
        );

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new BitmapShader(
                b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP
        ));

        float r = Math.min(b.getWidth(), b.getHeight()) * k;

        new Canvas(o).drawRoundRect(
                new RectF(0, 0, b.getWidth(), b.getHeight()),
                r, r, p
        );

        return o;
    }

    public static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);

        SharedPreferences p = c.getSharedPreferences("sb_widget", 0);

        String t = p.getString("t", "");
        String a = p.getString("a", "");
        String art = p.getString("art", "");

        boolean pl = p.getBoolean("pl", false);
        boolean has = !t.isEmpty();

        Bitmap bmp = art.isEmpty() ? null : BitmapFactory.decodeFile(art);

        Class<?>[] ks = { S.class, M.class, L.class };

        int[] ly = {
                R.layout.sb_widget_s,
                R.layout.sb_widget_m,
                R.layout.sb_widget_l
        };

        PendingIntent open = PendingIntent.getActivity(
                c,
                0,
                c.getPackageManager().getLaunchIntentForPackage(c.getPackageName()),
                PendingIntent.FLAG_IMMUTABLE
        );

        for (int i = 0; i < 3; i++) {
            int[] ids = m.getAppWidgetIds(new ComponentName(c, ks[i]));

            if (ids.length == 0) continue;

            RemoteViews v = new RemoteViews(c.getPackageName(), ly[i]);

            if (bmp != null) {
                v.setImageViewBitmap(
                        R.id.w_art,
                        round(bmp, i == 0 ? .14f : .2f)
                );
            }

            v.setImageViewResource(
                    R.id.w_pp,
                    pl ? android.R.drawable.ic_media_pause
                       : android.R.drawable.ic_media_play
            );

            v.setOnClickPendingIntent(R.id.w_root, open);

            v.setOnClickPendingIntent(
                    R.id.w_pp,
                    has ? act(c, pl ? "pause" : "play", 11) : open
            );

            if (i > 0) {
                v.setTextViewText(R.id.w_title, has ? t : "Sound Beat");
                v.setTextViewText(R.id.w_artist, has ? a : "");

                v.setOnClickPendingIntent(
                        R.id.w_prev,
                        act(c, "prev", 12)
                );

                v.setOnClickPendingIntent(
                        R.id.w_next,
                        act(c, "next", 13)
                );
            }

            m.updateAppWidget(ids, v);
        }
    }
}
