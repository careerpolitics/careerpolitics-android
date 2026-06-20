# CareerPolitics Android — Modern Architecture Blueprint (2026)

This proposal modernizes your current WebView-centric app into a scalable Android architecture using:

- **MVVM**
- **Clean Architecture**
- **Hilt**
- **Jetpack Compose** (recommended)
- **Modularization** with a practical path from **single-module** to multi-module

---

## 1) Recommended Architecture (2026)

Use a **Hybrid App Shell** approach:

- Keep WebView for existing platform screens and fast iteration.
- Add native Compose screens for:
  - Auth/session bootstrap
  - Push notification inbox/preferences
  - Settings/account/security
  - Native fallback/error/offline surfaces

### Why this is a good fit

- Preserves business velocity with existing web stack.
- Improves Android UX and reliability for critical mobile flows.
- Enables gradual migration from WebView screens to native Compose.

---

## 2) Layer-by-layer responsibilities (Clean + MVVM)

### Presentation Layer

**What lives here**
- Compose UI screens, components, themes
- ViewModels (state + UI events)
- Navigation graph and route arguments
- UI state models (immutable)

**Rules**
- No Retrofit/Room/WebView internals directly in composables.
- ViewModel depends on UseCases only.
- One directional data flow: `UserAction -> ViewModel -> UseCase -> State`.

### Domain Layer

**What lives here**
- UseCases / Interactors (`LoginWithGoogleUseCase`, `HandleDeepLinkUseCase`)
- Domain models (`Session`, `UserProfile`, `NotificationPayload`)
- Repository contracts/interfaces
- Business policies (token freshness rules, deep-link routing rules)

**Rules**
- Pure Kotlin where possible.
- No Android framework dependencies.
- Central place for business invariants.

### Data Layer

**What lives here**
- Repository implementations
- Remote data sources (API, WebView bridge contracts)
- Local data sources (DataStore/Room/Encrypted storage)
- DTO mappers (`Dto <-> Domain`, `Entity <-> Domain`)
- Push/deep link/auth adapters

**Rules**
- Implements domain repository interfaces.
- Handles caching, source-of-truth, and mapping.
- Wraps errors to domain-safe result types.

### Core/App Platform Layer

**What lives here**
- Hilt modules and qualifiers
- Logging/analytics/crash reporting abstraction
- Dispatchers and coroutine scopes
- Feature flags and remote config
- Security primitives (keystore, encrypted storage, cert pinning)

**Rules**
- Shared infra only.
- No feature-specific business logic.

---

## 3) Folder structure (start as single module, migration-ready)

Use this in `app/src/main/java/...` for now:

```text
app/
  src/main/java/com/careerpolitics/
    app/
      CareerPoliticsApp.kt
      MainActivity.kt
      navigation/
        AppNavGraph.kt
        Destinations.kt
    core/
      common/
        Result.kt
        DispatcherProvider.kt
      ui/
        theme/
        components/
      network/
        ApiClient.kt
        NetworkMonitor.kt
      security/
        TokenCipher.kt
      analytics/
        AnalyticsTracker.kt
      di/
        AppModule.kt
        NetworkModule.kt
        AuthModule.kt
        NotificationModule.kt
    feature/
      shell/
        presentation/
          ShellViewModel.kt
          ShellScreen.kt
        data/
          WebViewBridge.kt
          ShellRepositoryImpl.kt
        domain/
          ShellRepository.kt
          LoadInitialUrlUseCase.kt
      auth/
        presentation/
          AuthViewModel.kt
          AuthScreen.kt
          AuthUiState.kt
        domain/
          model/
            Session.kt
          usecase/
            SignInWithGoogleUseCase.kt
            RefreshSessionUseCase.kt
          repository/
            AuthRepository.kt
        data/
          remote/
            GoogleAuthDataSource.kt
          local/
            SessionLocalDataSource.kt
          repository/
            AuthRepositoryImpl.kt
          mapper/
            SessionMapper.kt
      notifications/
        presentation/
          NotificationCenterViewModel.kt
        domain/
          usecase/
            SyncPushTokenUseCase.kt
            HandlePushOpenUseCase.kt
          repository/
            NotificationRepository.kt
        data/
          service/
            CareerPoliticsFirebaseMessagingService.kt
          repository/
            NotificationRepositoryImpl.kt
      deeplink/
        domain/
          usecase/
            ResolveDeepLinkUseCase.kt
        data/
          DeepLinkParser.kt
```

### Modularization plan

- **Phase A (now)**: single `:app` module, but strict package boundaries above.
- **Phase B**: extract to `:core:*` and `:feature:*` modules without refactor churn.
- **Phase C**: dynamic feature modules for low-frequency experiences if size/perf requires it.

---

## 4) Technology decisions (2026 defaults)

