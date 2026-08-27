# v2rayNG fork —— Bug 分析

分析对象：本仓库 `main`（基线为 `a88a61e` 的上游导入，其后 10 个 fork 提交）。
分析方式：逐文件读代码 + 与基线 `git diff` 交叉对照 + 资源/清单/模板 JSON 核对。
构建环境无 Android SDK，无法执行 `./gradlew test` 或装机验证，因此下文每一条都只写代码本身能证明的结论；无法证明的猜测一律没有写进来。

共 20 条。已修 10 条（含 1 条文档），未修 10 条。

**标注约定**：`【已修】` 表示本次已提交补丁；`(fork 引入)` 表示该问题由本 fork 的改动带来，其余为基线已有。

---

## 一、崩溃 / 无响应

### 1. root 模式停止时在主线程阻塞等待 root 脚本，可触发 ANR

- **严重度**：崩溃/无响应（未修）
- **涉及**：`service/CoreRootService.kt#onDestroy`、`root/RootProxyManager.kt#stop/teardown`、`root/RootShell.kt#exec`
- **触发条件**：root 模式下停止服务（或切节点重启）。
- **为什么是 bug**：`onDestroy` 运行在主线程，里面串了两段阻塞操作：

```75:79:V2rayNG/app/src/main/java/com/v2ray/ang/service/CoreRootService.kt
        runBlocking { setupJob?.cancelAndJoin() }
        // Remove routing rules BEFORE stopping the core so traffic is never redirected
        // to a dead listener. Synchronous on purpose — leaving rules behind breaks the net.
        RootProxyManager.stop(this)
        CoreServiceManager.stopCoreLoop()
```

  `setupJob` 里跑的是 `RootShell.runScript` → `Process.waitFor(30, SECONDS)`。协程取消不会中断这个阻塞 IO 调用，所以 `cancelAndJoin()` 实际要等脚本自己结束；而 setup 脚本内部还有 `i=0; while [ $i -lt 20 ]; do ... sleep 0.3; done` 这段最长 6 秒的等待。紧接着 `RootProxyManager.stop()` 又同步执行一遍 teardown 脚本（同样最长 30 秒超时）。主线程连续阻塞 5 秒以上就会被系统判定 ANR。
- **建议修法**：把 onDestroy 的清理搬到服务自己的后台线程，并在服务停止前用一个 `stopping` 标志让 setup 协程在每个脚本之间检查取消点（`ensureActive()`），而不是靠 `cancelAndJoin` 去打断不可中断的 `waitFor`。若必须保证"规则先于核心移除"，把整段清理放进一个带超时的 `goAsync`/`WorkManager` 式收尾任务，onDestroy 只负责触发。

---

## 二、功能错误

### 2.【已修】升级后本地入站认证被静默关闭 (fork 引入)

- **严重度**：功能错误 + 安全
- **涉及**：`handler/SettingsManager.kt#readLocalAuthCredentials`、`AppConfig.PREF_LOCAL_AUTH_ENABLED`
- **触发条件**：老版本里设过 SOCKS 用户名/密码的用户升级到本 fork。
- **为什么是 bug**：基线的判定是"用户名和密码都非空就启用认证"：

```
if (socksUsername != null && socksPassword != null) { inbound1.settings?.auth = "password" ... }
```

  fork 新增了 `PREF_LOCAL_AUTH_ENABLED` 开关，`readLocalAuthCredentials()` 第一句就是 `if (!decodeSettingsBool(PREF_LOCAL_AUTH_ENABLED, false)) return null`，而这个 key 没有任何迁移代码（`initApp` 里只有 `migrateServerListToSubscriptions` 和 `migrateHysteria2PinSHA256`）。升级后凭据还在 MMKV 里、设置页的用户名/密码字段还显示着值，但生成的 inbound 变成 `"auth": "noauth"`。如果该用户同时开了 `PREF_PROXY_SHARING`（监听 0.0.0.0），升级瞬间就从"带认证的局域网代理"变成"无认证开放代理"。
