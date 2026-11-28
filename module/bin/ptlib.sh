#!/system/bin/sh
# Profiles Tuner - shared engine library (sourced by ptctl.sh / ptd.sh)
# Env overrides (offline testing only): PT_DATA, PT_SYSROOT, PT_MOD

MODDIR=${PT_MOD:-/data/adb/modules/profiles_tuner}
DATA=${PT_DATA:-/data/adb/profiles_tuner}
SR=${PT_SYSROOT:-}
CONF=$DATA/profiles.conf
LOG=$DATA/apply.log
ORIG=$DATA/orig
CPUFREQ=$SR/sys/devices/system/cpu/cpufreq
SCONFIG=$SR/sys/class/thermal/thermal_message/sconfig
MODES="battery normal performance extreme"
TOUCH_NODES="$SR/sys/devices/virtual/touch/touch_dev/bump_sample_rate $SR/sys/class/touch/touch_dev/bump_sample_rate"

# ---- HARD-CODED defaults (built into this script, no external file needed) ----
hc_all() {
cat <<'HC'
opt.enabled=1
opt.takeover=1
opt.default=normal
opt.autobackup=1
opt.bundle=1
opt.watch=0
sec.threshold=1
sec.priority=1
sec.freq=1
sec.gpu=1
sec.thermal=1
sec.touch=1
sec.thp=1
sec.gov=1
sec.cpuset=0
thermal.normal=0
thermal.huanji=1
thermal.nolimits=6
thermal.class0=7
thermal.arvr=9
thermal.navigation=10
thermal.video=11
thermal.videochat=14
thermal.camera=15
thermal.4k=16
thermal.mgame=19
thermal.highfps=26
thermal.tgame18=18
game.master=0
cs.background=0
cs.camera-daemon=0,1,2,3,4,5,7
cs.foreground=0,1,2,3,4,5
cs.foreground_window=0,1,2,3,4,6,7
cs.restricted=0,1,2,3,5,6,7
cs.surfaceflinger=0,1,2,3,4,5,7
cs.system-background=0,1
cs.top-app=0,1,2,3,4,5,6,7
battery.threshold=6
battery.touch=0
battery.thp=0
battery.thermal=normal
battery.little.prio=3
battery.little.max=1478400
battery.little.hsfreq=1113600
battery.little.hsload=90
battery.little.tls=4
battery.little.boost=0
battery.big.prio=1
battery.big.max=1824000
battery.big.hsfreq=1190400
battery.big.hsload=85
battery.big.tls=4
battery.big.boost=3
battery.prime.prio=0
battery.prime.max=1651200
battery.prime.hsfreq=1459200
battery.prime.hsload=85
battery.prime.tls=4
battery.prime.boost=3
battery.gpu.max=500000000
battery.gpu.min=255000000
normal.threshold=6
normal.touch=0
normal.thp=0
normal.thermal=normal
normal.little.prio=3
normal.little.max=2016000
normal.little.hsfreq=1113600
normal.little.hsload=90
normal.little.tls=4
normal.little.boost=3
normal.big.prio=2
normal.big.max=2188800
normal.big.hsfreq=1190400
normal.big.hsload=85
normal.big.tls=4
normal.big.boost=3
normal.big.minc=3
normal.prime.prio=2
normal.prime.max=2150400
normal.prime.hsfreq=1459200
normal.prime.hsload=85
normal.prime.tls=4
normal.prime.boost=3
normal.prime.minc=0
normal.gpu.max=835000000
normal.gpu.min=255000000
performance.threshold=3
performance.touch=1
performance.thp=1
performance.thermal=nolimits
performance.little.prio=2
performance.little.max=2016000
performance.little.hsfreq=1113600
performance.little.hsload=90
performance.little.tls=4
performance.little.boost=12
performance.big.prio=3
performance.big.max=2361600
performance.big.hsfreq=1190400
performance.big.hsload=85
performance.big.tls=4
performance.big.boost=18
performance.prime.prio=3
performance.prime.max=2668800
performance.prime.hsfreq=1459200
performance.prime.hsload=85
performance.prime.tls=4
performance.prime.boost=15
performance.gpu.max=900000000
performance.gpu.min=353000000
extreme.threshold=0
extreme.touch=1
extreme.thp=1
extreme.thermal=nolimits
extreme.little.prio=3
extreme.little.max=2016000
extreme.little.hsfreq=1113600
extreme.little.hsload=90
extreme.little.tls=4
extreme.little.boost=10
extreme.big.prio=3
extreme.big.max=2803200
extreme.big.hsfreq=1190400
extreme.big.hsload=85
extreme.big.tls=4
extreme.big.boost=15
extreme.prime.prio=3
extreme.prime.max=3014400
extreme.prime.hsfreq=1459200
extreme.prime.hsload=85
extreme.prime.tls=4
extreme.prime.boost=20
extreme.gpu.max=1100000000
extreme.gpu.min=500000000
HC
}

