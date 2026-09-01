## 2024-05-19 - Added Content Descriptions to Playback Speed Controls
**Learning:** Found multiple instances where icon-only buttons in the Media Player controls had `contentDescription = null` (e.g., increase/decrease speed, reset speed). In Jetpack Compose, missing content descriptions severely impact TalkBack users, rendering these controls effectively invisible or uninterpretable.
**Action:** Always ensure icon-only `IconButton` or `FilledTonalIconButton` elements are accompanied by a descriptive `contentDescription` linked via `stringResource`. Next time auditing UI components, specifically grep for `contentDescription = null` to quickly identify and fix these a11y gaps.

## 2024-05-20 - Accessibility improvement in CrashActivity
**Learning:** Discovered that the "Copy Logs" button in `CrashActivity` was missing an ARIA equivalent (`contentDescription = null`), rendering it invisible/unintelligible to TalkBack users. It was using a `FilledIconButton` with only an icon.
**Action:** Ensure that all icon-only interactive elements, especially in utility screens like crash reporting, have a meaningful `contentDescription` using string resources (e.g., `stringResource(R.string.copy)`). Continually check for `contentDescription = null` within `IconButton` components.

## 2024-05-22 - Accessibility improvement in Vault PinPad
**Learning:** Found that the backspace icon-only button used in the video vault's PIN pad had `contentDescription = null`. This obscured the erase functionality for TalkBack users navigating the vault pad.
**Action:** Always provide localized `contentDescription` for custom numpads or PIN entry controls (e.g., using `stringResource(com.graviton.core.ui.R.string.delete)`).
