# 从 ClashMetaForAndroid 借鉴什么（对照笔记）

对照对象：[MetaCubeX/ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid)（下称 **CMFA**），阅读版本 `c454d0e`（2026-08-16，`Bump version to 2.11.33 (211033)`）。
本仓：[chaogei/v2rayNG-Plus](https://github.com/chaogei/v2rayNG-Plus)（fork 自 [2dust/v2rayNG](https://github.com/2dust/v2rayNG) v2.3.5）。

本文只做「对照 + 结论」，**不改任何业务代码**。每条结论都注明依据文件，便于后续代理直接接手；凡是没有实现的东西，本文只写「待实现」，不杜撰接口名。

- 发版规则的权威描述在根 [README「自动发版」](../README.md#自动发版github-actions)与 [`.github/scripts/compute-version.sh`](../.github/scripts/compute-version.sh) 的头注释，本文第 3 节只做与 CMFA 的对照和一致性核对结论。
- 外部控制（第 5 节）目前是**占位**，等对应实现合入后由实现方补具体 action 字符串。

---

## 1. 工程结构：CMFA 多模块 vs 本仓单模块

CMFA 的 `settings.gradle.kts` 拆成六个模块：

| 模块 | 职责 |
| --- | --- |
| `app` | Activity 层（`MainActivity`、`ProxyActivity`、`ProfilesActivity`、`ExternalControlActivity` …），只做编排 |
| `design` | 全部 UI（传统 View + XML，不是 Compose）：`XxxDesign` 对象持有视图并通过 channel 往上抛 `Request` |
| `service` | 前台服务、profile 存储与更新、tun module |
| `core` | Kotlin 侧核心模型 + Go 侧 clash 源码（`core/src/foss/golang/clash` 是 git 子模块），CI 里现编现打 |
| `common` | 常量（含 `Intents`）、日志、系统 compat |
| `hideapi` | 隐藏 API 的编译期 stub |

本仓是单 `:app` 模块（`V2rayNG/app`），UI 已经 Compose 化。

**结论：不拆模块。** CMFA 拆模块的主要动力是「Kotlin + Go 双语言构建」和「design 层要被 alpha/meta 多 flavor 复用」，这两个压力本仓都没有——本仓的核心是 [2dust/AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) 发布的预编译 `libv2ray.aar`。为对齐结构而拆模块会产生一次全仓 diff，并让后续与上游 2dust/v2rayNG 的合并变得昂贵。

真正值得借鉴的是**颗粒度而不是模块数**：CMFA 把「决定做什么」和「怎么显示」分开。本仓已经在这么做，纯逻辑都下沉成了无 Android 依赖、可单测的类：`core/LocalInboundConfigurator.kt`、`core/LocalProxyDirectPolicy.kt`、`core/StartupToastPolicy.kt`、`handler/LocalAuthPolicy.kt`。新功能继续按这个方式写即可。

---

## 2. 借鉴总表

### 2.1 CMFA 有、本仓没有（候选，按性价比排序）

| # | 能力 | CMFA 依据 | 建议 |
| --- | --- | --- | --- |
| 1 | **外部 Intent 控制**：任意第三方 App / 自动化工具可发 intent 启停代理 | `app/.../ExternalControlActivity.kt` + `app/src/main/AndroidManifest.xml`（`exported="true"` 的 activity，三个 `…action.{START,STOP,TOGGLE}_CLASH`） | **值得做**。本仓目前只有 Tasker 插件（`ui/shortcut/TaskerActivity`）和内部快捷方式 `ScStart/ScStop/ScSwitch`（`exported="false"`），普通自动化工具调不到。细节见第 5 节 |
| 2 | **导入 URL 支持更多参数**：`clash://install-config?url=…&type=url|file&name=…&update-interval=…` | `ExternalControlActivity.kt` 的 `ACTION_VIEW` 分支 | **小幅可做**。本仓已有 `v2rayng://install-config` / `install-sub`（`ui/UrlSchemeActivity.kt`），但只吃 `url`；补 `name` / 自动更新间隔属于低风险增量 |
| 3 | **运行时一键切 Rule / Global / Direct** | `design/.../component/ProxyMenu.kt`（`Request.PatchMode`）、`design/.../MainDesign.kt` | **谨慎**。语义不对等：clash 的 `PatchMode` 是热更新核心的一个字段；本仓的「模式」是路由规则集预设（`enums/RoutingType`：`WHITE`/`BLACK`/`GLOBAL`/`WHITE_IRAN`/`WHITE_RUSSIA`），切换要重建配置并重连核心。可以做的是「主界面菜单直接切规则集预设」这一交互，但必须明确提示会重连 |
| 4 | **崩溃自述页**：崩溃后单独一屏展示 logcat，方便用户直接复制反馈 | `app/.../AppCrashedActivity.kt` + `log/SystemLogcat.dumpCrash()` | **值得做，成本低**。本仓有日志页（`ui/logcat/LogcatActivity`）但没有崩溃落地页，用户反馈时拿不到现场 |
| 5 | **安装包完整性提示页**：检测到二次打包 / split 缺失时引导用户去官方渠道 | `app/.../ApkBrokenActivity.kt` | 可选。对一个会被到处二次分发的代理类 App 有实际意义 |
| 6 | **依赖自动升级 PR** | `.github/workflows/update-dependencies.yaml` + `renovate.json` | **不照抄**，理由见第 4 节 |
| 7 | **独立的 Pre-Release 通道**（固定 tag `Prerelease-alpha`，每次 push 覆盖） | `.github/workflows/build-pre-release.yaml` | **不照抄**，本仓 prerelease 是另一层含义，见第 3 节 |

### 2.2 本仓已有对应能力（不必重复造）

| 能力 | CMFA | 本仓 |
| --- | --- | --- |
| 分应用代理 | `AccessControlActivity` | `ui/perappproxy/PerAppProxyActivity`（黑/白名单） |
| 日志查看 | `LogsActivity` / `LogcatActivity` | `ui/logcat/LogcatActivity` |
| 快捷设置磁贴 | `TileService` | `service/QSTileService` |
| 订阅 / 配置定时更新 | `Profile.interval` + `ProvidersActivity` | `handler/SubscriptionUpdater`（`autoUpdate` + `updateInterval`）+ `ui/subscription/SubSettingActivity` |
| 配置文件手工编辑 | `FilesActivity` | `ui/server/ServerCustomConfigActivity`（CUSTOM 配置，本 fork 会往里注入同一套本地入站） |
| URL scheme 导入 | `clash://` / `clashmeta://` | `v2rayng://install-config`、`v2rayng://install-sub` |
| 开机自启 | `BootReceiver` | `receiver/BootReceiver` |
| 手动指定发版 tag | `Build Release` 的 `release-tag` 输入 | `workflow_dispatch` 的 `release_tag` 输入 |

反过来，**本仓有而 CMFA 没有**的（核对过 CMFA 全仓，确无对应实现）：桌面小组件（`receiver/WidgetProvider`）、Tasker 插件、应用内检查更新（`handler/UpdateCheckerManager` + `ui/checkupdate/CheckUpdateActivity`，CMFA 靠 F-Droid / Release 页分发）、WebDAV 备份（`handler/WebDavManager`）、以及本 fork 自己的完整本地入站配置能力（见 [local_inbound_zh.md](local_inbound_zh.md)）。

### 2.3 明确不抄

| 不抄的东西 | 原因 |
| --- | --- |
| **Clash / Meta 核心本身** | 本仓是 Xray/V2Ray 核心（`libv2ray.aar`）。抄核心等于换产品 |
| **YAML profile 模型**（`core/.../ConfigurationOverride.kt`、providers、per-profile override） | 本仓的配置模型是 Xray JSON + MMKV 里的 server 条目，两套模型不可调和；强行兼容只会长出一个双向转换层 |
| **`external-controller` / RESTful 控制面** | clash 的 `/proxies`、`/connections` 是核心自带的 HTTP API。Xray 侧没有语义等价物，且本 fork 的安全基调是「本地入站默认只听 `127.0.0.1`」，再开一个控制端口与之相悖 |
| **「活动连接列表」** | 需要澄清一个常见误解：**CMFA 应用本体并没有连接列表界面**（`app/` 与 `design/` 下只有 `LogsActivity`/`LogcatActivity`，没有任何 Connections 屏），连接数据是留给外部面板经 `external-controller` 读的。所以这不是「从 CMFA 抄」的事；本仓若要做只能自研，且要先解决 Xray 侧的数据来源 |
| **多模块拆分** | 见第 1 节 |
| **拨号盘暗码入口**（`DialerReceiver`，`*#*#252746382#*#*`） | 本仓的 "dialer" 指 browser dialer（`service/DialerNativeService`、`DialerWebviewService`），含义完全不同，抄过来只会制造术语混乱 |
| **Go 核心源码作为子模块现编** | 本仓 CI 直接下载上游 aar，构建时长与失败面小一个量级 |

---

## 3. 发布卫生（release hygiene）

### 3.1 CMFA 的做法（事实）

- `build-release.yaml`：**只有** `workflow_dispatch`，`release-tag` 是必填项（`v2.x.x`）。工作流从 tag 反推 `versionCode`（`printf "%1d%02d%03d"`），用 `advance-android-version-actions` 改写 `build.gradle.kts`，然后 **commit + tag + `git push --follow-tags` 回写仓库**，最后 `softprops/action-gh-release` 发布。
- `build-pre-release.yaml`：push `main` 或手动触发即构建 alpha，**删掉并重建固定 tag `Prerelease-alpha`**，以 `prerelease: true` 发布。所以 CMFA 的 "prerelease" 是一条**长期存在的 alpha 通道**。
- `build-debug.yaml`：PR / push 构建未签名 APK，只上传 artifact（签名步骤整段注释掉了）。
- README 的 Maintenance 一节把这三条规则直接写给用户看。

### 3.2 与本仓对照

| 维度 | CMFA | 本仓（`build.yml` + `compute-version.sh`） |
| --- | --- | --- |
| 正式发版触发 | 只有手动 dispatch，且必须填 tag | push `master` 自动发；dispatch 可选填 `release_tag` |
| 版本号来源 | 人填 tag → 推导 code | 扫已有 `v2.3.5.N-plus` tag → `N+1`；`versionCode = 746 + N + OFFSET` |
| 是否回写仓库 | **是**（bump commit + `push --follow-tags`） | **否**。这是关键差异：不回写就不存在「发版 commit 又触发一次发版」的死循环，也不需要给 CI 配额外的 push 权限 |
| 版本号注入方式 | 改写 `build.gradle.kts` 源文件 | `-PversionCode` / `-PversionName` 传给 Gradle，仓库文件不动，本地构建仍用默认值 |
| prerelease 含义 | 固定的 alpha 通道 | **签名降级标记**：缺 `APP_KEYSTORE_*` secrets 时用仓库内 debug 签名，Release 标 prerelease 且正文写明「不能覆盖安装正式包」 |
| PR 构建 | 未签名 artifact | arm64 artifact；`versionName = 2.3.5.<最近已发 N>-pr.<短 sha>-plus`，**不占正式号**，误装后应用内检查更新仍会提示真正的新版 |
| 重复触发同一版本 | `update-tag` 覆盖同名 tag | 重跑会重新扫 tag 走到 `N+1`，不会撞上自己刚打的 tag |
| 手填 tag 的副作用 | 直接改写版本，无副作用 | 手填 tag 不推进 `N`，但确实消耗了一个 `versionCode`；`OFFSET`（不在 `v2.3.5.N-plus` 序列里的 `-plus` tag 数，基线 `v2.3.5-plus` 除外）负责跳过它，否则两个 Release 撞同一个 code，设备上无法互相升级 |

**本仓发版规则一句话版**（与脚本逐条核对过）：

1. push `master` → 自动构建并发布一版，tag `v2.3.5.<N>-plus`；
2. 手动 `workflow_dispatch` + `release_tag` → 用你填的 tag 发版（会占一个 versionCode，后续自动版本号自动跳过）；
3. PR → 只构建 arm64 artifact，不发版、不占号；
4. 没有正式签名 secrets → Release 标 prerelease，正文自带 debug 签名警告。

### 3.3 一致性核对（本轮实际执行的结果）

- **`run_number` 残留**：全仓搜索 `run_number` / `runNumber` 只命中 5 处，全部是「说明我们**不**用它」的注释或文档（`build.yml` 头注释 ×2、`compute-version.sh` 头注释 ×1、根 README ×1、`AGENTS.md` ×1）。没有任何一处算法真的读 `github.run_number`。**无残留，无需修改。**
- **README ↔ 脚本**：README「自动发版」小节的六条（746 基线、`N+1`、`OFFSET` 定义、基线 tag 不计入、手填 tag 自动补 `v` 前缀、PR 复用 `LAST_N`）与 `compute-version.sh` 的实现逐条一致。
- **当前线上 tag**：`v2.3.5.3-plus` / `.4` / `.5` / `.6`（没有基线 tag `v2.3.5-plus`，也没有 `.1` / `.2`）。按现行脚本，下一次 `master` push 会发 **`v2.3.5.7-plus` / versionCode 753**（`LAST_N=6`、`OFFSET=0`）。
- **自测**：`bash .github/scripts/compute-version-test.sh` 在本轮环境跑过，全部断言通过。本轮另补了两条断言（见 4.2），把上面这个「序列从 `.3` 起、且没有基线 tag」的真实形态钉进测试——原有用例清一色从 `.1` 起且带基线 tag，覆盖不到它。

---

## 4. CI：依赖升级与版本自测

### 4.1 依赖升级：不引入 renovate

CMFA 的做法：上游核心仓更新后用 `repository_dispatch`（`core-updated`）触发 `update-dependencies.yaml`，拉子模块 + `go mod tidy`，再用 `peter-evans/create-pull-request` 自动开 PR（打 `Update` 标签）；另有 `renovate.json`（`config:recommended`）管其余依赖。CMFA README 也明说：PR 里如果有编译错误，**需要人工修好再合**。

本仓现状：`.github/dependabot.yml` 只开了 `github-actions` 生态（daily）。Gradle / AGP / Kotlin / Compose 依赖是人工升级。

**结论：保持现状，不加 renovate，也不给 gradle 生态开 dependabot。** 理由：

1. AGP / Kotlin / KSP / Compose BOM 是一个强耦合矩阵，自动 bump 出来的 PR 在**没有真机、没有 UI 测试矩阵**的情况下无法验证；本仓 CI 只能证明「编得过」。
2. 真正影响用户的核心版本根本不是 gradle 依赖：`libv2ray.aar` 的版本跟着子模块 `AndroidLibXrayLite` 的 tag 走（`build.yml` 里 `git describe --tags` 后交给 `release-downloader`），renovate 管不到。
3. CMFA 的自动 PR 成立，是因为它由上游核心仓主动 dispatch 触发、且有人 review。本仓没有这条上游 dispatch 链路。
4. 本仓每次 push `master` 都会**自动发一版**。让机器人往 `master` 方向持续产出依赖 PR，等于把「未经真机验证的依赖变更」直接推向发版流水线，风险与收益不成比例。

依赖升级因此是**人工动作**：由维护者（或明确接到升级任务的代理）自己改 `V2rayNG/app/build.gradle.kts` / version catalog，并说明为什么升。这条范围约定已经写进 `.github/dependabot.yml` 的注释。

### 4.2 版本自测已在流水线里

`compute-version-test.sh` **已经**接在 `build.yml` 里：step `Test version script` 位于 checkout 之后、`Compute version` 之前、Android SDK 安装之前，脚本失败会在一分钟内让整个 job 挂掉。**接线无缺口，不需要改 workflow。**

覆盖面上补了两条断言（不改任何既有判据，只新增）：现有用例的 tag 序列全都从 `.1` 开始且带基线 tag `v2.3.5-plus`，而仓库真实状态是从 `.3` 开始、没有基线 tag。新增的 `gapped line without a baseline tag` / `PR against a gapped line` 钉住这个形态：扫描认的是最大的 `N` 而不是完整的 `1..N`，基线 tag 缺失也不该让 `OFFSET` 偏移。

### 4.3 本轮唯一的 CI 变更：`version-preview.yml`

新增 `.github/workflows/version-preview.yml`，**只有 `workflow_dispatch`**、`permissions: contents: read`，不打 tag、不发 Release、不写仓库、不改任何版本算法。它做两件事：

1. 跑一遍 `compute-version-test.sh`；
2. 用**当前仓库真实的 tag**跑 `compute-version.sh` 的三种路径（下一次 master push / PR 构建 / 手填某个 tag），把结果写进 job summary。

补的是一个确定缺口：在此之前，想知道「下一版是几号」或验证脚本改动，唯一办法是触发一次完整 Android 构建（装 NDK、编 hev-tun、全 ABI），而那条路径会**真的发一个 Release**。

---

## 5. 外部控制（占位 — 待另一代理实现）

本节先只记录 CMFA 的做法与本仓落地前必须回答的问题。**本仓当前没有对外暴露的启停 action，本节因此不写任何 action 字符串**；实现合入后由实现方在此补「action 名 + 示例调用 + 安全说明」，并把根 README 的对应锚点指过来。

CMFA 的做法（事实，仅作参照，**不是本仓的接口**）：

- 一个 `exported="true"` 的 `ExternalControlActivity`，`onCreate` 里按 `intent.action` 分发，处理完立即 `finish()`；
- 三个 action，命名空间是包名 + `.action.`（START / STOP / TOGGLE），常量集中在 `common` 模块的 `Intents`；
- 另有 `ACTION_VIEW` 分支处理 `clash://install-config?url=…`；
- 全部三个 action **不做任何调用方校验**，任意 App 都能启停 VPN；
- README 的 `Automation` 一节把包名、action 名、URL scheme 直接列给用户。

本仓落地前要回答的问题：

1. **flavor 后缀**：本仓 applicationId 分 `com.v2ray.ang`（playstore）与 `com.v2ray.ang.fdroid`（fdroid）。action 字符串必须走 `${applicationId}` 占位，否则两个 flavor 的自动化脚本不通用。仓库里已有这个套路可循：`AppConfig.BROADCAST_ACTION_*` 基于 `ANG_PACKAGE`，manifest 里 widget 用 `${applicationId}.action.widget.click`。
2. **安全**：暴露 exported 入口等于让任意已安装 App 启停 VPN。至少要在文档里写明风险；是否加校验（签名级权限 / 允许名单）由实现方决定，但不能默认抄 CMFA 的「完全不校验」而不作说明。
3. **复用现有路径**：必须走与 Tasker 插件（`ui/shortcut/TaskerActivity`）和快捷方式（`ScStart` / `ScStop` / `ScSwitch`，当前 `exported="false"`）相同的启停实现，不要再写第二套服务控制逻辑。
4. **与本 fork 的直连模式的关系**：本 fork 允许不选节点直接启动（「本地代理 · 直连」）。外部启动时若没有选中节点，应明确是启动直连还是报错——这是本仓独有、CMFA 不存在的分支。

---

## 6. 结论速查

- **抄**：外部 Intent 控制（第 5 节）、导入 URL 的附加参数、崩溃自述页。
- **看情况**：主界面直接切路由规则集预设（注意会重连）、安装包完整性提示页。
- **不抄**：Clash 核心与 YAML profile 模型、`external-controller` 控制面、「连接列表」（CMFA 本体也没有）、多模块拆分、暗码入口、Go 源码现编、renovate 自动 bump、固定 alpha 通道。
- **本轮改了什么**：文档、一个 dispatch-only 的 `version-preview.yml`、`dependabot.yml` 的范围注释，以及 `compute-version-test.sh` 里两条新增断言。发版算法（`compute-version.sh`）一行未动，既有断言的判据也一条未改。
