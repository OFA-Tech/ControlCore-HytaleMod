---
name: controlcore-release
description: Prepare and publish a ControlCore release (version bump, dated changelog, jar check, review gates, the owner's git steps, and the CurseForge and Discord texts). Use when the user says a ControlCore feature branch is tested and asks to release, prepare a release, cut a version, or wants the CurseForge changelog or Discord post for a version.
---

# ControlCore release

ControlCore (OFA-Tech, Hytale server mod) ships from a `release/<version>` branch that the owner pushes, merges and
tags. The GitHub `Release` workflow then builds the jar, publishes to GitHub Packages and creates a pre-release. The
owner uploads the jar to CurseForge (project 1757053) and posts in the Discord thread.

## Before the release branch

1. The feature branch is tested in game by the owner. Never release untested panel or HUD changes.
2. Run the **banca-de-revisao** skill on `git diff main...<feature-branch>`, with the **security-audit** skill in
   guidance mode for the hacker reviewer (permission checks on panel actions and commands, player-supplied input,
   file paths, outbound network calls). Fix every CRÍTICO/ALTO item and run the banca again.
3. Run the **publish-mod** pre-release lint: no Hytale game files, decompiled code, keys or dev-server data in the jar;
   README, credits and AI-use disclosure are current.

## Release branch

1. `git checkout -b release/<version>` from the tested feature branch (one PR covers everything).
2. Bump `version` in `gradle.properties` and `"Version"` in `src/main/resources/manifest.json`, plus the Gradle
   examples in `docs/api.md` and `wiki/Mod-Author-Quick-Start.md`.
3. In `CHANGELOG.md`, turn `## Unreleased` into `## <version> - <yyyy-mm-dd>` with a short intro (beta, Hytale range
   `>=0.7.2 <0.8.0`, API version and whether mods keep working, migration notes), and put an empty `## Unreleased`
   above it.
4. Bump the API version in the docs only when the public API changed (additions = minor, removals = major).
5. `./gradlew clean build -Pstrict` must pass. Check the jar: `manifest.json` version and `ServerVersion`, and no
   `history/`, `snapshots/`, `diagnostics/`, `*Rules.json`, `SettingValues.json`, `bans.json` or other runtime files.
6. `git ls-files -ci --exclude-standard` must be empty, then commit `Release <version>`.

## Owner steps (give them exactly)

```powershell
git push -u origin release/<version>
# open the PR on GitHub and merge it
git checkout main
git pull
git tag v<version>
git push origin v<version>
git branch -D release/<version> <feature-branch>
```

## After the tag

1. Check `https://api.github.com/repos/OFA-Tech/ControlCore-HytaleMod/releases/tags/v<version>` (pre-release, jar
   attached) and that the `Release`, `Build` and `Wiki` workflow runs succeeded.
2. CurseForge form: display name `ControlCore <version> (beta)`, game `0.7`, release type **Beta**, changelog in
   Markdown starting with the beta line (Hytale range, tested version, no migration, back up
   `mods/OFATech_ControlCore/`), grouped by feature, ending with the API note and the full changelog link.
3. Discord thread reply: under 2,000 characters, `**ControlCore <version> is out**: <theme>`, 4-6 bullets, one
   example, the CurseForge link and the changelog link in `< >`. Suggest which of the owner's screenshots to attach.
4. The owner takes all CurseForge gallery screenshots at once after the roadmap; do not ask for screenshots per
   release.