- **修法**：在 `SettingsManager.initApp()` 增加一次性迁移 `migrateLocalAuthEnabled()`——凭据两项都非空就把开关置为 true，用独立 marker key 记录已迁移（与现有两处迁移写法一致）。新装用户凭据为空，迁移不生效。

### 3.【已修】"仅 HTTP" 模式下被强制保留的 SOCKS 入站会跟随 0.0.0.0 暴露 (fork 引入)

- **严重度**：功能错误 + 安全
- **涉及**：`core/CoreConfigManager.kt#configureInbounds`
- **触发条件**：本地入站模式选"仅 HTTP"，同时（VPN + hev-tun）或 root 模式，且监听地址选 0.0.0.0。
- **为什么是 bug**：`socksRequired = forcedByHev || forcedBySocksRoot || mode != HTTP`，所以"仅 HTTP"模式下 SOCKS 入站仍会保留——这是对的，hev-tun/root 需要它当隧道目标。但它的 `listen` 直接取了用户选的 `inbound.listenAddress`。而这个 SOCKS 入站的两个真实消费者都写死了回环：`TProxyService.buildConfig()` 里 `address: ${AppConfig.LOOPBACK}`，`RootProxyManager.buildHevConfig()` 里 `address: '${AppConfig.LOOPBACK}'`。也就是说绑 0.0.0.0 对功能毫无用处，纯粹是把一个用户明确取消勾选的 SOCKS 代理发布到了局域网上；由于 `Utils.isXray()` 在本项目所有 flavor 下恒为 true（`applicationId = "com.v2ray.ang"`，fdroid 只加后缀），Xray 的 socks 入站还会顺带接受 HTTP 请求，等于把"仅 HTTP"这个选择完全架空。
- **修法**：`mode == HTTP` 时把这个内部 SOCKS 入站固定绑到 `AppConfig.LOOPBACK`。

### 4.【已修】端口 +1 回退在 65535 处溢出成非法端口 65536 (fork 引入)

- **严重度**：功能错误
- **涉及**：`dto/LocalInboundSnapshot.kt`、`handler/SettingsManager.kt#getLocalInboundSnapshot`、`core/CoreConfigManager.kt#configureInbounds`
- **触发条件**：SOCKS 端口设为 65535，且处于会派生相邻端口的场景（HTTP 端口与之相同时的冲突回退；或非 Xray 核心的 MIXED 模式）。
- **为什么是 bug**：三处都写死了 `socksPort + 1`，65535 + 1 = 65536 超出端口范围，核心会以 "invalid port" 拒绝启动。UI 的冲突校验拦不住这条路径：`validateLocalPort` 给 SOCKS 端口传冲突值时有条件 `if (modeHasSeparateHttpPort) httpPortInt else null`，在 MIXED 模式下这一项是 `null`，所以可以先在 SOCKS+HTTP 模式把 HTTP 端口设成 65535、切到 MIXED 再把 SOCKS 端口也设成 65535，切回 SOCKS+HTTP 时快照就会算出 65536。
- **修法**：抽出 `LocalInboundSnapshot.neighborPort(port)`，在 65535 处改为向下取 65534，三处调用点统一走它。已补 `LocalInboundSnapshotTest` 覆盖。

### 5.【已修】root 模式的 hev 配置没有转义单引号，含 `'` 的密码会让隧道起不来

- **严重度**：功能错误
- **涉及**：`root/RootProxyManager.kt#buildHevConfig`
- **触发条件**：root 模式 + 开启本地认证 + 用户名或密码含 `'`。
- **为什么是 bug**：`TProxyService.buildConfig()` 里做了 YAML 单引号转义（`socksUsername?.replace("'", "''")`），root 路径拼同样的 YAML 块时却没有：

