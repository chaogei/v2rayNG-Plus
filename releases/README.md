# 预编译 APK

仓库不再检入 debug APK。换一台云机构建就会换一把随机 debug 密钥，覆盖安装必失败，而且 80MB 级二进制会把 git 撑得很重。

请从源码编译：

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

`V2rayNG/debug.keystore` 已检入，debug 构建一律用它：

- 别名：`androiddebugkey`
- 密码：`android`
- 证书 SHA-256：`AA248DDF13EB4919DCDED01A4D7308567DC75ECADDA1EB2B5344AC0CB3077E55`

同一把钥匙打出来的 debug 包可以互相覆盖安装，不必先卸载。

## 版本身份

- 应用版本：`2.3.5-plus`（`versionCode` 746）
- applicationId（fdroid）：`com.v2ray.ang.fdroid`
- 检查更新 / 关于 / 源码：`https://github.com/chaogei/v2rayNG-Plus`