- UI: **Jetpack Compose + Material 3**
- State: `StateFlow` + immutable `UiState`
- Navigation: `navigation-compose` + typed routes
- DI: **Hilt** with constructor injection and entry points only where unavoidable
- Async: Coroutines + structured concurrency
- Storage: DataStore (preferences), Room (structured/offline), EncryptedSharedPreferences only if legacy
- Network: Retrofit/OkHttp + Kotlin serialization
- Background work: WorkManager for guaranteed jobs (token sync/retry)
- Testing: JUnit5 (where supported), Turbine for flows, Compose UI tests

---

## 5) How each current feature maps into architecture

### OAuth (Google native)
- `feature/auth/data/remote/GoogleAuthDataSource`
- `SignInWithGoogleUseCase` handles auth flow orchestration
- `SessionLocalDataSource` persists tokens securely
- `RefreshSessionUseCase` centralizes renewal policy

### FCM push notifications
- `CareerPoliticsFirebaseMessagingService` only receives and delegates
- `HandlePushOpenUseCase` maps payload -> domain deep link route
- `SyncPushTokenUseCase` via WorkManager for reliable backend registration

### Deep links
- `ResolveDeepLinkUseCase` is the single routing decision point
- Parser in data layer, navigation command emitted from ViewModel

### Session management
- Domain-owned session model and freshness rules
- Data layer owns storage and refresh API integration
- Presentation consumes only `SessionState` from ViewModel

### WebView shell
- WebView remains a feature module (`feature/shell`)
- Bridge calls wrapped in repository abstractions (no direct JS bridge usage from UI)
- Add native fallback for auth expired / offline / unsupported route

---

## 6) Recommended migration sequence

1. **Create architecture skeleton** (packages, Hilt base modules, App class).
2. **Introduce Compose app shell** around existing WebView activity flow.
3. **Move auth/session into domain+data+presentation boundaries.**
4. **Move deep-link resolution into a domain use case.**
5. **Refactor FCM handling into use cases + WorkManager sync.**
6. **Adopt unified UI state/error model across all ViewModels.**
7. **Extract modules when boundaries are stable.**

---

## 7) Prompts you can use to build this step-by-step

Use these prompts one by one with your coding assistant.

### Prompt 1 — Project skeleton + Hilt

> Create a clean-architecture package skeleton in my Android app with `presentation/domain/data/core` boundaries, all inside single `:app` module. Add Hilt setup (`@HiltAndroidApp`, `@AndroidEntryPoint`, base DI modules for dispatchers, network, storage). Do not change business behavior yet.

### Prompt 2 — Compose app shell + navigation

> Add Jetpack Compose app shell with `MainActivity` using `setContent`, Material 3 theme, and navigation graph. Keep existing WebView flow functional by embedding it as a screen/interop host. Create typed destinations and baseline `UiState` patterns.

### Prompt 3 — Auth feature isolation

> Refactor Google OAuth and session management into `feature/auth` with MVVM + Clean Architecture. Create `SignInWithGoogleUseCase`, `RefreshSessionUseCase`, `AuthRepository` interface, and `AuthRepositoryImpl`. Store session securely and expose `AuthUiState` from ViewModel.

### Prompt 4 — Deep link domain handling

> Implement deep-link handling via `ResolveDeepLinkUseCase`. Parse incoming links in data layer, convert to domain routes, and have ViewModel emit navigation intents. Remove deep-link decisions from Activity/WebView-specific code.

### Prompt 5 — FCM clean flow

> Refactor Firebase Messaging integration so service delegates to use cases only. Implement `SyncPushTokenUseCase` (with WorkManager retry) and `HandlePushOpenUseCase`. Ensure notification tap routing goes through domain and navigation layer.

### Prompt 6 — WebView shell hardening

> Isolate WebView bridge into `feature/shell/data` and expose interactions via repository interfaces. Add native error states for offline, expired session, and unsupported links. Keep behavior backward compatible.

### Prompt 7 — Testing layer coverage

> Add tests: ViewModel state tests (Turbine), UseCase unit tests, repository tests with fakes, and one Compose navigation test for auth-to-shell route. Focus on critical flows: login, token refresh, push open, and deep-link routing.

### Prompt 8 — Modularization extraction

> Extract stable packages into Gradle modules: `:core:common`, `:core:ui`, `:core:data`, `:feature:auth`, `:feature:shell`, `:feature:notifications`, `:feature:deeplink`. Keep APIs internal by default and expose only required interfaces.

### Prompt 9 — Performance + observability pass

> Add startup tracing, baseline profiles, strict mode in debug, and structured analytics events for auth/deeplink/push funnel. Produce a before-vs-after startup and crash-free metrics checklist.

---

## 8) Single-module vs modularized recommendation

If team size is small or timelines are tight, start **single-module with strict package boundaries**.

Move to Gradle modules only after:
- package boundaries are respected,
- tests are in place,
- ownership per feature is clear.

This avoids “premature modularization” while keeping you fully migration-ready.