```
appendLine("  username: '$socksUsername'")
appendLine("  password: '$socksPassword'")
```

  写出去的 `tun2socks.yml` 语法就坏了，hev-socks5-tunnel 解析失败直接退出，setup 脚本随后 `ip link show $TUN` 检测不到设备而失败并回滚——用户看到的是"root 模式起不来"，日志里也只有一行脚本输出。（配置内容走 `<<'HEVCFG'` 引号 heredoc，所以不构成 shell 注入，只是 YAML 破损。）
- **修法**：与 `TProxyService` 一致，写入前 `replace("'", "''")`。

### 6. 动态 SOCKS 端口是进程内静态量，UI 进程和守护进程各生成一份

- **严重度**：功能错误（未修）
- **涉及**：`handler/SettingsManager.kt#runtimeSocksPort/getSocksPort/refreshRuntimeSocksPort`、`core/LauncherManager.kt:96`、`AndroidManifest.xml`
- **触发条件**：打开"动态 SOCKS 端口"（`PREF_DYNAMIC_SOCKS_PORT`），本地入站模式为 MIXED 或仅 SOCKS。
- **为什么是 bug**：`runtimeSocksPort` 是 `SettingsManager` 这个 object 的字段，只存在于内存，不落 MMKV。所有服务都跑在 `:RunSoLibV2RayDaemon`（清单里 `CoreVpnService` / `CoreProxyOnlyService` / `CoreRootService` / `SubscriptionUpdateService` / `QSTileService` 全部带 `android:process=":RunSoLibV2RayDaemon"`），而 `MainActivity` 及其余设置类 Activity 在默认进程。于是：
  - 从主界面点连接时，`LauncherManager.refreshRuntimeSocksPort()` 在 **UI 进程**里生成端口 A；
  - 守护进程生成配置时 `getSocksPort()` 发现自己的 `runtimeSocksPort` 为 null，独立生成端口 B，核心真正监听 B；
  - UI 进程后续所有走本地代理的功能拿到的都是 A：`UserAssetActivity`（geo 文件下载）、`PerAppProxyViewModel`（应用列表拉取）、`UpdateCheckerManager`（检查更新）、`AngConfigManager`（订阅更新，若在 UI 进程触发）都会连 `127.0.0.1:A`，那里没人监听。

  同时"动态"本身也没生效：守护进程只在 `MSG_STATE_RESTART` 那条路径（`ReceiveMessageHandler` → `LauncherManager.startService`）才会刷新，从主界面启动时它一直复用第一次生成的 B，直到进程被杀。
- **建议修法**：让随机端口有唯一权威来源——生成后写入 MMKV（多进程模式已开启，`MMKV.MULTI_PROCESS_MODE`），`getSocksPort()` 从 MMKV 读；`refreshRuntimeSocksPort()` 改为只在真正生成配置的守护进程里调用（`CoreServiceManager.doStartCoreLoop` 之前），并顺手覆盖 MMKV 里的值。这样 UI 进程读到的永远是核心当前监听的端口。

### 7. 切换节点的重启是"异步停 + 固定 500ms + 起"，慢一点就整条断开

- **严重度**：功能错误 / 竞态（未修）
- **涉及**：`ui/main/MainActivity.kt#setSelectServer`、`core/CoreServiceManager.kt#stopCoreLoop / startCoreLoop / ReceiveMessageHandler`
- **触发条件**：运行中点列表里的另一个节点；核心 `stopLoop()` 耗时超过 500 ms（节点多、连接多、低端机时容易发生）。
- **为什么是 bug**：链路是 `setSelectServer` → `LauncherManager.restartService` → 守护进程收到 `MSG_STATE_RESTART`：

