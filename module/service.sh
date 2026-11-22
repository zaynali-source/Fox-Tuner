#!/system/bin/sh
MODDIR=${0%/*}
DATA=/data/adb/profiles_tuner
(
  while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done
  rm -f "$DATA/boot_guard"
  sleep 25   # let the ROM's own perf/profile services finish post_boot
  rm -f "$DATA/ptd.pid"
  sh "$MODDIR/bin/ptd.sh"
) >/dev/null 2>&1 &
