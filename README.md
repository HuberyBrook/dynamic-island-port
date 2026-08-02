# Dynamic Island Port — A16 to A15

LSPosed module that backports A16 Dynamic Island (超级岛/灵动岛) enhancements to A15 SystemUI.

## What's different in A16

| Area | A15 | A16 |
|---|---|---|
| Freeform window from island | `supportFreeFormAnim=false` | `media_island_support_freeform=true` |
| Live notification updates | missing | `island_support_liveupdate=true` |
| IslandStretchAnimation fields | 13 fields, 5 methods | 23 fields, 10 methods |
| Pad clock blur | none | blur + scale animation |
| Notification icon shift | basic | pad-aware translation |
| DynamicIslandController deps | 13 params | 19 params |
| Media island architecture | direct class | interface + impl |
| Blur/expand listeners | none | 3 new listeners |

## What this module does

1. **FeatureFlagEnabler** — Force-enables `support_dynamic_island_blur`, `support_dynamic_island_middle`, freeform, and liveupdate flags
2. **IslandStretchEnhancer** — Adds pad clock blur animation and notification translation from A16
3. **DynamicIslandEnhancer** — Wires missing DI dependencies into DynamicIslandController

## Build

### GitHub Actions (recommended)

Push to `main` or trigger `workflow_dispatch`. The APK will be available as an artifact.

### Local

```
# Prerequisites: Android SDK 35, JDK 17
./gradlew assembleRelease
```

## Install

1. Install the APK (no launcher icon — it's an Xposed module)
2. Open LSPosed Manager → Modules → Enable "Dynamic Island Port"
3. Check scope: `com.android.systemui` (System UI)
4. Check "System Framework" if available
5. Reboot or soft-restart SystemUI

## Verify

After reboot, check LSPosed logs for:
- `FeatureFlagEnabler: settings flags written`
- `IslandStretchEnhancer: hooks applied`
- `DynamicIslandEnhancer: hooks applied`

## Limitations

- Pad clock blur requires the device to have a MiuiClock view in status bar — phone-only devices will see no visual change from IslandStretchEnhancer
- Some A16 features depend on framework.jar changes not present in A15 — those cannot be ported without modifying the framework
- This module only hooks Java/Kotlin code; native code or resources in the island plugin APK are not affected
- The island plugin (DynamicIslandContent provider) is a separate APK — updating that may also be needed for full A16 parity

## How it works

```
LSPosed loads HookEntry
  └─ handleLoadPackage("com.android.systemui")
       ├─ FeatureFlagEnabler.hook()
       │    ├─ Hook DynamicIslandController.start() → write Settings.Global flags
       │    ├─ Hook DynamicFeatureConfig getters → force true
       │    └─ Hook freeform controller constructors
       ├─ IslandStretchEnhancer.hook()
       │    ├─ Hook initMiuiViewsOnViewCreated → find padClockView
       │    ├─ Hook onIslandStatusChanged → add pad blur animation
       │    └─ Hook setTranslationX → add notif translation
       └─ DynamicIslandEnhancer.hook()
            ├─ Hook constructor → inject StatusBarDelegate
            ├─ Hook start() → call updateIslandDimenData
            └─ Hook hasCustomFocusView → return true
```

## Related

- SuperIslandLyric: Xposed module that injects lyrics into Dynamic Island
- The island UI is provided by a separate plugin APK loaded by DynamicIslandPluginController
