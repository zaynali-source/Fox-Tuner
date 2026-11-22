# Fox Tuner · Profiles Tuner v1.1 Stable

Battery / Normal / Performance / Extreme profile tuner for Snapdragon 8s Gen 3 (POCO F6 "peridot"), Nothing-OS inspired UI.

- **`module/`**: KernelSU / APatch module (engine + WebUI + bundled PowerHAL / thermal configs).
- **`app/`**: standalone Android app. It shows the same UI (`module/webroot/index.html` is the single source) and talks to the module through root.

## Features
Frequency threshold, per-profile WALT governor tuning (hispeed_freq, hispeed_load, target_load_shift, target_load_thresh), cluster priority, per-cluster max frequency (full frequency tables), GPU min/max, thermal profile, Cpuset per cgroup, High Touch Sampling (per profile and global), per-section toggles, backup / restore to `/sdcard/ProfilesTuner`.

The app additionally has a **Display** tab: resolution and DPI changer with a 20 s auto-revert safety.

## Install
1. Flash `ProfilesTuner-module-*.zip` in KernelSU / APatch, reboot.
2. Open the WebUI from the manager, or install `ProfilesTuner-app-*.apk` and grant root.

Both are built by GitHub Actions on every push (Actions → latest run → Artifacts; tagged `v*` builds also create a Release).

Author: Zane
