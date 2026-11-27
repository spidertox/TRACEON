# Building TRACEON

## Requirements
* JDK 17 (the project targets Java 17; AGP 8.7 runs on 17 or 21)
* Android SDK Platform 35 and build-tools (Android Studio installs these)
* Gradle 8.10.2 (Android Studio downloads it from `gradle/wrapper/gradle-wrapper.properties`)
* Network access for the first dependency download

The Gradle wrapper **jar and scripts are not included** (they cannot be generated offline). Either open the folder in Android Studio
(Ladybug or newer), or once run `gradle wrapper --gradle-version 8.10.2` with any installed Gradle to create `gradlew`.

## Commands
```
gradle :app:testDebugUnitTest          # JVM unit tests
gradle :app:assembleDebug              # app/build/outputs/apk/debug/app-debug.apk  (applicationId com.traceon.debug)
gradle :app:connectedDebugAndroidTest  # instrumented + Compose tests; needs a device or emulator
gradle :app:assembleRelease            # minified + shrunk; signed only if keystore.properties exists
```
Create `local.properties` with `sdk.dir=/path/to/Android/sdk` if Gradle cannot find the SDK.

## Building without a PC (phone-only workflow)
Building Android apps directly inside Termux is possible but fragile (it needs an aarch64 build of `aapt2`). The reliable route is CI:
1. Push this folder to a GitHub repository.
2. The `Android build` workflow runs unit tests, builds the debug APK and uploads it as the `traceon-debug-apk` artifact.
3. Download the artifact on the phone and install it (allow "install unknown apps" for your browser/file manager).
4. Run the workflow manually (`workflow_dispatch`) to also run the instrumented tests on an emulator.

## Release signing
```
keytool -genkeypair -v -keystore release.jks -alias traceon -keyalg RSA -keysize 2048 -validity 10000
cp keystore.properties.example keystore.properties   # edit the passwords
gradle :app:assembleRelease                          # app/build/outputs/apk/release/app-release.apk
```
`keystore.properties` and `*.jks` are git-ignored. CI passes the same values as `TRACEON_KEYSTORE_FILE`, `TRACEON_KEYSTORE_PASSWORD`, `TRACEON_KEY_ALIAS`, `TRACEON_KEY_PASSWORD` environment variables. Without either, the release build is produced unsigned.

Other properties: `-PappVersionName=1.2.3 -PappVersionCode=10203` (the release workflow sets these) and `-PminifyRelease=true` (R8 is off by default). Publishing to GitHub Releases: see [RELEASE.md](RELEASE.md).

## Room schema export
`app/schemas/<db>/<version>.json` is generated on build and must be committed: `MigrationTest` validates against it.
When you change an entity: bump `version` in `TraceonDatabase`, add a `Migration` to `Migrations.ALL`, extend `MigrationTest`.

## If the first build fails
Expect some compile errors: the code was never compiled where it was written. Typical places to look first: Compose/Material3 API
signatures that differ between BOM versions, Room KSP messages about the `activity_events` view or POJO columns, and Koin DSL imports
(`org.koin.androidx.viewmodel.dsl.viewModel`). Fix, rebuild, and run the unit tests before the instrumented ones.
