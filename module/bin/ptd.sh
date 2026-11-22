#!/system/bin/sh
# Profiles Tuner daemon: re-applies the active profile if the ROM / perf HAL / thermal
# engine changes CPU, GPU or cpuset values behind our back. Idle cost: a few shell builtins / 5 s.
DIR=${0%/*}
. "$DIR/ptlib.sh"

if [ -f "$DATA/ptd.pid" ] && kill -0 "$(cat "$DATA/ptd.pid")" 2>/dev/null; then exit 0; fi
echo $$ > "$DATA/ptd.pid"
log "daemon started (pid $$)"

rm -f "$ORIG" "$DATA/disabled"
[ "$(opt enabled 1)" = 0 ] && : > "$DATA/disabled"
takeover
[ -f "$DATA/disabled" ] || apply_mode "$(active_mode)" >/dev/null

n=0
while :; do
  sleep 5
  [ -f "$DATA/disabled" ] && continue
  n=$((n + 1))
  if [ $((n % 6)) = 0 ]; then takeover; fi
  if drift; then
    log "drift detected, re-applying"
    apply_mode "$(active_mode)" >/dev/null
  fi
done
