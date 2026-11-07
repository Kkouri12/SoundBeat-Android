#!/usr/bin/env bash
# Generates the native Android project, injects the MusicLibrary plugin,
# permissions and launcher icon, then syncs the web assets.
set -euo pipefail
cd "$(dirname "$0")"

npm install --no-audit --no-fund
[ -d android ] || npx cap add android

PKG_DIR=android/app/src/main/java/com/soundbeat/player
mkdir -p "$PKG_DIR"
cp android-overlay/java/*.java "$PKG_DIR/"
mkdir -p android/app/src/main/res && cp -r android-overlay/res/. android/app/src/main/res/

# --- permissions ---------------------------------------------------------
MANIFEST=android/app/src/main/AndroidManifest.xml
python3 - "$MANIFEST" <<'PY'
import sys, re
p = sys.argv[1]
s = open(p, encoding="utf-8").read()
perms = [
 '<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />',
 '<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />',
 '<uses-permission android:name="android.permission.WAKE_LOCK" />',
 '<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />',
 '<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />',
 '<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />',
 '<uses-permission android:name="android.permission.VIBRATE" />',
]
for line in perms:
    key = re.search(r'android:name="([^"]+)"', line).group(1)
    if key not in s:
        s = s.replace("<application", line + "\n    <application", 1)
svc = '<service android:name=".PlaybackService" android:exported="false" android:foregroundServiceType="mediaPlayback" />'
if "PlaybackService" not in s:
    s = s.replace("</application>", "    " + svc + "\n    </application>", 1)
for n, r in (("S","sb_widget_s"),("M","sb_widget_m"),("L","sb_widget_l")):
    if ".SBWidget$"+n not in s:
        s = s.replace("</application>", '    <receiver android:name=".SBWidget$%s" android:exported="false"><intent-filter><action android:name="android.appwidget.action.APPWIDGET_UPDATE" /></intent-filter><meta-data android:name="android.appwidget.provider" android:resource="@xml/%s" /></receiver>\n    </application>' % (n, r), 1)
s = s.replace('android:allowBackup="false"', 'android:allowBackup="true"')
open(p, "w", encoding="utf-8").write(s)
PY

# --- launcher icon (PNG, replaces the default adaptive icon) --------------
python3 - <<'PY'
import os, shutil
try:
    from PIL import Image
except Exception:
    import subprocess, sys
    subprocess.call([sys.executable, "-m", "pip", "install", "--quiet", "pillow"])
    from PIL import Image
res = "android/app/src/main/res"
src = Image.open("www/assets/icon.png").convert("RGBA")
sizes = {"mdpi":48,"hdpi":72,"xhdpi":96,"xxhdpi":144,"xxxhdpi":192}
for d, px in sizes.items():
    folder = os.path.join(res, "mipmap-" + d)
    os.makedirs(folder, exist_ok=True)
    im = src.resize((px, px), Image.LANCZOS)
    for n in ("ic_launcher.png", "ic_launcher_round.png", "ic_launcher_foreground.png"):
        im.save(os.path.join(folder, n))
shutil.rmtree(os.path.join(res, "mipmap-anydpi-v26"), ignore_errors=True)
PY

npx cap sync android
echo "Android project ready in ./android"
