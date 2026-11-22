#!/system/bin/sh
# Fox Tuner Helper: apply the app's saved profile after boot (light: two short runs, no polling by default).
MODDIR=${0%/*}
DATA=/data/adb/profiles_tuner
(
  while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done
  rm -f "$DATA/boot_guard"
  grep -q '^opt.bundle=0' "$DATA/profiles.conf" 2>/dev/null || sh "$MODDIR/bin/bundle.sh" verify   # re-mount anything the ROM left unmounted
  sleep 20; sh "$MODDIR/bin/ptctl.sh" boot     # after the ROM's own post_boot
  sleep 60; sh "$MODDIR/bin/ptctl.sh" boot     # once more in case the perf HAL re-applied its defaults
) >/dev/null 2>&1 &
