#!/system/bin/sh
# Fox Tuner - extra features decoded from the ROM's own Parts app (parts.ulquiorra.clover):
#   Game Space (per-app touch tuning + mode override), touch filters, THP boost.
# Sourced by ptlib.sh. Nodes are the same ones the ROM app writes.

TDEV=$SR/sys/devices/virtual/touch/touch_dev
TOUCHF=$TDEV/touch_filters
GMODE=$TDEV/game_mode
THP=$SR/sys/kernel/mm/transparent_hugepage/enabled
# games the ROM knows about (also the ones that get the THP boost)
GAMEPKGS="com.wanmei.hotta.wm com.levelinfinite.dfm com.miHoYo.GenshinImpact com.miHoYo.Yuanshen com.HoYoverse.hkrpgoversea com.miHoYo.hkrpg com.HoYoverse.Nap com.miHoYo.Nap com.miHoYo.bh3oversea com.miHoYo.bh3global com.kurogame.wutheringwaves.global com.kurogame.kjw.kuro com.kurogame.wutheringwaves.bilibili com.activision.callofduty.warzone com.carxtech.sr com.levelinfinite.hotta.gp"

# ---- touch (touch_filters / game_mode / bump_sample_rate) ----------------------
# touch_filters: "6 <level>" (pre-tuned preset 1..3) or "<up> <tol> <aim> <tap> <edge> <orientation>"
touch_ok() { [ -e "$TOUCHF" ]; }
touch_commit() { # gameMode expert level up tol aim tap edge orient
  [ -e "$TOUCHF" ] || return 1
  if [ "$1" != 1 ]; then [ -e "$GMODE" ] && echo 0 > "$GMODE" 2>/dev/null; return 0; fi
  if [ "$2" = 1 ]; then echo "6 $3" > "$TOUCHF" 2>/dev/null
  else echo "$4 $5 $6 $7 $8 $9" > "$TOUCHF" 2>/dev/null; fi
  [ -e "$GMODE" ] && echo 1 > "$GMODE" 2>/dev/null
  return 0
}
cur_rotation() { # 0..3 (Display.getRotation); 0 when unknown
  r=$(dumpsys display 2>/dev/null | grep -m1 -o 'orientation=[0-3]')
  echo "${r#orientation=}" | grep -E -o '^[0-3]' || echo 0
}

# ---- Game Space ------------------------------------------------------------------
# per-app keys: game.<pkg>.en | mode (follow|battery|normal|performance|extreme) | super | expert | xpre | t2 t3 t4 t5 t7
gcfg() { v=$(cget "game.$1.$2"); [ -n "$v" ] && echo "$v" || echo "$3"; }
GAME_ALT=
game_build() { # one pass over the config -> regex of enabled apps
  GAME_ALT=
  while IFS== read -r k v; do
    case "$k" in game.*.en) [ "$v" = 1 ] && { p=${k#game.}; p=${p%.en}; GAME_ALT="$GAME_ALT${GAME_ALT:+|}$p"; } ;; esac
  done < "$CONF"
}
fg_match() { # $1 = regex of package names; prints the one sitting in the top-app cpuset (foreground)
  [ -n "$1" ] && [ -n "$CSROOT" ] || return
  f=$CSROOT/top-app/cgroup.procs; [ -r "$f" ] || f=$CSROOT/top-app/tasks
  [ -r "$f" ] || return
  files=
  for p in $(cat "$f" 2>/dev/null); do [ -r "$SR/proc/$p/cmdline" ] && files="$files $SR/proc/$p/cmdline"; done
  [ -n "$files" ] || return
  grep -a -o -E "^($1)" $files 2>/dev/null | head -1 | cut -d: -f2- | tr -d '\000'
}
fg_game() { fg_match "$GAME_ALT"; }
fg_thp() { fg_match "$THP_ALT"; }
THP_ALT=$(echo "$GAMEPKGS" | tr ' ' '|')
game_apply() { # pkg
  p=$1
  mode=$(gcfg "$p" mode follow); sup=$(gcfg "$p" super 1); ex=$(gcfg "$p" expert 0); xp=$(gcfg "$p" xpre 1)
  t2=$(gcfg "$p" t2 3); t3=$(gcfg "$p" t3 3); t4=$(gcfg "$p" t4 3); t5=$(gcfg "$p" t5 3); t7=$(gcfg "$p" t7 2)
  case "$mode" in
    battery|normal|performance|extreme)
      [ -f "$DATA/game_prev" ] || echo "$(active_mode)" > "$DATA/game_prev"
      apply_mode "$mode" >/dev/null ;;
  esac
  touch_commit 1 "$ex" "$xp" "$t2" "$t3" "$t4" "$t5" "$t7" "$(cur_rotation)"
  tn=$(touch_node)
  if [ -n "$tn" ]; then
    [ -f "$DATA/game_bump" ] || rd "$tn" > "$DATA/game_bump"
    echo "$sup" > "$tn" 2>/dev/null
  fi
  echo "$p" > "$DATA/game_active"
  log "game on: $p (mode=$mode)"
}
game_revoke() {
  touch_commit 0
  rm -f "$DATA/game_active"
  if [ -f "$DATA/game_bump" ]; then tn=$(touch_node); [ -n "$tn" ] && echo "$(cat "$DATA/game_bump")" > "$tn"; rm -f "$DATA/game_bump"; fi
  if [ -f "$DATA/game_prev" ]; then
    read -r pm < "$DATA/game_prev"; rm -f "$DATA/game_prev"
    apply_mode "$pm" >/dev/null
  else
    apply_mode "$(active_mode)" >/dev/null   # puts the profile's own touch rate back
  fi
  log "game off"
}
thp_capable() {
  [ "$(getprop vendor.ulquiorra.ram_ge_12gb 2>/dev/null)" = 1 ] && return 0
  while read -r k v _; do [ "$k" = "MemTotal:" ] && { [ "$v" -ge 11000000 ]; return; }; done < "$SR/proc/meminfo"
  return 1
}
thp_set() { [ -w "$THP" ] && echo "$1" > "$THP" 2>/dev/null; }
is_default_game() { case " $GAMEPKGS " in *" $1 "*) return 0 ;; esac; return 1; }

# ---- background daemon is needed when any of these is on ----------------------------
want_daemon() {
  [ "$(cget opt.watch)" = 1 ] && return 0
  [ "$(cget game.master)" = 1 ] && return 0
  return 1
}
daemon_up() { [ -f "$DATA/ptd.pid" ] && kill -0 "$(cat "$DATA/ptd.pid")" 2>/dev/null; }
daemon_sync() {
  if want_daemon; then
    if daemon_up; then : > "$DATA/ptd.reload"; else (sh "$DIR/ptd.sh" >/dev/null 2>&1 &); fi
  else
    daemon_up && { kill "$(cat "$DATA/ptd.pid")" 2>/dev/null; rm -f "$DATA/ptd.pid"; }
  fi
}
