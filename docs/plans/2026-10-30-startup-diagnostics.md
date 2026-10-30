# KitKat TV Startup Diagnostics Implementation Plan

> **REQUIRED SUB-SKILL:** Use the executing-plans skill to implement this plan task-by-task.

**Goal:** Obtain useful on-screen evidence from a TV that exits before showing the desktop, without ADB or external logs; do not claim to fix an unknown crash.

**Architecture:** A separately installable diagnostic build uses a framework-only Activity as its ordinary/TV launcher entry, and runs the actual MainActivity in a private secondary process. An Application installs a best-effort uncaught-exception recorder; explicit MainActivity checkpoints persist synchronously. A private report survives desktop-process death and is displayed when the diagnostic page resumes or is reopened. The normal build never activates recording.

**Tech Stack:** Existing Android/Java/Gradle project (minSdk 19), Holo framework widgets, private UTF-8 files, JUnit/Robolectric SDK 19/21/28, Python packaging contracts, aapt/apksigner.

---

## Evidence and constraints

- User confirmed case A: tapping the installed `1.6.1-kitkat.2` icon exits before any desktop appears on the real TV. System logs are unavailable. Actual Android API/VM are not yet measured.
- Existing JVM checks exercise debug classes, not the shipped R8/Dalvik/vendor GPU runtime. No causal exception or proven root cause is available. Do not guess by broadly disabling effects or swallowing launcher exceptions.
- Alternatives: (1) same-package crash overlay, but a desktop startup loop could prevent access and would replace the current APK; (2) blindly simplify rendering/fonts, which might hide rather than locate the failure; (3) independent reporter with a separately running desktop (chosen).
- Use application ID `com.gathin.tvhome.diagnostic`; app label `TVHome 启动诊断`; no HOME intent filters. Keep the same minSdk, signing schemes, release shrinking/optimisation and TV-only default app discovery.
- The diagnostic installation has fresh preferences; it does NOT reproduce the existing app's private settings. Success alone is not a confirmed fix.
- No extra permissions, uploads, storage access or root. Record only app version, platform/model/ABI/VM, display/heap details, startup stage and available exception text. No identifiers, SSID, location, installed-app list or user files.
- Java failures before the custom Application starts, native faults and system kills may leave no stack trace. Stages identify the last checkpoint reached, not the exact failing statement. Reporter failure itself may require recording whether its screen ever appeared.
- Existing work is already staged in the current tree. Keep that index and all existing APKs untouched; make precise unstaged additions here rather than committing/copying the user's staged baseline into another branch.

## Task 1: Contracts and diagnostic storage tests

**Files:** create `tools/tests/test_startup_diagnostics_contract.py`; create `launcher/app/src/test/java/com/gathin/tvhome/StartupDiagnosticsTest.java`.

1. Add source/manifest contracts: separate release-derived build, reporter entry, separate desktop process, no HOME entry, no new permissions, early Application installation, checkpoints preceding inflation/fonts/config and app enumeration.
2. Run `python -m unittest discover -s tools/tests -v`; observe failures for the missing diagnostic variant/entry/instrumentation before implementation.
3. Add Robolectric tests using real private files: persistence across report instances, phase replacement, captured cause/stack, bounded report size, failed storage is non-fatal, prior uncaught handler delegation, screen opens without automatically launching the desktop, explicit launch uses component string and fresh task, and reopening retains evidence.
4. Use reflection only to assert missing new types without making the initial Java test compilation fail; run `:app:testDebugUnitTest` and verify missing-type assertions fail for the intended reason.

## Task 2: Minimal instrumentation and UI

**Files:** create `launcher/app/src/main/java/com/gathin/tvhome/StartupDiagnostics.java`; create `launcher/app/src/main/java/com/gathin/tvhome/DiagnosticsActivity.java`; modify `MainActivity.java`; modify `launcher/app/build.gradle`; create `launcher/app/src/diagnostic/AndroidManifest.xml`; create `launcher/app/proguard-diagnostic.pro`.

