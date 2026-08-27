# 本地入站（Local Inbound）配置说明

本文档描述本 fork 中「本地服务器代理」各模式下生成的 Xray/V2Ray `inbounds` JSON，便于审查与排错。
所有示例均为本地示例配置，不含真实服务器信息。

相关设置项（设置 → 核心设置）：

| 设置项 | MMKV 键 | 默认值 |
| --- | --- | --- |
| 启用本地代理 | `pref_enable_local_proxy` | 开 |
| 本地入站协议 | `pref_local_inbound_mode` | `mixed`（可选 `socks_http` / `socks` / `http`） |
| 本地监听地址 | `pref_proxy_sharing_enabled`（`0.0.0.0` ↔ 开） | `127.0.0.1` |
| 本地 SOCKS 端口 | `pref_socks_port` | `10808` |
| 本地 HTTP 端口 | `pref_http_port` | `10809` |
| 动态 SOCKS 端口 | `pref_dynamic_socks_port` | 关 |
| （随机端口的当前值，非用户可见） | `pref_runtime_socks_port` | 空 |
| 本地代理认证 | `pref_local_auth_enabled` | 关 |
| 用户名 / 密码 | `pref_socks_username` / `pref_socks_password` | 空 |
| SOCKS5 UDP | `pref_socks_enable_udp` | 开（配置生成时） |
| 透明代理入站 | `pref_local_redir_enabled` | 关 |
| 透明代理端口 | `pref_local_redir_port` | `10810` |

生成逻辑入口：`CoreConfigManager.configureInbounds()`。

---

## 模式一：Mixed（默认，SOCKS + HTTP 同端口）

Xray 核心的 `socks` 入站自 v1.8.24 起原生兼容 HTTP（按首字节自动识别 SOCKS4/4a/5 或 HTTP，认证共用），因此单个入站即可同时服务两种协议：

```json
{
  "inbounds": [
    {
      "tag": "socks",
      "port": 10808,
      "protocol": "socks",
      "listen": "127.0.0.1",
      "settings": {
        "auth": "noauth",
        "udp": true,
        "userLevel": 8
      },
      "sniffing": {
        "enabled": true,
        "destOverride": ["http", "tls", "quic"],
        "routeOnly": false
      }
    }
  ]
}
```

> 若核心不是 Xray（`Utils.isXray()` 为 false 的分包），Mixed 模式会退化为在 `SOCKS端口+1` 上额外生成一个 `http` 入站，行为与上游一致。

## 模式二：SOCKS + HTTP（双端口）

SOCKS 与 HTTP 分开监听，两个端口都可独立配置：

```json
{
  "inbounds": [
    {
      "tag": "socks",
      "port": 10808,
      "protocol": "socks",
      "listen": "127.0.0.1",
      "settings": { "auth": "noauth", "udp": true, "userLevel": 8 },
      "sniffing": { "enabled": true, "destOverride": ["http", "tls", "quic"], "routeOnly": false }
    },
    {
      "tag": "http",
      "port": 10809,
      "protocol": "http",
      "listen": "127.0.0.1",
      "settings": { "userLevel": 8 },
      "sniffing": { "enabled": true, "destOverride": ["http", "tls", "quic"], "routeOnly": false }
    }
  ]
}
```

若 HTTP 端口被误设为与 SOCKS 端口相同，`SettingsManager.getLocalInboundSnapshot()` 会把 `httpInboundPort` 回退到相邻端口（一般是 `SOCKS端口+1`，SOCKS 端口为 65535 时改取 65534，避免派生出非法端口）。这只是兜底：UI 层在当前模式确实存在独立 HTTP 端口时会拦截该冲突，而切换到这类模式时若发现两个端口相同，会当场把 `pref_http_port` 规范化成相邻端口并提示，设置页显示的端口因此始终与核心监听的一致。

## 模式三：仅 SOCKS

只保留 `socks` 入站（JSON 同模式一）。注意：在 Xray 核心上该端口依旧会兼容 HTTP 请求，这是核心行为；在非 Xray 核心上则完全没有 HTTP 入站，此时应用内需要 HTTP 代理的功能（订阅更新、测速等）自动降级为直连。

## 模式四：仅 HTTP

只生成 `http` 入站：

```json
{
  "inbounds": [
    {
      "tag": "http",
      "port": 10809,
      "protocol": "http",
      "listen": "127.0.0.1",
      "settings": { "userLevel": 8 },
      "sniffing": { "enabled": true, "destOverride": ["http", "tls", "quic"], "routeOnly": false }
    }
  ]
}
```

例外：**VPN（hev-tun）模式和 root 模式必须有 SOCKS 入站**作为 tun2socks 的对接目标，此时即便选择「仅 HTTP」，内部 `socks` 入站仍会保留（监听「本地 SOCKS 端口」，且固定绑 `127.0.0.1`）。

