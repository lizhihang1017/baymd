package com.zhli.baymd.admin.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.zhli.baymd.framework.auth.RequireAdmin;
import com.zhli.baymd.framework.convention.Result;
import com.zhli.baymd.framework.web.Results;
import com.zhli.baymd.rag.core.agent.AgentTool;
import com.zhli.baymd.rag.core.agent.AgentToolRegistry;
import com.zhli.baymd.rag.core.agent.ToolSwitchStore;
import com.zhli.baymd.rag.core.mcp.McpToolRegistry;
import com.zhli.baymd.rag.service.RuntimeConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具管理控制器（管理员）— 列出本地 Agent 工具 + MCP 远程工具，切换启用/停用。
 * <p>开关即时生效（热生效，无需重启），持久化到 DB {@code t_app_config.skill} 分区。</p>
 */
@Slf4j
@RequireAdmin
@RestController
@RequiredArgsConstructor
public class AdminToolController {

    private final AgentToolRegistry agentToolRegistry;
    private final McpToolRegistry mcpToolRegistry;
    private final RuntimeConfigService runtimeConfigService;

    public record ToolItem(String name, String label, String source, String description, boolean enabled, String type) {
    }

    /** 列出所有工具（本地 + MCP）+ 开关状态 */
    @GetMapping("/admin/tools")
    public Result<List<ToolItem>> list() {
        List<ToolItem> items = new ArrayList<>();

        // 本地 Agent 工具（ReAct 循环可用）
        for (AgentTool tool : agentToolRegistry.listAllToolsUnfiltered()) {
            items.add(new ToolItem(tool.getName(), tool.getName(), "local",
                    StrUtil.nullToEmpty(tool.getDescription()),
                    ToolSwitchStore.isEnabled(tool.getName()), tool.getType()));
        }

        // MCP 远程工具（意图定向检索可用）
        mcpToolRegistry.listAllExecutors().forEach(executor -> {
            var def = executor.getToolDefinition();
            String id = executor.getToolId();
            items.add(new ToolItem(id, def.name() != null ? def.name() : id, "mcp",
                    StrUtil.nullToEmpty(def.description()),
                    ToolSwitchStore.isEnabled(id), "mcp"));
        });

        return Results.success(items);
    }

    /**
     * 保存工具开关。
     *
     * @param body {@code {"enabled": ["medical_calculator", "drug_interaction"]}}
     */
    @PutMapping("/admin/tools")
    public Result<Void> save(@RequestBody Map<String, Object> body) {
        Object enabled = body.get("enabled");
        Set<String> enabledTools = new HashSet<>();
        if (enabled instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) enabledTools.add(String.valueOf(o));
            }
        }

        // 热生效
        ToolSwitchStore.setEnabledTools(enabledTools);
        // 持久化（重启后仍生效）
        runtimeConfigService.saveSection("skill", JSONUtil.toJsonStr(new ArrayList<>(enabledTools)));

        log.info("工具开关已保存: enabled={}", enabledTools);
        return Results.success();
    }
}
