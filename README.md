# WakePact

**The alarm clock you can't talk yourself out of.** Your alarm only truly switches off when a friend in your *pact* confirms you're up — and they'll only get the chance after you've physically walked enough steps to prove it. Snoozing isn't a button; it's letting your mates down.

Built for Android (Kotlin + Jetpack Compose), works fully offline solo, goes social with an optional free Firebase project.

## Screenshots

*(placeholder — add captures of: alarm list, editor, ringing screen with step dial, pact screen with a pending deactivation, two-pane fold layout)*

## Features

- **Alarms** with weekday repeat or one-shot, labels, and a next-fire countdown; exact delivery via `AlarmManager.setAlarmClock`, rescheduled after reboot.
- **Wake-proof mission**: the ring won't quiet down until you walk a configurable number of steps (10–100, default 30). Uses the hardware step detector when available, an accelerometer peak detector otherwise.
- **Anti-spoof cadence gate**: steps only count in rhythmic chains (3+ steps, 0.25–2 s apart). Shaking the phone in bed does nothing. (Honest threat model below.)
- **The pact**: a small friend group joined by 6-character invite code. When your alarm fires, your pact sees it live; once you've walked your proof, *only they* can switch it off.
- **Unattended resolution** so nobody is hostage to a sleeping buddy: proof done + grace window (1–10 min) with a silent pact → **auto-cleared**; no proof by the max-ring cap (5–30 min) → **missed**, visible to the pact. Solo users deactivate immediately on proof.
- **Lock-screen ring UI** owned by a foreground service — killing the app's UI doesn't kill the alarm; Back doesn't dismiss an unresolved ring.
- **Foldable-aware**: ≥ 840 dp gets a two-pane alarms+editor layout (tested logic for Pixel Fold posture changes; editor state survives rotation and process death).
- **Solo mode** is first-class: with no Firebase configured, everything works and your ring history feeds a local-only log.

## Tech stack

| Layer | Choice |
|---|---|
| Language / UI | Kotlin 2.3.0, Jetpack Compose (BOM 2026.05.01, Material 3) |
| Build | AGP 8.13.2, Gradle 8.13 (wrapper), JDK 17, KSP 2.3.9 |
| Architecture | Layered MVVM — Compose → ViewModel → Repository/Gateway → Room / DataStore / Firestore |
| DI | Hilt 2.58 (+ hilt-navigation-compose) |
| Persistence | Room 2.8.4 (alarms, ring records), DataStore Preferences (identity) |
| Navigation | navigation-compose 2.9.8, type-safe `@Serializable` routes |
| Social sync | Firebase BOM 34.14.0 — Firestore + anonymous Auth, **optional at runtime** |
| Async | Coroutines + Flow end to end |
| Tests | JUnit4, kotlinx-coroutines-test, Turbine, MockK (JVM-only) |
| Forensics | Timber, StrictMode, LeakCanary (debug builds only) |

Full pin rationale with verification sources: [`docs/STACK.md`](docs/STACK.md).

## Architecture in one paragraph

A single-activity Compose app. `RingService` (foreground, `mediaPlayback` type) owns the entire ring lifecycle — audio, vibration, the step mission, the resolution timers and the Firestore live-link — and publishes an observable `RingSession`; `RingActivity` is a dumb lock-screen window onto it (ADR-002). The social layer hides behind a `PactGateway` interface with Firestore and local (solo) implementations chosen once at startup from `local.properties`-injected BuildConfig (ADR-003). Step events pass through a pure `StepChainValidator` before they count (ADR-004), and unattended outcomes are decided by a pure `RingPolicy` (ADR-005), both fully unit-tested. Decisions live in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) and [`docs/adr/`](docs/adr/).

### The anti-spoof threat model, honestly

The cadence gate defeats the lazy bypasses: single shakes, table taps, nudging the phone from under the duvet. It does **not** defeat a person rhythmically swinging the phone arm for the full goal — that's 30+ deliberate movements at a walking beat, which is arm cardio at 6 a.m. We count that as a win, not a bypass. Details in `StepChainValidator`'s KDoc and ADR-004.