mkdir -p "$DATA" 2>/dev/null
[ -f "$CONF" ] || : > "$CONF"
# add any key that is missing (keeps the user's edits, fills new options after updates)
KEYS="
$(while IFS== read -r k _; do echo "$k"; done < "$CONF")
"
MISS=$(hc_all | while IFS== read k v; do case "$KEYS" in *"
$k
"*) ;; *) echo "$k=$v" ;; esac; done)
[ -n "$MISS" ] && echo "$MISS" >> "$CONF"
# migrations run once each, by level (opt.migr = highest level done)
MG=$(sed -n 's/^opt\.migr=//p' "$CONF" | tail -1); case "$MG" in ''|*[!0-9]*) MG=0 ;; esac
if [ "$MG" -lt 2 ]; then   # thermal indices follow the ROM's own list
  sed -i -e '/^thermal\./d' -e 's/\.thermal=tgame$/.thermal=mgame/' "$CONF"
  hc_all | grep '^thermal\.' >> "$CONF"
  _ok=" normal huanji nolimits class0 arvr navigation video videochat camera 4k mgame highfps tgame18 default "
  while IFS== read -r k v; do
    case "$k" in *.thermal) case "$_ok" in *" $v "*) ;; *) sed -i "s/^$k=.*/$k=normal/" "$CONF" ;; esac ;; esac
  done < "$CONF"
fi
if [ "$MG" -lt 3 ]; then   # profile defaults follow the ROM's own tuner
  sed -i -E '/^(battery|normal|performance|extreme)\./d' "$CONF"
  hc_all | grep -E '^(battery|normal|performance|extreme)\.' >> "$CONF"
fi
if [ "$MG" -lt 4 ]; then   # per-profile touch sampling is a plain Default/Boosted choice
  sed -i -E -e '/^sec\.touch=/d' -e '/^(battery|normal|performance|extreme)\.touch=/d' "$CONF"
  hc_all | grep -E '^(sec\.touch|(battery|normal|performance|extreme)\.touch)=' >> "$CONF"
fi
if [ "$MG" -lt 5 ]; then sed -i '/^opt\.migr=/d' "$CONF"; echo "opt.migr=5" >> "$CONF"; fi

log() { echo "$(date +%H:%M:%S) $*" >> "$LOG" 2>/dev/null; }
# rd/wr use shell builtins (no cat/fork) -> fast
rd() { [ -r "$1" ] || return; read -r _rdv < "$1" 2>/dev/null; echo "$_rdv"; }
SEC=
wr() {
  [ -w "$1" ] || { log "skip (not writable) $1"; return 1; }
  read -r _cur < "$1" 2>/dev/null
  [ "$_cur" = "$2" ] && return 0            # already the wanted value
  if [ -n "$SEC" ] && ! grep -qF "|$1|" "$ORIG" 2>/dev/null; then
    echo "$SEC|$1|$_cur" >> "$ORIG"
  fi
  echo "$2" > "$1" 2>/dev/null || { log "FAIL $1 <- $2"; return 1; }
  return 0
}

