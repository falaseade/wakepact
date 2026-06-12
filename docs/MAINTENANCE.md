# Maintaining WakePact

## Health check

```bash
python3 tools/validate_project.py .
./gradlew testDebugUnitTest        # on a machine with Android SDK; CI runs this on every push
```

Green validator + green tests = the project is in the state the factory left it.

## Adding a feature

Mini-pipeline, every time:
1. **Brief delta** — add/adjust a user story + acceptance criteria in `docs/PRODUCT_BRIEF.md`.
2. **ADR if structural** — new `docs/adr/ADR-00X-*.md` when the change bends an existing decision; supersede explicitly, never drift silently.
3. **Implement** following `docs/ARCHITECTURE.md` conventions (layering, no `android.*` in ViewModels, strings in resources, Timber-only logging, dispatchers owned by repositories).
4. **Review** against the Blocker/Major checklists in `docs/REVIEW.md`'s source reference; append findings to `docs/REVIEW.md`.
5. **Tests** — map the new ACs in `docs/TEST_PLAN.md`; JVM-only in `app/src/test`.
6. **CHANGELOG entry** under Added/Changed/Fixed, then **one commit** for the feature.

## Updating dependencies

Where working apps quietly break. The safe procedure:
1. Re-verify versions against live sources (release pages, mvnrepository) before touching the catalog, the way `docs/STACK.md` documents each pin.
2. Bump **coupled sets together, never alone**: Kotlin + Compose compiler plugin + KSP move as one; AGP + Gradle move as one. Watch the documented cliff: **Hilt 2.59+ requires AGP 9 + Gradle 9.1** — taking it means taking the whole AGP 9 migration deliberately.
3. One branch, one purpose; merge only on green CI; update the "Verified" date in `docs/STACK.md`.
4. Major-version jumps: skim release notes for breaking changes first.

Dependabot (`.github/dependabot.yml`) opens weekly grouped PRs so half a coupled set can't move alone. Merge only on green CI; never auto-merge.

## Releasing a version

Bump `versionCode`/`versionName` in `app/build.gradle.kts` → new CHANGELOG section → `git tag v<x.y.z>` → follow the release checklist at the bottom of `README.md` (keystore via `local.properties`, R8 on, internal testing track).

## When something breaks

- **Build/CI red:** read the **first** error in the log (later ones cascade). Check whether coupled versions moved together. Actions uploads HTML lint/test reports as artifacts on failure.
- **Runtime weirdness:** `adb logcat --pid=$(adb shell pidof -s app.wakepact)`; StrictMode flags main-thread I/O instantly in debug; LeakCanary notifies with the retention chain if something leaks.
- **Alarm didn't fire:** check the alarm is enabled and scheduled (`adb shell dumpsys alarm | grep wakepact`), battery optimization exemptions, and that boot happened with a first-unlock (reschedule runs post-unlock).
- **Pact not syncing:** confirm `local.properties` Firebase keys (see `docs/FIREBASE_SETUP.md`), anonymous auth enabled, and security rules published; the app intentionally degrades to solo rather than blocking the alarm.
