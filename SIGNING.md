# Permanent release signing

The app ID stays `com.norman.duckdough`. Every distributed release must use the
same private key and a greater `versionCode`. The public certificate and its
SHA-256 fingerprint are in `signing/`; neither file contains the private key.

## GitHub Actions

The repository secret `DUCKDOUGH_SIGNING_BUNDLE` contains a JSON object with
`keystoreBase64`, `alias`, `password` and `certificateSha256`. Base64 is only a
binary transport format; the PKCS12 key is password-encrypted and the whole
bundle must be stored as a GitHub encrypted secret, never as a source file.

Only trusted builds on `main` consume this secret. Pull requests do not build
signed releases. The key is decoded into a temporary private file, used by the
release-build subprocesses and deleted before emulator verification. Passwords
and key data are not printed. Signing does not fall back to a debug key.

The release process verifies both APK signatures against the pinned certificate.
It also builds a temporary higher-version APK and checks `adb install -r` on an
emulator while retaining a user-entered pizza count. This probe is not included
in the downloadable release artifact.

## Continuing from another computer

Clone the repository, make your changes, increase `versionCode` and update
`versionName` in `app/build.gradle.kts`, then push to `main`. GitHub Actions uses
the same encrypted repository secret. No signing file is needed on the new
development computer when using CI to produce signed APKs.

For local release signing, supply `DUCKDOUGH_KEYSTORE_PATH`,
`DUCKDOUGH_STORE_PASSWORD`, `DUCKDOUGH_KEY_ALIAS`, and `DUCKDOUGH_KEY_PASSWORD` to
the Gradle process. Keep these values and the PKCS12 file outside the repository.

## Backup and first installation

Retain the original PKCS12 file and its password in a private backup. GitHub does
not let you download secret values later. Without the original signing key,
future updates cannot replace installed releases.

The old temporary debug APK uses a different key. If it is already installed,
remove it once before installing the first permanent-key release. Future
releases with the permanent key update in place and retain app data.