# ---- config helpers -------------------------------------------------------
cget() { grep -m1 "^$1=" "$CONF" 2>/dev/null | cut -d= -f2-; }
cset() {
  if grep -q "^$1=" "$CONF" 2>/dev/null; then
    sed -i "s|^$1=.*|$1=$2|" "$CONF"
  else
    echo "$1=$2" >> "$CONF"
  fi
}
opt() { v=$(cget "opt.$1"); [ -n "$v" ] && echo "$v" || echo "$2"; }
load_secs() { eval "$(grep '^sec\.' "$CONF" | sed 's/^sec\./S_/; s/=\(.*\)$/="\1"/')"; }
sec_on() { eval "_s=\${S_$1:-1}"; [ "$_s" != 0 ]; }

restore_sec() { # put back what the ROM had before we touched this section
  if [ -f "$ORIG" ]; then
    grep "^$1|" "$ORIG" | while IFS='|' read sec p v; do
      [ -n "$v" ] && [ -w "$p" ] && echo "$v" > "$p" 2>/dev/null
    done
    sed -i "/^$1|/d" "$ORIG"
  fi
  case "$1" in
    freq)   sed -i '/^policy/d' "$DATA/expected" 2>/dev/null ;;
    gpu)    sed -i '/^gpu=/d' "$DATA/expected" 2>/dev/null ;;
    cpuset) sed -i '/^cs\./d' "$DATA/expected" 2>/dev/null ;;
  esac
}
touch_node() { for n in $TOUCH_NODES; do [ -e "$n" ] && { echo "$n"; return; }; done; }

# ---- hardware discovery ---------------------------------------------------
clusters() { # prints "role policyN" ordered little -> prime
  L=$(for p in "$CPUFREQ"/policy*; do
        [ -d "$p" ] && echo "$(rd "$p/cpuinfo_max_freq") ${p##*/}"
      done | sort -n | cut -d' ' -f2)
  i=0
  for pol in $L; do
    case "$i" in 0) r=little ;; 1) r=big ;; 2) r=prime ;; *) r=extra$i ;; esac
    echo "$r $pol"
    i=$((i + 1))
  done
}
gpu_dev() {
  for c in "$SR/sys/class/kgsl/kgsl-3d0/devfreq" "$SR"/sys/devices/platform/soc/*kgsl-3d0/devfreq/*; do
    [ -d "$c" ] && [ -e "$c/max_freq" ] && { echo "$c"; return; }
  done
}

# ---- cpuset ---------------------------------------------------------------
cpuset_root() {
  for r in "$SR/dev/cpuset" "$SR/sys/fs/cgroup/cpuset" "$SR/sys/fs/cgroup"; do
    [ -d "$r/top-app" ] && { echo "$r"; return; }
  done
}
cs_file() { # root group
  [ -e "$1/$2/cpus" ] && { echo "$1/$2/cpus"; return; }
  [ -e "$1/$2/cpuset.cpus" ] && echo "$1/$2/cpuset.cpus"
}
to_ranges() { # "0,1,2,4" -> "0-2,4"
  out=""; st=""; pv=""
  for c in $(echo "$1" | tr ',' ' '); do
    if [ -z "$st" ]; then st=$c; pv=$c
    elif [ "$c" -eq $((pv + 1)) ]; then pv=$c
    else
      if [ "$st" = "$pv" ]; then out="$out${out:+,}$st"; else out="$out${out:+,}$st-$pv"; fi
      st=$c; pv=$c
    fi
  done
  if [ -n "$st" ]; then
    if [ "$st" = "$pv" ]; then out="$out${out:+,}$st"; else out="$out${out:+,}$st-$pv"; fi
  fi
  echo "$out"
}
apply_cpuset() {
  root=$CSROOT; [ -n "$root" ] || { log "cpuset root not found"; return 1; }
  for d in "$root"/*/; do
    g=${d%/}; g=${g##*/}
    f=$(cs_file "$root" "$g"); [ -n "$f" ] || continue
    v=$(cget "cs.$g"); [ -n "$v" ] || continue
    r=$(to_ranges "$v")
    wr "$f" "$r" && echo "cs.$g=$r" >> "$DATA/expected"
  done
}

GPUDEV=$(gpu_dev)
CSROOT=$(cpuset_root)

# ---- appliers ---------------------------------------------------------------
apply_cap() { # policy cap(kHz)
  d=$CPUFREQ/$1; cap=$2
  [ -d "$d" ] || return 1
  hmax=$(rd "$d/cpuinfo_max_freq")
  [ -n "$hmax" ] && [ "$cap" -gt "$hmax" ] && cap=$hmax
  cmin=$(rd "$d/scaling_min_freq")
  [ -n "$cmin" ] && [ "$cmin" -gt "$cap" ] && wr "$d/scaling_min_freq" "$cap"
  wr "$d/scaling_max_freq" "$cap"
}

apply_prio() { # policy prio(0-3) [min_cpus override]   (same table as the ROM daemon)
  d=$CPUFREQ/$1; prio=$2; ovr=$3
  set -- $(rd "$d/related_cpus"); first=$1; n=$#
  cc=$SR/sys/devices/system/cpu/cpu$first/core_ctl
  [ -d "$cc" ] || { log "core_ctl missing for cpu$first"; return 1; }
  case "$prio" in
    0) mn=0; mx=$(( n > 1 ? 1 : 0 )); busy=95 ;;
    1) mn=0; mx=$(( (n > 2 ? n : 2) - 1 )); busy=80 ;;
    2) mn=1; mx=$n; busy=70 ;;
    *) mn=$n; mx=$n; busy=60 ;;
  esac
  case "$ovr" in ''|*[!0-9]*) ;; *) mn=$ovr ;; esac
  [ "$ROLE" = little ] && [ "$mn" -lt 1 ] && mn=1
  [ "$mx" -lt "$mn" ] && mx=$mn
  [ "$mn" -gt "$n" ] && mn=$n; [ "$mx" -gt "$n" ] && mx=$n
  wr "$cc/max_cpus" "$mx"
  wr "$cc/min_cpus" "$mn"
  wr "$cc/max_cpus" "$mx"
  [ -e "$cc/busy_up_thres" ] && wr "$cc/busy_up_thres" "$busy"
}

