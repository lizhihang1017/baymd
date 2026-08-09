package com.zhli.baymd.admin.controller;

import com.zhli.baymd.framework.auth.RequireAdmin;
import com.zhli.baymd.framework.convention.Result;
import com.zhli.baymd.framework.web.Results;
import com.zhli.baymd.rag.core.mcp.McpClientManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * MCP Server 管理控制器（管理员）— 动态添加 / 删除 / 测试外部 MCP Server，即时生效并持久化。
 */
@Slf4j
@RequireAdmin
@RestController
@RequestMapping("/admin/mcp")
@RequiredArgsConstructor
public class AdminMcpController {

    private final McpClientManager mcpClientManager;

    /** 列出所有 MCP Server（含 yaml 内置 + DB 动态添加）及连接状态 */
    @GetMapping("/servers")
    public Result<List<McpClientManager.ServerStatus>> list() {
        return Results.success(mcpClientManager.status());
    }

    /** 添加并连接 MCP Server（持久化，重启后仍生效） */
    @PostMapping("/servers")
    public Result<List<String>> add(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String url = body.get("url");
        String apiKey = body.get("apiKey");
        List<String> toolIds = mcpClientManager.addServer(name, url, apiKey);
        return Results.success(toolIds);
    }

    /** 测试连接（不注册、不持久化），返回该 server 的工具名列表 */
    @PostMapping("/servers/test")
    public Result<List<String>> test(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String url = body.get("url");
        String apiKey = body.get("apiKey");
        List<String> toolNames = mcpClientManager.testConnection(name, url, apiKey);
        return Results.success(toolNames);
    }

    /** 删除 MCP Server（断开 + 注销工具 + 持久化） */
    @DeleteMapping("/servers/{name}")
    public Result<Boolean> remove(@PathVariable String name) {
        return Results.success(mcpClientManager.removeServer(name));
    }
}
