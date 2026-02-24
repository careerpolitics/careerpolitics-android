# Startup and Performance Verification

## Baseline profile artifact
- Baseline profile rules are defined in `app/src/main/baseline-prof.txt`.

## Verification checklist
1. Build release APK/AAB and confirm baseline profile packaged.
2. Run Macrobenchmark startup tests on API 34+ physical device.
3. Validate cold/warm/hot startup percentiles after each major refactor slice.
4. Track regressions in CI using perf thresholds.

## Suggested commands
- `./gradlew :app:assembleRelease`
- `./gradlew :app:lintRelease`
- `adb shell am force-stop com.murari.careerpolitics`
- `adb shell am start -W com.murari.careerpolitics/.activities.MainActivity`
