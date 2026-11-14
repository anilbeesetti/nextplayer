---
name: nextplayer-release
description: Run the Next Player release process in anilbeesetti/nextplayer. Use whenever the user asks to prepare, cut, ship, or resume a Next Player release, bump its release version, or coordinate release notes, the release PR, a GitHub draft, and Google Play internal testing. Ask for a missing version, curate notes from real release history, update the Fastlane changelog, watch CI, merge, and publish only after merge. Honor requests to prepare only or dry-run.
compatibility: Git, authenticated GitHub CLI, and the repository's GitHub Actions/Fastlane publishing setup. Google Play CLI access is useful for version and internal-track verification.
---

# Next Player release

Deliver a reviewed version bump and changelog on `release-X.Y.Z`, merge after CI passes, then create/update a **draft** GitHub release and distribute to Google Play **internal testing**. Do not publish the GitHub draft or promote to another Play track.

A request to run this complete process authorizes its PR, merge, draft, and internal upload. A request only to prepare notes, bump a version, or dry-run does not authorize the remaining steps. Creating or editing this skill is not a release request.

## 1. Establish the release

- Read `AGENTS.md` and `docs/agents/domain.md`, then any required domain docs. Work from the repository root and confirm the remote is `anilbeesetti/nextplayer`.
- Extract `X.Y.Z` from the request (accept a leading `v`). If absent, ask **"Which version should I release?"** and wait before edits or remote mutations. Do not guess the next patch/minor version. Clarify pre-release versions instead of forcing them through the stable-version workflow.
- Record the version, previous published tag, target/base commit, version code, PR, merged commit, publish run, draft release, and Play status as they become known. Reuse these identifiers when resuming.
- Fetch remote refs and inspect worktree status. Do not discard, stash, commit, or publish unrelated user changes. Use the harness's supported isolated checkout or ask if the checkout is not safe to use.
- Discover the default branch rather than assuming it. Inspect existing release branches, open/merged release PRs, tags, draft/published releases, Actions runs, and Play versions before creating anything. Resume matching work instead of duplicating it. Stop if the requested version is already publicly released or its existing artifacts disagree with the intended commit.
- Read `app/build.gradle.kts`, `.github/workflows/{android_build,bump-version,publish}.yaml`, `fastlane/{Fastfile,Appfile}`, and recent Fastlane changelogs. These files, not this document's examples, are the current source of truth.
- Compare store listing/image files under `fastlane/metadata` with the previous published tag, excluding release changelogs. A full-release request includes the established publisher's sync of unchanged checked-in metadata; disclose that behavior without asking again. If unrelated listing/image changes would be uploaded, obtain explicit approval before publishing. Do not modify the Fastlane lane or workflow merely to avoid that approval.
- Read available `file-pr` and `babysit-pr` skills for PR operations, and Google Play skills if using `gplay`. Discover exact CLI flags with `--help`. Use the harness secret-entry tool for missing credentials; never ask for secrets in chat or print them.

## 2. Research and curate both sets of notes

Inspect at least the last three relevant published GitHub releases, not just generated notes or commit subjects:

```bash
gh release list --repo anilbeesetti/nextplayer --limit 10
gh release view <previous-tag> --repo anilbeesetti/nextplayer --json tagName,body,targetCommitish
```

Choose the previous published release in this version line; exclude drafts and unrelated pre-releases. Resolve its tag and compare it with the intended release base. Use the complete commit/merged-PR range, paginate API results, and inspect ambiguous changes. Do not include open/unmerged PRs, reverted changes, or work outside the release range. Auto-generated notes are research input, not the final release body.

Follow the recent releases' actual style. At the time this skill was written it is:

```markdown
## New features
* Added <user-visible capability> by @<actual-author> in <actual-PR-URL>

## Improvements
* Improved <user-visible behavior> by @<actual-author> in <actual-PR-URL>

## Fixes
* Fixed <user-visible problem> by @<actual-author> in <actual-PR-URL>

**Full Changelog**: https://github.com/anilbeesetti/nextplayer/compare/<previous-tag>...vX.Y.Z
```

