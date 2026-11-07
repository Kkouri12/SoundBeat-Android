package com.soundbeat.player;

import android.Manifest;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Size;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Reads every audio file on the phone through MediaStore (so the library is
 * built from the real music folders) and extracts cover art on demand.
 */
@CapacitorPlugin(
    name = "MusicLibrary",
    permissions = {
        @Permission(strings = { Manifest.permission.READ_MEDIA_AUDIO }, alias = "audio"),
        @Permission(strings = { Manifest.permission.READ_EXTERNAL_STORAGE }, alias = "legacy")
    }
)
public class MusicLibraryPlugin extends Plugin {

    private String alias() {
        return Build.VERSION.SDK_INT >= 33 ? "audio" : "legacy";
    }

    @PluginMethod
    public void scan(PluginCall call) {
        if (getPermissionState(alias()) != PermissionState.GRANTED) {
            requestPermissionForAlias(alias(), call, "permCallback");
        } else {
            doScan(call);
        }
    }

    @PermissionCallback
    private void permCallback(PluginCall call) {
        if (getPermissionState(alias()) == PermissionState.GRANTED) {
            doScan(call);
        } else {
            call.reject("denied");
        }
    }

    private void doScan(PluginCall call) {
        try {
            String[] proj;
            if (Build.VERSION.SDK_INT >= 30) {
                proj = new String[] {
                    MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DATE_ADDED,
                    MediaStore.MediaColumns.BITRATE
                };
            } else {
                proj = new String[] {
                    MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DATE_ADDED
                };
            }
            String sel = MediaStore.Audio.Media.DURATION + " >= 30000 AND " +
                MediaStore.Audio.Media.IS_RINGTONE + " = 0 AND " +
                MediaStore.Audio.Media.IS_NOTIFICATION + " = 0 AND " +
                MediaStore.Audio.Media.IS_ALARM + " = 0";
            JSArray arr = new JSArray();
            Cursor c = getContext().getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, sel, null,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC");
            if (c != null) {
                while (c.moveToNext()) {
                    String path = c.getString(5);
                    if (path == null) continue;
                    JSObject o = new JSObject();
                    o.put("id", c.getLong(0));
                    o.put("title", c.getString(1));
                    o.put("artist", c.getString(2));
                    o.put("album", c.getString(3));
                    o.put("dur", c.getLong(4) / 1000.0);
                    o.put("path", path);
                    o.put("albumId", c.getLong(6));
                    o.put("added", c.getLong(7));
                    if (Build.VERSION.SDK_INT >= 30) o.put("bitrate", c.getInt(8));
                    arr.put(o);
                }
                c.close();
            }
            JSObject r = new JSObject();
            r.put("songs", arr);
            call.resolve(r);
        } catch (Exception e) {
            call.reject("scan failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void getArt(PluginCall call) {
        long id = call.getData().optLong("id", 0);
        long albumId = call.getData().optLong("albumId", 0);
        String path = call.getString("path", "");
        JSObject r = new JSObject();
        try {
            File dir = new File(getContext().getCacheDir(), "art");
            dir.mkdirs();
            String key = albumId > 0 ? "a" + albumId : "s" + id;
            File out = new File(dir, key + ".jpg");
            File none = new File(dir, key + ".none");
            if (out.exists()) {
                r.put("path", out.getAbsolutePath());
                call.resolve(r);
                return;
            }
            if (none.exists()) {
                call.resolve(r);
                return;
            }
            Bitmap bmp = null;
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    Uri u = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                    bmp = getContext().getContentResolver().loadThumbnail(u, new Size(600, 600), null);
                } catch (Exception ignored) {}
            }
            if (bmp == null && !path.isEmpty()) {
                try {
                    MediaMetadataRetriever mr = new MediaMetadataRetriever();
                    mr.setDataSource(path);
                    byte[] b = mr.getEmbeddedPicture();
                    mr.release();
                    if (b != null) bmp = BitmapFactory.decodeByteArray(b, 0, b.length);
                } catch (Exception ignored) {}
            }
            if (bmp == null && !path.isEmpty()) {
                File parent = new File(path).getParentFile();
                String[] names = { "cover", "folder", "front", "album", "albumart" };
                String[] exts = { "jpg", "jpeg", "png", "webp" };
                for (String n : names) {
                    for (String e : exts) {
                        File f = new File(parent, n + "." + e);
                        if (f.exists()) {
                            bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
                            if (bmp != null) break;
                        }
                    }
                    if (bmp != null) break;
                }
            }
            if (bmp != null) {
                int w = bmp.getWidth(), h = bmp.getHeight(), m = Math.max(w, h);
                if (m > 600) {
                    float k = 600f / m;
                    bmp = Bitmap.createScaledBitmap(bmp, Math.round(w * k), Math.round(h * k), true);
                }
                FileOutputStream fo = new FileOutputStream(out);
                bmp.compress(Bitmap.CompressFormat.JPEG, 88, fo);
                fo.close();
                r.put("path", out.getAbsolutePath());
            } else {
                none.createNewFile();
            }
        } catch (Exception ignored) {}
        call.resolve(r);
    }
}
