Fox Overlay v1.2
- vendor/etc/powerhint.json = your ROM's powerhint.json + converted hints from the Qualcomm powerhint.xml.
  Original ROM hints/nodes are untouched. Original copy: powerhint.rom_original.json (inside the zip).
- Converted (new hints): QC_VIDEO_ENCODE_HFR_480 (0x1334), QC_CAMERA_OPEN (0x1337), QC_CAMERA_CLOSE (0x1338),
  QC_CAMERA_SNAPSHOT (0x1339), QC_VIDEO_DECODE_240FPS (0x1505).
- Not converted: VR/QVR levels, empty hints (0x1203/0x1206/0x1207/0x1301), PSM and decode 30/60 (only "min 10 MHz" = release),
  camera 0x1330-0x1335 (already covered by the ROM's CAMERA_STREAMING_*), legacy cpubw devfreq nodes (not on this kernel).
- Cluster map used: 0=cpu0-2, 1=cpu3-6, 2=cpu7 (from targetconfig.xml). Freq MHz x 1000 = kHz.
- The Android Power HAL only fires hint names it knows; the QC_* hints run only if something calls them by that name.
- Thermal files: unchanged. Log: /data/adb/fox_overlay/bundle.log
- v1.2: if powerhint.json had to be re-mounted after boot, the Power HAL (android.hardware.power-service*) is restarted once so it re-reads it.
