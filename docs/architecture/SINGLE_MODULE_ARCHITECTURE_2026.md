# CareerPolitics Android — 2026 Single-Module Architecture

This document proposes a **modern single-module architecture** for CareerPolitics while preserving your current hybrid strengths (WebView shell + native OAuth, push, deep links, and session controls).

> Goal: keep one Gradle module (`:app`) for delivery simplicity, while enforcing **Clean Architecture boundaries** at package level so migration to full modularization remains straightforward later.

---

## 1) Recommended stack (2026 baseline)

- **Kotlin + Coroutines + Flow**
- **MVVM + UDF (unidirectional data flow)**
- **Clean Architecture (presentation / domain / data / platform)**
- **Hilt** for DI and runtime wiring
- **Jetpack Compose** as primary UI layer
  - Keep `WebView` through `AndroidView` interop for hybrid screens
- **Navigation Compose** + deep link integration
- **Room/DataStore** for local persistence/state/session metadata
- **WorkManager** for durable background sync/retry operations
- **Firebase Cloud Messaging** via dedicated gateway

---

## 2) Why Compose is still recommended for a WebView-heavy app

Even with a WebView-centric product, Compose adds value:

- Better host UI composition (top bars, loading, dialogs, error overlays, permission prompts)
- Cleaner state rendering from `StateFlow`
- Easier incremental native screens (settings, notifications center, auth handoff)
- Better testability with Compose UI tests

Use WebView as a feature surface, not as the app architecture itself.

---

## 3) Single-module folder structure (`:app`)

```text
app/src/main/java/com/murari/careerpolitics/
  app/
    CareerPoliticsApp.kt                // @HiltAndroidApp
    MainActivity.kt                     // Compose host only
    navigation/
      AppNavGraph.kt
      Destinations.kt

  core/
    common/
      result/AppResult.kt
      error/AppError.kt
      dispatchers/DispatcherProvider.kt
      util/
    designsystem/
      theme/
      components/
    analytics/
      AnalyticsTracker.kt
      CrashReporter.kt

  platform/                             // Android/Firebase/WebView concrete adapters
    auth/
      GoogleOAuthLauncher.kt
      CustomTabOAuthLauncher.kt
    notifications/
      FcmTokenProvider.kt
      NotificationPermissionManager.kt
      PushMessageRouter.kt
    deeplink/
      AndroidDeepLinkParser.kt
      IntentRouter.kt
    webview/
      CareerPoliticsWebViewClient.kt
      CareerPoliticsChromeClient.kt
      WebBridge.kt
      CookieSessionManager.kt

  feature/
    shell/
      presentation/
        ShellViewModel.kt
        ShellUiState.kt
        ShellEvent.kt
        ShellEffect.kt
      ui/
        ShellScreen.kt

    auth/
      domain/
        model/
        repository/AuthRepository.kt
        usecase/
          StartOAuthUseCase.kt
          CompleteOAuthUseCase.kt
          LogoutUseCase.kt
      data/
        AuthRepositoryImpl.kt
        mapper/
      presentation/
        AuthViewModel.kt
        AuthUiState.kt
        AuthEffect.kt
      di/
        AuthModule.kt

    notifications/
      domain/
        repository/NotificationsRepository.kt
        usecase/
          RegisterPushTokenUseCase.kt
          HandlePushTapUseCase.kt
      data/
        NotificationsRepositoryImpl.kt
      presentation/
        NotificationsViewModel.kt
      di/
        NotificationsModule.kt

    deeplink/
      domain/
        repository/DeepLinkRepository.kt
        usecase/
          ResolveDeepLinkUseCase.kt
      data/
        DeepLinkRepositoryImpl.kt
      presentation/
        DeepLinkCoordinator.kt
      di/
        DeepLinkModule.kt

    session/
      domain/
        repository/SessionRepository.kt
        usecase/
          ObserveSessionUseCase.kt
          RefreshSessionUseCase.kt
      data/
        SessionRepositoryImpl.kt
      presentation/
        SessionViewModel.kt
      di/
        SessionModule.kt

  data/
    local/
      datastore/
      room/
    remote/
      api/
      dto/
      interceptors/
    sync/
      worker/

  di/
    AppModule.kt                        // app-wide bindings
    DispatcherModule.kt
    NetworkModule.kt
    WebViewModule.kt
```

