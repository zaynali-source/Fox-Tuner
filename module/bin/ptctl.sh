#!/system/bin/sh
# Profiles Tuner - control API used by the WebUI and by you from a terminal.
#   ptctl.sh get | mode <m> | set <key> <val> | opt <key> <val> | apply
#            reset <m> | resetall | tmap <name> <idx> | game-remove <pkg> | touch game|super 0|1
DIR=${0%/*}
. "$DIR/ptlib.sh"

ok_key() { echo "$1" | grep -Eq '^[A-Za-z0-9._-]+$'; }
ok_val() { echo "$1" | grep -Eq '^[A-Za-z0-9._,-]*$'; }

cmd=$1; shift
case "$cmd" in
get)
  echo "active=$(active_mode)"
  echo "hw.ver=$(grep -m1 '^version=' "$DIR/../module.prop" 2>/dev/null | cut -d= -f2)"
  [ -s "$DATA/devinfo" ] || { echo "$(getprop ro.product.device 2>/dev/null)|$(getprop ro.product.model 2>/dev/null)" > "$DATA/devinfo"; }
  read -r _di < "$DATA/devinfo"
  echo "hw.device=${_di%%|*}"
  echo "hw.model=${_di#*|}"
  echo "hw.bundle=$(rd "$DATA/bundle.stat")"
  echo "hw.ovl2=$(fox_overlay_active && echo 1 || echo 0)"
  echo "hw.ulq=$([ -d "$SR/sys/kernel/ulquiorra" ] && echo 1 || echo 0)"
  echo "hw.touch=$([ -n "$(touch_node)" ] && echo 1 || echo 0)"
  echo "hw.touch.cur=$(rd "$(touch_node)")"
  echo "hw.sconfig=$([ -w "$SCONFIG" ] && echo 1 || echo 0)"
  g=$GPUDEV
  echo "hw.gpu.ok=$([ -n "$g" ] && echo 1 || echo 0)"
  if [ -n "$g" ]; then
    echo "hw.gpu.freqs=$(rd "$g/available_frequencies" | tr -s ' \n' ' ')"
    echo "hw.gpu.cur_max=$(rd "$g/max_freq")"
  fi
  CL=$(clusters)
  echo "$CL" | while read role pol; do
    d=$CPUFREQ/$pol
    fr=$(rd "$d/scaling_available_frequencies" | tr -s ' \n' ' ')
    hm=$(rd "$d/cpuinfo_max_freq")
    echo "$fr" | grep -qw "$hm" || fr="$fr$hm"
    set -- $(rd "$d/related_cpus")
    echo "hw.$role.policy=$pol"
    echo "hw.$role.cpus=$#"
    echo "hw.$role.freqs=$fr"
    echo "hw.$role.cur_max=$(rd "$d/scaling_max_freq")"
    echo "hw.$role.corectl=$([ -d "$SR/sys/devices/system/cpu/cpu$1/core_ctl" ] && echo 1 || echo 0)"
    echo "hw.$role.walt=$([ -d "$d/walt" ] && echo 1 || echo 0)"
    echo "hw.$role.boost=$([ -e "$d/walt/boost" ] && echo 1 || echo 0)"
    echo "hw.$role.tlt=$([ -e "$d/walt/target_load_thresh" ] && echo 1 || echo 0)"
  done
  echo "hw.roles=$(echo "$CL" | cut -d' ' -f1 | tr '\n' ' ')"
  echo "hw.present=$(rd "$SR/sys/devices/system/cpu/present")"
  cr=$CSROOT
  echo "hw.cs=$([ -n "$cr" ] && echo 1 || echo 0)"
  if [ -n "$cr" ]; then
    gl=""
    for d in "$cr"/*/; do
      g=${d%/}; g=${g##*/}; f=$(cs_file "$cr" "$g"); [ -n "$f" ] || continue
      gl="$gl $g"; echo "cs.cur.$g=$(rd "$f")"
    done
    echo "hw.cs.groups=$gl"
  fi
  echo "hw.tf=$([ -e "$TOUCHF" ] && echo 1 || echo 0)"
  echo "hw.thp=$(thp_capable && [ -e "$THP" ] && echo 1 || echo 0)"
  echo "game.on=$(rd "$DATA/game_active")"
  echo "svc=$([ -f "$DATA/ptd.pid" ] && kill -0 "$(cat "$DATA/ptd.pid")" 2>/dev/null && echo 1 || echo 0)"
  cat "$CONF"
  ;;
bundle-verify)
  [ "$(opt bundle 1)" = 0 ] && { echo "disabled in app"; exit 0; }
  fox_overlay_active && { echo "yielded to FoxOverlay" > "$DATA/bundle.stat"; echo "yielded to FoxOverlay"; exit 0; }
  sh "$DIR/bundle.sh" verify; [ -f "$DATA/hal_restart" ] && { rm -f "$DATA/hal_restart"; pkill -f android.hardware.power-service; }
  cat "$DATA/bundle.stat" 2>/dev/null ;;
boot)
  # called by the helper at boot: apply once, start the watcher only if enabled
  rm -f "$DATA/disabled"
  [ "$(opt enabled 1)" = 0 ] && { : > "$DATA/disabled"; exit 0; }
  rm -f "$DATA/game_prev" "$DATA/game_active"
  takeover; apply_mode "$(active_mode)" >/dev/null
  daemon_sync
  echo ok ;;
