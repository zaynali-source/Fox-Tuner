#!/system/bin/sh
MODDIR=${0%/*}
DATA=/data/adb/fox_overlay
mkdir -p "$DATA"
if [ -f "$DATA/boot_guard" ]; then
  echo "previous boot did not complete -> overlay skipped $(date)" > "$DATA/bundle.log"
  echo "skipped (boot guard)" > "$DATA/bundle.stat"; rm -f "$DATA/boot_guard"; exit 0
fi
touch "$DATA/boot_guard"
[ -f "$DATA/disable_overlay" ] && { echo "disabled" > "$DATA/bundle.stat"; exit 0; }
sh "$MODDIR/bin/bundle.sh" mount