---

## 4) Layer-by-layer responsibilities

## Presentation layer (MVVM)

- Compose screens + `ViewModel`
- Receives user actions (`UiEvent`), emits immutable `UiState` + one-time `UiEffect`
- No direct SDK calls (no direct Firebase/WebView/CustomTabs logic)
- Depends only on domain use cases

**Rules:**
- `ViewModel` is state machine; UI is pure renderer.
- Keep state in `StateFlow`; side effects via `SharedFlow`/`Channel`.

## Domain layer (Clean core)

- Business rules and use cases:
  - `ResolveDeepLinkUseCase`
  - `StartOAuthUseCase`
  - `RegisterPushTokenUseCase`
  - `ObserveSessionUseCase`
- Repository interfaces (contracts), domain models, policy decisions
- Pure Kotlin where possible; platform-free

**Rules:**
- No Android framework classes.
- One use case should solve one business action.

## Data layer

- Repository implementations connecting domain contracts to data/platform
- Mapping DTO/entity ↔ domain model
- Source-of-truth policy (cache vs remote vs WebView session)

**Rules:**
- Coordinate local/remote/platform sources.
- Convert errors to domain-level `AppError`.

## Platform layer

- Android and SDK adapters only:
  - Chrome Custom Tabs OAuth launch/callback glue
  - FCM token retrieval and push routing
  - Intent/deep-link parsing
  - WebView clients, JS bridge, cookie/session glue

**Rules:**
- Keep all framework glue here.
- Expose simple interfaces consumed by data/domain.

---

## 5) Hilt dependency strategy

- `SingletonComponent`:
  - repositories, API clients, WebView/session managers, analytics trackers
- `ViewModelComponent`:
  - use cases and view-model-scoped collaborators
- Qualify dispatchers (`@IoDispatcher`, `@MainDispatcher`, `@DefaultDispatcher`)

Recommended binding style:
- `feature/*/di/*Module.kt` for feature-local bindings
- global cross-cutting modules in `/di`

---

## 6) Flow design for your key concerns

## OAuth (Custom Tabs)

1. `AuthViewModel` handles `LoginClicked`.
2. Calls `StartOAuthUseCase` → gets authorization URI + PKCE/state.
3. Emits `AuthEffect.LaunchCustomTab(uri)`.
4. Callback deep link arrives through nav/intent.
5. `CompleteOAuthUseCase` validates state, exchanges code, updates session.

## Push notifications (FCM)

1. `NotificationsViewModel` checks permission state.
2. `RegisterPushTokenUseCase` gets token via platform gateway.
3. Token sync through repository (retry via WorkManager).
4. Notification tap payload routed by `HandlePushTapUseCase` into app destination.

## Deep links

1. Intent URI sent to `ResolveDeepLinkUseCase`.
2. Validate allowlist/host/path policy.
3. Output typed navigation target (web route/native destination/external browser).

## Session management

- Single source of truth in `SessionRepository`.
- Observe session with `Flow<SessionState>`.
- On expiry/invalid cookie/token: emit logout effect + clear local state + route to auth.

---

## 7) Recommended conventions

- Feature package standard inside single module:
  - `feature/<name>/presentation|domain|data|di`
- State naming:
  - `XUiState`, `XEvent`, `XEffect`, `XViewModel`
- Avoid cross-feature direct calls; collaborate via domain contracts/use cases.
- Keep web-specific policies explicit (allowed hosts, JS bridge command allowlist).

---

## 8) Migration path from current code (incremental)

1. Convert `MainActivity` to thin Compose host.
2. Extract current WebView logic into `feature/shell` + `platform/webview`.
3. Move OAuth orchestration to `feature/auth` use cases + platform launcher.
4. Move push registration/tap handling to `feature/notifications`.
5. Move deep-link parsing to `feature/deeplink`.
6. Centralize session rules in `feature/session`.
7. Add tests per feature (ViewModel + use-case first).

This achieves modern architecture now, without forcing immediate multi-module complexity.
