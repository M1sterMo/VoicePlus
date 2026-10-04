# Development

## Project Setup

To run the project, open it in the latest version of Android Studio and build as usual.
VoicePlus requires **JDK 21**; the Gradle toolchain resolves it automatically via the Foojay convention.

The default development variant is `libreDebug`. It needs no Google services or credentials — VoicePlus has no Firebase,
analytics, or remote config. Release builds use the `libre` flavor.

By default, there is not enough memory configured for Gradle. You can fix this by running:

```sh
scripts/gradle_bootstrap.sh
```

This configures your global `~/.gradle/gradle.properties` to use more memory, depending on your machine.
Check the `gradle_bootstrap.sh` script for exact details.

## Tests

### Unit tests

To run the unit tests, run the following command:

```sh
./gradlew voiceUnitTest
```

Run `clean` separately from tests; use `--no-configuration-cache` on the following
test command to recreate the generated Robolectric SDK configuration.

### Instrumentation tests

To run the instrumentation tests, run the following command:

```sh
./gradlew voiceDeviceLibreDebugAndroidTest
```

## Ktlint

VoicePlus uses **Ktlint** to enforce consistent code formatting.

- Check for formatting issues:

```sh
./gradlew lintKotlin
```

- Auto-fix formatting:

```sh
./gradlew formatKotlin
```

- To make commits fail on formatting errors, set up a pre-commit hook:

```sh
echo "./gradlew lintKotlin" > .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

## Dependency updates

Run Renovate locally from the repository root:

```sh
./scripts/renovate_local.sh
```

The script uses pinned Renovate and Node versions, validates `renovate.json`, and
runs Renovate's local lookup-only mode for Gradle dependencies. Local mode does
not edit files or create branches. Apply reviewed updates in small batches, then
run the unit, lint, build, and relevant emulator tests before committing them.

## Releasing

Before tagging, refresh the README, F-Droid description and version-code changelog.
Follow [the screenshot checklist](screenshots/README.md): retain the published image
filenames, export current captures, and run `python3 docs/screenshots/validate.py`.
CI checks dimensions, slot coverage, matching captioned frames, stale exports and
duplicate decoded pixels in both source captures and final images.
Deleting or renaming a published image can leave an old screenshot on F-Droid.

Follow the [release checklist](release-checklist.md) before merging or tagging.
Pushing a `vMAJOR.MINOR` tag starts the
[Release Workflow](https://github.com/Mistermo-vibecode/VoicePlus/actions/workflows/release.yml).
Manual runs must also use that version's matching tag.

The workflow builds a signed `libre` release APK, independently rebuilds it with
F-Droid's prebuild adjustments, and checks reproducibility by copying and verifying
the APK signature. A mismatch stops the release before draft creation.
It then publishes a draft GitHub release. F-Droid picks up the binary
from the published release later; the draft must first be published. Verify the
live F-Droid screenshot listing after its metadata refresh. Local validation is
not evidence that the public listing has already changed.

CI signs the APK from base64-encoded keystore secrets. The release requires these secrets:

| Secret              | Purpose                                  |
|---------------------|------------------------------------------|
| `KEYSTORE_BASE64`   | Release keystore, base64 encoded.        |
| `KEYSTORE_PASSWORD` | Release keystore password.               |
| `KEY_ALIAS`         | Release key alias.                       |
| `KEY_PASSWORD`      | Release key password.                    |

## Versioning

VoicePlus uses simple `MAJOR.MINOR` versions (e.g. `1.30`), set as `versionName` and `versionCode` in
[`app/build.gradle.kts`](../app/build.gradle.kts). Each release is tagged `vMAJOR.MINOR`.