```480:488:V2rayNG/app/src/main/java/com/v2ray/ang/core/CoreServiceManager.kt
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            serviceControl.stopService()
                            delay(500L)
                            LauncherManager.startService(serviceControl.getService())
                        } finally {
                            pendingResult.finish()
                        }
                    }
```

  而 `stopCoreLoop()` 里的 `coreController.stopLoop()` 是丢进 `CoroutineScope(Dispatchers.IO)` 异步执行的，函数立刻返回。500 ms 是一个没有任何确认的固定猜测：如果这时核心还没停干净，新一轮 `startCoreLoop()` 第一句 `if (isRunning()) { ...; return false }` 直接返回 false，`CoreVpnService.startService()` 收到 false 后调用 `stopAllService()`——用户的操作是"换个节点"，得到的结果是"整个断开"，且没有重试。
- **建议修法**：`stopCoreLoop()` 返回一个可等待的句柄（把 `stopLoop()` 的协程 Job 暴露出去），重启流程 `join()` 它而不是 `delay(500)`；再给 `startCoreLoop` 加一个有上限的重试（例如每 200 ms 轮询 `isRunning()`，最多 3 秒），失败才走 `stopAllService`。

### 8.【已修】密码类设置在输入对话框里明文显示

- **严重度**：功能错误（参数被忽略）
- **涉及**：`ui/compose/SettingsItem.kt#SettingsEditItem`
- **触发条件**：点开"本地代理密码"等 `isPassword = true` 的设置项。
- **为什么是 bug**：`isPassword` 只被用来把行摘要显示成 `******`，弹出的对话框却写死 `visualTransformation = VisualTransformation.None`，输入和已有值全程明文。fork 在 `SettingsActivity` 新增的本地代理密码项（`isPassword = true`）正好踩在这个参数上。
- **修法**：`isPassword` 时传 `PasswordVisualTransformation()`。

---

## 三、状态不一致

### 9.【已修】SOCKS UDP 开关的 UI 默认值与配置生成默认值相反

- **严重度**：状态不一致
- **涉及**：`ui/settings/SettingsActivity.kt:127`、`handler/SettingsManager.kt#getLocalInboundSnapshot`
- **触发条件**：新装后从未动过这个开关。
- **为什么是 bug**：UI 读 `rememberMmkvBool(PREF_SOCKS_ENABLE_UDP, false)`，配置生成读 `decodeSettingsBool(PREF_SOCKS_ENABLE_UDP, true)`。key 未写入时开关显示"关"，实际 inbound 里是 `"udp": true`。而且因为 `rememberMmkvBool` 用 `snapshotFlow{}.drop(1)`，只显示不会回写，这个矛盾会一直保持到用户主动拨动开关。
- **修法**：UI 默认值改为 `true`，与实际行为对齐（不改行为，只改显示）。

### 10.【已修】hev TUN 日志级别的默认值不在候选项里，列表显示成 "error"

- **严重度**：状态不一致
- **涉及**：`ui/settings/SettingsActivity.kt:115`、`res/values/arrays.xml#hev_tunnel_loglevel`、`service/TProxyService.kt`
- **触发条件**：新装后打开设置页看 "Hev TUN log level"。
- **为什么是 bug**：`hev_tunnel_loglevel` 数组是 `error / warn / info / debug`，UI 默认值却写的 `"warning"`。`SettingsListItem` 用 `options.find { it.second == selectedValue } ?: options.firstOrNull()` 兜底，匹配不上就退回第一项，于是界面显示 "error"；而 `TProxyService` 读同一个 key 拿到 null 后用的是 `?: "warn"`。三个值互不相同：显示 error、默认 warning、实际 warn。
- **修法**：UI 默认值改成候选项里真实存在的 `"warn"`。

### 11.【已修】切换主题预设会重启核心服务 (fork 引入)

