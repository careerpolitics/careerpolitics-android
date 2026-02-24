# Release & Deployment Checklist

Use this runbook for every staging/release deployment.

## A) One-time setup (project/repo)

### 1. Access and ownership
- [ ] Define release owners (primary + backup).
- [ ] Grant least-privilege access to:
  - GitHub repo (Actions, environments, secrets)
  - Firebase project
  - Google Play Console
  - Keystore storage vault

### 2. GitHub environments
- [ ] Create environments:
  - `staging`
  - `production`
- [ ] Configure required reviewers for `production`.
- [ ] Add environment protection rules (manual approval for production deploy).

### 3. Required CI/CD secrets
- [ ] `PUSHER_INSTANCE_ID`
- [ ] `GOOGLE_WEB_CLIENT_ID` (if overriding local file defaults)
- [ ] `KEYSTORE_BASE64`
- [ ] `KEYSTORE_PASSWORD`
- [ ] `KEY_ALIAS`
- [ ] `KEY_PASSWORD`
- [ ] `GOOGLE_SERVICES_JSON_BASE64` (if injecting from CI)

### 4. Android/Google configuration
- [ ] Firebase:
  - [ ] Crashlytics enabled
  - [ ] Cloud Messaging enabled
- [ ] Google OAuth:
  - [ ] Web client configured
  - [ ] SHA cert fingerprints registered for debug/staging/release
- [ ] Play Console:
  - [ ] App signing configured
  - [ ] Internal/Closed/Production tracks created

### 5. Signing and key management
- [ ] Keystore stored in secure encrypted vault.
- [ ] Rotation and incident policy documented.
- [ ] Two secure backups of keystore + credentials maintained.

---

## B) Per-release preflight (before cutting release)

### 1. Branching & version
- [ ] Merge all approved PRs into `main`.
- [ ] Confirm release tag/versioning strategy for this release.
- [ ] Confirm changelog/release notes source (PR titles/labels/manual notes).

### 2. Environment & toolchain
- [ ] JDK 21 active locally and in CI.
- [ ] Android SDK platform 36 installed.
- [ ] Gradle config check passes:
  - `./gradlew help`
  - `./gradlew projects`

### 3. Security/config checks
- [ ] `app/secrets.properties` exists locally (not committed).
- [ ] `google-services.json` available per target environment.
- [ ] OAuth callback path and client ID validated against backend settings.

### 4. Quality gates
- [ ] Lint release:
  - `./gradlew :app:lintRelease`
- [ ] Unit tests:
  - `./gradlew testDebugUnitTest`
- [ ] Static analysis:
  - `./gradlew detekt`
  - Optional strict mode: `./gradlew detekt --all-rules`

### 5. Build artifacts
- [ ] Build debug for smoke:
  - `./gradlew assembleDebug`
- [ ] Build release artifacts:
  - `./gradlew assembleRelease bundleRelease`
- [ ] Verify generated outputs:
  - APK: `app/build/outputs/apk/release/`
  - AAB: `app/build/outputs/bundle/release/`
  - Mapping: `app/build/outputs/mapping/release/mapping.txt`

### 6. Performance checks
- [ ] Baseline profile included.
- [ ] Startup checks executed on device:
  - `adb shell am force-stop com.murari.careerpolitics`
  - `adb shell am start -W com.murari.careerpolitics/.activities.MainActivity`

---

## C) Staging deployment steps

1. **Create release candidate**
   - [ ] Create/confirm release tag (e.g., `vX.Y.Z-rcN` if used).
   - [ ] Push tag/branch to trigger CI.

2. **Run CI and verify**
   - [ ] Versioning automation succeeds.
   - [ ] Lint, tests, debug build, release build succeed.
   - [ ] Artifacts uploaded in GitHub Actions.

3. **Distribute staging build**
   - [ ] Upload AAB/APK to internal/staging distribution channel.
   - [ ] Share release notes and test charter to QA.

4. **QA sign-off**
   - [ ] Deep links verified.
   - [ ] OAuth Google login verified.
   - [ ] Notification permission + registration + click routing verified.
   - [ ] WebView bridge media interactions verified.
   - [ ] Crash-free smoke test completed.

---

## D) Production deployment steps

1. **Release candidate approval**
   - [ ] QA sign-off complete.
   - [ ] Product/engineering approval recorded.

2. **Promote/build production artifact**
   - [ ] Ensure production secrets/env selected.
   - [ ] Trigger production release workflow.
   - [ ] Confirm AAB + mapping produced from approved commit SHA.

3. **Deploy to Play Console**
   - [ ] Upload AAB to production track (or promote from closed track).
   - [ ] Attach release notes.
   - [ ] Rollout strategy selected:
     - [ ] staged rollout (recommended)
     - [ ] full rollout

4. **Post-deploy verification**
   - [ ] Install from store and run smoke checks.
   - [ ] Monitor Crashlytics for regressions.
   - [ ] Monitor auth/deeplink/push metrics for anomalies.

5. **Close release**
   - [ ] Publish final release notes.
   - [ ] Update internal release log with:
     - version, commit SHA, build URL, rollout %, known issues.

---

## E) Rollback/incident plan

- [ ] If severe regression detected:
  - [ ] Halt rollout in Play Console.
  - [ ] Roll back to previous stable release.
  - [ ] Open incident ticket with timeline + impact.
- [ ] Prepare hotfix branch and repeat checklist sections B→D.

---

## F) Required configuration summary

### CI
- Java: 21
- Required jobs: configuration (`help/projects`), lintRelease, unit tests, debug build, release build, artifacts

### Secrets
- Pusher, OAuth, keystore credentials, optional google-services injection

### Google/Firebase
- Messaging + Crashlytics enabled
- OAuth client + SHA fingerprints configured

### Signing
- Release keystore valid and backed up

### Performance
- Baseline/startup verification completed each release
