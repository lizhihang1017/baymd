package com.zhli.baymd.rag.core.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Agent 工具注册表 — 管理所有 Agent 可调用的工具。
 *
 * <p>本地工具（{@link AgentTool} 接口的 Spring Bean）在启动时自动收集注册；
 * MCP 远程工具由 {@code McpClientAutoConfiguration} 动态注册到独立的
 * {@code McpToolRegistry}（供意图驱动的 RAG 检索路径使用），本注册表仅供
 * ReAct 循环使用。</p>
 */
@Slf4j
@Component
public class AgentToolRegistry {

    /** 按名称索引的工具（保持插入顺序用于 Prompt 生成） */
    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    /** 锁 */
    private final Object lock = new Object();

    /**
     * 启动时自动收集所有本地 {@link AgentTool} Bean。
     * <p>声明为 {@code @Component} 的工具类会被 Spring 注入此列表并自动注册，
     * 无需手动注册代码。</p>
     */
    public AgentToolRegistry(List<AgentTool> localTools) {
        if (localTools != null) {
            for (AgentTool tool : localTools) {
                if (Objects.nonNull(tool) && Objects.nonNull(tool.getName())) {
                    register(tool);
                }
            }
        }
        log.info("AgentToolRegistry 本地工具自动注册完成: count={}, names={}",
                size(), localTools == null ? "[]" : localTools.stream()
                        .filter(Objects::nonNull)
                        .map(AgentTool::getName)
                        .toList());
    }

    /**
     * 注册工具
     */
    public void register(AgentTool tool) {
        synchronized (lock) {
            tools.put(tool.getName(), tool);
            log.info("Agent 工具已注册: name={}, type={}", tool.getName(), tool.getType());
        }
    }

    /**
     * 注销工具
     */
    public void unregister(String name) {
        synchronized (lock) {
            tools.remove(name);
            log.info("Agent 工具已注销: name={}", name);
        }
    }

    /**
     * 根据名称获取工具
     */
    public Optional<AgentTool> getTool(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * 获取所有工具列表（不可变快照）
     */
    public List<AgentTool> listTools() {
        synchronized (lock) {
            return List.copyOf(new ArrayList<>(tools.values()));
        }
    }

    /**
     * 按类型筛选工具
     */
    public List<AgentTool> listByType(String type) {
        synchronized (lock) {
            return tools.values().stream()
                    .filter(t -> type.equals(t.getType()))
                    .toList();
        }
    }

    /**
     * 已注册工具数量
     */
    public int size() {
        return tools.size();
    }

    /**
     * 清空所有工具
     */
    public void clear() {
        synchronized (lock) {
            tools.clear();
        }
    }

    /**
     * 生成工具描述的 Prompt 片段（用于注入 system prompt）
     */
    public String buildToolPromptSection() {
        synchronized (lock) {
            if (tools.isEmpty()) return "";

            StringBuilder sb = new StringBuilder();
            sb.append("\n## 可用工具\n\n");
            sb.append("你可以使用以下工具来获取信息。使用工具时，请按以下格式输出：\n\n");
            sb.append("```\n");
            sb.append("<tool_call>\n");
            sb.append("{\"name\": \"工具名称\", \"arguments\": {\"参数名\": \"参数值\"}}\n");
            sb.append("</tool_call>\n");
            sb.append("```\n\n");
            sb.append("工具列表：\n\n");

            int idx = 1;
            for (AgentTool tool : tools.values()) {
                sb.append("### ").append(idx++).append(". ").append(tool.getName()).append("\n");
                sb.append("- **描述**：").append(tool.getDescription()).append("\n");
                sb.append("- **参数**：").append(formatSchema(tool.getParametersSchema())).append("\n\n");
            }

            sb.append("重要规则：\n");
            sb.append("1. 每次只调用一个工具，等待结果后再决定下一步\n");
            sb.append("2. 获得工具结果后，基于结果给出最终回答，不要再调用工具\n");
            sb.append("3. 如果工具返回错误或空结果，告知用户并尝试其他方式\n");
            return sb.toString();
        }
    }

    private String formatSchema(Map<String, Object> schema) {
        if (schema == null || !schema.containsKey("properties")) {
            return "无参数";
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        if (props == null || props.isEmpty()) {
            return "无参数";
        }

        StringBuilder sb = new StringBuilder();
        for (var entry : props.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> propDef = (Map<String, Object>) entry.getValue();
            String type = String.valueOf(propDef.getOrDefault("type", "string"));
            String desc = String.valueOf(propDef.getOrDefault("description", ""));
            sb.append(entry.getKey()).append(" (").append(type).append("): ").append(desc).append("; ");
        }
        return sb.toString();
    }
}
