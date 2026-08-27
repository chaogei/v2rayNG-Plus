# 预编译 APK（debug）

云端从本仓库 `main` 源码编译的**可安装 debug APK**，包含本仓库全部增强：本地入站代理（SOCKS / HTTP / Mixed / 双端口 / 认证 / 透明入站）、配置生成性能优化、全局毛玻璃 UI 和六套主题配色。

## 文件

| 文件 | Variant | ABI | 大小 | SHA256 |
| --- | --- | --- | --- | --- |
| `v2rayNG-glass-fdroid-arm64-debug.apk` | fdroidDebug | arm64-v8a | 40,727,168 字节（约 39 MB） | `8a4c9d3d5384017ba391fdca904cc158775a182341fe591769cf9ba46286e60c` |
| `v2rayNG-glass-fdroid-universal-debug.apk` | fdroidDebug | universal（arm64-v8a + armeabi-v7a + x86 + x86_64） | 84,629,445 字节（约 81 MB） | `946b55456bd2909907a0f16cde541988570efcfa9f0445d080d5018fd72c9263` |

> 本次 APK 含全部 bug 修复（见 [`docs/bug-analysis.md`](../docs/bug-analysis.md) 第二轮，第 11–20 条）：主题切换不再重启核心、认证凭据为空时的状态与文案、HTTP 端口冲突时设置页跟随规范化后的值、透明入站端口冲突改为硬失败且入站绑回环、数字类设置弹出数字键盘、空状态避开悬浮按钮、对话框模糊只设置一次。第一轮（第 1–10 条）的 root 模式停止离主线程、动态 SOCKS 端口经 MMKV 跨进程共享、切换节点先等核心停稳再启动等修复也都已包含。
>
> 更早的 UI 交互与细节打磨（间距 token、选中/按压反馈、48dp 触控热区、空状态、FAB 防抖、主题切换过渡）同样在内，详见仓库 README「交互与细节打磨」。

**绝大多数近年的手机选 arm64 版即可**；不确定 CPU 架构或要装到模拟器/旧设备时用 universal 版。

- Gradle 任务：`assembleFdroidDebug`（JDK 21、SDK Platform 37.0、Gradle 9.5.1、AGP 9.3.1 自带的 Build-Tools 36.0.0）
- 核心库：`libv2ray.aar` 来自上游 [AndroidLibXrayLite v26.8.20](https://github.com/2dust/AndroidLibXrayLite/releases/tag/v26.8.20)（SHA256 `670cf11d9d10a6bb6548ac4f593acfa4339155732f6f8de4d45923f30a74deed`）
- tun2socks 原生库：`libhev-socks5-tunnel.so` / `libhevsockstun.so`（VPN 模式与 root 模式所需）提取自上游官方 [v2rayNG 2.3.5 release APK](https://github.com/2dust/v2rayNG/releases/tag/2.3.5)，已打入本 APK，各 ABI 齐全
- 应用 ID：`com.v2ray.ang.fdroid`（fdroid flavor 带后缀，可与官方 Play 版共存）
- 版本：2.3.5（arm64 包 versionCode 5074501，universal 包 5074500）
- 源码提交：`da46bde`（`main`；其后的 `14daa9e` 只添加了本目录的 APK 与说明，不含源码改动）
- 签名证书：Android Debug（`C=US, O=Android, CN=Android Debug`），证书 SHA-256 `5d06e758e4a4e7db7cfc733e29592cf1669f929e664969be537c87507fa2b945`

校验：下载后运行 `sha256sum <apk>`，与上表比对。

## 可复现性

在干净的云端环境里按上述工具链重新执行了一次 `assembleFdroidDebug`（同一份源码、同一份 `libv2ray.aar` 与 hev 原生库），解包后与上表两个 APK 的全部条目**逐字节一致**（`classes*.dex`、资源、`lib/*/*.so` 均相同），仅 APK 签名块因 debug 密钥随机生成而不同。因此上表文件确认就是「修复完成后」的构建产物，无需重新下载或卸载重装。

单元测试 `testFdroidDebugUnitTest` 与 `apksigner verify`（v2 方案）在该次复现中均通过。

## 安装方法

方式一（adb）：

```bash
adb install -r v2rayNG-glass-fdroid-arm64-debug.apk
```

方式二（直接安装）：把 APK 传到手机（网盘 / USB / 聊天工具"文件"），在文件管理器中点击安装。

## 注意事项

- **debug 签名**：APK 用 Android 默认 debug 证书签名（非上游官方签名）。系统会提示"未知来源应用"，需在设置中允许当前来源安装；**不能覆盖安装**官方签名的 v2rayNG（签名不同，且本包 applicationId 带 `.fdroid` 后缀，通常是并存而非冲突）。
- **debug 构建**：未混淆未优化，体积和运行时开销略大于 release；日常使用无碍。
- 之前装过本目录里同名的 APK 时可以直接覆盖安装（签名和 applicationId 都没变，设置与订阅会保留）。
- 首次开启 VPN 模式需授权 VPN 连接；Android 13+ 还会请求通知权限。

## 已知限制

- 未做 release 签名/对齐优化，不适合分发，仅供本人安装测试。
- hev-socks5-tunnel 原生库为上游 2.3.5 官方产物（非本仓库源码编译），与本仓库其余代码版本一致（本仓库基于同一 2.3.5 快照，未改动协议/核心逻辑）。
- 云端无真机，未做安装后的真机回归；编译、单元测试（`testFdroidDebugUnitTest`）与签名验证（`apksigner verify`，v2 方案、Android Debug 证书）均通过。
- 修复后仍存在的行为变化：透明入站端口冲突现在是启动失败而不是静默降级；dokodemo-door 入站绑定 `127.0.0.1`，靠 PREROUTING `REDIRECT` 给局域网客户端做透明代理的手工玩法不再可行（内建 LAN 共享走 hev-tun，不受影响）。详见 `docs/bug-analysis.md` 末尾「修复后仍存在的已知限制」。
