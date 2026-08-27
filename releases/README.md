# 预编译 APK（debug）

云端从本仓库 `main` 源码编译的**可安装 debug APK**，包含本仓库全部增强：不选节点也能开的「本地代理 · 直连」、本地入站代理（SOCKS / HTTP / Mixed / 双端口 / 认证 / 透明入站）、配置生成性能优化、全局毛玻璃 UI 和六套主题配色。

## 文件

| 文件 | Variant | ABI | 大小 | SHA256 |
| --- | --- | --- | --- | --- |
| `v2rayNG-glass-fdroid-arm64-debug.apk` | fdroidDebug | arm64-v8a | 40,729,696 字节（约 38.8 MB） | `8e6e68a2d0daab9057c8208e06c0ccd5b840cc2d9d1d3dc33f6de85ca2a558a7` |
| `v2rayNG-glass-fdroid-universal-debug.apk` | fdroidDebug | universal（arm64-v8a + armeabi-v7a + x86 + x86_64） | 84,631,973 字节（约 80.7 MB） | `490cfa57db64c745f6bf71d038b8532341215e6882bcf813d982cd9bc6499baf` |

**绝大多数近年的手机选 arm64 版即可**；不确定 CPU 架构或要装到模拟器/旧设备时用 universal 版。

校验：下载后运行 `sha256sum <apk>`，与上表比对。

## 本次新增：不选节点也能开本地代理（直连）

这是相对上一版 APK 唯一的功能变化，来自源码提交 `294b388` / `1c95f5d`：

- 以前没有选中节点时整条启动链路都会直接拒绝（连接 FAB、快捷设置磁贴、桌面小部件、快捷方式、`LauncherManager`、核心启动循环都各自拦一道），本地 SOCKS/HTTP/Mixed 入站没法单独用。
- 现在**不选中任何节点**（新装、节点列表为空、选中的节点被删掉）直接点连接就能起核心；已经选了节点时，主界面右上角「⋮」→ **本地代理 · 直连** 也能显式切过去，选中项会保留，点回列表里的节点即可切回。
- 该模式下 `proxy` 标签是 `freedom` 出站而不是伪造的假节点，所以路由规则、DNS 分流、`block` 规则、tun / dokodemo 入站路由都照常生效，只是流量直接出去；指向自定义出站标签的规则也落到直连，不会意外走到远程节点。
- VPN / 仅代理 / root 三种运行模式都支持；不会静默替你选节点（订阅更新不再在无选中项时自动选第一个）。
- 状态文案：底栏「未连接 · 本地代理（直连）」/「本地代理 · 直连。点击检查连接。」，通知栏标题与快捷设置磁贴显示「本地代理 · 直连」。

