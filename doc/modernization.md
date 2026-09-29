# Coxswain / rowdidow — Android 14/15 Modernization

**Status**: Source edits complete, **not yet build-verified** (JDK/SDK env pending).  
**Scope**: Play-ready modernization (targetSdk 35, minSdk 24) — keep Java/Views architecture; out-of-scope: Compose rewrite, Health Connect, full edge-to-edge opt-in.
---

## 1. Environment Baseline & Deferred Setup

**Current**: JRE 8 only, no Android SDK.  
**Workspace has**: `.local/jdk-27/` (JDK 27 binary).

**Required to build**:
```powershell
# 1. Install cmdline-tools (if not using Android Studio)
# 2. Accept licenses
sdkmanager --licenses
# 3. Install platform & build-tools
sdkmanager "platforms;android-35" "build-tools;34.0.0" "platform-tools"
# 4. Write local.properties
echo "sdk.dir=%LOCALAPPDATA%\Android\Sdk" > local.properties
# 5. Set JAVA_HOME to .local/jdk-27 (or JDK 17+)
$env:JAVA_HOME = "H:\Clouds\Github\rowdidow\.local\jdk-27"
# 6. Build
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

---

## 2. Build System Changes (Done)

| File | Change |
|------|--------|
| `build.gradle` (root) | AGP 8.5.2 via plugins DSL, removed `jcenter`, removed GitHub-release plugin (`co.riiid`) |
| `settings.gradle` | `pluginManagement` + `dependencyResolutionManagement` with `FAIL_ON_PROJECT_REPOS`, repo list (Google, MavenCentral, JitPack), `rootProject.name = "coxswain"` |
| `gradle.properties` | Added `android.nonTransitiveRClass=true` |
| `app/build.gradle` | `namespace`, `compileSdk 35`, `minSdk 24`, `targetSdk 35`, Java 17, `proguard-android-optimize.txt`, deps bumped (Material 1.12.0, Fit/Auth 21.1.0/21.0.0, Robolectric 4.11.1, recyclerview 1.3.2, constraintlayout 2.1.4, appcompat 1.7.0, localbroadcastmanager 1.1.0, preference 1.2.1) |
| `propoid/*/build.gradle` (5 modules) | AGP 8 namespace DSL, `compileSdk 35`, `minSdk 24`, Java 17, `consumerProguardFiles` |

---

## 3. Propoid Sibling Library

Cloned to `H:\Clouds\Github\propoid/` (5 modules: core, db, ui, util, validation).  
All `AndroidManifest.xml`: **removed `package=` attribute** (AGP 8 requirement — namespace comes from `build.gradle`).  
All `proguard-rules.pro` files present for `consumerProguardFiles`.
---

## 4. Manifest & Permissions

### `app/src/main/AndroidManifest.xml`

| Permission | API guard | Rationale |
|------------|-----------|-----------|
| `POST_NOTIFICATIONS` | — | Android 13+ notification permission |
| `VIBRATE` | — | Kept |
| `FOREGROUND_SERVICE` | — | Base FGS permission |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | — | Replaces `location` for BT rower/HR (Android 14+) |
| `ACCESS_COARSE_LOCATION` | `maxSdkVersion="30"` | Legacy BT scanning (API ≤ 30) |
| `ACCESS_FINE_LOCATION` | `maxSdkVersion="30"` | Legacy BT scanning (API ≤ 30) |
| *(removed duplicate unrestricted `ACCESS_FINE_LOCATION`)* | — | Not needed: `BLUETOOTH_SCAN` declared `neverForLocation` |
| `WRITE_EXTERNAL_STORAGE` | `maxSdkVersion="32"` | Scoped storage from API 33 |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` | `maxSdkVersion="30"` | Legacy |
| `BLUETOOTH_SCAN` | `usesPermissionFlags="neverForLocation"` | Android 12+ BLE scan without location |
| `BLUETOOTH_CONNECT` | — | Android 12+ BT connect |
| `BODY_SENSORS` | — | HR sensor |
| `READ_CALENDAR` / `WRITE_CALENDAR` | `uses-permission-sdk-23` | Calendar export |

### Component `exported` flags
All `<activity>`, `<service>`, `<receiver>`: explicit `android:exported="true/false"`.  
`GymService`: `android:foregroundServiceType="connectedDevice"` (was `location`).  
`CompactService`: `android:permission="android.permission.BIND_JOB_SERVICE"` unchanged.  
`FileProvider` paths: added `<files-path name="internal_files" path="."/>` alongside existing `<external-files-path>`.
---

## 5. Runtime Code Changes by Area

### 5.1 Scoped Storage & FileProvider
- `Coxswain.getExternalFilesDir()` → `context.getExternalFilesDir(null)` with fallback to `context.getFilesDir()`.
- Removed `Environment.getExternalStoragePublicDirectory()`, `dir.setReadable(true,false)`, `Intent.ACTION_MEDIA_SCANNER_SCAN_FILE` with `Uri.fromFile()`.
- Replaced with `MediaScannerConnection.scanFile(context, new String[]{path}, null, null)`.
- `file_provider_paths.xml`: added `<files-path name="internal_files" path="."/>`.

### 5.2 `Compat` helper (`app/src/main/java/svenmeier/coxswain/util/Compat.java`)
```java
// PendingIntent immutability (API 31+)
pendingIntentFlags(base) -> base | FLAG_IMMUTABLE

// Receiver registration with export flag (API 33+)
registerReceiver(ctx, receiver, filter) -> RECEIVER_NOT_EXPORTED

// Typed parcelable extra (API 33+)
usbDeviceExtra(intent) -> getParcelableExtra(EXTRA_DEVICE, UsbDevice.class)
```

### 5.3 Bluetooth Permission Flow (Rower + Heart)
- MinSdk 24 → removed JellyBean MR2 checks.
- `Permissions.open()` requests `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` on API 31+, else `ACCESS_FINE_LOCATION`.
- `LocationServices.isRequired()` returns `false` on API 31+ (BLE scan no longer needs location when `neverForLocation` declared).
- Adapter enable logic guarded: `hasConnectPermission()` checks `BLUETOOTH_CONNECT` grant; `isEnabled(adapter)` wraps `SecurityException`; `adapter.enable()` tried only when permission held, catch `SecurityException` (Android 13+ no-op). `STATE_ON` receiver proceeds when user enables BT manually.

### 5.4 Activity Result API Migration
- `MainActivity`: `REQUEST_IMPORT` → `registerForActivityResult(ActivityResultContracts.StartActivityForResult())`.
- `SettingsFragment`: `requestCode` map → single `ResultPreference pendingResult` + one `ActivityResultLauncher`.
- `BindingDialogFragment`: `DialogFragment` → `androidx.fragment.app.DialogFragment`; `getFragmentManager()` → `getSupportFragmentManager()`.
- Removed `onActivityResult()` overrides in `MainActivity`, `SettingsFragment`.

### 5.5 Notification Permission
`MainActivity.onResume()` requests `POST_NOTIFICATIONS` on API 33+ if not granted.

### 5.6 Foreground Service Fix (`GymService`)
- `onStartCommand()` → `ensureForeground()` **first** (basic "Connecting…" notification, same ID/type `connectedDevice`).
- If `startRowing()` fails → `stopForeground(true); stopSelf()`.
- `Foreground` inner class updates same notification when rower connects / progress changes.
- All `PendingIntent` calls use `Compat.pendingIntentFlags()`.

### 5.7 `PermissionBlock` Ordering Bug Fix
`PermissionActivity.start()` could broadcast rejection **synchronously** (background-activity-start denial) before `PermissionBlock` registered its receiver. Split into:
```java
IntentFilter filter = PermissionActivity.filter(); // static, no side effects
receiver = ...; registerReceiver(receiver, filter);
PermissionActivity.start(ctx, permissions); // now safe
```

### 5.8 `BluetoothActivity` (device picker)
- `IntentReceiver.register()` → `Compat.registerReceiver()` (filter contains app-specific `ACTION_CANCEL`, throws `SecurityException` on API 34+ without flag).
- `NewScanning.start()` wrapped in try/catch: `SecurityException` (denied `BLUETOOTH_SCAN`), NPE (BT off → no scanner).
- Removed `OldScanning` (`startLeScan`) — dead code since minSdk 24 > Lollipop.
- Removed unused `UUID` import.

### 5.9 Exports (TCX / Program)
- `TcxExport`, `ProgramExport`: `WRITE_EXTERNAL_STORAGE` only requested on API ≤ 28 (`Build.VERSION_CODES.P`). API 29+ uses app-specific dir → no permission.
- `MediaScannerConnection.scanFile()` replaces deprecated broadcast.

### 5.10 Cleanups
- Deleted `app/src/androidTest/.../ApplicationTest.java` (uses removed `android.test.ApplicationTestCase`).
- ANT AAR (`antpluginlib_3-8-0.aar`) manifest inspected: no components with intent-filters → no `exported` overrides needed.
---

## 6. This Session's Fixes (A1–A9)

| ID | Area | Summary |
|----|------|---------|
| A1 | Propoid manifests | Removed `package=` from 5 modules |
| A2 | App manifest | Dropped duplicate unrestricted `ACCESS_FINE_LOCATION`, removed unused `FOREGROUND_SERVICE_HEALTH` |
| A3 | `PermissionBlock` | Register receiver **before** starting `PermissionActivity` |
| A4 | `BluetoothHeart` | Collapsed triple-`proceed()` into guarded enable flow; added `bluetooth_enable` string |
| A5 | `BluetoothActivity` | `Compat.registerReceiver`, scan try/catch, removed `OldScanning`, removed `UUID` import |
| A6 | `GymService` | `ensureForeground()` first, `stopForeground/stopSelf` on failure |
| A7 | AndroidTest | Deleted obsolete `ApplicationTest` |
| A8 | ANT AAR | Verified — no manifest overrides needed |
| A9 | Static sweep | No remaining `startLeScan`, `getFragmentManager`, `onActivityResult`, un-flagged `registerReceiver`, single-arg `getParcelableExtra` in new paths, `Criteria` |

---

## 7. Known Limitations & Risks

| Risk | Mitigation / Note |
|------|-------------------|
| **Not build-verified** | No JDK 17 / Android SDK on this machine yet. |
| Android 13+ `adapter.enable()` no-op | Handled: try/catch + `STATE_ON` receiver proceeds once user enables BT. |
| `connectedDevice` FGS prerequisites | `GymService` starts before BT permissions granted → **latent `SecurityException` on API 34+**. Fix: request `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` in `MainActivity` *before* `GymService.start()`. |
| Google Fit API | Deprecated upstream; export kept but untested. |
| Edge-to-edge / targetSdk 35 UI | `WorkoutActivity` uses `FLAG_TURN_SCREEN_ON \| FLAG_SHOW_WHEN_LOCKED \| FLAG_DISMISS_KEYGUARD` + `setSystemUiVisibility` — may need `WindowCompat.setDecorFitsSystemWindows(false)` + insets handling for Android 15. |
| Legacy public-dir workout DB | Users with `preference_data_external=true` on API 29 had DB at `Environment.getExternalStoragePublicDirectory("coxswain")`; not migrated to `getExternalFilesDir()`. |
| `FLAG_TURN_SCREEN_ON` etc. deprecated | `WorkoutActivity` should migrate to `WindowManager.LayoutParams` + `setTurnScreenOn` / `setShowWhenLocked` APIs. |

---

## 8. Verification Status

| Check | Status |
|-------|--------|
| Gradle config syntax | ✅ Edits applied |
| Manifest well-formed | ✅ Edits applied |
| Java syntax (edited files) | ✅ Static reads OK |
| `gradlew :app:assembleDebug` | ❌ **Pending env** |
| `gradlew :app:testDebugUnitTest` | ❌ **Pending env** |
| `gradlew lint` | ❌ **Pending env** |
| Device / emulator run | ❌ No SDK / adb |

---

## 9. Next Steps (when build env ready)

1. Run `./gradlew :app:assembleDebug` → fix any compile errors.
2. Run `./gradlew :app:testDebugUnitTest` → fix Robolectric test failures (API 35 shadows).
3. `adb install` on API 34/35 device → test BT rower/HR connect, workout start/stop, exports, notifications.
4. If `SecurityException` on `startForeground(connectedDevice)`: move Bluetooth permission request to `MainActivity` before `GymService.start()`.
5. Optional: edge-to-edge opt-in, DB migration, window-flag modernization.

---

*Generated 2026-09-28 from working-tree state (commit `0138185` + local edits).*