## 动态 SOCKS 端口

打开后 SOCKS 端口每次启动随机取（10000–65534）。这个随机值写在 `pref_runtime_socks_port` 里，而不是进程内存里：服务跑在 `:RunSoLibV2RayDaemon`，界面跑在默认进程，只有落 MMKV（settings 库为 `MULTI_PROCESS_MODE`）两边才会认同一个端口。刷新只由守护进程在服务的 `onStartCommand` 里做一次（核心运行中跳过），此后订阅更新、geo 下载、检查更新等走本地代理的功能读到的都是核心真正监听的端口。

## 认证（默认关闭）

打开「本地代理认证」并填写用户名/密码后，SOCKS 入站启用密码认证，HTTP 入站启用 Basic 认证，凭据共用：

```json
{
  "tag": "socks",
  "port": 10808,
  "protocol": "socks",
  "listen": "127.0.0.1",
  "settings": {
    "auth": "password",
    "accounts": [{ "user": "your-username", "pass": "your-password" }],
    "udp": true,
    "userLevel": 8
  }
}
```

```json
{
  "tag": "http",
  "port": 10809,
  "protocol": "http",
  "listen": "127.0.0.1",
  "settings": {
    "accounts": [{ "user": "your-username", "pass": "your-password" }],
    "userLevel": 8
  }
}
```

说明：

- Xray 的 Mixed（socks）入站在开启密码认证后，落到同端口的 HTTP 请求同样需要携带相同凭据。
- 认证开关关闭时，即使填写了用户名/密码也不会写入配置（`auth` 保持 `noauth`），应用内部请求也不会携带 `Proxy-Authorization`。
- 只填了用户名或只填了密码视为未配置：开关会保持打开（否则输入框会被禁用，用户没法补填），但该行的说明文字变成「未生效」，每次编辑凭据也会 toast 提示。
- 认证只覆盖 `socks` 与 `http` 入站。`redir`（dokodemo-door）没有账号机制，因此它固定只监听 `127.0.0.1`。

## 透明代理入站（dokodemo-door，可选）

开启后在任意模式基础上额外生成：

```json
{
  "tag": "redir",
  "port": 10810,
  "protocol": "dokodemo-door",
  "listen": "127.0.0.1",
  "settings": {
    "network": "tcp,udp",
    "followRedirect": true,
    "userLevel": 8
  },
  "sniffing": { "enabled": true, "destOverride": ["http", "tls", "quic"], "routeOnly": false }
}
```

`followRedirect: true` 表示识别 iptables REDIRECT/TPROXY 重定向来的原始目标地址。需要自行下发类似规则（需要 root）：

```bash
iptables -t nat -A OUTPUT -p tcp -j REDIRECT --to-ports 10810
```

这个入站固定监听 `127.0.0.1`，不跟随「本地监听地址」：dokodemo-door 协议没有账号机制，也拿不到本地认证凭据，绑到 `0.0.0.0` 就等于在局域网上开一个无认证入站。相应地，用 PREROUTING 的 `REDIRECT` 把**转发**流量送进来的手工方案不可行（转发流量的 REDIRECT 目标是入口网卡地址），上面这条针对本机 OUTPUT 的规则不受影响。

若透明代理端口与其他本地入站端口冲突，配置生成会直接失败并给出明确错误（以前只写一行警告日志然后跳过，开关看着是开的、端口却没人监听）。设置页在该开关的说明里也会提前标出冲突。

## 监听地址与路由兼容性

- 「本地监听地址」写入 `socks` / `http` 入站的 `listen` 字段：`127.0.0.1`（默认）或 `0.0.0.0`。选择 `0.0.0.0` 等价于上游的「允许局域网连接」，会触发相同的本地网络权限申请与启动提示。两个例外始终绑回环：「仅 HTTP」模式下为 hev-tun / root 保留的内部 `socks` 入站，以及 `redir` 入站。
- 所有本地入站共用相同的 sniffing 配置；未命中路由规则的流量走首个出站（`proxy`），与上游路由/出站行为完全兼容，不影响既有 VPN / 分应用代理 / 路由规则。
- 「启用本地代理」关闭时（且未被 hev-tun/root 模式强制），`socks`/`http` 入站全部移除，仅保留 tun 等系统入站。

## 快速验证

```bash
# SOCKS5
curl -x socks5h://127.0.0.1:10808 https://www.gstatic.com/generate_204 -v
# HTTP CONNECT
curl -x http://127.0.0.1:10809 https://www.gstatic.com/generate_204 -v
# UDP over SOCKS（需要 SOCKS5 UDP 开启）
dig @8.8.8.8 example.com +tcp  # 或使用支持 socks5 UDP 的工具验证
```