详见 [`docs/local_inbound_zh.md`](../docs/local_inbound_zh.md#不选节点只开本地代理直连) 与仓库 README。

## 此前已包含的修复与增强（仍在本包内）

> 全部 bug 修复见 [`docs/bug-analysis.md`](../docs/bug-analysis.md)。第二轮（第 11–20 条）：主题切换不再重启核心、认证凭据为空时的状态与文案、HTTP 端口冲突时设置页跟随规范化后的值、透明入站端口冲突改为硬失败且入站绑回环、数字类设置弹出数字键盘、空状态避开悬浮按钮、对话框模糊只设置一次。第一轮（第 1–10 条）：root 模式停止离主线程、动态 SOCKS 端口经 MMKV 跨进程共享、切换节点先等核心停稳再启动等。
>
> 更早的 UI 交互与细节打磨（间距 token、选中/按压反馈、48dp 触控热区、空状态、FAB 防抖、主题切换过渡）同样在内，详见仓库 README「交互与细节打磨」。

## 构建信息

- Gradle 任务：`assembleFdroidDebug`（JDK 21、SDK Platform 37.0、Gradle 9.5.1、AGP 自带的 Build-Tools 36.0.0）
- 核心库：`libv2ray.aar` 来自上游 [AndroidLibXrayLite v26.8.20](https://github.com/2dust/AndroidLibXrayLite/releases/tag/v26.8.20)（SHA256 `670cf11d9d10a6bb6548ac4f593acfa4339155732f6f8de4d45923f30a74deed`）
- tun2socks 原生库：`libhev-socks5-tunnel.so` / `libhevsockstun.so`（VPN 模式与 root 模式所需）提取自上游官方 [v2rayNG 2.3.5 release APK](https://github.com/2dust/v2rayNG/releases/tag/2.3.5)，已打入本 APK，四个 ABI 齐全（解包核对过 `lib/*/libhev*.so`）
- 应用 ID：`com.v2ray.ang.fdroid`（fdroid flavor 带后缀，可与官方 Play 版共存）
- 版本：2.3.5（arm64 包 versionCode 5074501，universal 包 5074500）
- 源码提交：`1c95f5d`（`main`）
- 签名证书：Android Debug（`C=US, O=Android, CN=Android Debug`），证书 SHA-256 `c62063bb866090166d9071c28c01103041dbdd1cfa4743ffeb664bebe22740d7`
- 验证：`apksigner verify` 通过（v2 方案）；单元测试 `testFdroidDebugUnitTest` 通过

## 安装方法

方式一（adb）：

```bash
adb install -r v2rayNG-glass-fdroid-arm64-debug.apk
```

方式二（直接安装）：把 APK 传到手机（网盘 / USB / 聊天工具「文件」），在文件管理器中点击安装。

## 注意事项

- **本次需要先卸载旧版**：debug 密钥是每台构建机随机生成的，本次构建机与上一版不是同一台，签名证书从 `5d06e758…` 变成了 `c62063bb…`。装过上一版 APK 的设备会报「应用未安装 / 签名不一致」，必须先卸载旧的 `com.v2ray.ang.fdroid` 再装。**卸载会清掉设置与订阅**，建议先用应用内「备份与恢复」导出一份再卸载：

```bash
adb uninstall com.v2ray.ang.fdroid
adb install v2rayNG-glass-fdroid-arm64-debug.apk
```

- **debug 签名**：APK 用 Android 默认 debug 证书签名（非上游官方签名）。系统会提示「未知来源应用」，需在设置中允许当前来源安装；**不能覆盖安装**官方签名的 v2rayNG（签名不同，且本包 applicationId 带 `.fdroid` 后缀，通常是并存而非冲突）。
- **debug 构建**：未混淆未优化，体积和运行时开销略大于 release；日常使用无碍。
- 首次开启 VPN 模式需授权 VPN 连接；Android 13+ 还会请求通知权限。

## 已知限制

- 未做 release 签名/对齐优化，不适合分发，仅供本人安装测试。
- 每次在新的云端构建机上重编都会换一把 debug 密钥，因而都需要卸载重装。仓库里没有固定的 debug keystore，要避免这一点得引入一份稳定的签名配置。
- hev-socks5-tunnel 原生库为上游 2.3.5 官方产物（非本仓库源码编译），与本仓库其余代码版本一致（本仓库基于同一 2.3.5 快照，未改动协议/核心逻辑）。
- 云端无真机，未做安装后的真机回归；编译、单元测试（`testFdroidDebugUnitTest`）与签名验证（`apksigner verify`，v2 方案、Android Debug 证书）均通过，「本地代理 · 直连」的三条新增字符串资源也在解包后的 APK 里核对过。
- 修复后仍存在的行为变化：透明入站端口冲突现在是启动失败而不是静默降级；dokodemo-door 入站绑定 `127.0.0.1`，靠 PREROUTING `REDIRECT` 给局域网客户端做透明代理的手工玩法不再可行（内建 LAN 共享走 hev-tun，不受影响）。详见 `docs/bug-analysis.md` 末尾「修复后仍存在的已知限制」。