apply_walt() { # policy role threshold   (rate limits + auto load shift)
  w=$CPUFREQ/$1/walt; t=$3
  [ -d "$w" ] || { log "walt tunables missing for $1"; return 1; }
  wr "$w/up_rate_limit_us" $((t * 250))
  wr "$w/down_rate_limit_us" $((2000 + t * 1000))
  tl=$((4 + t / 5)); [ "$tl" -gt 10 ] && tl=10
  wr "$w/target_load_shift" "$tl"
}

apply_gov() { # policy role hispeed_freq hispeed_load target_load_shift target_load_thresh
  w=$CPUFREQ/$1/walt
  [ -d "$w" ] || return 1
  case "$2" in
    little) dl=90; df=1113600 ;;
    big)    dl=85; df=1190400 ;;
    *)      dl=85; df=1459200 ;;
  esac
  hf=${3:-$df}; hl=${4:-$dl}
  case " $(rd "$CPUFREQ/$1/scaling_available_frequencies") " in *" $hf "*) wr "$w/hispeed_freq" "$hf" ;; esac
  wr "$w/hispeed_load" "$hl"
  if [ -n "$5" ]; then tl=$(( $5 + ${GT:-0} / 5 )); [ "$tl" -gt 10 ] && tl=10; [ "$tl" -lt 0 ] && tl=0; wr "$w/target_load_shift" "$tl"; fi
  [ -n "$6" ] && wr "$w/target_load_thresh" "$6"
  # rtg boost + pl belong to the governor section only (ROM [governor] defaults), so Threshold never fights it
  wr "$w/rtg_boost_freq" "${rtg:-$( [ "$2" = little ] && echo 595200 || echo 0 )}"
  wr "$w/pl" 0
}

apply_gpu() { # max(Hz) min(Hz)
  g=$GPUDEV; [ -n "$g" ] || { log "gpu devfreq missing"; return 1; }
  gmax=$1; gmin=$2
  [ -n "$gmax" ] && [ -n "$gmin" ] || return 1
  [ "$gmin" -gt "$gmax" ] && gmin=$gmax
  lo=$(rd "$g/available_frequencies" | tr ' ' '\n' | grep . | sort -n | head -1)
  [ -n "$lo" ] && wr "$g/min_freq" "$lo"
  wr "$g/max_freq" "$gmax"
  wr "$g/min_freq" "$gmin"
  k=$(dirname "$(dirname "$g")")/kgsl/kgsl-3d0
  [ -e "$k/max_gpuclk" ] || k=$SR/sys/class/kgsl/kgsl-3d0
  [ -e "$k/max_gpuclk" ] && wr "$k/max_gpuclk" "$gmax"
}

