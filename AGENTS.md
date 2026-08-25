# Repository Guidelines

## Project Structure & Module Organization

Next Player is a Kotlin/Jetpack Compose multi-module Android app. `app/` owns application entry points and top-level navigation. Reusable layers live under `core/`; user-facing areas are split across `feature/player`, `feature/videopicker`, `feature/settings`, and `feature/network`.

Kotlin sources are under each module's `src/main/java/`; resources are in `src/main/res/`. Place JVM and Robolectric tests in `src/test/`, and device tests in `src/androidTest/`. Release metadata and screenshots belong in `fastlane/metadata/`.

## Build, Test, and Development Commands

Use the checked-in Gradle wrapper and JDK 17:

- `./gradlew assembleDebug` builds all debug APK variants.
- `./gradlew :app:installDebug` installs the debug app on an emulator.
- `./gradlew test` runs local JUnit and Robolectric tests across modules.
- `./gradlew connectedDebugAndroidTest` runs instrumentation and Compose UI tests.
- `./gradlew ktlintCheck` checks Kotlin formatting; `./gradlew ktlintFormat` applies safe formatting fixes.

For faster feedback, target a module: `./gradlew :core:media:test`.

## Coding Style & Naming Conventions

- Follow `.editorconfig` and the Android Studio ktlint style: four-space indentation and trailing commas where supported. Use `PascalCase` for classes, files, and `@Composable` functions; use `camelCase` for methods and properties; keep packages lowercase under `dev.anilbeesetti.nextplayer`.
- Place UI string and plurals resources in `core/ui/src/main/res/values/strings.xml` and reference them through `dev.anilbeesetti.nextplayer.core.ui.R`; do not create feature-local string resource files.
- Choose the simplest implementation that fully meets the current requirements.
- Prefer established, well-maintained libraries over custom implementations.
- Optimize for the next reader: use clear names, small focused units, straightforward control flow, and existing module boundaries. Avoid unnecessary abstractions, clever shortcuts, and speculative flexibility.

## Testing Guidelines

Tests use JUnit 4, Robolectric, AndroidX Test/Espresso, coroutine test utilities, and Compose UI testing. Name test classes after the subject with a `Test` suffix, such as `MediaRequestRunnerTest`. Add focused regression tests for behavior changes.

After implementing a feature, or when asked to review one, validate the affected user flow on a disposable emulator in addition to automated tests. Record the device/API level, tested flow, and result in the pull request or review.

## Commit & Pull Request Guidelines

Write short, imperative commit subjects consistent with history, such as `Fix vault reservation concurrency`. Optional prefixes like `fix(media):`, `test:`, or `docs:` are acceptable when useful. Keep each commit focused.

During feature work, commit each coherent, verified intermediate milestone instead of leaving multiple completed steps uncommitted until the end.

Pull requests should explain the problem and solution, link the relevant issue, and list verification commands. Include before/after screenshots or recordings for UI changes and note device/API coverage for Android-specific behavior. Ensure `assembleDebug`, `test`, and `ktlintCheck` pass before requesting review.

## Security & Local Configuration

Do not commit `local.properties`, signing keys, API credentials, `google-services.json`, or generated APKs. Supply SDK paths and release secrets only through local configuration or CI secrets.
