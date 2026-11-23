#!/system/bin/sh
SKIPUNZIP=0
ui_print "*******************************************"
ui_print "   Fox Overlay v1.2"
ui_print "   PowerHAL + powerhint + thermal overlay"
ui_print "*******************************************"
DEV=$(getprop ro.product.device)
ui_print "- Device: $DEV"
[ "$DEV" != "peridot" ] && ui_print "! Not a POCO F6 (peridot): files may not match"
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm_recursive "$MODPATH/bin" 0 0 0755 0755
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
ui_print "- $(find "$MODPATH/bundle" -type f | wc -l) files bundled"
ui_print "- Reboot. Log: /data/adb/fox_overlay/bundle.log"
