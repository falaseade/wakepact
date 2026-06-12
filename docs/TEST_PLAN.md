# Test Plan

All tests are pure JVM under `app/src/test` (JUnit4 + kotlinx-coroutines-test +
Turbine + MockK). `MainDispatcherRule` swaps `Dispatchers.Main`;
`FakePactGateway` is a behavioral in-memory backend (writes mutate its flows,
calls are recorded, failures injectable). MockK is used only at boundaries we
don't own outright in a JVM test: the Room-backed `AlarmRepository`, the
DataStore-backed `IdentityRepository`, and the AlarmManager-backed
`AlarmScheduler`. `androidx.lifecycle.SavedStateHandle` appears in tests by
design — it is JVM-safe (KMP) and exactly what the process-death tests exercise.

## Coverage map

| Acceptance criterion | Test(s) |
|---|---|
| AC-1.1 weekday alarm computes next trigger | `NextTriggerCalculatorTest."AC-1_1 weekday alarm before its time today fires today"` |
| AC-1.2 one-shot past today fires tomorrow | `NextTriggerCalculatorTest."AC-1_2 one-shot whose time passed today fires tomorrow"` (+ exact-fire-time and week-wrap edges) |
| AC-1.3 reboot reschedules enabled alarms | Not unit-testable (BootReceiver + AlarmManager) — see instrumentation list |
| AC-2.1 rhythmic chains credit steps | `StepChainValidatorTest."AC-2_1 …retroactively at the third"`, `"…each add one"`, `"interval bounds are inclusive"` |
| AC-2.2 gap restarts the chain | `StepChainValidatorTest."AC-2_2 a long gap restarts the chain…"`, `"shake-speed intervals never credit a step"` |
| AC-2.3 mission completes exactly once at the goal | `StepChainValidatorTest."AC-2_3 the goal count is reached at the exact validated step"` + `RingSessionTest."markProof transitions…"` (service once-only guard → instrumentation) |
| AC-2.4 denied permission falls back to accelerometer | Not unit-testable (SensorManager) — selector logic is two lines over Android APIs; see instrumentation list |
| AC-3.1 buddy's PROOF_DONE appears in pending live | `PactViewModelTest."AC-3_1 another member's unresolved ring appears in pending without refresh"` |
| AC-3.2 deactivate writes my id and clears pending | `PactViewModelTest."AC-3_2 deactivating writes my auth uid and removes the event from pending"` |
| AC-4.1 remote DEACTIVATED stops the ring | `RingSessionTest."AC-4_1 resolving as DEACTIVATED carries the deactivator's name"` + `RingViewModelTest."AC-4_1 remote deactivation reaches the ui…"` (service collect→stop → instrumentation) |
| AC-4.2 silent grace window auto-clears | `RingPolicyTest."AC-4_2 silent pact through the grace window resolves AUTO_CLEARED"`, `"inside the grace window resolves nothing"` + `RingViewModelTest."AC-4_2 proof done exposes a grace countdown"` |
| AC-5.1 max-ring cap resolves MISSED | `RingPolicyTest."AC-5_1 no proof at the max-ring cap resolves MISSED"`, `"proof beats missed…"` |
| AC-6.1 solo proof deactivates immediately | `RingSessionTest."AC-6_1 solo resolution is DEACTIVATED without losing proof"` (service solo branch → instrumentation) |
| AC-7.1 create pact → 6-char code, I'm a member | `InviteCodesTest."AC-7_1 generated codes are 6 chars and always valid"`, `"…never contain ambiguous characters"` + `PactViewModelTest."AC-7_1 creating a pact sends the trimmed name…"` |
| AC-7.2 join by code → in member list | `PactViewModelTest."AC-7_2 joining a pact lands me in the member list"` |
| AC-8.1 feed newest-first with outcome/owner/deactivator | `PactViewModelTest."AC-8_1 the feed passes gateway events through in gateway order"` + `AlarmMappersTest` field fidelity (DESC ordering lives in the Room query / Firestore orderBy → instrumentation) |

## Beyond the criteria

- Empty states: `AlarmsViewModelTest."empty repository yields an empty row list"`, blank pact name short-circuit, `PactViewModelTest` solo `isLive=false`.
- Error paths: gateway Offline on create, Failed on join, leave confirmation — each surfaces the right one-shot `PactMessage`.
- Rapid/duplicate invoke: day toggled twice returns to empty; `selectNew` twice yields distinct stamps (fresh editor per draft); delete on a never-saved draft is a no-op.
- State restoration: `EditorViewModelTest."load is once-only — process-death edits are not clobbered by a reload"` seeds a `SavedStateHandle` as the framework would after death.
- Persistence robustness: unknown `RingState` string from a future schema maps to `MISSED` instead of crashing (`AlarmMappersTest`).
- Clamping: mission sliders coerce to the documented bounds from `Alarm`'s companion.
- Scheduling contract: save schedules under the **returned** row id (auto-generated on first save), not the draft's id 0; toggling off cancels; delete cancels before removing.

## Not unit-testable (roadmap: instrumentation)

- `BootReceiver` reschedule loop (AC-1.3) and `AlarmScheduler`'s `setAlarmClock`/`canScheduleExactAlarms` branches.
- `RingService` end-to-end: foreground start, once-only proof under concurrent step events, remote-deactivation collect stopping audio, solo immediate resolution, overlap re-arm (REVIEW #1), wake-lock release.
- `StepSourceSelector` permission/sensor branches and `HardwareStepSource`/`AccelStepSource` against real `SensorManager` (AC-2.4).
- `RingActivity` lock-screen flags, back-swallow while unresolved.
- Firestore: security-rule enforcement of buddy-only deactivation, feed `orderBy` DESC, offline write queueing.
- Room DAO queries (`activeRecord` state filter, `recent` ordering/limit).
