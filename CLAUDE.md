# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

**Android AI Native Agent** — a learning/portfolio project building an on-device AI agent as an ordinary third-party Android app. Explicit non-goals: **not** a system-level agent, not a full phone-automation assistant, not dependent on system signature or root. Design within normal app permissions.

The repo is currently a fresh **Kotlin Multiplatform + Compose Multiplatform** starter template (Greeting/`getPlatform()` sample code). The agent itself has **not been built yet** — nearly all work ahead is greenfield, added incrementally per the roadmap below.

## Build & test

```bash
./gradlew :androidApp:assembleDebug        # build the Android APK
./gradlew :shared:testAndroidHostTest      # run shared JVM host tests (fast, no device/emulator)
./gradlew :shared:testAndroidHostTest --tests "com.yongda.ainativeagent.SharedCommonTest"   # single test class
./gradlew installDebug                     # install on connected device/emulator
```

- On-device instrumented tests use `androidx.test.runner.AndroidJUnitRunner` (device test source set); prefer `androidHostTest` for logic that doesn't need a device.
- The `android` CLI skill / `android-cli` is available for deploy, SDK management, and environment diagnostics.

## Module structure

- **`:shared`** — Kotlin Multiplatform library (`com.yongda.ainativeagent.shared`). Configured via the newer `com.android.kotlin.multiplatform.library` plugin (not the classic android-library plugin). Only `commonMain` + `androidMain` targets exist today; agent logic (Compose UI, Agent Runtime, Tools, Model layer) is intended to live here so it stays platform-agnostic. Uses `expect`/`actual` for platform APIs (see `Platform.kt` / `Platform.android.kt`).
- **`:androidApp`** — thin Android application (`com.yongda.ainativeagent`). `MainActivity` just calls `App()` from `:shared`. Keep this module thin; put real code in `:shared`.

Package root everywhere: `com.yongda.ainativeagent`. Kotlin source lives under `src/*/kotlin/` (not `java/`).

## Versions & conventions

- Dependencies are managed exclusively through the version catalog: **`gradle/libs.versions.toml`**. Add libraries there and reference via `libs.*` — do not hardcode versions in `build.gradle.kts`.
- Compose is **Compose Multiplatform** (`org.jetbrains.compose.*`), not androidx.compose. Import accordingly.
- JVM target 11, `kotlin.code.style=official`, `android.nonTransitiveRClass=true`, Gradle configuration-cache enabled.
- Planned stack per roadmap: Coroutines · Retrofit/OkHttp (or Ktor) · Room (optional) · DeepSeek / OpenAI-compatible API · later MNN for on-device models.

## UI 技术选型（项目规则，中文）

聊天界面（stream chat UI）的技术选型已定，后续开发遵循：

- **Markdown 渲染** — 采用 OSS **`com.mikepenz:multiplatform-markdown-renderer`**（配 `-m3` Material3 主题模块，代码高亮用 `-code`）。理由：专为 LLM 流式设计，`collectAsStreamingMarkdownState()` 可直接消费 `Flow<String>`，append-only 只重解析尾部、逐 token 追加也轻量；CMP 全平台、`commonMain` 可调用；Apache-2.0。版本走版本目录 `libs.*`，实装时按依赖解析确定最新（0.4x 系）。
- **聊天 UI 本体（吹き出し/气泡列表、输入栏、自动滚动）— 自建**，不引入重量级聊天 SDK（如 Stream Chat Android：仅 Android、重、需后端，属过度设计）。用 Compose Multiplatform + Material3 手写，保持可控且可作为独立可复用模块。
- **模块化**：聊天 UI 做成边界清晰、可独立发布的模块（纯 UI 组件 `ChatScreen(state, onSend, onCancel, onRetry)`，不含业务/网络依赖）；ViewModel 依赖抽象 `LlmProvider`，负责满足验收基准（多轮历史 · 流式追加 · 异常处理 · 中断生成 · 重试）。
- 一律用 **Compose Multiplatform**（`org.jetbrains.compose.*`），不用 androidx.compose。

## UI 设计规范（Design System，中文）

