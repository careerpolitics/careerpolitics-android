# Environment, CI, and Release Readiness Checklist

Use this as the single checklist before merging/refactoring/releasing.

## 1) JDK
- [ ] Pin local JDK to **21**.
- [ ] Pin CI JDK to **21**.
- [ ] Verify with:
  - `java -version`
  - `./gradlew --version`

## 2) Android SDK / Toolchain
- [ ] Install Android SDK platform **36**.
- [ ] Install Android Build Tools compatible with AGP and SDK 36.
- [ ] Ensure `compileSdk = 36` and `targetSdk = 36` remain aligned with local SDK install.
- [ ] Verify with:
  - `./gradlew :app:tasks --all`
  - `./gradlew help`

## 3) Secrets (`app/secrets.properties`)
- [ ] Create `app/secrets.properties` from template.
- [ ] Fill required values:
  - `pusher.instance.id`
  - `keystore.path`
  - `keystore.password`
  - `key.alias`
  - `key.password`
  - Optional OAuth overrides (`google.web.client.id`, `native.google.login.path`).
- [ ] Confirm file is **not committed**.

## 4) Google / Firebase
- [ ] Ensure `google-services.json` is present per environment (local/CI) and not committed.
- [ ] In Firebase Console, enable:
  - [ ] Cloud Messaging
  - [ ] Crashlytics
- [ ] Verify app has required plugin/dependency wiring in Gradle.

## 5) OAuth
- [ ] Verify `GOOGLE_WEB_CLIENT_ID` value for active environment.
- [ ] Verify callback path (`NATIVE_GOOGLE_LOGIN_CALLBACK_PATH`) matches backend contract.
- [ ] Register SHA fingerprints in Google/Firebase for:
  - [ ] debug
  - [ ] staging
  - [ ] release

## 6) Signing
- [ ] Verify release keystore path/password/alias/key-password are valid.
- [ ] Verify release build uses release signing config.
- [ ] Keep keystore backups in secure encrypted storage (at least 2 locations).
- [ ] Document ownership + recovery policy for signing key material.

## 7) CI required jobs
- [ ] Configuration checks:
  - `./gradlew help`
  - `./gradlew projects`
- [ ] Lint release:
  - `./gradlew :app:lintRelease`
- [ ] Unit tests (all modules):
  - `./gradlew testDebugUnitTest`
- [ ] Optional stricter static analysis:
  - `./gradlew detekt --all-rules`
- [ ] Debug and release artifacts produced and uploaded.

## 8) Performance checks
- [ ] Build release:
  - `./gradlew :app:assembleRelease`
- [ ] Confirm baseline profile packaged.
- [ ] Run startup checks from perf doc:
  - `adb shell am force-stop com.murari.careerpolitics`
  - `adb shell am start -W com.murari.careerpolitics/.activities.MainActivity`
- [ ] Capture cold/warm/hot startup results and compare against baseline.

## 9) Suggested pre-merge command set
```bash
./gradlew help
./gradlew projects
./gradlew :app:lintRelease
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease bundleRelease
```


## 10) Deployment runbook
- [ ] Follow `docs/checklists/RELEASE_AND_DEPLOYMENT_CHECKLIST.md` for staging/production rollout.
