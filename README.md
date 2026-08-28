# v2rayNG（本地入站代理增强版）

本项目基于 [2dust/v2rayNG](https://github.com/2dust/v2rayNG) fork 而来（上游提交 `a1b45bb`，v2.3.5），仓库是 [chaogei/v2rayNG-Plus](https://github.com/chaogei/v2rayNG-Plus)，版本号 **2.3.5-plus**（`versionCode` 746）。
应用内「关于 / 源码 / 反馈 / 检查更新 / 隐私政策」都指向本仓库，不会再去下官方包。
本 fork 的目标是**增强「本地服务器代理」（local inbound / 本地入站）的配置与管理能力**：让用户能更清楚地选择本地 SOCKS5 / HTTP / Mixed 等入站方式、监听地址、端口与可选认证。

上游原始 README 见 [docs/README_upstream.md](docs/README_upstream.md)，许可证沿用上游 [GPL-3.0](LICENSE)。

---

## 本 fork 增强了什么

上游只提供一个「本地代理端口」（SOCKS 口，Xray 下顺带兼容 HTTP），本 fork 把本地入站做成完整可配置的一组能力：

| 能力 | 说明 |
| --- | --- |
| 本地入站协议可选 | 设置中新增「本地入站协议」四种模式：**Mixed（SOCKS+HTTP 同端口，默认）**、**SOCKS+HTTP（双端口）**、**仅 SOCKS**、**仅 HTTP** |
| 独立 HTTP 端口 | 新增「本地 HTTP 端口」设置（默认 `10809`），双端口 / 仅 HTTP 模式下生效 |
| 监听地址可选 | 原「允许局域网连接」开关改为明确的「本地监听地址」选择：`127.0.0.1`（仅本机）或 `0.0.0.0`（所有网络接口，局域网可访问） |
| 可选本地认证 | 新增「本地代理认证」开关（**默认关闭**）：开启后本地 SOCKS 使用用户名/密码认证，HTTP 入站使用 Basic 认证，两者共用同一组凭据 |
| SOCKS5 UDP | 保留并理顺 UDP over SOCKS 开关（仅 SOCKS 入站生效） |
| 透明代理入站（可选） | 新增 dokodemo-door 透明转发入站（默认关闭，端口默认 `10810`），配合 iptables REDIRECT 等使用，通常需要 root |
| 端口/认证校验 | 设置页对端口做合法性（1-65535）与互相冲突校验，认证开启但未填全用户名/密码时给出提示 |
| 不选节点也能开 | 没有选中节点（或从「⋮」菜单显式选择「本地代理 · 直连」）时也能启动核心：本地入站照常监听，出站为 `freedom` 直连 |
| 兼容性保障 | VPN（hev-tun）与 root 模式仍强制保留内部 SOCKS 入站作为隧道目标；订阅更新、测速等应用内功能自动使用当前模式下实际可用的 HTTP 端口 |

生成的 Xray/V2Ray `inbounds` JSON 细节与各模式示例见 [docs/local_inbound_zh.md](docs/local_inbound_zh.md)。

主要改动源码位置（便于审查 diff）：

- `V2rayNG/app/src/main/java/com/v2ray/ang/core/LocalInboundConfigurator.kt` — 入站布局纯函数（统一配置 + CUSTOM JSON）
- `V2rayNG/app/src/main/java/com/v2ray/ang/core/CoreConfigManager.kt` — 调用 configurator 生成入站
- `V2rayNG/app/src/main/java/com/v2ray/ang/core/LocalProxyDirectPolicy.kt`、`handler/LocalAuthPolicy.kt` — 直连默认仅代理、认证开关与凭据解耦
- `V2rayNG/app/src/main/java/com/v2ray/ang/handler/SettingsManager.kt` — 模式 / 端口 / 监听地址 / 认证读取
- `V2rayNG/app/src/main/java/com/v2ray/ang/enums/LocalInboundMode.kt` — 入站模式枚举
- `V2rayNG/app/src/main/java/com/v2ray/ang/ui/settings/SettingsActivity.kt`、`SettingsViewModel.kt` — 设置 UI 与校验
- `V2rayNG/app/src/main/java/com/v2ray/ang/AppConfig.kt`、`dto/V2rayConfig.kt`、`service/CoreVpnService.kt` — 常量、DTO、防御性处理

---

## 如何使用

1. 打开应用 → 左侧抽屉 → **设置** → 「核心设置」分组。
2. 选择 **本地入站协议**：
   - **Mixed（默认）**：一个端口（默认 `10808`）同时服务 SOCKS4/4a/5 和 HTTP 代理（Xray 核心原生支持）。给系统/浏览器/第三方 App 配代理时，SOCKS 与 HTTP 都填 `127.0.0.1:10808` 即可。
   - **SOCKS + HTTP（双端口）**：SOCKS 在「本地 SOCKS 端口」（默认 `10808`），HTTP 在「本地 HTTP 端口」（默认 `10809`），适合明确要求两种协议分开监听的场景。
   - **仅 SOCKS** / **仅 HTTP**：只开一种入站，减少暴露面。
3. 需要给局域网内其他设备共享代理时，把 **本地监听地址** 改为 `0.0.0.0`，其他设备用 `你的手机IP:端口` 连接（见下方安全注意）。
4. 需要认证时，打开 **本地代理认证** 并同时填写用户名和密码。
5. **只想要本地代理、不走任何远程节点**（直连）：不选中任何节点直接点连接按钮即可；已经选了节点的话，用右上角「⋮」→ **本地代理 · 直连** 切过去（选中项会保留，点回列表里的节点就能切回来）。此时底栏显示「本地代理 · 直连」，核心的出站是 `freedom`。若当时运行模式是 VPN，启动直连会自动改成「仅代理」并提示，避免整机 tun 只做直连。CUSTOM 配置也会注入同一套本地入站。详见 [docs/local_inbound_zh.md](docs/local_inbound_zh.md#不选节点只开本地代理直连)。
6. 连接后验证（在电脑上把 `<IP>` 换成手机 IP 或 `127.0.0.1`）：

```bash
# SOCKS5（含远端域名解析）
curl -x socks5h://<IP>:10808 https://www.gstatic.com/generate_204 -v

# HTTP CONNECT
curl -x http://<IP>:10808 https://www.gstatic.com/generate_204 -v   # Mixed 模式同端口
curl -x http://<IP>:10809 https://www.gstatic.com/generate_204 -v   # 双端口模式

# 带认证
curl -x socks5h://user:pass@<IP>:10808 https://www.gstatic.com/generate_204 -v
```

连接后底栏第二行会显示当前传输方式和出站，例如 `VPN · Tokyo 01` 或 `仅代理 · 本地代理 · 直连`。设置页的模式行用同一套词。

Tasker / MacroDroid / `adb` 可以直接发 Intent 启停，不必打开主界面：

```bash
adb shell am start -a com.v2ray.ang.action.TOGGLE
```

约定、flavor 差异与安全边界见 [docs/external_control_zh.md](docs/external_control_zh.md)。`v2rayng://install-config?url=` / `install-sub` 缺参或 host 不对会明确提示，不再静默打开主界面。

---

## 自动发版（GitHub Actions）

每次推送到 `master`，CI（`.github/workflows/build.yml`）都会自动编译并**发布一个 GitHub Release**（fdroid + playstore 两个 flavor 的 release 包，全 ABI split + universal）。应用内「检查更新」读取的就是本仓库的 Releases（`https://api.github.com/repos/chaogei/v2rayNG-Plus/releases`）。

- **版本算法**（`.github/scripts/compute-version.sh`，不回写仓库，杜绝「发版 commit 又触发发版」死循环；完全不使用 `run_number`，PR 构建不会让发版序号跳号）：
  - 正式发版：`N = 已有 v2.3.5.N-plus tag 的最大 N + 1`（一个 tag 都没有则 `N = 1`），`versionCode = 746 + N + OFFSET`（746 为仓库内基线），`versionName = 2.3.5.<N>-plus`，Release tag 为 `v<versionName>`。例：已发 `v2.3.5.3-plus`，无论中间跑了多少 PR，下一次 `master` push 一定是 `v2.3.5.4-plus` / versionCode 750
  - PR 构建（不发 Release）：`versionCode = 746 + 最近已发版的 N + OFFSET`，`versionName = 2.3.5.<最近 N>-pr.<短 sha>-plus`，数字部分不超过已发布版本，误装 PR 产物后检查更新仍会提示正式新版
  - `OFFSET` = 不在 `v2.3.5.N-plus` 序列里的 `-plus` tag 数量（即 `workflow_dispatch` 手动指定的 tag；基线 tag `v2.3.5-plus` 本身占用 746，不计入）。手动 tag 不会推进 `N`，如果不跳过它占掉的号，`v2.4.0-plus` 和随后的 `v2.3.5.4-plus` 会同时是 versionCode 750，两个包在设备上无法互相升级
  - 手动 tag 只保证 versionCode 单调；`versionName` 仍从 `2.3.5` 这条线继续。如果要长期发 `2.4.x`，请改脚本里的 `BASE_VERSION_NAME`，而不是一直用手动 tag
  - 手动 tag 会自动补 `v` 前缀并去掉首尾空白，留空则回落到自动编号
  - 应用内 `VersionUtil` 比较版本时会丢掉 `-`/`+` 后缀，所以由第 4 位数字段保证「新版本 > 已装版本」
  - 版本号通过 `-PversionCode` / `-PversionName` 注入 Gradle（`app/build.gradle.kts` 读 project property，本地构建仍用仓库里的默认值）
  - 版本脚本可离线自测：`bash .github/scripts/compute-version-test.sh`，CI 在算版本号之前也会先跑一遍
- **触发规则**：`push` 到 `master` → 构建 + 发 Release；PR → 只构建 arm64 artifact，不发 Release；`workflow_dispatch` → 手动发版，可用 `release_tag` 输入指定 tag（如 `v2.4.0-plus`）
- **需要的 GitHub secrets**（仓库 Settings → Secrets and variables → Actions）：
  | Secret | 作用 | 缺失时 |
  | --- | --- | --- |
  | `APP_KEYSTORE_BASE64` | release keystore 的 base64 | 改用仓库内 `V2rayNG/debug.keystore` 签名，Release 标记为 prerelease，正文注明「debug 签名，不能覆盖正式包」 |
  | `APP_KEYSTORE_PASSWORD` / `APP_KEYSTORE_ALIAS` / `APP_KEY_PASSWORD` | keystore 口令 / 别名 / key 口令 | 同上（四个 secrets 必须齐全才算配置了正式签名） |
  | `GPG_PRIVATE_KEY` | 给 APK 附加 `.sig` 签名 | 跳过 GPG 步骤，不影响发版 |
- **Release 附件命名**与应用内更新器匹配：`v2rayNG_<versionName>-fdroid_<abi>.apk`（fdroid flavor）与 `v2rayNG_<versionName>_<abi>.apk`（playstore flavor）。
- **发版前想先看号**：手动触发 `Version preview`（`.github/workflows/version-preview.yml`，只有 `workflow_dispatch`）即可在不构建、不打 tag、不发 Release 的前提下，打印「下一次 master push / PR 构建 / 手填某个 tag」分别会得到的 versionName 与 versionCode，并顺带跑一遍版本脚本自测。

---

## 本地编译

完整工程可用 Android Studio（Ladybug+）或命令行编译，步骤与上游 CI（`.github/workflows/build.yml`）一致：

1. 环境：JDK 21、Android SDK Platform `android-37.0`、Build-Tools `37.0.0`。
2. 获取核心 AAR（本仓库不包含二进制）：

```bash
mkdir -p V2rayNG/app/libs
curl -fsSL -o V2rayNG/app/libs/libv2ray.aar \
  https://github.com/2dust/AndroidLibXrayLite/releases/latest/download/libv2ray.aar
```

3. （可选，仅 hev-tun VPN 模式需要）编译 hev-socks5-tunnel 原生库：克隆上游子模块 `hev-socks5-tunnel` 后运行仓库根目录的 `compile-hevtun.sh`（需要 NDK `29.x`），产物拷入 `V2rayNG/app/libs`。缺少该库不影响 APK 编译，只影响 hev-tun 运行模式。
4. 编译：

```bash
cd V2rayNG
echo "sdk.dir=<你的 Android SDK 路径>" > local.properties
./gradlew assembleFdroidDebug     # 或 assemblePlaystoreDebug / assembleRelease
```

本 fork 已在 Linux + JDK 21 + SDK 37 环境用 `assembleFdroidDebug` 完整编译通过。

Debug 包用仓库内固定的 `V2rayNG/debug.keystore`（密码 `android` / 别名 `androiddebugkey`）签名，换机构建也可以覆盖安装。仓库不再检入预编译 APK，请本地或 CI 自行 `assembleFdroidDebug`。

> 注：本仓库为普通源码快照，未初始化 `AndroidLibXrayLite` 与 `hev-socks5-tunnel` 两个子模块目录；如需自行编译核心，请到对应上游仓库获取。

---

## UI / 毛玻璃（Glassmorphism）

全应用（Jetpack Compose）统一改为半透明毛玻璃风格，覆盖主界面节点列表、设置页、订阅、路由、资产、分应用代理、日志、关于、抽屉、顶栏、底栏、下拉菜单和全部对话框：

- **实现方式**：核心在 `ui/compose/Glass.kt` + `Theme.kt`。
  - 主题根部先画一层柔和渐变背景（双色基底 + 三个大面积品牌色光晕，纯渐变绘制，一个 draw pass，零模糊开销）；
  - `toGlassScheme()` 把 Material3 配色的所有 surface 角色变为半透明（日间白 40–70%、夜间深色 25–50%，背景全透明），因此所有引用 `colorScheme.surface*` 的组件（顶栏、底栏、Tab、卡片、弹层）自动变玻璃，动态取色（Material You）同样生效；
  - 面板统一 `glassPanel`：圆角（卡片/面板 16–24dp）+ 半透明填充 + 1dp 白色渐变细描边（日间 30–85%、夜间 4–16% 白）；节点卡片、设置行、应用列表行、订阅/路由/资产列表项都是这种小玻璃卡，选中节点卡片带主题色描边；
  - 对话框统一走 `GlassAlertDialog`。
- **模糊与低版本降级**：**API 31+** 的对话框窗口启用真实 blur-behind（`FLAG_BLUR_BEHIND` + `blurBehindRadius`，仅在系统开启跨窗口模糊时生效）；低版本自动退化为「半透明 + 细描边」，不崩溃、不掉帧。列表 item 一律不做实时模糊，避免低端机大面积 blur 掉帧。
- **日夜两套**：日间玻璃偏亮白、夜间偏深灰蓝，`drawable(-night)/bg_window_glass.xml` 让窗口第一帧就是同款渐变，文字对比度保持 Material 默认 on-color，可读性达标。
- **已知限制**：云端环境无真机/模拟器，无法截图预览；菜单/抽屉等独立窗口在所有版本均用较高不透明度（88–94%）保证可读性，仅对话框有真实模糊。

### 主题配色（Theme presets）

在毛玻璃材质之上提供 6 套可切换的预设配色（`ui/compose/ThemePresets.kt`），每套都有日间 + 夜间两组色板，并驱动同一套玻璃 tokens：

| 预设 | 说明 |
| --- | --- |
| 琥珀橙 Amber（默认） | 与原品牌橙一致，保持出厂观感 |
| 午夜蓝 Midnight Blue | 皇家蓝 + 青色光晕 |
| 极光绿 Aurora Green | 翠绿 + 青蓝极光光晕 |
| 樱花粉 Sakura Pink | 玫瑰粉 + 紫罗兰光晕 |
| 石墨紫 Graphite Purple | 紫罗兰 + 靛蓝光晕 |
| 冷灰银 Silver Gray | 低饱和中性钢灰，适合低干扰使用 |

- **切换方式**：设置 → 用户界面设置 → **主题配色**，点选即时生效（Compose 主题直接重组，无需重启）；选择持久化到 MMKV（`pref_ui_theme_preset`），重启后保留。
- **与毛玻璃的配对**：每套预设只替换 Material3 的 primary / secondary / tertiary 强调色组和背景三个品牌色光晕，中性 surface 底色全部共用，再统一经 `toGlassScheme()` 玻璃化——玻璃材质（半透明度、细描边、圆角）在所有主题下完全一致，变的只有主色、强调件（FAB、开关、Tab 指示、选中节点描边、节点类型文字）和背景光晕。
- **动态取色**：「莫奈取色 / Material You」（Android 12+）仍然是一个选项，开启时按系统壁纸取色并推导光晕；**选择任一预设会自动关闭动态取色**，反之打开动态取色则覆盖预设。

### 交互与细节打磨

在毛玻璃与主题体系之上做了一轮组件级打磨（不改协议/入站/热路径，不加新组件库）：

- **间距节奏统一**：新增 `GlassSpacing` token（卡片外距 12dp / 卡片间隙 4dp / 卡内边距 16dp）和 `Modifier.glassCard()`，节点卡、设置行、订阅/路由/资产/应用列表全部走同一配方，消灭了散落的 10/14dp 边距；分组标题统一缩进，与其下玻璃卡的从属关系一眼可读；Tab 起始边距与卡片对齐。
- **状态反馈克制而明确**：选中节点卡的强调色描边 + 微弱主题色浸染以 180ms 过渡出现，按压时卡片轻微缩放（0.985）；FAB 启停颜色 200ms 交叉渐变；折叠分组的箭头 200ms 旋转；所有涟漪裁剪在圆角内（含选择对话框行、分组标题）。
- **触控热区**：节点卡的分享/编辑/删除/更多按钮恢复 48dp 最小触控目标；选择对话框行最小 48dp 高。
- **空状态**：节点列表（含搜索无结果）和订阅页在为空时显示图标 + 标题 + 提示的空状态（中英文）。
- **防抖与稳定**：连接/断开 FAB 600ms 防抖，双击不会让服务启停竞态；延迟数字使用等宽数字（tnum）对齐；设置行长文案与开关之间保留 12dp 间距不再顶到控件。
- **主题切换零闪白**：切换预设或日/夜时玻璃背景与光晕以 220ms 色彩过渡（纯色值动画，无重型动效、无全屏模糊动画）。

---

## 性能优化

在本地入站增强之后，对连接 / 切换节点 / 网络切换重连时的**配置生成热路径**做了一轮针对性优化（只动逻辑质量，不改任何路由语义与入站行为）：

- **节点解析从 O(标签数 × 节点数) 降到 O(节点数)**：`CoreConfigContextBuilder` 原先每解析一个路由标签 / 代理链成员 / 兜底标签，都要把全部节点从 MMKV 逐个 Gson 反序列化再线性扫描（`getServerViaRemarks`）。现在每次构建配置最多全量解码一次，并建一个 remarks → 节点 的索引（惰性构建，没有自定义标签时完全不触发），后续查找都是 O(1)。节点多、路由规则多时切换节点明显更快。
- **路由规则集只解析一次**：用户路由规则 JSON 原先在出站解析、DNS 分流、路由规则生成三处各自 `decodeRoutingRulesets()`（三次 Gson 解析）。现在在 `CoreConfigContextBuilder` 解析一次后放进 `CoreConfigContext.rulesetItems`，三个消费方共用同一份不可变列表。
- **本地入站设置改为不可变快照**：新增 `LocalInboundSnapshot`（`SettingsManager.getLocalInboundSnapshot()`），模式、监听地址、SOCKS/HTTP/redir 端口、认证等每个 MMKV 键只读一次，端口冲突和认证有效性只推导一次；`configureInbounds()` 与 `getHttpPort()` 都消费同一快照，杜绝了「生成的入站端口」与「应用自用 HTTP 端口」两处推导不一致的可能。认证三键的读取也从原先最多 4 次合并为 1 次。
- **去掉热路径上的 Gson 序列化往返**：HTTP 入站原先通过「把 SOCKS 入站序列化成 JSON 再反序列化」来克隆，现在直接构造目标对象（含 sniffing 深拷贝），产物 JSON 完全一致，但不再有反射和中间字符串分配。
- **死代码清理**：移除只被注释块引用的 `collectUserRuleDomainsByTag` / `collectCustomOutboundDomains` 及整段注释掉的旧版 `configureDns`，热路径文件更易审查。

用户可感知的变化：节点/订阅数量较多（几百个）且配置了自定义路由标签或代理链时，点击连接、切换节点、网络切换重连的配置生成阶段更快、更稳定；行为（四种入站模式、端口、认证、监听地址）与优化前完全一致，`assembleFdroidDebug` 编译验证通过。

**已知限制**：本环境无 Android 真机/模拟器，以上为代码路径层面的复杂度与分配优化，未做真机 profiling 数据对比。

---

## 安全注意

- **监听地址**：默认 `127.0.0.1` 仅本机可用。改为 `0.0.0.0` 后，**同一局域网内的任何设备都能连接你的代理**，请只在可信网络中开启，并强烈建议同时开启本地认证。
- **本地认证**：默认关闭。开启后 SOCKS 使用 RFC 1929 用户名/密码认证、HTTP 使用 Basic 认证；注意 SOCKS5 的 UDP 通道认证并不完全可靠（设置项中有相应提示）。
- **明文协议**：本地 SOCKS/HTTP 入站本身不加密，仅适合本机或可信局域网使用，切勿暴露到公网。
- **透明代理入站**：dokodemo-door 入站只是打开监听端口，流量需要你自行用 iptables REDIRECT/TPROXY 等方式重定向进来（通常需要 root），普通用户保持关闭即可。
- 本仓库不包含任何真实服务器节点、密钥或订阅地址；示例均使用本地/占位配置。

---

## 上游与许可

- 上游项目：[2dust/v2rayNG](https://github.com/2dust/v2rayNG)（GPL-3.0）
- 核心：[XTLS/Xray-core](https://github.com/XTLS/Xray-core)（经 [2dust/AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) 封装）
- 本 fork 同样以 [GPL-3.0](LICENSE) 发布

---

<a id="cmfa-lessons"></a>

## 横向对照：ClashMetaForAndroid

同类项目 [MetaCubeX/ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid)（CMFA）的自动化、发版流程与模块划分被逐条读过并与本仓对照，完整笔记在 **[docs/cmfa_lessons_zh.md](docs/cmfa_lessons_zh.md)**。结论摘要：

- **已落地**：面向第三方自动化的外部 Intent 控制（`com.v2ray.ang.action.TOGGLE` / `START` / `STOP`，约定见 [docs/external_control_zh.md](docs/external_control_zh.md)）；URL scheme 缺参会明确失败；主界面显示当前传输方式与出站；订阅失败不再把「上次更新」写成刚才。
- **仍可做**：导入 URL 的附加参数（名称 / 自动更新间隔）、崩溃后自动展示日志的自述页。
- **本仓已有**：分应用代理、日志页、快捷设置磁贴、订阅定时更新、URL scheme 导入、手填 tag 发版；本仓另有 CMFA 没有的桌面小组件、Tasker 插件、应用内检查更新与 WebDAV 备份。
- **明确不抄**：Clash 核心与 YAML profile 模型、`external-controller` 控制面、多模块拆分、renovate 自动 bump 依赖。「活动连接列表」也不在借鉴范围——CMFA 应用本体并没有这个界面。
- **发版差异**：CMFA 手动填 tag 并把版本号回写进 `build.gradle.kts` 后 push；本仓不回写仓库，版本号由已有 tag 推出并通过 `-PversionName` / `-PversionCode` 注入，因此不存在「发版 commit 又触发发版」。详见上文[自动发版](#自动发版github-actions)。
