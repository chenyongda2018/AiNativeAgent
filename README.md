# Android AI Native Agent

<p align="left">
  <img src="https://img.shields.io/badge/Android-API%2029%2B-3DDC84?logo=android&logoColor=white" alt="Android API 29+" />
  <img src="https://img.shields.io/badge/Kotlin-2.4.10-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.4.10" />
  <img src="https://img.shields.io/badge/Compose-Multiplatform-4285F4?logo=jetpackcompose&logoColor=white" alt="Compose Multiplatform" />
  <img src="https://img.shields.io/badge/Ktor-3.0.3-087CFA?logo=ktor&logoColor=white" alt="Ktor 3.0.3" />
  <img src="https://img.shields.io/badge/LLM-DeepSeek-4D6BFE" alt="DeepSeek" />
  <img src="https://img.shields.io/badge/Agent-Tool%20Calling-CC785C" alt="Tool Calling" />
  <img src="https://img.shields.io/badge/Status-Active%20Development-D97706" alt="Active Development" />
</p>

一个运行在普通第三方 Android 应用中的 AI Agent 学习与实践项目，基于 **Kotlin Multiplatform + Compose Multiplatform** 构建。

项目当前已完成 DeepSeek 多轮流式聊天、思考过程展示，通用 **Tool Registry** 与**有界多步 Agent Loop**，并落地了两类端侧工具：读取手机电量，以及对系统日历的**查询 / 新增 / 修改 / 删除**（带运行时权限按需申请与写操作前的应用级确认）。

> 本项目不依赖 Root、系统签名或系统级权限，目标是在普通 Android 应用权限边界内逐步实现可解释、可测试的 Agent Runtime。

## 界面预览

<p align="center">
  <img src="./screencap/img.png" width="360" alt="Android AI Native Agent 获取手机电量演示" />
</p>

上图展示了完整的电量工具调用链：用户询问电量，模型生成 `get_battery_level` 调用，应用通过 Android `BatteryManager` 读取设备状态，再由模型输出最终回答。

## 已实现能力

- DeepSeek OpenAI-compatible API 接入
- 多轮聊天与模型切换
- SSE 流式响应
- 思考过程与正式回答分离展示
- Markdown 流式渲染
- 停止生成、失败重试与部分内容保留
- 通用 **Tool Registry**：面向 `AgentTool` 集合注册与分发，电量已适配为其中一个工具
- **有界、串行的多步 Agent Loop**：支持「先查询、再按结果更新/删除」，限制最大步数、禁止并行调用与重复调用
- **日历日程 CRUD**：`query_calendar_events` / `create_calendar_event` / `update_calendar_event` / `delete_calendar_event`
  - 查询走 `CalendarContract.Instances` 展开重复日程；写操作走 `Events`
  - 严格 JSON 参数校验、带时区的 ISO-8601 时间、`end > start` 与全天事件处理
  - 每轮向模型注入设备当前时间与时区，可靠处理「今天 / 明天」等相对时间
- **运行时权限按需申请**：`READ_CALENDAR` / `WRITE_CALENDAR` 危险权限绝不在启动时申请；查询申请 READ，写操作在目标解析与确认后再申请 WRITE，二者独立不依赖权限组联动
- **写操作应用级确认**：新增展示标题/时间/目标日历，修改展示前后差异，删除展示目标；取消返回 `USER_CANCELLED`
- 权限拒绝 / 永久拒绝 / 取消、事件不存在、无可写日历、工具异常均返回结构化错误
- 手机电量百分比、充电状态读取
- 最小调用链 Trace：LLM 请求 → Tool Call → Tool Result → 最终回答
- 暖石与 Terracotta 风格的自定义聊天 UI

## Agent Loop 与工具流程

```text
用户提问（含设备当前时间/时区上下文）
        ↓
DeepSeek 返回一次工具调用（或直接作答）
        ↓
应用校验：工具名 / 参数 / 禁止并行 / 禁止重复调用
        ↓
执行工具（日历写操作：按需 READ 权限 → 解析目标 → 应用级确认 → WRITE 权限 → 落库）
        ↓
回填 Assistant Tool Call + Tool Result，进入下一步（有界，达到上限强制自然语言收尾）
        ↓
模型不再调用工具 → 流式生成最终自然语言回答
```

日历写操作推荐链路：**按需获取读权限并解析目标 → 确认 → 获取写权限 → 执行**。`chat:ui` 保持纯 UI，Android `Context` / `Activity` 只存在于平台组合边界。

## 技术栈

| 类别 | 技术 |
|---|---|
| 语言 | Kotlin 2.4 |
| UI | Compose Multiplatform、Material 3 |
| 架构 | Kotlin Multiplatform、ViewModel、StateFlow |
| 并发 | Kotlin Coroutines、Flow |
| 网络 | Ktor Client、OkHttp、SSE |
| 序列化 | Kotlinx Serialization |
| Markdown | multiplatform-markdown-renderer |
| 模型 | DeepSeek OpenAI-compatible Chat Completions API |
| Android API | BatteryManager、CalendarContract（Instances / Events）、Activity Result 权限 API |
| 构建 | Gradle 9、Android Gradle Plugin 9 |

## 模块结构

