# 外部控制与链接导入

本文档描述 v2rayNG-Plus 对外暴露的自动化接口：**外部开关 Intent** 与 **URL Scheme 导入**。
两者都可以在 Tasker、MacroDroid、Automate、`adb shell am` 或任意第三方应用中使用，
无需打开主界面。

设计参考了 [ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid) 的
`ExternalControlActivity` 与 `clash://install-config` 约定，接口语义等价，action 字符串使用本项目自己的命名空间。

## 1. 外部开关 Intent

目标组件：`com.v2ray.ang.ui.ExternalControlActivity`

| 动作 | Action 字符串 | 行为 |
| --- | --- | --- |
| 切换 | `com.v2ray.ang.action.TOGGLE` | 正在运行则停止，未运行则启动 |
| 启动 | `com.v2ray.ang.action.START` | 未运行则启动；已在运行则提示「服务已在运行」，不重复启动 |
| 停止 | `com.v2ray.ang.action.STOP` | 正在运行则停止；未运行则提示「服务未在运行」 |

Action 字符串固定为 `com.v2ray.ang.action.*`，**不随 flavor 的 applicationId 后缀变化**
（F-Droid 版的包名是 `com.v2ray.ang.fdroid`）。这样同一份自动化脚本在各渠道包上都能用。
如果设备上同时装了多个渠道包，用 `-p`/`setPackage()` 指定具体包名即可避免出现选择器。

### adb 示例

```bash
# 切换
adb shell am start -a com.v2ray.ang.action.TOGGLE

# 启动（指定 F-Droid 版）
adb shell am start -a com.v2ray.ang.action.START -p com.v2ray.ang.fdroid

# 停止
adb shell am start -a com.v2ray.ang.action.STOP
```

### 应用内示例

```kotlin
startActivity(
    Intent("com.v2ray.ang.action.TOGGLE").apply {
        // 可选：只想控制某一个渠道包时指定
        setPackage("com.v2ray.ang")
    }
)
```

### 行为细节

- 启动使用与快捷设置磁贴、桌面部件相同的入口（`LauncherManager.startServiceFromToggle`），
  因此同样遵守「仅本地代理 · 直连」降级规则：直连启动时若当前是 VPN 模式，会自动改用仅代理模式。
- 未选择节点也可以启动，此时以「本地代理 · 直连」运行。
- VPN 权限尚未授予时，服务会以「缺少 VPN 权限」提示失败，而不是静默无反应。
- 启动/停止结果通过 toast 反馈；`START`/`STOP` 遇到状态本就正确的情况会明确说明，而不是假装做了事。

### 安全边界

`ExternalControlActivity` 是 `exported="true"`，但它**只读取 action**：

- 只有上表三个 action 会被执行，其余一律拒绝并提示「不支持的外部控制指令」；
- 不读取调用方传入的 component、data URI 或任何 extra，因此不存在把本应用当作跳板
  去启动内部组件的转发漏洞；
- 该 Activity 运行在 `:RunSoLibV2RayDaemon` 进程内——只有该进程能看到内核的真实运行状态，
  否则 `TOGGLE` 会永远把「运行中」误判为「已停止」。

需要注意：任何应用都可以发送这三个 action，这与 CMFA 的公开接口一致，是「可自动化」的代价。
不希望被外部控制时，可在系统设置里停用本应用的该组件。

## 2. URL Scheme 导入

```
v2rayng://install-config?url=<URL 编码后的分享链接>
v2rayng://install-sub?url=<URL 编码后的订阅地址>
```

也支持从其他应用「分享」纯文本（`ACTION_SEND` + `text/plain`）。

- `#` 之后的部分会作为节点备注补回链接末尾；链接自身已带备注时以链接内的为准。
- 缺少 `url` 参数会明确提示「链接缺少 url 参数」，而不是静默打开主界面。
- host 不是 `install-config` / `install-sub` 时提示「不支持的链接」。
- 导入成功/失败都会有 toast；成功后停留在主界面，可以直接看到新导入的条目。
- 日志只记录脱敏后的链接（`scheme://host:port/<redacted>`），订阅 token 和节点密码不会进入 logcat。

### adb 示例

```bash
adb shell am start -a android.intent.action.VIEW \
  -d 'v2rayng://install-sub?url=https%3A%2F%2Fexample.com%2Fsub%2Ftoken'
```
