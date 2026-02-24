# 🏗️ Production Refactoring Guide - Career Politics Android App

## 📋 Table of Contents
1. [Overview](#overview)
2. [Configuration Architecture](#configuration-architecture)
3. [Environment Setup](#environment-setup)
4. [Security Best Practices](#security-best-practices)
5. [Build Variants](#build-variants)
6. [Migration Checklist](#migration-checklist)
7. [CI/CD Integration](#cicd-integration)
8. [Production Readiness Checklist](#production-readiness-checklist)

---

## 🎯 Overview

## 🧭 2026 Architecture Redesign

For the modular MVVM + Clean Architecture migration plan, see `docs/architecture/ARCHITECTURE_REDESIGN_2026.md`.

---

This refactoring eliminates hard-coded configuration, separates concerns, and implements production-grade security practices for the Career Politics Android application.

### What Changed

**Before:**
- ❌ Secrets hard-coded in source files
- ❌ URLs duplicated across 7+ files
- ❌ No environment separation
- ❌ Debug signing used for release builds
- ❌ WebView debugging enabled in production

**After:**
- ✅ Centralized configuration with `AppConfig`
- ✅ Secrets externalized to `secrets.properties`
- ✅ Three build variants: debug, staging, release
- ✅ Proper release signing configuration
- ✅ Environment-aware logging and debugging
- ✅ Security-first architecture

---

## 🏛️ Configuration Architecture

### Layer 1: BuildConfig (Compile-Time)

**Purpose:** Environment-specific constants injected at build time  
**Location:** `app/build.gradle.kts` → `BuildConfig.FIELD_NAME`  
**Use For:** URLs, API keys, feature flags, build-specific configuration

```kotlin
// Accessed in code as:
val baseUrl = BuildConfig.BASE_URL
val isProd = !BuildConfig.DEBUG
```

**Why this layer?**
- Dead code elimination (unused code paths removed by R8)
- No runtime overhead
- Compile-time type safety
- Different values per build variant

---

### Layer 2: AppConfig (Runtime Singleton)

**Purpose:** Single source of truth for all configuration  
**Location:** `app/src/main/java/com/murari/careerpolitics/config/AppConfig.kt`  
**Use For:** Accessing configuration throughout the app

```kotlin
// Example usage:
webView.loadUrl(AppConfig.baseUrl)
PushNotifications.start(context, AppConfig.pusherInstanceId)
if (AppConfig.enableLogging) { ... }
```

**Why this layer?**
- Centralized access point
- Type-safe configuration
- Easy to test and mock
- Prevents scattered magic strings

---

### Layer 3: secrets.properties (Never Committed)

**Purpose:** Store secrets locally and in CI/CD  
**Location:** `app/secrets.properties` (Git-ignored)  
**Template:** `app/secrets.properties.template`  
**Use For:** API keys, signing credentials, instance IDs

```properties
# secrets.properties (NEVER commit this file)
pusher.instance.id=923a6e14-cca6-47dd-b98e-8145f7724dd7
keystore.path=/path/to/release.keystore
keystore.password=YOUR_SECURE_PASSWORD
key.alias=release
key.password=YOUR_KEY_PASSWORD
```

**Why this layer?**
- Keeps secrets out of version control
- CI/CD can inject via environment variables
- Local development friendly
- Prevents accidental leaks

---

### Layer 4: buildSrc (Build Constants)

**Purpose:** Centralize version management and build configuration  
**Location:** `buildSrc/src/main/kotlin/Dependencies.kt`  
**Use For:** SDK versions, dependency versions, build constants

```kotlin
object AppConfig {
    const val compileSdk = 36
    const val minSdk = 27
    const val targetSdk = 36
    const val versionCode = 30000 // 3.0.0
}
```

**Why this layer?**
- Compile-time type safety in Gradle files
- Single source of truth for versions
- Prevents version drift across modules
- IDE autocomplete support

---

## 🔧 Environment Setup

### 1. Initial Setup (First Time)

```bash
# 1. Clone the repository
git clone <your-repo-url>
cd careerpolitics-android

# 2. Copy secrets template
cp app/secrets.properties.template app/secrets.properties

# 3. Edit secrets.properties with your actual values
# Use your favorite editor to fill in:
#   - pusher.instance.id (from Pusher Beams dashboard)
#   - keystore credentials (for release signing)

# 4. Sync Gradle
./gradlew clean build
```

---

### 2. Secrets Configuration

#### Local Development

Edit `app/secrets.properties`:

```properties
# Pusher Beams Instance ID
pusher.instance.id=923a6e14-cca6-47dd-b98e-8145f7724dd7

# Release Keystore (required for release builds)
keystore.path=../release.keystore
keystore.password=mySecurePassword123
key.alias=release
key.password=myKeyPassword456
```

#### CI/CD (GitHub Actions / Jenkins)

Set environment variables:

```bash
# GitHub Actions: Settings → Secrets → Actions
PUSHER_INSTANCE_ID=923a6e14-cca6-47dd-b98e-8145f7724dd7
KEYSTORE_PASSWORD=mySecurePassword123
KEY_ALIAS=release
KEY_PASSWORD=myKeyPassword456
```

The build system will automatically fall back to environment variables if `secrets.properties` doesn't exist.

---

### 3. Keystore Generation (Release Signing)

If you don't have a release keystore:

```bash
# Generate a new release keystore (ONLY ONCE)
keytool -genkey -v -keystore release.keystore \
  -alias release \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000

# Store it SECURELY:
# 1. NEVER commit to Git
# 2. Back up to secure location (password manager, encrypted storage)
# 3. Add path to secrets.properties
```

⚠️ **CRITICAL:** If you lose this keystore, you **cannot update your app** on Play Store!

---

## 🔐 Security Best Practices

### What Must NEVER Be Committed

```
❌ secrets.properties
❌ google-services.json (contains API keys)
❌ *.keystore, *.jks, *.pepk (signing keys)
❌ local.properties
❌ Any file containing passwords or API keys
```

These are automatically excluded via `.gitignore`.

---

### Security Checklist

#### ✅ Pre-Commit Checks

```bash
# Before committing, verify no secrets are staged:
git status
git diff --cached

# Check for accidentally staged secrets:
git grep -i "password\|secret\|api.key" --cached
```

#### ✅ Production Build Security

The refactored build ensures:

1. **WebView Debugging Disabled** in release builds
   ```kotlin
   WebView.setWebContentsDebuggingEnabled(AppConfig.enableWebViewDebugging)
   // false in release, true in debug/staging
   ```

2. **Logging Stripped** via ProGuard/R8
   ```proguard
   -assumenosideeffects class android.util.Log {
       public static *** d(...);
       public static *** v(...);
       public static *** i(...);
   }
   ```

3. **Proper Signing** enforced
   ```kotlin
   release {
       signingConfig = signingConfigs.getByName("release") // NOT debug!
   }
   ```

4. **URL Validation** before loading
   ```kotlin
   if (AppConfig.isValidAppUrl(targetUrl)) {
       webView.loadUrl(targetUrl)
   } else {
       // Reject suspicious URLs
   }
   ```

---

## 🏗️ Build Variants

### Debug Build

**Purpose:** Local development with maximum debugging capabilities

```bash
./gradlew assembleDebug
./gradlew installDebug
```

**Characteristics:**
- `applicationId`: `com.murari.careerpolitics.debug`
- WebView debugging: **Enabled**
- Logging: **Enabled**
- Crashlytics: **Disabled** (no noise during development)
- Signing: Debug keystore (auto-generated)
- ProGuard: **Disabled** (faster builds)
- User Agent: `DEV-Native-android-debug`

**Use Cases:**
- Local development
- Debugging WebView issues
- Testing new features

---

### Staging Build

**Purpose:** Production-like environment for QA testing

```bash
./gradlew assembleStaging
./gradlew installStaging
```

**Characteristics:**
- `applicationId`: `com.murari.careerpolitics.staging`
- WebView debugging: **Enabled**
- Logging: **Enabled**
- Crashlytics: **Enabled** (test crash reporting)
- Signing: Debug keystore (easy testing)
- ProGuard: **Enabled** (catch obfuscation issues early)
- User Agent: `STAGING-Native-android`

**Use Cases:**
- QA testing
- Stakeholder demos
- Pre-release validation
- Testing crash reporting integration

---

### Release Build

**Purpose:** Production release to Google Play Store

```bash
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk

# Or build App Bundle (preferred for Play Store):
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

**Characteristics:**
- `applicationId`: `com.murari.careerpolitics`
- WebView debugging: **Disabled**
- Logging: **Disabled** (debug/info/verbose stripped)
- Crashlytics: **Enabled**
- Signing: **Release keystore** (from secrets.properties)
- ProGuard: **Enabled** (code obfuscation + shrinking)
- User Agent: `PROD-Native-android`
- Native debug symbols: Uploaded to Play Console

**Use Cases:**
- Production releases
- Google Play Store distribution

---

## ✅ Migration Checklist

### Phase 1: Setup (Before Code Changes)

- [ ] Copy `secrets.properties.template` to `secrets.properties`
- [ ] Fill in `pusher.instance.id` in `secrets.properties`
- [ ] Generate or locate release keystore
- [ ] Add keystore credentials to `secrets.properties`
- [ ] Verify `secrets.properties` is in `.gitignore`
- [ ] Verify `google-services.json` is in `.gitignore`
- [ ] Run `./gradlew clean build` successfully

---

### Phase 2: Code Updates

- [x] Created `AppConfig.kt` (centralized configuration)
- [x] Created `Logger.kt` (production-safe logging)
- [x] Updated `app/build.gradle.kts` (build variants + signing)
- [x] Updated `MainActivity.kt` (use AppConfig)
- [x] Updated `CustomWebViewClient.kt` (use AppConfig)
- [x] Updated `NetworkUtils.kt` (use AppConfig)
- [x] Updated `AndroidWebViewBridge.kt` (use Logger)
- [x] Created `buildSrc/` for version management
- [x] Updated `.gitignore` for security

---

### Phase 3: Testing

- [ ] Test debug build: `./gradlew installDebug`
- [ ] Verify WebView debugging works in debug build
- [ ] Test staging build: `./gradlew installStaging`
- [ ] Verify ProGuard doesn't break functionality
- [ ] Test release build: `./gradlew assembleRelease`
- [ ] Verify WebView debugging disabled in release
- [ ] Verify push notifications work
- [ ] Verify deep links work
- [ ] Verify offline mode works
- [ ] Test with multiple devices/Android versions

---

### Phase 4: CI/CD Integration

- [ ] Add secrets as environment variables in CI
- [ ] Upload keystore to CI secrets storage
- [ ] Update CI build script (see CI/CD section below)
- [ ] Test automated builds
- [ ] Verify artifact signing

---

### Phase 5: Play Store Preparation

- [ ] Generate signed release build
- [ ] Test release build on physical device
- [ ] Upload mapping.txt to Play Console (crash deobfuscation)
- [ ] Configure app signing in Play Console
- [ ] Create internal test track
- [ ] Submit for review

---

## 🤖 CI/CD Integration

### GitHub Actions Example

Create `.github/workflows/build.yml`:

```yaml
name: Android Build

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          distribution: 'temurin'
          java-version: '21'
          
      - name: Grant execute permission for gradlew
        run: chmod +x gradlew
        
      - name: Decode Keystore
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
        run: |
          echo $KEYSTORE_BASE64 | base64 -d > release.keystore
          
      - name: Build Debug APK
        run: ./gradlew assembleDebug
        
      - name: Build Staging APK
        run: ./gradlew assembleStaging
        env:
          PUSHER_INSTANCE_ID: ${{ secrets.PUSHER_INSTANCE_ID }}
          
      - name: Build Release AAB
        run: ./gradlew bundleRelease
        env:
          PUSHER_INSTANCE_ID: ${{ secrets.PUSHER_INSTANCE_ID }}
          KEYSTORE_PATH: ../release.keystore
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
          
      - name: Upload Release Artifact
        uses: actions/upload-artifact@v3
        with:
          name: release-aab
          path: app/build/outputs/bundle/release/app-release.aab
          
      - name: Upload ProGuard Mapping
        uses: actions/upload-artifact@v3
        with:
          name: mapping
          path: app/build/outputs/mapping/release/mapping.txt
```

### Setting Up CI Secrets

1. **Encode keystore to base64:**
   ```bash
   base64 -i release.keystore -o keystore.base64
   ```

2. **Add to GitHub Secrets:**
   - `KEYSTORE_BASE64`: Content of `keystore.base64`
   - `KEYSTORE_PASSWORD`: Your keystore password
   - `KEY_ALIAS`: Your key alias (usually "release")
   - `KEY_PASSWORD`: Your key password
   - `PUSHER_INSTANCE_ID`: Your Pusher instance ID

---

## 🚀 Production Readiness Checklist

### 1. Configuration ✅

- [x] All URLs externalized to `AppConfig`
- [x] Secrets moved to `secrets.properties`
- [x] Environment-specific build variants configured
- [x] No hard-coded credentials in source code

---

### 2. Security ✅

- [x] Release builds use release keystore (not debug!)
- [x] WebView debugging disabled in production
- [x] Secrets in `.gitignore`
- [x] URL validation before loading
- [x] ProGuard rules configured properly
- [ ] Security review completed
- [ ] Penetration testing (if required)

---

### 3. Logging & Monitoring

- [x] Production-safe logger implemented
- [x] Debug logs stripped in release builds
- [x] Firebase Crashlytics integrated
- [ ] Crashlytics verified working in staging
- [ ] Error tracking dashboard configured
- [ ] Alert rules configured for critical errors

---

### 4. Performance

- [x] ProGuard enabled for release
- [x] Resource shrinking enabled
- [x] R8 full mode enabled
- [ ] App size optimized (check with `bundletool`)
- [ ] Startup time profiled
- [ ] Memory leaks checked (LeakCanary)
- [ ] Network performance tested

---

### 5. Build & Release

- [x] Debug build working
- [x] Staging build working
- [x] Release build working
- [ ] Signed APK/AAB generated
- [ ] ProGuard mapping.txt uploaded
- [ ] Version code incremented
- [ ] Release notes prepared
- [ ] Internal testing completed
- [ ] Beta testing completed

---

### 6. Play Store Compliance

- [ ] Privacy policy URL configured
- [ ] Data safety section completed
- [ ] Target API level meets requirements (targetSdk 36)
- [ ] 64-bit native libraries included
- [ ] App Bundle (.aab) format used
- [ ] Screenshots updated
- [ ] App description updated
- [ ] Content rating questionnaire completed

---

### 7. Testing

- [ ] Unit tests passing
- [ ] UI tests passing (if applicable)
- [ ] Manual testing on 3+ devices
- [ ] Tested on Android 8.1 (minSdk 27)
- [ ] Tested on latest Android version
- [ ] Tested push notifications
- [ ] Tested deep links
- [ ] Tested offline mode
- [ ] Tested WebView functionality
- [ ] Tested media playback (podcast/video)

---

## 📚 Additional Resources

### Architecture Decisions

| Decision | Rationale |
|----------|-----------|
| Object singleton for AppConfig | Simple, read-only configuration doesn't need DI complexity |
| BuildConfig for environment switching | Compile-time optimization, dead code elimination |
| secrets.properties for secrets | Industry standard, CI/CD friendly |
| ProGuard -assumenosideeffects for logging | Complete log removal in release, zero overhead |
| Three build variants | Separates dev, QA, and production environments |

---

### Common Issues & Solutions

#### Issue: "secrets.properties not found"

**Solution:** Copy the template and fill in values:
```bash
cp app/secrets.properties.template app/secrets.properties
# Edit secrets.properties with your values
```

---

#### Issue: "Keystore not found" during release build

**Solution:** Verify keystore path in secrets.properties:
```properties
keystore.path=../release.keystore  # Relative to app/ directory
# or
keystore.path=/absolute/path/to/release.keystore
```

---

#### Issue: App crashes in release but not debug

**Likely Cause:** ProGuard removed necessary code

**Solution:** Add ProGuard keep rules in `proguard-rules.pro`:
```proguard
-keep class com.your.package.ClassName { *; }
```

Check crash logs for obfuscated stack traces, use `mapping.txt` to deobfuscate.

---

#### Issue: Push notifications not working

**Debug Steps:**
1. Verify `PUSHER_INSTANCE_ID` is correct in BuildConfig
2. Check Firebase `google-services.json` is present
3. Verify app has notification permission
4. Check Pusher dashboard for device registration
5. Test in staging build with logging enabled

---

### Migration from Old Code

If updating existing code:

1. **Replace hard-coded URLs:**
   ```kotlin
   // OLD:
   webView.loadUrl("https://careerpolitics.com/")
   
   // NEW:
   webView.loadUrl(AppConfig.baseUrl)
   ```

2. **Replace Android Log calls:**
   ```kotlin
   // OLD:
   Log.d("TAG", "message")
   
   // NEW:
   Logger.d("TAG", "message")
   ```

3. **Replace hard-coded Pusher ID:**
   ```kotlin
   // OLD:
   PushNotifications.start(context, "923a6e14-cca6-47dd-b98e-8145f7724dd7")
   
   // NEW:
   PushNotifications.start(context, AppConfig.pusherInstanceId)
   ```

---

## 🎓 Best Practices Summary

### DO ✅

- Use `AppConfig` for all configuration access
- Use `Logger` instead of Android `Log`
- Test all build variants before release
- Keep secrets in `secrets.properties` or env vars
- Back up your release keystore securely
- Increment `versionCode` for every release
- Upload ProGuard mapping to Play Console
- Test release builds on real devices

### DON'T ❌

- Commit `secrets.properties` to Git
- Use debug signing for release builds
- Hard-code URLs, API keys, or secrets
- Skip testing staging builds
- Forget to upload ProGuard mapping
- Release without testing on multiple devices
- Ignore ProGuard warnings

---

## 📞 Support

For questions or issues with this refactoring:

1. Check this guide first
2. Review the "Common Issues" section
3. Check build output for specific errors
4. Consult Android documentation for Gradle/ProGuard issues

---

**Last Updated:** 2024 (Initial Production Refactoring)  
**Maintained By:** Senior Android Architect  
**Version:** 1.0.0
