# Android CI/CD Setup (GitHub Actions)

This repository uses a **modular, production-grade CI/CD pipeline** with separate workflows for PR validation, post-merge sanity checks, and tagged releases.

## 1) Pipeline Overview

### Workflows
- **`pr-check.yml`**: Pull request quality gate (no secrets).
- **`merge-main.yml`**: Post-merge validation on `main`, with version metadata generation.
- **`release.yml`**: Signed release build + GitHub Release on semantic tags (`v*.*.*`).

### DevOps Principles Implemented
- Separation of CI and CD
- Immutable releases from tags only
- Deterministic build metadata captured per run
- No manual release build path
- Least-privilege workflow permissions
- Secret material decoded only when needed and deleted afterward

---

## 2) Required GitHub Secrets

Configure the following in **Settings → Secrets and variables → Actions**:

- `GOOGLE_SERVICES_JSON_BASE64`
  - Base64-encoded `google-services.json`.
- `GOOGLE_WEB_CLIENT_ID`
  - Web OAuth client ID injected as build environment variable.
- `KEYSTORE_BASE64`
  - Base64-encoded Android release keystore.
- `KEYSTORE_PASSWORD`
  - Keystore password.
- `KEY_ALIAS`
  - Alias for signing key inside keystore.
- `KEY_PASSWORD`
  - Password for signing key alias.
- `PUSHER_INSTANCE_ID`
  - Pusher Beams instance ID injected at build time.

> `GOOGLE_WEB_CLIENT_ID` and `PUSHER_INSTANCE_ID` are injected via workflow environment variables and consumed by Gradle env resolution.

---

## 3) Base64 Secret Generation

### Generate `KEYSTORE_BASE64`

```bash
base64 -w 0 app/release.keystore
```

Copy the output and save as `KEYSTORE_BASE64`.

> On macOS, use: `base64 -i app/release.keystore | tr -d '\n'`

### Generate `GOOGLE_SERVICES_JSON_BASE64`

```bash
base64 -w 0 app/google-services.json
```

Copy the output and save as `GOOGLE_SERVICES_JSON_BASE64`.

> On macOS, use: `base64 -i app/google-services.json | tr -d '\n'`

---

## 4) Trigger and Release Process

### Pull Requests
`pr-check.yml` runs on:
- `pull_request` opened
- `pull_request` synchronize
- `pull_request` reopened

Checks:
- `./gradlew clean`
- `./gradlew lint`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

Artifacts uploaded:
- lint reports
- unit test reports

### Main Branch
`merge-main.yml` runs on:
- `push` to `main`

Actions:
- Runs sanity build (`clean lint testDebugUnitTest assembleDebug`)
- Generates and uploads `build/ci/version-metadata.json`
- Sets CI version metadata (`CI_VERSION_CODE`, `CI_VERSION_NAME`)

### Tagged Release
`release.yml` runs on:
- tag push matching `v*.*.*`

Actions:
1. Validates required secrets.
2. Decodes:
   - `GOOGLE_SERVICES_JSON_BASE64` → `app/google-services.json`
   - `KEYSTORE_BASE64` → `app/release.keystore`
3. Builds signed AAB:
   - `./gradlew clean bundleRelease`
4. Uploads artifacts:
   - `*.aab`
   - `mapping.txt`
5. Creates GitHub Release and attaches artifacts.
6. Deletes decoded sensitive files.

---

## 5) Versioning Strategy (Semantic Versioning)

Use semantic tags:
- `vMAJOR.MINOR.PATCH` (e.g., `v2.3.1`)

Recommended rules:
- **MAJOR**: breaking changes
- **MINOR**: backward-compatible features
- **PATCH**: backward-compatible fixes

CI behavior:
- `CI_VERSION_NAME` uses tag name on release builds.
- `CI_VERSION_CODE` uses GitHub run number to provide monotonic increment in CI.

Create release tag:

```bash
git tag v2.3.1
git push origin v2.3.1
```

---

## 6) Deployment Flow Diagram (Explanation)

```text
Feature Branch -> Pull Request -> pr-check.yml (lint/test/assemble)
                                  |
                                  +-- pass required check -> merge allowed

Merge to main -> merge-main.yml (sanity + version metadata)

Tag vX.Y.Z -> release.yml (decode secrets -> signed bundleRelease -> upload artifacts -> GitHub Release)
```

Interpretation:
- PR workflow is the mandatory quality gate.
- Main workflow ensures `main` remains healthy and traceable.
- Release workflow is the only production artifact path, ensuring immutable release provenance.

---

## 7) Branch Protection Recommendations

Configure branch protection for `main`:
1. Require pull request before merging.
2. Require at least **1 approval**.
3. Require status checks to pass before merging:
   - `PR Check / Lint, unit tests, and debug assembly`
4. Dismiss stale approvals when new commits are pushed.
5. Restrict force pushes and branch deletion.

---

## 8) Troubleshooting

### PR build fails on lint
- Download uploaded lint artifacts from workflow run.
- Fix lint violations locally with `./gradlew lint`.

### Unit tests fail in CI only
- Confirm deterministic test setup (timezone/locale assumptions).
- Review test reports uploaded from `testDebugUnitTest`.

### Release fails with signing errors
- Verify `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- Confirm `KEYSTORE_BASE64` decodes to correct keystore file.

### Release fails due to missing Firebase config
- Confirm `GOOGLE_SERVICES_JSON_BASE64` is valid and up-to-date.
- Ensure decoded file is written to `app/google-services.json`.

---

## 9) Security Best Practices

- Use environment-scoped secrets with restricted repository access.
- Never commit `google-services.json` or keystore files.
- Restrict who can create tags on protected branches.
- Rotate secrets regularly and immediately after personnel changes.
- Keep workflow permissions minimal (`contents:read` by default).
- Clean up sensitive decoded files in every release run (`if: always()`).

---

## 10) Secret Rotation Procedure

1. Generate new credential material (Firebase config and/or keystore).
2. Base64-encode updated files.
3. Update GitHub Actions secrets.
4. Trigger a PR run to validate CI.
5. Create a test tag (e.g., `v0.0.0-rc` with adjusted pattern if needed) in non-prod repo or run release in a controlled branch.
6. Revoke old credentials after successful validation.

---

## 11) Artifact Retention Policy

- PR reports: **14 days**
- Main metadata artifacts: **30 days**
- Release artifacts: **180 days**

This balances auditability, storage cost, and incident investigation windows.
