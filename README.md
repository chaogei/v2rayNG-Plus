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