1. Implement a bounded, best-effort private report store. Separate metadata, latest phase and exception files; flush/sync writes and retain evidence until an explicit new test. No retry or auto-restart loop.
2. Install recording only through the diagnostic manifest's custom Application. Preserve default crash termination by invoking the previous uncaught handler after saving. In the ordinary build, checkpoints are no-ops.
3. Build the reporter entirely with framework widgets, system fonts and a system Holo theme. Show version/platform and report text; offer explicit Start and Refresh controls, and remote-scrollable details. Starting the desktop uses a class-name string so the reporter does not need to load MainActivity.
4. Keep the reporter in the default process and MainActivity in `:desktop`, with a separate task affinity. Remove inherited MainActivity intent filters in this build only; reporter is the LAUNCHER/LEANBACK entry. Disable duplicate accessibility/screensaver services, keep the disabled input component required by existing startup code.
5. Add checkpoints before superclass startup, layout inflation, custom font loading, configuration, weather/UI setup, onStart receiver registration, app discovery and end of onResume. Do not wrap/alter the original desktop behavior.
6. Run tests; verify that ordinary TV-only discovery regressions still pass.

## Task 3: Review, build and handoff

**Files:** create `docs/startup-diagnostics.md`; create `tvhome-zh-kitkat-diagnostic.apk` and `.sha256`; preserve `tvhome-zh-kitkat.apk` and original APKs.

1. Run Python contracts, `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:lintDiagnostic`, `:app:assembleDiagnostic`.
2. Review handler delegation, evidence lifetime, cross-process files, pre-Activity recording and manifest entry points. Do not install anything on connected devices.
3. Inspect the actual APK: application ID, minSdk 19, reporter launch entry, no HOME filter, desktop process/task, PNG fallbacks, ZIP integrity, DEX 035, v1/v2 signatures with API 19 verification.
4. Copy only the new diagnostic artifact; calculate and check SHA-256. Preserve its R8 mapping with local build output for interpreting reported stacks.
5. Document U-disk installation, photograph initial screen, press Start, reopen/refresh after failure, photograph first error and causes. If even the initial report page exits, report that distinction rather than asserting the crash was caught.

## Execution evidence (2026-10-30)

- Red: 5 new Python contract failures; 18 missing-diagnostic-implementation assertion failures among 57 JVM cases. The ordinary-build no-recorder checks passed.
- Green: 12 Python contracts; 60 JVM cases across SDK 19/21/28 (24 diagnostic, 36 existing compatibility/policy checks), all pass. An additional integration check verifies Application handler installation, real MainActivity checkpoints and preservation of prior evidence.
- First diagnostic lint run found TV declarations missing from the overlay source, although the merged manifest inherited them correctly. Repeated the base optional-feature/banner declarations explicitly; final debug/diagnostic lint: 0 errors and no NewApi/InlinedApi findings (131/133 other warnings).
- Independent read-only review found no blocking defects for a diagnostic TV trial. No claimed root cause or fix; no real device was accessed.
- Actual APK inspection confirms package `com.gathin.tvhome.diagnostic`, reporter entry, private desktop process/task, no HOME filter, minSdk 19, API-19 v1/v2 signature verification, CRC integrity and one DEX 035. PNG fallbacks were checked through AAPT's resource table because release optimisation shortens ZIP filenames.
- Published `tvhome-zh-kitkat-diagnostic.apk`: 506,817 bytes; SHA-256 `e44def9649d3f3fec346f2db25cb0e282570914cb417b8639eb3c034fc82da07`.
- Instructions: `docs/startup-diagnostics.md`. Existing compatibility docs now record the user's TV startup failure rather than stating there has been no TV trial.
- Existing staged index and all four previous APKs are preserved. The diagnostic package still needs the user's television trial and photographs.
