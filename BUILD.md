# Build

## Requirements

| Tool | Version |
| --- | --- |
| JDK | 17 |
| Android SDK | platform 35, build-tools 35.0.0 |
| Gradle | 8.7 (wrapper included) |
| Min Android | 8.0 (API 26) |

Android Studio Ladybug or newer works out of the box. For a command-line-only
setup, point `ANDROID_HOME` at your SDK or create `local.properties`:

```properties
sdk.dir=C\:\\Android\\sdk
```

## Debug build

```bash
# macOS / Linux
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

Install on a connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If the phone is not connected, copy the APK to the device and open it (allow
"install unknown apps" for the file manager).

## Release build

The build only signs automatically when `keystore.properties` exists. Without
it, `assembleRelease` produces an unsigned APK.

### 1. Create a keystore (once)

```bash
keytool -genkeypair -v -keystore keystore/release.jks \
  -alias cntrack -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass CHANGE_ME -keypass CHANGE_ME \
  -dname "CN=CNTrack, OU=Mobile, O=YourOrg, L=City, ST=State, C=CA"
```

### 2. Add `keystore.properties` in the project root

```properties
storeFile=keystore/release.jks
storePassword=CHANGE_ME
keyAlias=cntrack
keyPassword=CHANGE_ME
```

`storeFile` is resolved relative to the project root. Both the keystore and
`keystore.properties` are git-ignored — keep backups; losing the key means you
cannot update an installed release build.

### 3. Build

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

### 4. Verify the signature

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

`apksigner` lives in `$ANDROID_HOME/build-tools/35.0.0/`.

> Debug and release builds use different signing keys. Uninstall the debug app
> before installing a release build:

```bash
adb uninstall com.avancerifs.cntrack
```

## Clean

```bash
./gradlew clean
```

If a build behaves oddly after a plugin/dependency change, also delete `.gradle/`
and `app/build/` in the project.

## Offline / portable toolchain

A JDK, Android SDK and Gradle can be kept in a folder and activated per shell
instead of installing them globally. In that case run:

```bat
call "C:\path\to\toolchain\env.cmd"
gradle --no-daemon -p "C:\path\to\CNTrack-android-app" assembleDebug
```

The Gradle wrapper is still the recommended entry point for other machines.

## Troubleshooting

| Problem | Fix |
| --- | --- |
| `SDK location not found` | Set `ANDROID_HOME` or add `local.properties` |
| `Unsupported class file major version` | Use JDK 17 (`java -version`) |
| Dependency download failures | First build needs internet; check proxy/VPN, then retry |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Uninstall the previous app (signing key changed) |
| App shows no shipments | Deploy the sheet sync script, then pull from the ⋮ menu |
| App shows "Tracking URL is not set" | `⋮` → Settings → paste the proxy `/exec` URL |
| Map tiles blank | osmdroid needs network for tiles; the route itself is offline |