```text
AiNativeAgent/
├── androidApp/          Android 应用入口、平台依赖装配、Activity 作用域权限协调器
├── shared/              Compose 应用组合根、日历写操作确认弹窗
├── chat/
│   ├── ui/              无业务依赖的聊天 UI、主题与状态契约
│   └── vm/              ChatViewModel、流式编排、多步 Agent Loop 执行器
├── llm/
│   ├── core/            模型无关的消息、请求、流事件与 Tool Call 协议
│   ├── net/             Ktor Client 与 SSE 基础设施
│   └── deepseek/        DeepSeek 请求 DTO、协议转换与流解析
└── tool/
    ├── core/            通用 AgentTool、ToolRegistry、工具结果与统一错误结构
    ├── battery/         电量工具契约、纯逻辑与 Android 实现
    └── calendar/        日历工具契约/参数校验/时间处理/权限与确认抽象（common）+ ContentResolver 实现（android）
```

核心依赖方向：

```text
androidApp → shared → chat:vm → llm:core
                   ↘ llm:deepseek → llm:net
                   ↘ tool:battery → tool:core → llm:core
                   ↘ tool:calendar → tool:core
```

`chat:ui` 保持为纯 UI 组件；Android `Context` / `Activity` 只存在于平台组合边界，不进入 `commonMain`、ViewModel 或 LLM 层。日历的运行时权限与写操作确认都通过 `commonMain` 抽象注入平台实现。

## 环境要求

- Android Studio 或 IntelliJ IDEA
- JDK 17+
- Android SDK 36
- Android 10（API 29）及以上设备或模拟器
- DeepSeek API Key

## 快速开始

### 1. 配置 DeepSeek API Key

在项目根目录的 `local.properties` 中加入：

```properties
DEEPSEEK_API_KEY=你的_API_Key
```

如果文件中已经存在 `sdk.dir`，保留原配置并追加上述字段即可。`local.properties` 已被 Git 忽略。

> API Key 会在本地构建时写入 `BuildConfig`，仅适用于学习和调试。正式产品应通过安全后端代理模型请求，不应把长期密钥打包进 APK。

### 2. 构建 Debug APK

```bash
./gradlew :androidApp:assembleDebug
```

APK 输出目录：

```text
androidApp/build/outputs/apk/debug/
```

### 3. 安装到设备

连接 Android 设备或启动模拟器后执行：

```bash
./gradlew installDebug
```

也可以直接通过 Android Studio 运行 `androidApp`。

### 4. 验证工具能力

启动应用后可分别验证：

读取电量（无需额外权限）：

```text
我手机还剩多少电？
```

日历日程（首次使用会按需弹出读/写权限，写操作会先弹确认框）：

```text
帮我看看明天有什么安排
明天下午 3 点安排一个 1 小时的项目评审，地点会议室 A
把刚才那个评审改到下午 4 点
删除明天的项目评审
```

模型会先查询拿到 `eventId`，再据此修改/删除；每次写入前应用都会弹出确认框，取消则不会落库。

## 测试与检查

运行主要 JVM Host 测试：

```bash
./gradlew \
  :tool:core:testAndroidHostTest \
  :tool:battery:testAndroidHostTest \
  :tool:calendar:testAndroidHostTest \
  :chat:vm:testAndroidHostTest \
  :llm:deepseek:testAndroidHostTest \
  :shared:testAndroidHostTest
```

构建与 Android Lint：

```bash
./gradlew :androidApp:assembleDebug :androidApp:lintDebug
```

> 日历相关测试全部使用内存 fake（`FakeCalendarDataSource` / 假权限协调器 / 假确认器），不会读写真实设备日历。

只运行 DeepSeek 流协议单元测试：

```bash
./gradlew :llm:deepseek:testAndroidHostTest \
  --tests "com.yongda.ainativeagent.llm.deepseek.DeepSeekStreamTest"
```

`DeepSeekProviderIntegrationTest` 在检测到 API Key 时会进行真实网络请求；离线环境可只运行上面的协议单元测试。

## 当前边界

- 目前仅支持 Android 目标。
- 工具集：只读电量 + 日历日程 CRUD。
- 日历首版不支持参会人、复杂提醒与单个实例（重复日程的修改/删除作用于整个系列，确认框会明确提示）。
- 不具备系统级设备控制能力。
- 不使用 Root、系统签名或隐藏 API。
- Agent Loop 为有界串行：每步最多一个工具调用，达到最大步数后强制自然语言收尾。
- 运行时权限在 Activity 销毁等异常场景返回结构化错误；跨配置变更的进行中申请不做恢复。
- API Key 的本地注入方式只面向开发调试。

## 后续方向

项目会按可运行、可演示、可测试的顺序逐步演进：

1. Context Manager 与 Token Budget
2. Task Manager 与 Memory
3. Context 压缩
4. MCP 接入
5. MNN 端侧模型与云端/端侧路由
6. Trace、恢复机制与性能评测

## 设计原则

- 先实现最小闭环，再扩展抽象。
- 模型只能提出工具调用，实际执行权始终由应用掌握。
- Android 平台能力保持在平台边界内。
- 所有工具参数必须完整聚合、校验后才能执行。
- 权限拒绝、工具失败和协议异常必须明确返回，不伪造成功结果。
- UI、Agent 编排、工具和模型协议保持清晰依赖边界。