- Include only important features, improvements, and fixes users can recognize. Group overlapping work and omit empty sections. Verify authors/PR URLs; do not invent attribution for direct commits.
- Omit routine dependency bumps, CI/build tooling, internal refactors, tests, version bumps, and translation churn. Include a dependency change only when it delivers a material user-facing fix, compatibility benefit, or security correction; describe that benefit rather than just the library version.
- Include `New Contributors` only when supported by actual history and consistent with recent notes.
- Inspect recent `fastlane/metadata/android/en-US/changelogs/*.txt` in numeric version-code order. Write a **shorter, plain-text highlight list** using `- ` bullets, no GitHub headings, attribution, PR links, or comparison footer. Prioritize the most useful changes and keep within Google Play's 500-character per-language limit. Older notes sometimes mention library versions; that is not a reason to include routine dependency bumps.
- Add `fastlane/metadata/android/en-US/changelogs/<new-versionCode>.txt`; the filename is the Android **version code**, not `X.Y.Z`. Preserve old changelogs. Follow existing locale practice; do not invent translations or touch unrelated Fastlane metadata.
- Keep the full curated GitHub notes in a temporary file outside the commit for reuse in the PR description and final draft update.

## 3. Prepare the release PR

Create or resume `release-X.Y.Z` from the fresh default branch. This is the **PR branch**, deliberately different from the publisher trigger branch.

- Update `versionName` and `versionCode` in `app/build.gradle.kts`. Normally increment the current code once, but ensure it is greater than every code already uploaded to Play, including drafts/testing tracks and concurrent releases. Reuse the allocated code on resume unless a new binary upload requires a new one. If Play state cannot be checked, disclose the uncertainty and resolve it before uploading rather than assuming the local code is free.
- Commit only the version change and its new Fastlane changelog. Handle necessary publishing fixes in a separate reviewed PR before releasing, not as incidental release-PR changes. Do not use `bump/vX.Y.Z`: the existing bump workflow increments separately and can duplicate the bump/PR.
- Inspect the complete diff and validate the changelog filename, contents, length, version values, and comparison range. Run the repository-required `./gradlew assembleDebug`, `./gradlew test`, and `./gradlew ktlintCheck` with JDK 17; record failures honestly and resolve them or obtain the equivalent successful CI evidence before merging.
- Follow the repo's emulator validation policy: smoke-test the release's important affected flows on a disposable emulator, recording device/API, flows, and results in the PR. State any unavailable coverage; do not claim a test ran when it did not.
- Push `release-X.Y.Z` and create a non-draft PR against the default branch, or update the existing one. Include version/code, concise rationale, curated GitHub notes, Play highlights, and verification evidence. Do not open a second PR for the same version.

## 4. Watch, fix, and merge

Own the PR through completion, not just until it is opened. Follow `babysit-pr`: handle current feedback, then use the harness PR watcher (in T3, `watch_pull_request`) and yield until notified. Outside T3 use that skill's `pr-wait`. Use one watcher per PR, never a custom polling/sleep loop. Stop/unwatch when blocked or handing off.

- Diagnose failing checks and actionable reviews. Fix real release-related problems, push, and wait again for the **new head**. Do not bypass failures, unresolved blocking reviews, branch protection, or required approvals.
- Require all relevant release CI jobs, including Android build/local tests/ktlint and instrumentation where configured, to finish successfully on the latest head. A green run on an older head or a still-pending job is not sufficient.
- Immediately before merging, fetch and recheck PR head, mergeability, reviews, base changes, and CI. If behind/conflicted or relevant base changes invalidate testing, update the branch and wait for fresh green checks. Do not use admin/bypass merging.
- Merge using a repository-supported method only when the full-process request authorizes it. For prepare-only work, stop at the requested boundary. Read back `mergeCommit.oid`; do not confuse the former PR head or a later default-branch HEAD with the actual merged release commit.

