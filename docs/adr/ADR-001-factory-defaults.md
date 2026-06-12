# ADR-001: Adopt factory defaults
Date: 2026-06-12   Status: Accepted

## Context
The factory prescribes proven defaults; deviations need justification, not the reverse.

## Decision
Single `:app` module (package-by-feature) · MVVM with immutable `UiState` via `StateFlow` · thin domain layer (pure Kotlin: `NextTriggerCalculator`, `StepChainValidator`, `RingPolicy` — the brief's acceptance criteria are mostly about these rules, so they earn a layer) · Navigation Compose with `@Serializable` type-safe routes · Hilt · Room + DataStore · kotlinx-serialization · Coil not needed (no images).

## Alternatives considered
- Multi-module — rejected: 3 features + 1 service, no shared-code consumer, build-failure surface not worth it.
- MVI — rejected: only the ring flow is a true state machine and it lives in the service layer (ADR-002), not the UI layer.
- No domain layer — rejected: trigger math, step validation and ring policy are real business rules with 8 user stories of acceptance criteria pointing at them.

## Rex's objection & resolution
Rex: "A 'thin domain layer' always thickens into ceremony — use cases that forward calls." Resolution: domain contains only pure logic classes with real branching; repositories are called directly from ViewModels where no rule exists. Any forward-only use case found in review (Phase 6) gets deleted.

## Consequences
Fast builds, one Gradle file to maintain; if the roadmap's multi-pact/Wear items land, `domain` and `data` packages lift cleanly into modules later.