- **严重度**：状态不一致
- **涉及**：`handler/SettingsChangeManager.kt#uiOnlyKeys`、`AppConfig.PREF_UI_THEME_PRESET`
- **触发条件**：连接状态下在设置页换一个主题配色。
- **为什么是 bug**：`rememberMmkvString` 每次写入都会 `SettingsChangeManager.notifySettingChanged(key)`，`notifySettingChanged` 里 `if (key !in uiOnlyKeys) makeRestartService()`。fork 新加的 `PREF_UI_THEME_PRESET` 没有进 `uiOnlyKeys`（相邻的 `PREF_DYNAMIC_COLOR`、`PREF_UI_MODE_NIGHT` 都在），于是换个颜色就把重启标志置上，回到主界面时 VPN 断一次重连。
- **修法**：把 `PREF_UI_THEME_PRESET` 加入 `uiOnlyKeys`。

### 12. 认证开关打开但凭据为空时，UI 显示"已启用"而配置里是 noauth (fork 引入)

- **严重度**：状态不一致 + 安全（未修）
- **涉及**：`ui/settings/SettingsActivity.kt:491-515`、`ui/settings/SettingsViewModel.kt#warnIfLocalAuthCredentialsMissing`、`handler/SettingsManager.kt#readLocalAuthCredentials`
- **触发条件**：打开认证开关但没填完凭据；或者先填好凭据、开好开关，之后再把用户名清空。
- **为什么是 bug**：`readLocalAuthCredentials()` 要求开关为 true **且**两个字段都非空，否则返回 null → `auth = "noauth"`、`accounts = null`。UI 侧只在 `onCheckedChange` 里 `if (it) warnIfLocalAuthCredentialsMissing(...)` 弹一次 toast；用户名/密码两个 `SettingsEditItem` 的 `onValueChanged` 完全不做校验。结果是开关一直显示"打开"，但代理实际无认证——配合 0.0.0.0 监听就是用户以为受保护、实际是开放代理。
- **建议修法**：把凭据完整性做成派生状态：开关行的 summary 在凭据不全时改成一句显式的"未生效（凭据不完整）"，并在用户名/密码的 `onValueChanged` 里复用 `warnIfLocalAuthCredentialsMissing`。更彻底的做法是凭据不全时不允许开关保持 true。

### 13. HTTP 端口与 SOCKS 端口冲突时运行期回退，设置页仍显示旧值 (fork 引入)

- **严重度**：状态不一致（未修）
- **涉及**：`handler/SettingsManager.kt#getLocalInboundSnapshot`、`ui/settings/SettingsActivity.kt#validateLocalPort` 调用处
- **触发条件**：让 `PREF_HTTP_PORT` 等于 SOCKS 端口（例如先在 MIXED 模式下改 SOCKS 端口——此时 HTTP 端口输入框是禁用的、也不参与冲突校验——再切回 SOCKS+HTTP）。
- **为什么是 bug**：快照里 `httpInboundPort = if (httpConfiguredPort == socksPort) neighborPort(socksPort) else httpConfiguredPort`，核心监听的是回退后的端口，而设置页 `httpPort` 显示的是 MMKV 里的原值。用户按界面上的端口去配浏览器代理会连不上，界面上也没有任何提示。UI 的冲突校验只在"当前模式确实有独立 HTTP 端口"时才把对方端口传进 `validateLocalPort`，所以它拦不住跨模式产生的这种冲突。
- **建议修法**：把冲突解决从"运行期静默回退"改成"入口处收敛"——切换本地入站模式时若检测到 `httpPort == socksPort`，当场把 `PREF_HTTP_PORT` 规范化成 `neighborPort(socksPort)` 并写回 MMKV，让界面和核心看到同一个值；或者在设置页把实际生效端口作为该行的 summary 显示出来。

### 14. 透明入站端口冲突被静默吞掉，开关仍显示"已开启" (fork 引入)

- **严重度**：状态不一致（未修）
- **涉及**：`core/CoreConfigManager.kt#configureInbounds`
- **触发条件**：dokodemo-door 端口与 SOCKS/HTTP 端口相同。
- **为什么是 bug**：

