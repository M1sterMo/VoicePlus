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

To release a new version, push a `vMAJOR.MINOR` tag, or dispatch the
[Release Workflow](https://github.com/Mistermo-vibecode/VoicePlus/actions/workflows/release.yml) manually.

The workflow builds a signed `libre` release APK and publishes it as a draft GitHub release. F-Droid picks up the binary
from the release a few days later.

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