backup)
  if [ "$1" = auto ] && [ "$(opt autobackup 1)" != 1 ]; then echo skipped; exit 0; fi
  do_backup && echo ok || echo "fail" ;;
restore)
  do_restore "$1" && { [ "$(opt enabled 1)" = 0 ] && : > "$DATA/disabled" || rm -f "$DATA/disabled"; apply_mode "$(active_mode)" >/dev/null; echo ok; } || echo "no backup" ;;
backup-info)
  if [ -s "$BK" ]; then echo "path=$BK"; echo "size=$(wc -c < "$BK")"; echo "time=$(date -r "$BK" '+%d %b %Y, %H:%M' 2>/dev/null)"; else echo "none"; fi ;;
mode)
  rm -f "$DATA/game_prev"; m=$1; apply_mode "$m" && echo ok ;;
cycle)
  # one call for the QS tile: next enabled mode after the active one
  cur=$(active_mode); first=""; nx=""; found=0
  IFS=,; for m in $1; do
    [ -z "$first" ] && first=$m
    [ "$found" = 1 ] && { nx=$m; break; }
    [ "$m" = "$cur" ] && found=1
  done; unset IFS
  [ -z "$nx" ] && nx=$first
  rm -f "$DATA/game_prev"
  apply_mode "$nx" >/dev/null && echo "ok $nx" || echo fail ;;
apply)
  apply_mode "$(active_mode)" && echo ok ;;
set)
  ok_key "$1" && ok_val "$2" || { echo "bad args"; exit 1; }
  cset "$1" "$2"
  case "$1" in game.*) : > "$DATA/ptd.reload"; daemon_sync; echo ok ;; opt.*|thermal.*) echo ok ;; sec.*) apply_mode "$(active_mode)" >/dev/null; echo ok ;; *) apply_mode "$(active_mode)" >/dev/null; echo ok ;; esac
  ;;
setq)
  ok_key "$1" && ok_val "$2" || { echo "bad args"; exit 1; }
  cset "$1" "$2"
  case "$1" in game.*) [ -f "$DATA/ptd.reload" ] || : > "$DATA/ptd.reload" ;; esac
  echo ok ;;
game-sync)
  daemon_sync; echo ok ;;
game-remove)
  ok_key "$1" || { echo "bad args"; exit 1; }
  sed -i "/^game\.$1\./d" "$CONF"; : > "$DATA/ptd.reload"; echo ok ;;
touch)
  # touch game|super 0|1  (manual test switches, same nodes the ROM app uses)
  case "$1" in
    game) [ -e "$GMODE" ] && echo "$2" > "$GMODE" && echo ok || echo na ;;
    super) tn=$(touch_node); [ -n "$tn" ] && echo "$2" > "$tn" && echo ok || echo na ;;
    *) echo "bad args" ;;
  esac ;;
opt)
  ok_key "$1" && ok_val "$2" || { echo "bad args"; exit 1; }
  cset "opt.$1" "$2"
  if [ "$1" = enabled ]; then [ "$2" = 0 ] && : > "$DATA/disabled" || rm -f "$DATA/disabled"; fi
  [ "$1" = takeover ] && [ "$2" = 1 ] && takeover
  [ "$1" = watch ] && daemon_sync
  echo ok ;;
tmap)
  ok_key "$1" && ok_val "$2" || { echo "bad args"; exit 1; }
  if [ -z "$2" ]; then sed -i "/^thermal\.$1=/d" "$CONF"; else cset "thermal.$1" "$2"; fi
  apply_mode "$(active_mode)" >/dev/null
  echo ok ;;
resetcs)
  sed -i '/^cs\./d' "$CONF"; hc_all | grep '^cs\.' >> "$CONF"
  apply_mode "$(active_mode)" >/dev/null; echo ok ;;
reset)
  m=$1
  case " $MODES " in *" $m "*) ;; *) echo "bad mode"; exit 1 ;; esac
  sed -i "/^$m\./d" "$CONF"
  hc_all | grep "^$m\." >> "$CONF"
  [ "$(active_mode)" = "$m" ] && apply_mode "$m" >/dev/null
  echo ok ;;
resetall)
  sed -i -E '/^(battery|normal|performance|extreme)\./d' "$CONF"
  hc_all | grep -E '^(battery|normal|performance|extreme)\.' >> "$CONF"
  apply_mode "$(active_mode)" >/dev/null
  echo ok ;;
*)
  echo "usage: ptctl.sh get|mode|apply|boot|set|opt|tmap|reset|resetall|backup|restore|game-remove|touch"; exit 1 ;;
esac
case "$cmd" in apply|mode|cycle|reset|resetall|resetcs|tmap|opt|set)
  [ "$(opt autobackup 1)" = 1 ] && do_backup >/dev/null 2>&1 ;;
esac
