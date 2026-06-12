# Review — 2026-06-12

Panel: Sofia (Staff Engineer — correctness, architecture, lifecycle) and Pavel
(Security & Performance — leaks, threading, recomposition, data exposure).
Every source and build file read, newest first, before any tests were written.

## Findings

| # | File:line | Severity | Finding | Resolution |
|---|-----------|----------|---------|------------|
| 1 | `ring/RingService.kt:91` | **Major** | A second alarm firing while a ring is active was silently dropped: `session != null` skipped `begin()`, so the newcomer's record was never created **and** — because re-arming lives in `begin()` — a repeating second alarm was never rescheduled (dead until edited) and a one-shot stayed enabled-but-spent. | **Fixed.** `handleOverlappingAlarm()` (line 111): logs the skipped occurrence, re-arms repeating alarms via `AlarmScheduler.scheduleNext`, disables one-shots. The live ring keeps the stage (MVP overlap policy — no second concurrent ring; documented in ROADMAP under "alarm queueing"). |
| 2 | `feature/alarms/AlarmsScreen.kt:288` | Minor | POST_NOTIFICATIONS banner stayed visible after the user granted the permission via system Settings — `granted` was only refreshed by the in-app launcher callback. | **Fixed.** `LifecycleResumeEffect` re-checks the grant on every resume. |
| 3 | `feature/pact/PactScreen.kt:272` | Minor | Pact members rendered in a plain `Row`: chips overflow off-screen for pacts with many or long-named members. | **Fixed.** `FlowRow` (ExperimentalLayoutApi opt-in) wraps chips onto new lines. |
| 4 | `feature/alarms/AlarmsScreen.kt:182` | Minor | Two-pane (≥840 dp) layout nests the editor's `Scaffold` inside the list `Scaffold`, stacking two top bars. Functional (inner bar carries the Delete action) but visually heavy. | Logged — roadmap note: dedicated two-pane chrome (single shared top bar, editor actions inline) in a UI-polish pass. |
| 5 | `feature/alarms/AlarmsScreen.kt:271` | Minor | The alarm `Switch` has no own `contentDescription`; screen readers announce only the row text. Mitigated because the card text (time, label, next-fire) is adjacent. | Logged — roadmap note: a11y pass making the whole row `toggleable()` with proper semantics instead of a bare Switch. |
| 6 | `feature/alarms/AlarmsViewModel.kt:84` | Minor | Rapid double-toggle races `setEnabled` + scheduler calls; last write wins. No corruption (both sides idempotent per alarm id), worst case one redundant schedule/cancel pair. | Accepted — documented here; not worth serializing per-alarm for MVP. |
| 7 | `ring/RingService.kt:91` | Minor | Residual race in the overlap fix: a sticky restart that finds **no** active record (service about to stop) plus a simultaneous real fire treats the fire as an overlap — occurrence skipped, alarm re-armed. Window ≈ one Room query (~ms) and requires the service to have been killed mid-nothing. | Accepted — window negligible; queuing machinery would outweigh the risk. |

Summary: 0 blockers, 1 major, 6 minors — all blockers/majors resolved.

## Verified-clean sweeps

- No `android.util.Log`, `GlobalScope`, `runBlocking`, `allowMainThreadQueries`, or `!!` anywhere in `app/src/main`.
- No hardcoded UI strings — every user-visible string goes through resources (`strings.xml` inventory complete, apostrophes escaped).
- Every `catch` logs through Timber; `CancellationException` is rethrown in `FirestorePactGateway.runGateway`; no secrets/PII in any log statement (uids logged only at debug-relevant warn level, never API keys).
- `collectAsStateWithLifecycle` ↔ `lifecycle-runtime-compose` and `hiltViewModel()` ↔ `hilt-navigation-compose` both present in the catalog; `@Serializable` routes ↔ serialization plugin applied; Room/Hilt ↔ KSP applied.
- Manifest: `WakePactApp` in `android:name`, launcher activity exported, `RingActivity`/`RingService`/receivers declared non-exported, `INTERNET` present for the optional gateway, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` matches the declared service type.
- No Activity/Context captured in ViewModels or singletons (`StepSourceSelector` and `FirebaseProvider` hold only `@ApplicationContext`); all collectors run in `viewModelScope`/service scope; service scope cancelled in `onDestroy` with the wake lock released.
- Lazy lists use stable `key`s; UiState classes are immutable `data class`es with read-only lists.
- Dispatcher discipline per ADR-001: repositories/gateway own IO via injected `@IoDispatcher`; no `withContext(Dispatchers.IO)` in ViewModels.
- ADR conformance: ADR-002 (service owns session; activity is a dumb window — confirmed: `RingActivity` holds zero ring logic), ADR-003 (Firebase optional at runtime; `PactModule` selects gateway off `FirebaseProvider.app`), ADR-004 (validator gates both step sources), ADR-005 (policy timers evaluate through pure `RingPolicy`). No ADR conflicts; none superseded.

Gate: findings table complete, zero open Blockers/Majors, validator green after fixes (0 errors; 4 expected pre-Phase-7 test warnings).
