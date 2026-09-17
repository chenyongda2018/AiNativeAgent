package com.yongda.ainativeagent.tool.core

import com.yongda.ainativeagent.llm.core.ToolDefinition

/**
 * 通用工具抽象：一个可被模型调用的能力。取代早期写死在执行器里的单一电量工具，让 Agent Loop 面向
 * [AgentTool] 集合而非某个具体工具。
 *
 * 约定：
 *  - [definition] 是喂给模型的名称 / 描述 / JSON Schema，应保持稳定。
 *  - [execute] 接收模型给出的完整参数 JSON，返回结构化 [ToolExecutionResult]（成功或错误 JSON）。
 *  - 实现内部必须自行做严格参数校验，非法输入返回结构化错误而非抛异常。
 *  - 协程取消（CancellationException）必须向上传播，不得吞掉。
 */
interface AgentTool {

    val definition: ToolDefinition

    suspend fun execute(argumentsJson: String): ToolExecutionResult
}
