# v2rayNG（本地入站代理增强版）

本项目基于 [2dust/v2rayNG](https://github.com/2dust/v2rayNG) fork 而来（上游提交 `a1b45bb`，v2.3.5），是 Android 上的 V2Ray/Xray 客户端。
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
| 兼容性保障 | VPN（hev-tun）与 root 模式仍强制保留内部 SOCKS 入站作为隧道目标；订阅更新、测速等应用内功能自动使用当前模式下实际可用的 HTTP 端口 |

生成的 Xray/V2Ray `inbounds` JSON 细节与各模式示例见 [docs/local_inbound_zh.md](docs/local_inbound_zh.md)。

主要改动源码位置（便于审查 diff）：

- `V2rayNG/app/src/main/java/com/v2ray/ang/core/CoreConfigManager.kt` — `configureInbounds()` 按模式生成入站
- `V2rayNG/app/src/main/java/com/v2ray/ang/handler/SettingsManager.kt` — 模式 / 端口 / 监听地址 / 认证读取
- `V2rayNG/app/src/main/java/com/v2ray/ang/enums/LocalInboundMode.kt` — 入站模式枚举（新增）
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
5. 连接后验证（在电脑上把 `<IP>` 换成手机 IP 或 `127.0.0.1`）：

```bash
# SOCKS5（含远端域名解析）
curl -x socks5h://<IP>:10808 https://www.gstatic.com/generate_204 -v

# HTTP CONNECT
curl -x http://<IP>:10808 https://www.gstatic.com/generate_204 -v   # Mixed 模式同端口
curl -x http://<IP>:10809 https://www.gstatic.com/generate_204 -v   # 双端口模式

# 带认证
curl -x socks5h://user:pass@<IP>:10808 https://www.gstatic.com/generate_204 -v
```

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
