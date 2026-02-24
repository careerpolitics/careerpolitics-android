# Architecture Redesign (2026 Baseline)

## Scope of this phase
This document defines the target architecture and the incremental migration plan for the current hybrid Android app (WebView + native integrations). No big-bang rewrite is planned.

---

## 1) Current architecture weaknesses (observed)

### A. Activity-centric orchestration (high coupling)
`MainActivity` currently owns WebView setup, deep links, Google OAuth flow, notification permission, push initialization, gesture injection, and intent routing in one class. This creates high change risk and weak testability.  

### B. Domain and platform logic are mixed
Platform SDK calls (`GoogleSignIn`, `FirebaseMessaging`, `PushNotifications`, `WebView`) are directly invoked from UI layer code, preventing clean boundaries and making unit tests expensive.

### C. Lifecycle and state are not ViewModel-driven
Ephemeral UI/flow state (`isGoogleSignInInProgress`, `pendingGoogleOAuthState`, splash readiness, callback handling) is activity-local instead of state-holder driven, which complicates process death handling and deterministic tests.

### D. Eventing and cross-feature communication are implicit
EventBus usage and direct callbacks across unrelated concerns increase hidden dependencies and reduce traceability.

### E. Single module limits scalability
Current codebase is mostly in `:app`, mixing feature/UI/data/integration concerns. Build isolation, ownership boundaries, and focused testing are limited.

### F. Service boundaries are weak
Push, auth, deep-link routing, and WebView bridge contracts are not modeled as explicit use cases/repositories, so behavior is tied to concrete framework implementations.

---

## 2) Target architecture (Kotlin + MVVM + Clean + Hilt + modular)

## Architectural principles
- **UI-only Activities/Fragments/Compose entry points**.
- **Feature ViewModels own presentation state and intent handling**.
- **Use cases orchestrate business flows**.
- **Repositories/gateways abstract platform + network providers**.
- **Hilt provides composition root and lifecycle-aware scoping**.
- **One-way data flow with `StateFlow` + sealed UI events/effects**.

## Proposed Gradle modules

```text
:app                                // Thin host app, navigation shell, manifest merge point

:core:common                        // Result/Either, dispatchers, logging contracts, shared utils
:core:ui                            // design system, common compose/xml wrappers, ui state primitives
:core:network                       // okhttp/retrofit config, interceptors (if/when API needed)
:core:webview                       // hardened WebView abstractions + bridge contracts
:core:testing                       // test fixtures, fakes, coroutine test rules
:core:analytics                     // analytics/crash reporting contracts + impl

:feature:shell                      // WebView container, app shell ViewModel, top-level routing state
:feature:auth                       // OAuth (Google/GitHub) orchestration + callback handling
:feature:deeplink                   // deep-link parsing, validation, routing decisions
:feature:notifications              // push permission + FCM/Pusher registration flows
:feature:media                      // audio/video playback orchestration

:data:auth                          // concrete OAuth providers/adapters
:data:notifications                 // FCM/Pusher adapters, token sync
:data:web                           // web session/cookie/header policies
:data:device                        // connectivity/app lifecycle/device capabilities
```

## Package structure convention (inside each module)

```text
<module>/src/main/java/.../
  presentation/                     // ViewModel, UiState, UiEvent, UiEffect
  domain/                           // use cases, domain models, repository contracts
  data/                             // repository implementations, datasources, mappers
  di/                               // Hilt modules
```

For `:core:*` modules, use domain-neutral naming (`contracts`, `model`, `extensions`, `di`).

## Dependency direction
- `feature:* -> core:* + data:* (contracts only where possible)`
- `data:* -> core:*`
- `app -> feature:* + core:* + data:*` (temporary; reduced over time)
- No feature-to-feature concrete dependency; cross-feature interaction via contracts/events.

---

## 3) Incremental refactor plan (feature by feature)

## Phase 0 — Foundation (this sprint)
1. Introduce module skeleton and dependency graph.
2. Enable Hilt at app level; add base DI conventions.
3. Add architecture guardrails (Detekt/Lint module boundaries where feasible).

## Phase 1 — App shell extraction
1. Create `feature:shell` with `ShellViewModel` + `ShellUiState`.
2. Move non-UI orchestration out of `MainActivity`:
   - startup route decision
   - pending intent/deep-link intent interpretation
   - notification click routing decision
3. Keep `MainActivity` as rendering + delegate only.

## Phase 2 — Deep link feature
1. Add `feature:deeplink` parser/validator use cases.
2. Introduce `DeepLinkRepository` contract and Android implementation.
3. Replace direct URI checks in Activity with ViewModel-driven decisions.

## Phase 3 — Auth feature (Google/GitHub)
1. Move native OAuth orchestration to `feature:auth` use cases.
2. Wrap Google Sign-In SDK in `data:auth` adapter.
3. Expose `AuthEffect` for launch intents and callback completion.
4. Keep WebView callback URL generation in domain-level use case (testable).

## Phase 4 — Notifications feature
1. Move permission + registration orchestration to `feature:notifications`.
2. Encapsulate FCM/Pusher operations in repository adapters.
3. Add retry policy and structured error reporting.

## Phase 5 — WebView core hardening
1. Extract WebView settings policy, bridge registry, navigation decision engine into `core:webview`.
2. Add lifecycle-safe attach/detach APIs to avoid leaks.
3. Convert JS bridge commands to typed contract handlers.

## Phase 6 — Media feature isolation
1. Move audio/video orchestration into `feature:media` with explicit service-controller contracts.
2. Remove EventBus coupling with scoped flows/channels.

## Phase 7 — Stabilization
1. Add unit tests for all use cases and ViewModels.
2. Add integration tests for deep links, notification routing, OAuth callback.
3. Add baseline profile + startup/perf verification.

---

## 4) Non-functional architecture guardrails
- **Memory safety**: no activity references in singleton components; WebView lifecycle managed via dedicated owner class.
- **Error handling**: sealed error taxonomy (`Recoverable`, `UserActionRequired`, `Fatal`) + telemetry.
- **Observability**: structured logs with trace IDs for OAuth/deeplink/notification flows.
- **Security**: centralized URL allow-list policy, strict scheme/host validation, reduced JS interface surface.
- **Testability**: every feature has fake repository and deterministic coroutine dispatcher injection.

---

## 5) First implementation slice to execute next
- Create modules: `:core:common`, `:feature:shell`, `:feature:deeplink`, `:feature:auth`, `:feature:notifications`.
- Integrate Hilt in `:app` and provide bridge interfaces for current implementations.
- Migrate deep-link decision path first (lowest risk, high architectural payoff).

This sequence keeps production behavior stable while progressively replacing high-coupling paths.
