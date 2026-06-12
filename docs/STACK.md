# Technology Stack

Verified: **2026-06-12** via web search (official release pages, mvnrepository) + raw GitHub probes (project changelogs, gradle.properties, RedMadRobot androidx catalog mirror), anchored on the factory compatibility matrix snapshot of 2026-06-11. Every pin below traces to a live source checked this session or to that dated matrix.

## Toolchain

| Component | Version | Source checked |
|---|---|---|
| JDK | 17 | matrix rule 5 (AGP 8 / Gradle 8 requirement); set in CI, compileOptions, jvmTarget |
| Gradle | 8.13 | matrix 2026-06-11 (pairs with AGP 8.13.x) |
| AGP | 8.13.2 | matrix 2026-06-11; developer.android.com/build/releases — last 8.x line, supports API ≤ 36.x. Staying on 8.x per matrix rule 8 (AGP 9 is a major break; Hilt's stable plugin requires it — see Hilt row) |
| Kotlin | 2.3.0 | matrix 2026-06-11 + kotlinlang.org whatsnew23. 2.3.20 / 2.4.0 exist but are freshness-coupled with library lines we are not taking (see Rejected) |
| Compose compiler plugin | = Kotlin (2.3.0) | matrix rule 3 — Kotlin 2.x bundles it; `kotlinCompilerExtensionVersion` never set |
| KSP | 2.3.9 | kotlinlang.org KSP quickstart (Apr 2026) shows `com.google.devtools.ksp version "2.3.9"`; github.com/google/ksp/releases — KSP2 versions independently of Kotlin (old `<kotlin>-1.0.x` scheme retired), 2.3.x line supports Kotlin 2.3 |
| compileSdk / targetSdk | 36 | Compose 1.11 floor (matrix rule 7); AGP 8.13 max supported API |
| minSdk | 26 | factory default (adaptive icons, java.time) |

## Libraries

| Library | Version | Why it's here | Source checked |
|---|---|---|---|
| compose-bom | 2026.05.01 | governs all `androidx.compose.*` (Compose UI 1.11.2, Material3 1.4.0) | mvnrepository newest stable; coherent with compileSdk 36 (Compose 1.12 would force compileSdk 37 + AGP 9) |
| activity-compose | 1.13.0 | ComponentActivity + setContent | AndroidX releases / RedMadRobot mirror catalog |
| lifecycle-runtime-compose, lifecycle-viewmodel-compose | 2.10.0 | collectAsStateWithLifecycle, viewModel() | AndroidX releases page (stable channel) |
| navigation-compose | 2.9.8 | type-safe `@Serializable` routes (Navigation 2, not 3) | RedMadRobot mirror, cross-checked AndroidX page |
| hilt-android / hilt-android-compiler / plugin | **2.58** | DI via KSP | github.com/google/dagger — **2.59+ Gradle plugin requires AGP 9 + Gradle 9.1** (dagger issue #5099); 2.58 is the documented AGP-8-compatible stable |
| androidx.hilt:hilt-navigation-compose | 1.3.0 | hiltViewModel() in nav graph | AndroidX releases |
| room-runtime / ktx / compiler (KSP) | 2.8.4 | alarms + ring records persistence | AndroidX releases |
| datastore-preferences | 1.2.1 | identity + settings key-values | AndroidX releases |
| core-ktx | 1.18.0 | core extensions | AndroidX releases |
| firebase-bom | 34.14.0 | governs firebase-auth + firebase-firestore | Firebase release notes (updated 2026-06-09). Plain artifacts only — **-ktx variants removed since BoM 34.0.0** |
| kotlinx-coroutines (android/test) | 1.10.2 | structured concurrency; matrix-aligned with Kotlin 2.3.0 | project CHANGES.md (1.11.0 just GA'd → freshness rule) |
| kotlinx-serialization-json | 1.10.0 | typed nav routes + Firestore mapping helpers | project CHANGELOG (2026-01-21, Kotlin-2.3.0-era; 1.11.0 pairs with Kotlin 2.3.20) |
| junit | 4.13.2 | factory default test runner | matrix |
| mockk | 1.14.10 | mocking | repo gradle.properties points next-dev at 1.14.11-SNAPSHOT ⇒ 1.14.10 is latest release |
| turbine | 1.2.1 | Flow assertion | project CHANGELOG (2025-06-11); README's 1.3.0 is unreleased docs |
| timber | 5.0.1 | logging (debug tree) | JakeWharton/timber releases |
| leakcanary-android | 2.14 | debugImplementation leak detection | square/leakcanary changelog — 2.14 (2024-04-17) is latest stable; entire 3.0 line still alpha (banned) |

## Coherence checks performed (matrix rules 1–11)

1. AGP 8.13.2 ↔ Gradle 8.13 — official pairing table. PASS
2. AGP 8.13.2 ↔ compileSdk 36 — within AGP 8.13 supported max. PASS
3. Kotlin 2.3.0 ↔ Compose compiler — plugin `org.jetbrains.kotlin.plugin.compose` at 2.3.0; no `kotlinCompilerExtensionVersion` anywhere. PASS
4. Kotlin 2.3.0 ↔ KSP 2.3.9 — KSP2 independent versioning, 2.3.x line supports Kotlin 2.3 per release notes. PASS
5. JDK 17 set three ways — CI setup-java 17, compileOptions 17, jvmTarget 17. PASS (enforced in Phase 4 files)
6. Compose BOM governs Compose — no individual `androidx.compose.*` version pins in the catalog. PASS
7. Compose 1.11.2 (BOM 2026.05.01) ↔ compileSdk 36 — floor satisfied; deliberately below the 1.12/compileSdk-37/AGP-9 cliff. PASS
8. AGP 9 avoided — staying on 8.x (also forced by Hilt 2.58 plugin compatibility). PASS
9. Stable channel only — zero alpha/beta/RC in the catalog. PASS
10. minSdk 26 — no library above demands higher. PASS
11. Exact versions only — no dynamic versions/ranges. PASS

## Library selection verdicts (non-defaults)

- **firebase-auth + firebase-firestore (via BoM 34.14.0)** — first-party Google, stable, Kotlin-friendly (plain artifacts now contain the KTX APIs), Apache-2.0. Needed for the live pact layer (anonymous auth + Firestore listeners). Initialised **programmatically** (ADR-003) so the google-services plugin is not in the build — absence of Firebase config degrades to solo mode instead of breaking the build. ACCEPTED.
- **kotlinx-serialization-json 1.10.0** — JetBrains, stable, needed for Navigation type-safe routes. ACCEPTED.

## Rejected candidates

| Candidate | Rejected because |
|---|---|
| Hilt 2.59.x | Gradle plugin requires AGP 9 + Gradle 9.1+ (dagger #5099); breaks factory AGP-8 baseline |
| Kotlin 2.3.20 / 2.4.0 (+ coroutines 1.11.0, serialization 1.11.0) | freshness rule + coupling: the 1.11.x library lines are documented against Kotlin 2.3.20+; matrix-verified 2.3.0 set is the known-good island |
| Navigation 3 | not factory default; Navigation 2.9.x type-safe routes cover the 4-screen graph |
| material3-adaptive / adaptive-layout / adaptive-navigation(-suite) | scaffold-navigator API churn across 1.1→1.2 (suspend navigateTo, scaffoldState reshuffles) is first-try-build risk in a no-compile sandbox. Foldable mandate is met with `BoxWithConstraints` at the Material3 expanded breakpoint (840 dp) — zero extra dependencies, version-proof |
| material-icons-extended / material-icons-core | icons artifacts froze at 1.7.x; whether current BOMs still map them is ambiguous → unresolvable-version risk. App ships a small hand-rolled `ImageVector` icon set instead |
| google-services Gradle plugin | replaced by programmatic FirebaseOptions init (ADR-003); keeps Firebase optional |
| Retrofit/OkHttp | no REST endpoints (Firestore SDK only) |
| Coil | no image loading |
| kapt | KSP everywhere (Hilt + Room support it) |
| firebase-*-ktx artifacts | removed upstream since BoM 34.0.0 — would fail resolution |
| LeakCanary 3.0-alpha-x | alpha channel banned |