thermal_idx() { [ "$1" = default ] && return; case "$1" in tgame18) echo 18 ;; *) cget "thermal.$1" ;; esac; }

fox_overlay_active() { m=${PT_MODS:-/data/adb/modules}/fox_overlay; [ -d "$m" ] && [ ! -e "$m/disable" ] && [ ! -e "$m/remove" ]; }

takeover() {
  [ "$(opt takeover 1)" = 1 ] || return
  command -v getprop >/dev/null 2>&1 || return
  if [ "$(getprop init.svc.vendor.ulquiorrad 2>/dev/null)" = running ]; then
    setprop ctl.stop vendor.ulquiorrad 2>/dev/null
    log "stopped ROM ulquiorrad (takeover)"
  fi
}

apply_mode() { # mode
  m=$1
  case " $MODES " in *" $m "*) ;; *) echo "bad mode"; return 1 ;; esac
  load_secs
  eval "$(grep "^$m\." "$CONF" | sed "s/^$m\.//; s/\./_/g; s/=\(.*\)\$/=\"\1\"/; s/^/M_/")"
  : > "$DATA/expected"
  eval "$(hc_all | grep "^$m\." | sed "s/^$m\.//; s/\./_/g; s/=\(.*\)\$/=\"\1\"/; s/^/D_/")"
  [ -n "$M_threshold" ] || M_threshold=$D_threshold
  [ -n "$M_gpu_max" ] || M_gpu_max=$D_gpu_max
  [ -n "$M_gpu_min" ] || M_gpu_min=$D_gpu_min
  t=${M_threshold:-0}
  # the governor's own load shift stacks on the threshold's, so it only counts while Threshold is on
  GT=$t; sec_on threshold || GT=0
  clusters | while read role pol; do
    eval "cap=\$M_${role}_max; prio=\$M_${role}_prio; hsf=\$M_${role}_hsfreq; hsl=\$M_${role}_hsload; tls=\$M_${role}_tls; tlt=\$M_${role}_tlt; minc=\$M_${role}_minc; rtg=\$M_${role}_rtg; bst=\$M_${role}_boost"
    ROLE=$role
    # "Auto" (empty) = the ROM's own value for this mode
    for _f in max:cap prio:prio hsfreq:hsf hsload:hsl tls:tls minc:minc boost:bst; do
      eval "[ -n \"\$${_f#*:}\" ] || ${_f#*:}=\$D_${role}_${_f%%:*}"
    done
    # the ROM daemon first makes sure the walt governor is active ([governor] name=walt)
    if sec_on threshold || sec_on gov; then
      case " $(rd "$CPUFREQ/$pol/scaling_available_governors") " in *" walt "*) [ "$(rd "$CPUFREQ/$pol/scaling_governor")" = walt ] || { SEC=gov; wr "$CPUFREQ/$pol/scaling_governor" walt; } ;; esac
    fi
    if sec_on freq && [ -n "$cap" ]; then
      SEC=freq; apply_cap "$pol" "$cap" && echo "$pol=$cap" >> "$DATA/expected"
    fi
    if sec_on priority && [ -n "$prio" ]; then SEC=priority; apply_prio "$pol" "$prio" "$minc"; fi
    if sec_on threshold; then SEC=threshold; apply_walt "$pol" "$role" "$t"; fi
    if sec_on gov; then SEC=gov; apply_gov "$pol" "$role" "$hsf" "$hsl" "$tls" "$tlt"; [ -n "$bst" ] && [ -e "$CPUFREQ/$pol/walt/boost" ] && wr "$CPUFREQ/$pol/walt/boost" "$bst"; fi
  done
  sec_on gov || restore_sec gov
  sec_on freq || restore_sec freq
  sec_on priority || restore_sec priority
  sec_on threshold || restore_sec threshold
  if sec_on gpu; then
    SEC=gpu
    apply_gpu "$M_gpu_max" "$M_gpu_min" && echo "gpu=$M_gpu_max" >> "$DATA/expected"
  else restore_sec gpu; fi
  if sec_on thermal; then
    SEC=thermal
    ix=$(thermal_idx "$M_thermal")
    if [ -n "$ix" ]; then wr "$SCONFIG" "$ix"
    elif [ -n "$M_thermal" ] && [ "$M_thermal" != default ]; then log "thermal '$M_thermal' has no sconfig index"; fi
  else restore_sec thermal; fi
  tn=$(touch_node)
  if [ -n "$tn" ] && sec_on touch; then
    SEC=touch; tv=${M_touch:-0}; echo "$tv" > "$DATA/touch_want"
    wr "$tn" "$tv"; [ "$(rd "$tn")" = "$tv" ] || { sleep 1; echo "$tv" > "$tn" 2>/dev/null; }
  else rm -f "$DATA/touch_want"; fi
  if sec_on thp && [ -w "$THP" ]; then   # one write per profile switch, only if it differs (no polling)
    want=madvise; [ "${M_thp:-0}" = 1 ] && want=always
    case "$(cat "$THP" 2>/dev/null)" in *"[$want]"*) ;; *) echo "$want" > "$THP" 2>/dev/null ;; esac
  fi
  if sec_on cpuset && [ "$S_cpuset" = 1 ]; then SEC=cpuset; apply_cpuset; else restore_sec cpuset; fi
  SEC=
  echo "$m" > "$DATA/active"
  return 0
}