移动端 AI 聊天 UI 的设计规范已定，后续 UI 开发一律遵循。**色值以 `chat/ui/src/commonMain/.../theme/ChatColors.kt` 为唯一事实来源**（本节只记语义与关键值，勿两处维护）。

- **设计哲学**：暖石 + Terracotta 温润色温（纸质阅读感），**拒绝科技蓝/霓虹渐变**；单手拇指优先——模型切换 / 附件 / 语音 / 发送全部下沉到底部输入坞，不放顶栏；信息高密度，聊天视口水平边距约 10dp。
- **色彩令牌（语义）**：
  - 核心品牌 = **Terracotta** `#CC785C`（暗 `#E08A6F`）→ 星芒 Logo、选中态、主高亮、推理进度微光、输入光标。`ChatTheme.colors.brand`。
  - 次级强调 = **琥珀** `#D97706`（暗 `#FBBF24`）→ **仅**深度思考标签 / 代码关键字高亮。`ChatTheme.colors.amber`（勿用 brand 顶替）。
  - 常态功能键背景 = 近黑 `#0F172A`（暗 zinc-800）→ 发送箭头键；停止键用 rose `error`；在线点用 emerald `success`。
  - 文本走**暖石**：主文本 stone-900 `#1C1917`（暗 zinc-100 `#F4F4F5`）、次文本 stone-500 `#78716C`（暗 zinc-400）。深色中性色统一走 **zinc**（bg `#18181B` / 卡片 `#27272A`），输入坞卡片暖石 `#F5F4EF`。
- **圆角**：底部输入大卡 24dp；功能圆钮（+/语音/发送）正圆 32–36dp，热区≥44dp；模型胶囊全圆角；用户气泡 `2xl + 右上直角`（rounded-tr 小）；抽屉/底部面板顶部 32dp。
- **字号层次**：AI 正文 14.5sp / 行高 1.65；用户气泡 14.5sp Medium；输入 15sp（自适应限高 120dp）；模型胶囊 12sp；思考折叠 / 代码块 Mono 12sp。
- **尚未实现（路线图，勿当既有规则）**：顶栏灵动岛状态栏、主题手动切换（Sun/Moon）、实时语音模式、空态问候衬线字体（Playfair）。这些是目标形态，实现前不要据此约束现有代码。

## Target architecture (the goal, mostly not yet built)

```
UI Layer → Agent Runtime (Agent Loop · Context Manager · Task Manager · Memory Manager · Permission Manager)
         → Tool Layer (Tool Registry · Calendar/Contacts/Device/Location/Intent)
         → MCP Layer → Model Layer (DeepSeek cloud · MNN local) → Model Router → Observability (Logs/Trace/Metrics)
```

## Development order — STRICT, do not skip phases

The reference roadmap (`~/Documents/Obsidian/03_Knowledge/编程/Android-AI-Native-Agent/00-总览-目标架构与时间表.md`) mandates building in this exact sequence, each step runnable and demoable:

1. LLM plain chat → 2. Streaming → 3. Single Tool Calling → 4. Tool Registry → 5. Multi-Step Agent Loop → 6. Human-in-the-loop → 7. Context Manager → 8. Task Manager → 9. Memory → 10. Context Compression → 11. MCP → 12. MNN local model → 13. Model Router → 14. Cloud/edge collaboration → 15. Performance Benchmark → 16. Trace / Recovery / Error Handling

Cross-cutting concerns pulled early (per roadmap notes): minimal **Trace/logging** (`LLM Request → Tool Call(args) → Tool Result`) and **runtime Permission handling with graceful degradation** land at the Tool Calling phase, not at the end. Track LLM **token usage & cost** starting at the Context/Token-Budget phase. Maintain a fixed set of 10–15 eval tasks to measure agent tool-selection accuracy / task pass rate after the Agent Loop phase.

## Notes

- This directory is **not a git repository** yet.
- `androidApp/src/main/AndroidManifest.xml` currently declares only the `INTERNET` permission; add runtime-dangerous permissions (Calendar, Contacts, etc.) as their tool phases arrive.
