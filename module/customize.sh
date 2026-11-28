#!/system/bin/sh
SKIPUNZIP=0
DATA=/data/adb/profiles_tuner

ui_print "*******************************************"
ui_print "   Fox Tuner Helper v3.4"
ui_print "   Apply helper + PowerHAL powerhint overlay"
ui_print "*******************************************"

DEV=$(getprop ro.product.device)
ui_print "- Device: $DEV ($(getprop ro.product.model))"
[ "$DEV" != "peridot" ] && ui_print "! Not a POCO F6 (peridot): frequency chips will auto-adapt"

mkdir -p "$DATA"
if [ -f "$DATA/profiles.conf" ]; then
  ui_print "- Keeping your existing profiles"
else
  : > "$DATA/profiles.conf"
  BKF=/sdcard/ProfilesTuner/profiles_tuner_backup.conf
  if [ -s "$BKF" ] && grep -Eq '^[a-z0-9._-]+=' "$BKF"; then
    grep -E '^[a-z0-9._-]+=[a-z0-9._,-]*$' "$BKF" | grep -v '^active=' > "$DATA/profiles.conf"
    A=$(grep -m1 '^active=' "$BKF" | cut -d= -f2); [ -n "$A" ] && echo "$A" > "$DATA/active"
    ui_print "- Restored your settings from /sdcard/ProfilesTuner backup"
  else
    ui_print "- Built-in default profiles will be created on first run"
  fi
fi
rm -f "$DATA/boot_guard" "$DATA/ptd.pid" "$DATA/bundle.log" "$DATA/bundle.stat"

set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm_recursive "$MODPATH/bin" 0 0 0755 0755
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755

ui_print "- PowerHAL powerhint.json bundled (systemless, original untouched)"
ui_print "- Reboot, then open the Fox Tuner app (this module has no UI)"
