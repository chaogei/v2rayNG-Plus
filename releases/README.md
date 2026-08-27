# 预编译 APK（debug）

云端从本仓库 `main` 源码编译的**可安装 debug APK**，包含本仓库全部增强：本地入站代理（SOCKS / HTTP / Mixed / 双端口 / 认证 / 透明入站）、配置生成性能优化、全局毛玻璃 UI 和六套主题配色。

## 文件

| 文件 | Variant | ABI | 大小 | SHA256 |
| --- | --- | --- | --- | --- |
| `v2rayNG-glass-fdroid-arm64-debug.apk` | fdroidDebug | arm64-v8a | 40,784,727 字节（约 39 MB） | `35da0a94f9e39b2842539942ab965044dee8c69f5a7b5c1feca81030662bd147` |
| `v2rayNG-glass-fdroid-universal-debug.apk` | fdroidDebug | universal（arm64-v8a + armeabi-v7a + x86 + x86_64） | 97,705,415 字节（约 93 MB） | `f61e3cf9137520b856c0b74c760a25110d21846cc7a0cfe1f4678db46c12ba5a` |

**绝大多数近年的手机选 arm64 版即可**；不确定 CPU 架构或要装到模拟器/旧设备时用 universal 版。

- Gradle 任务：`assembleFdroidDebug`（JDK 21、SDK Platform 37.0、Build-Tools 37.0.0、Gradle 9.5.1）
- 核心库：`libv2ray.aar` 来自上游 [AndroidLibXrayLite v26.8.20](https://github.com/2dust/AndroidLibXrayLite/releases/tag/v26.8.20)
- tun2socks 原生库：`libhev-socks5-tunnel.so` / `libhevsockstun.so`（VPN 模式与 root 模式所需）提取自上游官方 [v2rayNG 2.3.5 release APK](https://github.com/2dust/v2rayNG/releases/tag/2.3.5)，已打入本 APK，各 ABI 齐全
- 应用 ID：`com.v2ray.ang.fdroid`（fdroid flavor 带后缀，可与官方 Play 版共存）
- 版本：2.3.5（versionCode 745）

校验：下载后运行 `sha256sum <apk>`，与上表比对。

## 安装方法

方式一（adb）：

```bash
adb install -r v2rayNG-glass-fdroid-arm64-debug.apk
```

方式二（直接安装）：把 APK 传到手机（网盘 / USB / 聊天工具"文件"），在文件管理器中点击安装。

## 注意事项

- **debug 签名**：APK 用 Android 默认 debug 证书签名（非上游官方签名）。系统会提示"未知来源应用"，需在设置中允许当前来源安装；**不能覆盖安装**官方签名的 v2rayNG（签名不同，且本包 applicationId 带 `.fdroid` 后缀，通常是并存而非冲突）。
- **debug 构建**：未混淆未优化，体积和运行时开销略大于 release；日常使用无碍。
- 首次开启 VPN 模式需授权 VPN 连接；Android 13+ 还会请求通知权限。

## 已知限制

- 未做 release 签名/对齐优化，不适合分发，仅供本人安装测试。
- hev-socks5-tunnel 原生库为上游 2.3.5 官方产物（非本仓库源码编译），与本仓库其余代码版本一致（本仓库基于同一 2.3.5 快照，未改动协议/核心逻辑）。
- 云端无真机，未做安装后的真机回归；编译与签名验证（`apksigner verify`）均通过。
