#!/system/bin/sh
# Profiles Tuner daemon. Jobs (each only when enabled, idle cost = a few builtins per tick):
#   1. drift watcher  (opt.watch=1): re-apply the profile if the ROM / HAL changes values behind our back
#   2. Game Space     (game.master=1): per-app touch tuning + mode override while a chosen game is foreground
#   3. THP boost for the known heavy games (RAM >= 12 GB)
# Exits by itself when nothing is wanted.
DIR=${0%/*}
. "$DIR/ptlib.sh"

if [ -f "$DATA/ptd.pid" ] && kill -0 "$(cat "$DATA/ptd.pid")" 2>/dev/null; then exit 0; fi
echo $$ > "$DATA/ptd.pid"
log "daemon started (pid $$)"
cleanup() { [ -n "$CUR" ] && game_revoke; [ "$THPGAME" = 1 ] && thp_set madvise; rm -f "$DATA/ptd.pid"; exit 0; }
trap cleanup TERM INT

rm -f "$ORIG" "$DATA/disabled" "$DATA/game_prev" "$DATA/game_active"
[ "$(opt enabled 1)" = 0 ] && : > "$DATA/disabled"
takeover
[ -f "$DATA/disabled" ] || apply_mode "$(active_mode)" >/dev/null

load_cfg() {
  WATCH=$(opt watch 0); GM=$(cget game.master)
  game_build
  [ "$GM" = 1 ] && thp_capable && THPOK=1 || THPOK=0
}
load_cfg
CUR=; THPGAME=; THPOFF=0
n=0; nd=0; tick=0
while :; do
  if [ "$GM" = 1 ]; then sleep 3; TICK=3; else sleep 5; TICK=5; fi
  if [ -f "$DATA/ptd.reload" ]; then
    rm -f "$DATA/ptd.reload"; load_cfg
    # a running game whose settings changed gets re-applied
    [ -n "$CUR" ] && { game_apply "$CUR"; }
  fi
  want_daemon || { log "daemon idle, exiting"; cleanup; }
  [ -f "$DATA/disabled" ] && continue
  if [ "$GM" = 1 ]; then
    g=$(fg_game)
    if [ -n "$g" ] && [ "$g" != "$CUR" ]; then CUR=$g; game_apply "$g"
    elif [ -z "$g" ] && [ -n "$CUR" ]; then CUR=; game_revoke
    fi
    if [ "$THPOK" = 1 ]; then
      # THP boost: 'always' while a default heavy game is in front, 'madvise' 60 s after it leaves
      d=$(fg_thp)
      if [ -n "$d" ]; then thp_set always; THPGAME=1; THPOFF=0
      elif [ "$THPGAME" = 1 ]; then
        THPOFF=$((THPOFF + TICK))
        [ "$THPOFF" -ge 60 ] && { thp_set madvise; THPGAME=; THPOFF=0; }
      fi
    fi
  fi

  [ "$WATCH" = 1 ] || continue
  n=$((n + 1))
  [ "$TICK" = 3 ] && [ $((n % 2)) = 0 ] && continue
  if [ $((n % 10)) = 0 ]; then takeover; fi
  if drift; then
    nd=$((nd + 1))
    log "drift detected ($DK), re-applying"
    apply_mode "$(active_mode)" >/dev/null
    if [ "$nd" -ge 5 ]; then
      log "something keeps overwriting these values; backing off 60 s"
      sleep 60; nd=0
    fi
  else
    nd=0
  fi
done