## Build & run

```bash
git clone <your-repo-url>
# open in Android Studio (Ladybug or newer), let it sync, press Run ▶
```

No secrets needed for a solo build. To enable pacts, follow [`docs/FIREBASE_SETUP.md`](docs/FIREBASE_SETUP.md) (≈10 minutes, free tier).

## Push to GitHub & let CI prove it

One-time, on your machine (never paste tokens into a chat):

```bash
# with the GitHub CLI authenticated (gh auth login):
gh repo create wakepact --private --source=. --push

# or manually, after creating an empty repo on github.com:
git remote add origin https://github.com/<you>/wakepact.git
git branch -M main && git push -u origin main
```

The push triggers `.github/workflows/android-ci.yml`: **lint → unit tests → debug APK**. Check **GitHub → your repo → Actions → latest run** — green means the app compiles and all tests pass on a clean machine, and the built APK is attached as the `app-debug` artifact.

## Install on your phone

**Option 1 — Android Studio (best for iterating)**
1. Phone: Settings → About phone → tap **Build number** 7× to unlock Developer options.
2. Settings → System → Developer options → enable **USB debugging** (or **Wireless debugging** and pair via Device Manager → *Pair Devices Using Wi-Fi*).
3. Connect, accept the RSA prompt on the phone, press **Run ▶**.

**Option 2 — straight from GitHub, no laptop**
1. On the phone, open your repo → Actions → latest green run → **Artifacts** → download `app-debug`.
2. Unzip in the Files app, tap `app-debug.apk`, allow "install unknown apps" when prompted.
3. Debug signature — fine for personal use; Play distribution needs the release checklist below.

First-run prompts to accept: **notifications** (Android 13+, the ring depends on it) and **physical activity** (step detector; declining falls back to the accelerometer). On Android 14+ sideloads, if the full-screen ring only shows a notification, grant **"Display over other apps / Full-screen notifications"** for WakePact in Settings.

## Debugging

In Android Studio: **Logcat**, filter `package:mine` (add `level:error` to cut noise). From a terminal:

```bash
adb logcat --pid=$(adb shell pidof -s app.wakepact)   # live, this app only
adb logcat -d > dump.txt                              # dump buffer (post-crash forensics)
adb logcat -c                                         # clear before a clean repro
```

Debug builds plant Timber, scream via StrictMode on main-thread I/O, and run LeakCanary. CI uploads HTML lint/test reports as artifacts on failure.

## Known limitations (v0.1)

- Full-screen ring on Android 14+ sideloads may need the manual "full-screen notifications" grant (Play installs get it by default for alarm apps).
- Boot rescheduling runs after first unlock (`BOOT_COMPLETED` semantics on credential-encrypted storage).
- A display-name change doesn't rewrite your name on *past* feed events (denormalized by design — ADR-003).
- Offline pact writes queue in Firestore's local cache and sync when back online; the alarm itself never waits on the network.
- One ring at a time: an alarm firing mid-ring skips that occurrence (logged, re-armed) — queueing is on the roadmap.

## Project documents

Everything the factory produced lives in [`docs/`](docs/): `PRODUCT_BRIEF.md` (user stories + acceptance criteria), `MARKET.md`, `ROADMAP.md`, `ARCHITECTURE.md` + `adr/`, `STACK.md`, `REVIEW.md` (adversarial review log), `TEST_PLAN.md` (AC → test traceability), `FIREBASE_SETUP.md`, and `MAINTENANCE.md`. History: [`CHANGELOG.md`](CHANGELOG.md). Next steps: [`docs/ROADMAP.md`](docs/ROADMAP.md).

## Release checklist (when you outgrow debug builds)

Generate a keystore → signing config fed from `local.properties` (never committed; `.gitignore` already covers `*.keystore`/`*.jks`) → bump `versionCode`/`versionName` → enable R8 for release and verify rules → privacy policy if pact data leaves the device → Play Console **internal testing** track first.

---

*Built with android-app-factory v1.1 on 2026-06-12 — project documents in /docs*
