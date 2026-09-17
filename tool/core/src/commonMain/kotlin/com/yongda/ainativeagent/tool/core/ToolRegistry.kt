package com.yongda.ainativeagent.tool.core

import com.yongda.ainativeagent.llm.core.ToolCall
import com.yongda.ainativeagent.llm.core.ToolDefinition

/**
 * 工具注册表：以工具名为键聚合一组 [AgentTool]。Agent Loop 通过它拿到全部 [ToolDefinition] 喂给模型，
 * 并按模型给出的调用名分发执行。未知工具名返回结构化 [ERROR_UNKNOWN_TOOL] 失败，绝不静默或崩溃。
 *
 * 注册表本身无状态、可安全跨轮复用；重复注册同名工具视为装配错误，直接抛出。
 */
class ToolRegistry(tools: List<AgentTool>) {

    private val byName: Map<String, AgentTool> = buildMap {
        tools.forEach { tool ->
            val name = tool.definition.name
            require(name !in this) { "Duplicate tool name in registry: $name" }
            put(name, tool)
        }
    }

    /** 全部工具定义，喂给模型的 tools 字段。顺序与注册顺序一致，稳定可预期。 */
    fun definitions(): List<ToolDefinition> = byName.values.map { it.definition }

    fun get(name: String): AgentTool? = byName[name]

    val names: Set<String> get() = byName.keys

    /**
     * 按名分发执行。未知工具返回结构化失败；已知工具的执行异常由工具自身负责转结构化结果，
     * 这里不额外兜底（取消异常照常从工具向上传播）。
     */
    suspend fun execute(call: ToolCall): ToolExecutionResult {
        val tool = byName[call.name]
            ?: return ToolResults.failure(ERROR_UNKNOWN_TOOL, "Unknown tool: ${call.name}")
        return tool.execute(call.argumentsJson)
    }

    companion object {
        const val ERROR_UNKNOWN_TOOL: String = "UNKNOWN_TOOL"
    }
}
