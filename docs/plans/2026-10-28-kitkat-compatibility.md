# KitKat Compatibility Implementation Plan

> **REQUIRED SUB-SKILL:** Use the executing-plans skill to implement this plan task-by-task.

**Goal:** Produce a separately named, v1-signed APK with Android 4.4/API 19 as its minimum, for the user's CVTE_MSD338_512M_PB801 / YunOS 2.1.0 television.

**Architecture:** Keep one Java codebase with API-qualified resources and guarded/isolateable modern platform code, rather than maintaining a fork of MainActivity. Retain modern TV input functionality on API 21+; on KitKat delegate to the existing configurable vendor source panel without inventing CVTE-specific intents. Preserve the existing root APK files and publish the new build as `tvhome-zh-kitkat.apk`.

**Tech Stack:** Android Gradle Plugin 8.5.2, Java 17 source, Android SDK 35, existing RecyclerView/DynamicAnimation, Python unittest for build/resource contracts, Android Lint and Robolectric for API 19 regressions where supported.

---

## Scope and evidence

- User identifies the target as Android 4.4.4-compatible (API 19). No television connection is available; this is a target assumption, not a measured device property.
- Existing debug APK requires API 21 and has valid v1/v2 signatures; the release-named APK is older and requires API 26. Do not overwrite either artifact.
- Source uses Material-only theme, eagerly creates NetworkCallback, unconditionally loads banners/outlines, calls PopupWindow elevation and Canvas's API 21 float drawRoundRect overload, and uses TV Input Framework classes.
- Current signing config points at another developer's macOS private key. Use Gradle's normal local debug key, with explicit v1 signing, and document that it cannot update an APK signed with the upstream key.
- A standard app-launcher entry for tvhome itself is required because KitKat vendor launchers may not query LEANBACK_LAUNCHER. This does NOT change which other applications tvhome lists by default.
- User clarification: preserve the original TV-only default on every API level. Desktop and Favourites discover LEANBACK_LAUNCHER entries only; ordinary applications are included only after an explicit library selection. Even on API 19, do not fall back to importing all LAUNCHER applications.
- No changes to the connected phone; do not install or change the default launcher on any connected device.

## Task 1: Establish failing compatibility tests

**Files:** `tools/tests/test_kitkat_contract.py`, `launcher/app/src/test/java/com/gathin/tvhome/KitKatCompatibilityTest.java`, `launcher/app/build.gradle` (test infrastructure only).

1. Add offline tests for minSdk 19, explicit v1 signing, portable signing, a pre-21 theme fallback with Material retained for v21, an ordinary LAUNCHER intent, and PNG generation for vector icons.
2. Run `python -m unittest discover -s tools/tests -v`; record expected failures against the current source.
3. Add Robolectric 4.11.1/JUnit tests for API 19 activity lifecycle and layout/popup inflation, TV-only default discovery, opt-in ordinary apps, and icon fallback, network labels, and quick settings/screensaver startup. Run against the existing behavior to identify actual failures rather than hiding missing APIs with lint suppressions.
4. Use the existing Android Studio JBR and SDK through process-local environment variables. Download only the pinned build/test prerequisites if missing. Keep local SDK paths out of version control.

## Task 2: Implement the compatibility boundary

**Files:** `launcher/app/build.gradle`, `launcher/app/src/main/AndroidManifest.xml`, `launcher/app/src/main/res/values/styles.xml`, new `res/values-v21/styles.xml`, `MainActivity.java`, `AppAdapter.java`, `GlassView.java`, and focused helper classes/resources if necessary.

1. Lower minimum to 19 without lowering compileSdk/targetSdk; suffix the compatibility version and retain both v1/v2 signing. Enable raster fallbacks for vectors rather than feeding vector XML to KitKat framework ImageView.
2. Use a Holo fullscreen base theme on API 19 and the existing Material theme on API 21+. Add a separate standard MAIN/LAUNCHER intent filter.
3. Replace the eager NetworkCallback and mismatched unregister with a lifecycle-bound connectivity broadcast receiver; use NetworkInfo for the pre-23 connection label rather than displaying all KitKat networks as offline.
4. Isolate modern TV input code behind an API 21 boundary. On API 19 use the existing vendor/system source-panel fallback, preserving the physical remote's source button/default-off input interception.
5. Guard banner loading and popup elevation; replace API 21-only drawing with the RectF overload. Clip cards with a KitKat-safe path or explicitly isolate outline support.
6. Run the tests after each related change. Keep modern behavior and existing settings intact. Do not add guessed vendor commands or disable system package verification.

## Task 3: Verify and package

