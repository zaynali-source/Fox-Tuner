#!/system/bin/sh
kill "$(cat /data/adb/profiles_tuner/ptd.pid 2>/dev/null)" 2>/dev/null
rm -rf /data/adb/profiles_tuner
