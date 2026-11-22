#!/system/bin/sh
# Fox Tuner Helper: systemless overlay of the bundled PowerHAL / powerhint / thermal files.
#   bundle.sh mount   (post-fs-data: bind-mount every bundled file over its system path)
#   bundle.sh verify  (after boot: re-check, re-mount anything that is not our file, write status)
MODDIR=${0%/*}/..
DATA=${PT_DATA:-/data/adb/profiles_tuner}
ROOT=${PT_ROOT:-}
BL=$DATA/bundle.log
ST=$DATA/bundle.stat
mode=${1:-mount}
mkdir -p "$DATA"
[ "$mode" = mount ] && echo "--- $(date) ---" > "$BL"

ok=0; bad=0; skip=0
cd "$MODDIR/bundle" || exit 0
for f in $(find . -type f); do
  rel=${f#./}; tgt=$ROOT/$rel; src=$MODDIR/bundle/$rel
  [ -f "$tgt" ] || { skip=$((skip + 1)); echo "skip (target missing): $tgt" >> "$BL"; continue; }
  if [ "$mode" = verify ] && cmp -s "$src" "$tgt"; then ok=$((ok + 1)); continue; fi
  chcon --reference="$tgt" "$src" 2>>"$BL"
  if mount --bind "$src" "$tgt" 2>>"$BL"; then ok=$((ok + 1)); echo "ok $tgt" >> "$BL"
  else bad=$((bad + 1)); echo "FAIL $tgt" >> "$BL"; fi
done
echo "ok=$ok bad=$bad skip=$skip" > "$ST"
