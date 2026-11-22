#!/system/bin/sh
# Systemless bind-mount of the bundled PowerHAL / perf / thermal configs.
# Runs before `on boot`, which is when init starts mi_thermald, so it reads our files from its first load.
MODDIR=${0%/*}
DATA=/data/adb/profiles_tuner
mkdir -p "$DATA"

# Boot-loop guard: if the previous boot never reached boot_completed, skip the overlay once.
if [ -f "$DATA/boot_guard" ]; then
  echo "previous boot did not complete -> overlay skipped $(date)" > "$DATA/bundle.log"
  echo "skipped (boot guard)" > "$DATA/bundle.stat"
  rm -f "$DATA/boot_guard"
  exit 0
fi
touch "$DATA/boot_guard"

if grep -q '^opt.bundle=0' "$DATA/profiles.conf" 2>/dev/null; then
  echo "overlay disabled in app" > "$DATA/bundle.stat"; exit 0
fi
sh "$MODDIR/bin/bundle.sh" mount
