# Fox Tuner

Nothing-OS inspired tuner for Snapdragon 8s Gen 3 (POCO F6 "peridot").

- **`app/` → Fox Tuner app**: the whole UI. Profiles (Battery / Normal / Performance / Extreme), frequency threshold, cluster priority, max frequency, WALT governor tuning (hispeed_freq, hispeed_load, target_load_shift, target_load_thresh), GPU, Cpuset, thermal profile, High Touch Sampling (per profile + global), Display (resolution + DPI with 20 s auto-revert), backup / restore.
- **`module/` → Fox Tuner Helper (KernelSU / APatch)**: no UI, very light. Applies the app's settings at boot. Also overlays the PowerHAL `powerhint.json` (toggle in the app).

## Install
1. Flash `FoxTuner-helper-*.zip`, reboot.
2. Install `FoxTuner-app-*.apk`, grant root.

Both are built by GitHub Actions and published on the Releases page.

Author: Zane

- **`overlay/` → Fox Overlay (optional, separate module)**: custom thermal configs + the same `powerhint.json`. Use either its powerhint or the helper's, not both.

## v2.1: Ulquiorra-style tuner
- Profile engine now follows the ROM Parts daemon's own logic (decoded from `ulquiorrad`): rate limits `up = 250*T`, `down = 2000 + 1000*T`, load shift `base + T/5` (max 10), core_ctl priority table (0..3) with min/max/busy_up_thres, optional min-cores override, frequency boost, and the ROM's default Battery/Normal/Performance/Extreme values.
- Kept from the old tuner: GPU, CpuSet and Governor sections (the governor load shift stacks on top of the threshold, no conflict).
- Thermal list uses the ROM's real sconfig indexes (Normal 0, Huanji 1, No Limits 6, Class 0 7, AR/VR 9, Navigation 10, Video 11, Video Chat 14, Camera 15, 4K 16, Mobile Game 19, High FPS 26).
- New Game page: per-app mode override, touch tuning (swipe continuity, jitter, sensitivity, tap accuracy, palm rejection or expert preset), super report rate, THP boost on 12 GB+ devices.
- Touch test in More.
- Works without the Ulq kernel.

## v2.2: conflict audit
- Governor load shift now only stacks on the threshold's while Threshold is on (was adding it even when off).
- Helper's PowerHAL overlay yields automatically when the FoxOverlay module is active (both mount the same files); the toggle is greyed with the reason.
- Governor hispeed frequencies above the Max frequency cap are greyed out.

## v2.3
- RTG boost frequency and pl are now written only by the Governor section (ROM defaults), so Threshold never overwrites them.
- CPUs that Cluster priority parks (Minimal) are greyed out in Cpuset, with the reason shown.

## v2.4
- "Auto" now means the ROM (Ulq) value for that mode (cap, priority, min cores, hispeed, load shift, boost, GPU, threshold).
- Per-profile touch sampling is a plain Default/Boosted value written (and verified) on every profile apply. No background timer.
- Touch test reads the real MotionEvent sample rate (including batched history) instead of the frame-limited pointer events.
- Greyed-out conflicts refresh immediately when Priority / Max frequency / Governor values change.

## v2.7
- Game Space now uses an app accessibility service (event-driven, no polling) to detect the foreground game; the helper daemon steps aside while it is active. Root enables the service automatically.

## v2.8
- Profile tuning now opens from the 3-line icon on a profile (with a back button); the separate Kernel tab is removed. UI polish.

## v2.9
- Thermal list: TGame (sconfig 18). Kernel tab removed (v2.8).

## v3.1
- GameBar removed (power drain). Bottom nav now has icons.

## v3.2
- Game Space and its accessibility service removed; boot undoes anything it left (touch, THP).
