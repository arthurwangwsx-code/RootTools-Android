# GitHub Android Release

## 1. Repository identity

The Android application is published from `RootTools-Android` so the repository name stays
unambiguous next to the iOS `RootTools` project.

The old generic Android repository can remain as historical compatibility; new Android releases
and tags belong to `RootTools-Android`.

## 2. Release artifact

GitHub Releases publish one installable, signed APK:

```text
RootTools-Android-v<version>.apk
RootTools-Android-v<version>.apk.sha256
```

The release APK uses a persistent Android signing key. The keystore is never committed. A backup
is kept in the operator's private configuration directory and the CI copy is stored only in GitHub
Actions Secrets.

Required repository secrets:

```text
ROOTTOOLS_KEYSTORE_B64
ROOTTOOLS_KEYSTORE_PASSWORD
ROOTTOOLS_KEY_ALIAS
```

The current PKCS#12 keystore uses the store password as the private-key password, which avoids a
second credential that PKCS#12 does not portably support.

`app/build.gradle.kts` reads signing values only from environment variables. Ordinary local debug
builds therefore do not need release credentials.

## 3. Release workflow

`.github/workflows/release-android.yml` runs for every pushed `v*` tag. It:

1. checks out the exact tag;
2. restores the release key into the runner's temporary directory;
3. runs the repository quality and privileged-security guards;
4. runs JVM unit tests and Android lint;
5. builds `assembleRelease`;
6. verifies the APK signature with `apksigner`;
7. writes a SHA-256 sidecar;
8. creates or updates the GitHub Release.

Tags containing `-`, for example `v0.4.0-beta.1`, are published as GitHub prereleases.

## 4. Versioning

Before a new release:

1. increment `versionCode` monotonically;
2. set the intended `versionName`;
3. run the local quality/security/test/lint/assemble gate;
4. commit the release-ready source;
5. create a matching `v<versionName>` tag;
6. push the commit and tag.

The first remote-test release uses `0.4.0-beta.1` / versionCode `4` because Root Tailscale
coexistence still requires owner-device acceptance on Xiaomi 14.

## 5. Tester loop

The owner downloads the APK from GitHub Releases and installs it over the previous build. If a
privileged operation fails, collect the visible action result plus relevant RootTools audit/log
output. Do not delete `/data/adb/tailscale` before diagnosis because its bounded logs and preserved
Tailscale state are useful evidence.

For Root Tailscale specifically, useful evidence includes:

- Root Tailscale screen screenshot/state;
- current Android VPN owner;
- runtime version and mode;
- whether `tailscale0` exists;
- tailnet IPv4;
- route readiness;
- ADB `:5555` listener state;
- the most recent RootTools audit result.

No password, Tailscale credential, GitHub signing secret, or private key should be included in a
bug report.
