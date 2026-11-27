# Publishing a release from GitHub

The **Release** workflow (`.github/workflows/release.yml`) runs the unit tests, builds the APK, verifies its signature,
writes SHA-256 checksums and attaches everything to a GitHub Release. It has only been syntax-checked so far, not run.

## 1. One-time: create a signing key (recommended)

Without a key the workflow still works, but publishes a **debug-signed pre-release** (installs as `com.traceon.debug`, not an upgrade path).

In Termux (or any machine with a JDK):

```
pkg install openjdk-17
keytool -genkeypair -v -keystore release.jks -alias traceon -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks > release.jks.b64     # the file content is what you paste into the secret
```

When keytool asks for the key password, press Enter to reuse the keystore password. (Modern keystores are PKCS12, which needs both to be the same.)

Then in the repo: **Settings -> Secrets and variables -> Actions -> New repository secret**, four times:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | contents of `release.jks.b64` |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | `traceon` |
| `KEY_PASSWORD` | same as the keystore password |

**Back up `release.jks` and the passwords somewhere safe.** If the key is lost you can never publish an update that installs over existing installs.
Never commit the keystore (it is git-ignored).

## 2. Cut a release (pick one)

* **Tag:** `git tag v0.1.0 && git push origin v0.1.0`
* **Manual:** GitHub -> Actions -> *Release* -> *Run workflow* -> enter `0.1.0` (the tag `v0.1.0` is created for you)
* **Phone only:** GitHub -> Releases -> *Draft a new release* -> *Choose a tag* -> type `v0.1.0` -> *Create new tag* -> Publish. The workflow then attaches the APK to that release.

Version format: `MAJOR.MINOR.PATCH` with an optional suffix (`0.2.0-rc1`, which is published as a pre-release).
`versionCode = major*10000 + minor*100 + patch`, so minor and patch must be 99 or less and every release must be higher than the last.

## 3. What you get

`TRACEON-v0.1.0.apk` and `SHA256SUMS.txt` on the release page (plus a workflow artifact copy). Install by opening the APK on the phone.
Check integrity with `sha256sum -c SHA256SUMS.txt`; for signed builds `apksigner verify --print-certs TRACEON-v0.1.0.apk` shows the certificate.

## Options and troubleshooting

* **R8 minification** is off by default because the app has not been run on a device yet. After it has, add `-PminifyRelease=true` to the `assembleRelease` line in the workflow and keep the uploaded `mapping.txt` for crash decoding.
* **"Version must look like 1.2.3"** - fix the tag name.
* **Build fails with compile errors** - the sources were never compiled where they were written (see README). Fix, then delete the failed tag and release (`git push --delete origin v0.1.0`, `git tag -d v0.1.0`) and tag again.
* **"Resource not accessible by integration"** - Settings -> Actions -> General -> Workflow permissions -> *Read and write*.
* **No APK on the release** - open the failed step in the Actions log; unit tests run first, and a failing test intentionally blocks the release.
