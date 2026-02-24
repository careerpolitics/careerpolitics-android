# Release CI/CD setup (Play Store ready)

This repository includes a GitHub Actions workflow at:

- `.github/workflows/android-build.yml`

## What it does

- Runs CI (`lintDebug`, `testDebugUnitTest`, `assembleDebug`) on pushes/PRs for `main` and `develop`.
- Builds signed release artifacts when you:
  - manually run **Actions → Android CI/CD → Run workflow**, or
  - push a tag starting with `v` (example: `v2.0.1`).

Release job outputs:
- `app-release.aab` (**upload this to Play Console**)
- `app-release.apk` (optional side-loading/testing)
- `mapping.txt` (for Crashlytics / Play deobfuscation)

## Required GitHub secrets

Add these in **GitHub → Settings → Secrets and variables → Actions**:

- `KEYSTORE_BASE64` - base64 encoded upload keystore file
- `KEYSTORE_PASSWORD` - keystore password
- `KEY_ALIAS` - key alias
- `KEY_PASSWORD` - key password
- `PUSHER_INSTANCE_ID` - optional app runtime secret used in build config

## One-time command to create `KEYSTORE_BASE64`

Run locally from your keystore directory:

```bash
base64 -w 0 release.keystore
```

Copy output and save it as the `KEYSTORE_BASE64` GitHub secret.

## Release flow

1. Bump `versionCode`/`versionName` in `app/build.gradle.kts`.
2. Trigger release workflow (manual run or `git tag vX.Y.Z && git push origin vX.Y.Z`).
3. Download `release-aab` artifact from the workflow run.
4. Upload `app-release.aab` to Google Play Console.
