---
name: bump-version
description: Bump the app version (patch, minor, or major). Use this skill whenever the user asks to bump, increment, or release a new version of the app, or mentions "patch", "minor", "major" in a version/release context.
---

# bump-version skill

When the user asks to bump the version (patch, minor, or major), follow these steps exactly. Do NOT skip any step.

## Step 1 — Read current version

Read `app/build.gradle` and extract:
- `versionCode` (integer)
- `versionName` (semver string, e.g. "2.2.1")

## Step 2 — Compute new version

- New `versionCode` = old `versionCode` + 1
- New `versionName`:
  - **patch** bump (default): increment the third component (2.2.1 → 2.2.2)
  - **minor** bump: increment second, reset third (2.2.1 → 2.3.0)
  - **major** bump: increment first, reset second and third (2.2.1 → 3.0.0)

## Step 3 — Update app/build.gradle

Replace `versionCode` and `versionName` with the new values.

## Step 4 — Update CHANGELOG.md

Add a new section at the top (after the `# Changelog` heading):

```
## [<new versionName>] - <today's date YYYY-MM-DD>

### <category>
- <changes>
```

Ask the user for the changelog entry text if not already provided, or derive it from the current conversation context (e.g. what was just fixed/added).

## Step 5 — Create fastlane changelog files

Create two files, both containing the same short English-friendly changelog text (plain bullet lines, no markdown headers, 500 chars max each):

- `fastlane/metadata/android/en-US/changelogs/<new versionCode>.txt`
- `fastlane/metadata/android/de-DE/changelogs/<new versionCode>.txt`

The `de-DE` file must be in German.

Base the text on the CHANGELOG entry from Step 4.

## Step 6 — Update "What's New" dialog (feature releases only)

If the release includes new user-facing features (not just bug fixes), update the What's New popup shown to users after updating:

1. In `ui/i18n/src/main/res/values/strings.xml`, add a new string `whats_new_message_<version_underscored>` (e.g. `whats_new_message_2_5_0`) with the new features as HTML bullet points. Use `\u2022` for bullets and `<b>` for emphasis. See the existing `whats_new_message_2_4_0` as a template.
2. In `app/src/main/java/de/danoeh/antennapod/activity/MainActivity.java`:
   - Update `WHATS_NEW_VERSION` to the new `versionCode`.
   - Update the string resource reference in `showWhatsNewIfNeeded()` to point to the new message string.

For patch/bugfix-only releases, skip this step.

## Step 7 — Update README.md

Read `README.md`. The GitHub release badge auto-updates from tags, so no manual edit is needed. However:

1. Update the "Latest Release" heading and its bullet points to reflect the new version.
2. Move the **previous** "Latest Release" content into the "Previous Releases" section (as a new `#### <version>` entry at the top of that section). Do not drop it — every release should remain listed.
3. If the README contains a hardcoded version number anywhere else (not in a badge URL), update it too.

## Step 8 — Report

Tell the user what was changed:
- New version: `<versionName>` (versionCode `<versionCode>`)
- Files modified: `app/build.gradle`, `CHANGELOG.md`
- Files created: both fastlane changelog files
- What's New dialog: updated (if applicable)