## 5. Publish the merged commit, once

### Use the existing publisher

As written, `.github/workflows/publish.yaml` runs on a push to **`release/vX.Y.Z`**. It builds signed APKs/AAB via `bundle exec fastlane publish`, uploads to Play with `TRACK=internal`, then creates a GitHub draft with APK assets and generated notes. Re-read the workflow before executing: pushing that ref is a real deployment, not PR preparation.

After the PR is confirmed merged, verify the merged commit contains the expected version/code and changelog. Check that the publisher's configured Play track is `internal` and the GitHub release is `draft: true`. The current Fastlane lane also syncs listings/images. Recheck the metadata comparison and approval from step 1 against the exact merged commit; stop if unapproved unrelated listing/image changes would be uploaded.

If the workflow still uses its branch trigger and no matching deployment already exists, publish **the exact merged commit**:

```bash
# Set these only from the verified version, default branch, and merged PR response.
git fetch origin "$DEFAULT_BRANCH"
git cat-file -e "${MERGED_SHA}^{commit}"
git push origin "${MERGED_SHA}:refs/heads/release/v${VERSION}"
```

First verify that this remote ref does not already exist. If it exists, inspect its commit and corresponding run; resume a matching deployment, and stop on a mismatch. Do not force-push it to trigger a second upload. Do not push this trigger before merge or replace `release-X.Y.Z` as the PR branch.

Find the publish run by workflow, trigger branch, and exact SHA, then wait with `gh run watch <run-id> --repo anilbeesetti/nextplayer --exit-status` (or a supported harness workflow watcher). Do not report success just because the ref push or workflow start succeeded. Do not also call `gplay release` when Actions/Fastlane owns the upload.

If repository automation changes, adapt to its reviewed post-merge trigger; do not quietly change release timing or invent another publishing pipeline. If a necessary workflow change is discovered after merging, prepare and verify a follow-up PR rather than editing an unreviewed deployment ref.

### Finalize the GitHub draft

After the publisher succeeds, locate its existing draft for `vX.Y.Z`. Replace generated notes with the researched body:

```bash
gh release edit "v${VERSION}" --repo anilbeesetti/nextplayer \
  --draft=true --title "v${VERSION}" --notes-file "$NOTES_FILE" \
  --target "$MERGED_SHA"
```

Verify `isDraft`, title/tag, body, target, and the expected nonempty APK assets. A default-branch target in the old workflow can drift after merge: if a tag already exists, resolve its actual commit too, because editing a release target does not move an existing tag. Stop on a mismatched tag instead of moving/deleting it. Keep the draft unpublished.

### Verify Play and recover safely

Verify package `dev.anilbeesetti.nextplayer`, track `internal`, expected version name/code, and tester availability/processing status through available authenticated Play APIs/CLI/Console. Use Google Play CLI help and its skill for read operations; do not create/commit an unnecessary edit just to inspect. Never promote to alpha, beta, or production. Successful CI upload logs are evidence of upload, not automatically proof the build is available to testers; report processing or unavailable independent verification accurately.

A failed workflow may have **already uploaded to Play** before failing to create the GitHub draft. Inspect step logs and remote state before any retry. If the expected code is already uploaded, do not rebuild/re-upload or rerun the entire publish job. Recover the missing draft/assets only if signed artifacts from the same verified commit are available. The current publisher does not preserve them with `upload-artifact`, so a failed runner may leave no recoverable APKs: report missing artifacts/credentials as a blocker rather than assuming recovery is possible. Do not substitute debug-signed binaries. If another upload is genuinely needed, allocate a new code and repeat the reviewed bump/CI/merge process; never silently reuse a consumed code.

## 6. Report the outcome

Return version/code, release PR URL and merge SHA, publish-run URL, GitHub **draft** URL, and Google Play **internal** status. Mention significant omitted dependency/tooling changes only if useful. If blocked, state the exact incomplete step and the safe next action; never label a partial upload or unverified draft as a completed release.