```596:601:V2rayNG/app/src/main/java/com/v2ray/ang/core/CoreConfigManager.kt
        if (enableLocalProxy && inbound.redirEnabled) {
            val redirPort = inbound.redirPort
            val portInUse = v2rayConfig.inbounds.any { it.port == redirPort }
            if (portInUse) {
                LogUtil.w(AppConfig.TAG, "Transparent redirect port $redirPort conflicts with another local inbound, skipping redir inbound")
            } else {
```

  只写一行 warn 日志就跳过。用户在设置页看到透明代理开关是打开的，去配 iptables REDIRECT 时才发现端口根本没监听，而排查线索只在 logcat 里。UI 校验同样漏了一种情况：redir 端口校验传的冲突值是 `socksPortInt` 和 `httpPortInt`（配置值），而 MIXED 模式在非 Xray 核心上真实的 HTTP 端口是 `socksPort + 1`，不在校验范围内。
- **建议修法**：跳过时通过 `MessageHelper.sendMsg2UI` 回一条可见提示（复用现有的启动失败提示通道），或者干脆让配置生成失败并给出明确 errorMessage——静默降级对"我以为我开了"这类问题是最差的处理。

### 15. dokodemo-door 入站同样跟随 0.0.0.0，且不受认证开关保护 (fork 引入)

- **严重度**：状态不一致 + 安全（未修）
- **涉及**：`core/CoreConfigManager.kt#configureInbounds`、`res/values/strings.xml#summary_pref_local_auth_enabled`
- **触发条件**：同时开启透明入站与 0.0.0.0 监听。
- **为什么是 bug**：redir 入站用的是同一个 `listen = inbound.listenAddress`，但 dokodemo-door 协议本身没有账号机制，`authAccounts` 也没传给它。设置项文案写的是"Require username/password on the local inbounds"——"the local inbounds" 这个措辞覆盖不到这个例外。虽然 `followRedirect = true` 让非 NAT 重定向的直连请求拿不到目的地址、实际可利用性很低，但"打开认证 = 所有本地入站都受保护"这个心智模型是错的。
- **建议修法**：要么把 redir 入站固定绑回环（需要 LAN 转发时由 root 规则负责引流，这也是它现在的实际用法），要么在该设置项 summary 里明确写出 dokodemo-door 不参与认证。

---

## 四、体验缺陷

### 16.【已修】设置输入对话框全部变成单行，长值不再换行 (fork 引入)

- **涉及**：`ui/compose/Dialog.kt#InputDialog`、`ui/compose/SettingsItem.kt#SettingsEditItem`
- **为什么是 bug**：`InputField.singleLine` 的默认值是 `true`，基线的 `InputDialog` 一直无视它、硬编码 `singleLine = false, maxLines = 5`；fork 改成了 `singleLine = field.singleLine`，于是所有调用方一起退化为单行。受影响最明显的是 `title_pref_remote_dns` / `title_pref_domestic_dns` / `title_pref_dns_hosts` 这几个逗号分隔的长字段，编辑时只能横向滚动。
- **修法**：`SettingsEditItem` 只对端口类（`keyboardNumber`）和密码类传 `singleLine = true`，其余恢复多行。

### 17. `keyboardNumber` 仍然没有连到键盘类型

- **涉及**：`ui/compose/SettingsItem.kt#SettingsEditItem`、`ui/compose/Dialog.kt#InputField`（未修）
- **为什么是 bug**：端口、MTU、超时这些设置项都传了 `keyboardNumber = true`，但 `InputField` 没有 `keyboardOptions` 字段，这个参数从来没到达 `OutlinedTextField`，弹出的仍是全键盘。
- **建议修法**：给 `InputField` 增加 `keyboardOptions: KeyboardOptions = KeyboardOptions.Default`，`InputDialog` 透传给 `OutlinedTextField`，`SettingsEditItem` 在 `keyboardNumber` 时传 `KeyboardType.Number`。改动很小但会动到 `InputField` 的公共签名，所以本次没有顺手改。

