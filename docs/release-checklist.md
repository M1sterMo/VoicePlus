# Release checklist

Run these checks on the final release commit. Do not merge, tag or publish until
the checks and device acceptance are complete.

## Repository hygiene

- Keep signing keys, credentials, device/database pulls, local paths, internal
  planning reports and generated store exports out of the public tree.
- Review the final diff and commit identity. Use a public project identity or
  GitHub's private noreply address, and preserve contributor credits.

## Content

- Review [README screenshots](../README.md#screenshots) and the
  [full gallery](screenshots/preview.html) at phone and desktop widths.
- Review the [F-Droid publication-order preview](screenshots/fdroid-preview.html):
  22 captioned device frames with readable headlines and no clipped app screens.
- Review the F-Droid description and version-code changelog. Title and cover edits
  do **not** rewrite audiobook files; portable backups preserve them. Series
  grouping is manual, not automatic tag grouping.
- Run [screenshot export and validation](screenshots/README.md). Retain all 22
  metadata filenames; each slot must have a distinct capture within its set.

## Build and upgrade

- Run unit tests, formatting, Android lint and critical instrumentation
  regressions. Screenshot work is not a substitute for playback verification.
- Choose a release versionCode higher than published builds **and** signed test
  builds. Recheck the installed candidate before release; keep the version-code
  changelog filename aligned with `app/build.gradle.kts`.
- Build signed `libreRelease` using the existing key. Verify package, signature,
  version and minified APK; test an in-place upgrade without clearing data.
- Verify shelf/series order, covers, progress, chapter corrections, sleep-timer
  resume protection, search, headset/Auto controls, lock screen and widgets.

## Publication — separate approval required

- Merge the reviewed release branch to `main`; tag the tested release commit
  with `v` followed by its literal `versionName`.
- Use the existing release workflow and APK filename. Review and publish its
  draft GitHub release; retain the signing identity and install pathway.
- The APK must be named `app-libre-release-signed.apk`, matching the official
  F-Droid `Binaries` URL. The recipe in F-Droid's `fdroiddata` repository is
  authoritative; the local `metadata/` copy is only a reference. Check build
  toolchain compatibility and the published binary's reproducibility there.
- F-Droid updates asynchronously. Check build/reproducibility results, then
  inspect screenshots at their retained URLs. Only mark the duplicate problem
  resolved publicly after the live gallery shows the new unique set.
