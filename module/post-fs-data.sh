#!/system/bin/sh
# Systemless bind-mount of the PowerHAL powerhint.json. Runs before the Power HAL starts, so it reads our file from its first load.
MODDIR=${0%/*}
DATA=/data/adb/profiles_tuner
mkdir -p "$DATA"
if [ -f "$DATA/boot_guard" ]; then   # previous boot never finished -> skip once
  echo "previous boot did not complete -> overlay skipped $(date)" > "$DATA/bundle.log"
  echo "skipped (boot guard)" > "$DATA/bundle.stat"; rm -f "$DATA/boot_guard"; exit 0
fi
touch "$DATA/boot_guard"
if grep -q '^opt.bundle=0' "$DATA/profiles.conf" 2>/dev/null; then echo "disabled in app" > "$DATA/bundle.stat"; exit 0; fi
sh "$MODDIR/bin/bundle.sh" mount
