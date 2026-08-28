# v2rayNG — Agent guide

## Build

```sh
cd V2rayNG
./gradlew assembleFdroidDebug    # or assemblePlaystoreDebug
```

Kotlin 2.4.10, AGP 9.3.1, Gradle Kotlin DSL. Version `2.3.5-plus`. No lint/format/typecheck tasks configured.

## Test

```sh
cd V2rayNG && ./gradlew test
```

JUnit 4 + Mockito. Unit tests only under `app/src/test/java/`. No instrumented tests beyond defaults.

## Project structure

```
V2rayNG/
  app/
    src/main/java/com/v2ray/ang/
      AngApplication.kt          # extends MultiDexApplication, inits MMKV + WorkManager
      AppConfig.kt                # all constants, pref keys, URLs, ports, tags
      core/                       # v2ray core integration
        CoreServiceManager.kt      # start/stop core, traffic stats, delay measurement
        CoreConfigManager.kt       # generates JSON config for v2ray core
        LocalInboundConfigurator.kt # inbound layout (unified + CUSTOM)
        LocalProxyDirectPolicy.kt  # direct-start switches VPN → proxy-only
        RunModeLabels.kt           # one vocabulary for VPN / proxy-only / root / direct
        CoreNativeManager.kt       # JNI bridge to libv2ray AAR
        CoreOutboundBuilder.kt     # outbound config construction
        CoreConfigContextBuilder.kt
      service/                    # Android foreground services
        CoreVpnService.kt          # VPN mode (VpnService)
        CoreProxyOnlyService.kt    # proxy-only mode (no VPN)
        CoreTestService.kt         # delay test
        TProxyService.kt
        DialerNativeService.kt / DialerWebviewService.kt   # browser dialer
        RealPingWorkerService.kt   # WorkManager-based real ping
        QSTileService.kt           # quick settings tile
        ProcessService.kt
      handler/                    # business logic
        MmkvManager.kt             # all MMKV CRUD (servers, subs, settings, routing)
        SettingsManager.kt         # preference defaults, config generation (633 lines)
        AngConfigManager.kt        # server config operations
        NotificationManager.kt
        SpeedtestManager.kt
        SubscriptionUpdater.kt
        WebDavManager.kt
        UpdateCheckerManager.kt
        CertificateFingerprintManager.kt
        SettingsChangeManager.kt
      ui/                         # activities, adapters, fragments
        MainActivity.kt            # main screen with drawer + tabs
        ServerActivity.kt          # edit server config
        ServerCustomConfigActivity.kt / ServerGroupActivity.kt / ServerProxyChainActivity.kt
        SettingsActivity.kt / PerAppProxyActivity.kt / AppPickerActivity.kt
        ScannerActivity.kt / LogcatActivity.kt
        RoutingSettingActivity.kt / RoutingEditActivity.kt
        SubSettingActivity.kt / SubEditActivity.kt
        UserAssetActivity.kt / UserAssetUrlActivity.kt
        TaskerActivity.kt / UrlSchemeActivity.kt / ExternalControlActivity.kt
        BackupActivity.kt / CheckUpdateActivity.kt / AboutActivity.kt
      fmt/                        # protocol URL parsers (VMESS, VLESS, TROJAN, SS, SOCKS, etc.)
      dto/                        # data classes + entities/
      enums/                      # EConfigType, Language, RoutingType, etc.
      extension/_Ext.kt           # extension functions (toast, traffic string, etc.)
      util/                       # Utils, JsonUtil, HttpUtil, LogUtil, etc.
      receiver/                   # BootReceiver, TaskerReceiver, WidgetProvider
      contracts/                  # interfaces (ServiceControl, Tun2SocksControl)
      helper/                     # QRCodeScannerHelper, PermissionHelper, FileChooserHelper etc.
```

## Key facts

- **Storage**: MMKV exclusively — never SharedPreferences. `MmkvManager` is the data layer.
- **Core**: Native AAR (`libv2ray`) from [AndroidLibV2rayLite](https://github.com/2dust/AndroidLibV2rayLite) or [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite). Prebuilt `.aar` files go in `app/libs/`.
- **Services** run in dedicated process `:RunSoLibV2RayDaemon`. `CoreServiceManager` controls start/stop lifecycle.
- **Two modes**: VPN (`CoreVpnService`, uses `VpnService.Builder`) or proxy-only (`CoreProxyOnlyService`, local SOCKS/HTTP).
- **Flavors**: `fdroid` (suffix `.fdroid`) and `playstore` (no suffix). ABI version codes differ per flavor.
- **hev-socks5-tunnel**: Optional tun2socks binary. Build with `./compile-hevtun.sh` (requires `NDK_HOME`).
- **ViewBinding** enabled, no DataBinding.
- **CI / releases**: `.github/workflows/build.yml`. Every push to `master` builds all flavors/ABIs and publishes a GitHub Release automatically; PRs build an arm64 artifact only; `workflow_dispatch` allows a manual release with an optional `release_tag` input. Versions come from `.github/scripts/compute-version.sh` (self-test: `compute-version-test.sh`, run by CI before the version is computed) and never use `run_number`: a release is `versionCode=746+N+OFFSET` / `versionName=2.3.5.<N>-plus` with `N = highest released tag N + 1`, a PR build reuses the last released N as `2.3.5.<N>-pr.<sha>-plus` so it neither consumes a release number nor compares newer than a real release. `OFFSET` counts the `-plus` tags outside the `v2.3.5.N-plus` line (manual `release_tag` dispatches, excluding the `v2.3.5-plus` baseline): they consume a versionCode without advancing N, and two releases sharing a code cannot upgrade each other. A manual tag keeps the code monotonic but not the name — bump `BASE_VERSION_NAME` to move the whole line (no commit written back, so a release can never retrigger itself; `VersionUtil` strips `-plus`, the growing 4th segment keeps versions ordered). Release signing uses the `APP_KEYSTORE_*` secrets when present, otherwise the committed `V2rayNG/debug.keystore` and the Release is marked prerelease. `GPG_PRIVATE_KEY` is optional (skip, never fail). Asset names must keep matching `UpdateCheckerManager.getDownloadUrl`: `v2rayNG_<versionName>-fdroid_<abi>.apk` / `v2rayNG_<versionName>_<abi>.apk`.
- **Inbound layout**: `core/LocalInboundConfigurator.kt` (pure). Direct-start VPN→proxy-only: `core/LocalProxyDirectPolicy.kt`. Start-toast composition: `core/StartupToastPolicy.kt` (pure, messages joined into one toast because the snackbar host replaces rather than queues).
- **Release dry run**: `.github/workflows/version-preview.yml` is `workflow_dispatch`-only and `contents: read`. It runs the version self-test and prints what the next master push / a PR build / a given manual tag would produce, without building, tagging or publishing. Use it instead of triggering `build.yml`, which publishes a real Release.
- **Dependency policy**: `.github/dependabot.yml` covers GitHub Actions only — on purpose. No renovate, no gradle ecosystem: AGP/Kotlin/KSP/Compose move as one coupled matrix that CI can only compile-check, and every master push publishes a Release. Gradle upgrades are manual and must be justified. The core AAR version follows the `AndroidLibXrayLite` submodule tag, not a gradle coordinate.
- **Upstream comparison**: [cmfa_lessons_zh.md](cmfa_lessons_zh.md) holds the audited comparison with MetaCubeX/ClashMetaForAndroid (what to borrow, what already exists here, what is explicitly out of scope, and how the two release pipelines differ). External control is live: [external_control_zh.md](external_control_zh.md) (`com.v2ray.ang.action.{TOGGLE,START,STOP}`).