active_mode() {
  a=$(rd "$DATA/active"); [ -n "$a" ] || a=$(opt default normal)
  case " $MODES " in *" $a "*) echo "$a" ;; *) echo normal ;; esac
}

drift() { # 0 when something differs from the last applied state (builtins only, no forks)
  [ -s "$DATA/expected" ] || return 1
  while IFS== read k v; do
    c=
    case "$k" in
      gpu) [ -n "$GPUDEV" ] && { read -r c < "$GPUDEV/max_freq"; [ "$c" != "$v" ] && { DK="$k now=$c want=$v"; return 0; }; } ;;
      cs.*) g=${k#cs.}; f=$CSROOT/$g/cpus; [ -e "$f" ] || f=$CSROOT/$g/cpuset.cpus
            [ -e "$f" ] && { read -r c < "$f"; [ "$c" != "$v" ] && { DK="$k now=$c want=$v"; return 0; }; } ;;
      *) read -r c < "$CPUFREQ/$k/scaling_max_freq"; [ "$c" != "$v" ] && { DK="$k now=$c want=$v"; return 0; } ;;
    esac
  done < "$DATA/expected"
  return 1
}

# ---- backup / restore (kept on internal storage so it survives a ROM change) ----
BK_DIR=${PT_BK:-/sdcard/ProfilesTuner}
BK=$BK_DIR/profiles_tuner_backup.conf
do_backup() {
  mkdir -p "$BK_DIR" 2>/dev/null || return 1
  { echo "# Profiles Tuner backup"; echo "active=$(active_mode)"; cat "$CONF"; } > "$BK.tmp" 2>/dev/null && mv -f "$BK.tmp" "$BK"
}
do_restore() { # $1 = file (default: standard backup)
  f=${1:-$BK}
  [ -s "$f" ] || return 1
  grep -E '^[A-Za-z0-9._-]+=[A-Za-z0-9._,-]*$' "$f" > "$DATA/restore.tmp" || return 1
  a=$(grep -m1 '^active=' "$DATA/restore.tmp" | cut -d= -f2)
  grep -v '^active=' "$DATA/restore.tmp" > "$CONF"; rm -f "$DATA/restore.tmp"
  case " $MODES " in *" $a "*) echo "$a" > "$DATA/active" ;; esac
  # fill anything the backup did not contain with the built-in defaults
  hc_all | while IFS== read k v; do grep -q "^$k=" "$CONF" || echo "$k=$v" >> "$CONF"; done
  return 0
}

. "$DIR/ptext.sh"
