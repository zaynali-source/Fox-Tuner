#!/system/bin/sh
MODDIR=${0%/*}
DATA=/data/adb/fox_overlay
(
  while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done
  rm -f "$DATA/boot_guard"
  [ -f "$DATA/disable_overlay" ] || sh "$MODDIR/bin/bundle.sh" verify
  # if the PowerHAL started before our powerhint.json was in place, restart it so it re-reads the file (init respawns it)
  if [ -f "$DATA/hal_restart" ]; then rm -f "$DATA/hal_restart"; pkill -f android.hardware.power-service; fi
) >/dev/null 2>&1 &
