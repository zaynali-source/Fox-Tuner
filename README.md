# Fox Tuner

Nothing-OS inspired tuner for Snapdragon 8s Gen 3 (POCO F6 "peridot").

- **`app/` → Fox Tuner app**: the whole UI. Profiles (Battery / Normal / Performance / Extreme), frequency threshold, cluster priority, max frequency, WALT governor tuning (hispeed_freq, hispeed_load, target_load_shift, target_load_thresh), GPU, Cpuset, thermal profile, High Touch Sampling (per profile + global), Display (resolution + DPI with 20 s auto-revert), backup / restore.
- **`module/` → Fox Tuner Helper (KernelSU / APatch)**: no UI, very light. Applies the app's settings at boot PowerHAL, powerhint and custom thermal files are not part of it (they live in the separate Fox Overlay module).

## Install
1. Flash `FoxTuner-helper-*.zip`, reboot.
2. Install `FoxTuner-app-*.apk`, grant root.

Both are built by GitHub Actions and published on the Releases page.

Author: Zane
