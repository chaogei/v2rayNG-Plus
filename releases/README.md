# 获取 APK

仓库不检入任何 APK。**推荐直接从 [GitHub Releases](https://github.com/chaogei/v2rayNG-Plus/releases) 下载**：每次推送到 `master`，CI 都会自动编译并发布一个 Release，附带 fdroid 与 playstore 两个 flavor、全 ABI（arm64-v8a / armeabi-v7a / x86 / x86_64 / universal）的 release 包。

- Release tag 形如 `v2.3.5.<n>-plus`（`n` 随每次发版递增），应用内「检查更新」会自动比对并给出下载链接。
- 附件命名：`v2rayNG_<versionName>-fdroid_<abi>.apk`（fdroid）与 `v2rayNG_<versionName>_<abi>.apk`（playstore）。
- **签名注意**：仓库未配置正式签名 secrets 时，Release 会标记为 *prerelease*，APK 用仓库内固定 debug 密钥签名——**不能覆盖安装**正式签名的版本，需先卸载。配置了 `APP_KEYSTORE_*` secrets 后为正式签名。
- 配置了 `GPG_PRIVATE_KEY` secret 时每个 APK 附带 `.sig` 分离签名和公钥文件。

## 从源码编译

```bash
mkdir -p V2rayNG/app/libs
curl -fsSL -o V2rayNG/app/libs/libv2ray.aar \
  https://github.com/2dust/AndroidLibXrayLite/releases/latest/download/libv2ray.aar

cd V2rayNG
echo "sdk.dir=<你的 Android SDK 路径>" > local.properties
./gradlew assembleFdroidDebug
```

产物在 `V2rayNG/app/build/outputs/apk/fdroid/debug/`。

## 固定 debug 签名

`V2rayNG/debug.keystore` 已检入，debug 构建（以及未配置正式签名时的 CI release 构建）一律用它：

- 别名：`androiddebugkey`
- 密码：`android`
- 证书 SHA-256：`AA248DDF13EB4919DCDED01A4D7308567DC75ECADDA1EB2B5344AC0CB3077E55`

同一把钥匙打出来的包可以互相覆盖安装，不必先卸载。

<a id="release-flow"></a>

## 发版流程速查

| 触发方式 | 会发生什么 | 版本号 |
| --- | --- | --- |
| push 到 `master` | 自动构建全 flavor / 全 ABI 并发布 Release | `2.3.5.<N>-plus`，`N` = 已有 `v2.3.5.N-plus` tag 的最大值 + 1 |
| 手动 `workflow_dispatch` + `release_tag` | 用你填的 tag 发版（tag 会自动补 `v` 前缀） | 用你填的名字；`versionCode` 仍单调递增，后续自动编号会跳过它占掉的号 |
| PR | 只构建 arm64 artifact，**不发 Release、不占发版序号** | `2.3.5.<最近已发 N>-pr.<短 sha>-plus` |
| 手动 `Version preview` | 只读预演：打印上面三种情况的版本号并跑版本脚本自测，不构建、不打 tag、不发 Release | — |

- 未配置 `APP_KEYSTORE_*` secrets 时，Release 一律标记为 **prerelease**，正文附 debug 签名警告。这里的 prerelease 表示「签名降级」，不是「alpha 抢先版通道」。
- 版本号不会回写进仓库文件，而是构建时用 `-PversionName` / `-PversionCode` 注入，所以发版本身不会再触发一次发版。
- 算法与全部边界情况见 [`.github/scripts/compute-version.sh`](../.github/scripts/compute-version.sh)（自测：`bash .github/scripts/compute-version-test.sh`）；与 ClashMetaForAndroid 发版流程的逐条对照见 [docs/cmfa_lessons_zh.md](../docs/cmfa_lessons_zh.md)。

## 版本身份

- 仓库基线版本：`2.3.5-plus`（`versionCode` 746）；CI 发版时按已有 Release tag 自动递增为 `2.3.5.<n>-plus`，PR 构建不占用发版序号（见根 README「自动发版」）
- applicationId（fdroid）：`com.v2ray.ang.fdroid`
- 检查更新 / 关于 / 源码：`https://github.com/chaogei/v2rayNG-Plus`