**Files:** optional `tools/verify_kitkat_apk.py`, generated `tvhome-zh-kitkat.apk`.

1. Run all offline and JVM tests; run Android Lint at minSdk 19 and fix NewApi/InlinedApi issues that affect execution.
2. Run `launcher/gradlew :app:assembleRelease` with the pinned SDK/build tools.
3. Check the actual packaged manifest with aapt (minSdk 19, package/version, launcher categories), actual resource fallbacks, DEX header, archive CRC, and `apksigner verify --verbose --min-sdk-version 19`.
4. Copy the verified release APK to the new root filename only. Record its SHA-256 and keep the two existing APKs unchanged.
5. Explicitly distinguish static/JVM checks from unavailable Dalvik/YunOS device validation.

## Task 4: Document delivery

**Files:** `docs/kitkat-compatibility.md`, `README.md`.

1. Explain offline U-disk installation, local build commands, signing identity/update limitations, and compatible baseline.
2. Document unavailable/conditional functionality: standard TIF HDMI enumeration on pre-21, ICU lunar date on pre-24, and legacy TLS/certificate limitations for optional network features. Never weaken certificate validation.
3. Provide a TV test checklist: install, launch, D-pad navigation, TV-only default listing and manually added ordinary apps, quick settings, wallpaper, network labels, relaunch/background recovery, and vendor source button.
4. Keep the original launcher enabled until the compatibility APK has been validated on the TV.

## Initial execution results (2026-10-28, superseded by the user-requested filtering correction)

The first build below incorrectly auto-imported ordinary apps on KitKat. Preserve this record as historical evidence, not as the current behavior or current artifact hash.

- Red: 7/7 offline contracts failed against the original source. Robolectric API 19 package parsing reproduced `Requires newer sdk version #21 (current version is #19)`.
- After lowering the install floor, API 19 UI inflation exposed the additional ColorStateList-tint crash; the tint now has separate plain-color and v21-selector resources. Lint initially reported 62 errors at the new API floor.
- Green: 7 offline contracts and 22 Robolectric cases across API 19/21/28 passed. The existing standalone vendor-number mapping check also passed all 15 cases (it tests a copy of the mapping algorithm, not the populated TIF catalog).
- Both debug and release Lint completed with 0 errors, no NewApi/InlinedApi findings, and 127 other warnings. No compatibility baseline/suppression was used to hide API calls; two inlined primitive status constants are explicitly documented.
- An independent read-only code review found no blocking defects for a test release; actual Dalvik/YunOS execution and populated TIF catalogs remain unverified.
- Release build succeeded. `tvhome-zh-kitkat.apk`: 497,030 bytes, minSdk 19, versionCode 11, versionName `1.6.1-kitkat.1`, ordinary launcher entry, one DEX 035, no native libraries, 84 PNG entries. ZIP integrity and v1/v2 signatures passed, including apksigner's API 19 verification.
- SHA-256: `861351739eda15760f2ef1221677c1a06fbe1974c4ad749b3f083f51e3b8a4ad`.
- Existing root APK files are unchanged. No application was installed on any device. Build/test/review logs are under ignored `launcher/app/build/reports/kitkat/`; installation notes and device checklist are in `docs/kitkat-compatibility.md`.

## User-requested TV-only policy correction (2026-10-28)

- Restored original default discovery in AppAdapter and MainActivity's Favourites picker on every supported SDK: LEANBACK_LAUNCHER entries plus explicit LibraryApps selections only. Zero TV results must not import ordinary LAUNCHER apps.
- Restored TV-category launching on KitKat and TV-entry preference in the library. The category is an inlined string, safely queried on API 19; its field-level InlinedApi suppression does not hide framework calls. The compatibility-only guards around banner loading and other post-19 APIs remain intact.
- Added five policy regressions across SDK 19/21/28. Red run: 36 tests, 5 API 19 policy failures. Green run: all 36 pass; the 7 Python contracts also pass. Debug/release Lint: 0 errors, no NewApi/InlinedApi findings, 127 other warnings.
- Independent read-only review confirmed the filtering matches the original policy and found no blockers.
- Rebuilt the same output filename as `1.6.1-kitkat.2` / versionCode 12, signed with the same local key. Current APK: 496,915 bytes; minSdk 19; v1/v2 signatures verified for API 19; ZIP CRC valid, one DEX 035, no native libraries.
- Current SHA-256: `445ed2289045275d0571a468a2fb0d0f81505b23ddf006095df55e3863f95849`.
- No device installation was performed; original `tvhome-zh.apk` and `tvhome-zh-debug.apk` remain unchanged. TV validation remains outstanding.