### 18. 空状态忽略了列表的 contentPadding

- **涉及**：`ui/main/MainServerPager.kt#ServerListPage`、`ui/compose/Components.kt#EmptyState`（未修）
- **为什么是 bug**：`MainScreen` 给列表传的是 `PaddingValues(bottom = 80.dp)`，用来给悬浮在底栏上方的 FAB 留位；`servers.isEmpty()` 分支直接 `return` 一个 `EmptyState`，没有把这个 padding 传下去，而 `EmptyState` 内部是 `fillMaxSize()` + 垂直居中。屏幕较矮时提示文字会被 FAB 压住。
- **建议修法**：给 `EmptyState` 加一个 `contentPadding: PaddingValues = PaddingValues(0.dp)` 参数并应用到外层 `Column`，空状态分支把列表那份 padding 传进去。

### 19. 对话框模糊效果在每次重组时重设窗口属性 (fork 引入)

- **涉及**：`ui/compose/Glass.kt#GlassDialogWindowEffect`（未修）
- **为什么是 bug**：`SideEffect` 在每次成功重组后都会跑一遍 `window.attributes = window.attributes.also { it.blurBehindRadius = radiusPx }`，赋值 `attributes` 会触发一次 WindowManager relayout。输入类对话框每敲一个字符就重组一次，等于每个字符做一次窗口重排。半径其实是常量，重复设置没有任何意义。
- **建议修法**：换成 `DisposableEffect(radiusPx)`，只在半径变化时设置一次。

### 20.【已修】文档漂移：`local_inbound_zh.md` 引用了不存在的函数

- **涉及**：`docs/local_inbound_zh.md`
- **为什么是 bug**：文中写"`SettingsManager.getHttpInboundPort()` 会自动回退为 `SOCKS端口+1`"。`getHttpInboundPort()` 这个函数不存在（真实入口是 `SettingsManager.getLocalInboundSnapshot().httpInboundPort`），而且经第 4 条修复后回退规则也不再是无条件 +1。
- **修法**：更正函数名并说明 65535 处向下取的边界行为。

---

## 附：核对过但**不是** bug 的几处

为避免后来者重复排查，记录几处看起来可疑、实际验证下来没问题的地方：

- **多语言字符串数组条目数**：脚本比对了 `values/` 与全部 `values-*/` 下同名 `string-array` 的条目数，无一处不一致，`entries.zip(values)` 不会错位。
- **DNS hosts 里的 IPv6**：`configureDns` 用的是 `split(":", limit = 2)`，`example.com:2001:db8::1` 能正确解析，不会被截断。
- **`CoreVpnService.isStartingLock` 成功路径不解锁**：看起来像泄漏，实际它承担的是"运行中拒绝重复 start"的职责，`stopAllService` / `onDestroy` / 系统 always-on 重启三条路径都会解锁，没有发现会卡死的场景。只是日志文案 "Start already in progress" 有误导性。
- **`CoreConfigContextBuilder` 的 decode-once 改造**：`ProfileStore.findByRemarks` 用 `putIfAbsent` 保留首个同名 profile，与原 `getServerViaRemarks` 的 `firstOrNull` 语义一致；`decodeRoutingRulesets() ?: return` 改成 `.orEmpty()` 后的迭代行为也相同。没有引入解析结果差异。
- **`initConfigCache`**：缓存的是资产文件的字符串，每次仍重新 `fromJson` 出新对象，不存在跨次生成的可变状态污染。
- **`SOCKS-only` 模式下 `getHttpPort()` 返回 0**：`HttpUtil.buildOkHttpClient` 对 `httpPort == 0` 走直连，`CoreVpnService` 也加了 `httpPort > 0` 判断，不会连到 0 端口。（另外本项目所有 flavor 的 `isXray()` 都是 true，这条分支实际走不到。）
