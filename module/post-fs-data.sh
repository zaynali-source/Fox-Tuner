#!/system/bin/sh
# Systemless bind-mount of the bundled PowerHAL / perf / thermal configs.
MODDIR=${0%/*}
DATA=/data/adb/profiles_tuner
BL=$DATA/bundle.log
mkdir -p "$DATA"
echo "--- $(date) ---" > "$BL"

# Boot-loop guard: if previous boot never reached boot_completed, skip the bundle once.
if [ -f "$DATA/boot_guard" ]; then
  echo "previous boot did not complete -> bundle skipped" >> "$BL"
  rm -f "$DATA/boot_guard"
  exit 0
fi
touch "$DATA/boot_guard"

grep -q '^opt.bundle=0' "$DATA/profiles.conf" 2>/dev/null && { echo "bundle disabled in app" >> "$BL"; exit 0; }

cd "$MODDIR/bundle" || exit 0
for f in $(find . -type f); do
  rel=${f#./}
  tgt=/$rel
  src=$MODDIR/bundle/$rel
  if [ ! -f "$tgt" ]; then echo "skip (target missing): $tgt" >> "$BL"; continue; fi
  chcon --reference="$tgt" "$src" 2>>"$BL"
  if mount --bind "$src" "$tgt" 2>>"$BL"; then echo "ok $tgt" >> "$BL"; else echo "FAIL $tgt" >> "$BL"; fi
done
