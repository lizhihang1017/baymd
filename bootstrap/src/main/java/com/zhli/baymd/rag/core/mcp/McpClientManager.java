package com.zhli.baymd.rag.core.mcp;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.zhli.baymd.rag.service.RuntimeConfigService;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Server 运行时管理器 — 支持动态连接 / 断开 / 测试，并持久化到 DB {@code t_app_config.mcp} 分区。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>启动时合并 {@code application.yaml} 的 {@code rag.mcp.servers} 与 DB 中动态添加的 servers，统一连接</li>
 *   <li>运行时添加 / 删除 / 测试连接（管理后台「工具」tab）</li>
 *   <li>每个 server 连接成功后，将其工具注册到 {@link McpToolRegistry}</li>
 * </ul>
 */
@Slf4j
@Service
public class McpClientManager implements SmartInitializingSingleton {

    private static final String DB_SECTION = "mcp";

    private final McpToolRegistry toolRegistry;
    private final RuntimeConfigService runtimeConfigService;
    private final McpClientProperties properties;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /** serverName → client（已连接） */
    private final Map<String, McpSyncClient> clients = new LinkedHashMap<>();
    /** serverName → 该 server 注册到 McpToolRegistry 的工具 ID */
    private final Map<String, List<String>> serverToolIds = new LinkedHashMap<>();
    /** serverName → 配置（yaml 内置 + DB 动态添加，合并去重） */
    private final Map<String, ServerConfig> servers = new LinkedHashMap<>();
    /** serverName → 最近一次连接错误 */
    private final Map<String, String> errors = new LinkedHashMap<>();

    public McpClientManager(McpToolRegistry toolRegistry,
                            RuntimeConfigService runtimeConfigService,
                            McpClientProperties properties,
                            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.toolRegistry = toolRegistry;
        this.runtimeConfigService = runtimeConfigService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public record ServerConfig(String name, String url, String apiKey) {

        public ServerConfig(String name, String url) {
            this(name, url, null);
        }
    }

    public record ServerStatus(String name, String url, boolean connected, int toolCount, String error) {
    }

    // ==================== 启动初始化 ====================

    /**
     * 所有单例 Bean 创建完成后，连接 yaml 配置的 servers + DB 中动态添加的 servers。
     */
    @Override
    public void afterSingletonsInstantiated() {
        try {
            // 1. yaml 内置
            if (CollUtil.isNotEmpty(properties.getServers())) {
                for (McpClientProperties.ServerConfig s : properties.getServers()) {
                    servers.putIfAbsent(s.getName(), new ServerConfig(s.getName(), s.getUrl()));
                }
            }
            // 2. DB 动态添加
            loadDbServers();
            // 3. 统一连接
            for (ServerConfig cfg : new ArrayList<>(servers.values())) {
                connectInternal(cfg, false);
            }
        } catch (Exception e) {
            // DB 配置表缺失等场景不应阻塞应用启动，仅连接 yaml 内置 servers
            log.warn("MCP Server 启动初始化失败（DB 配置读取失败?），仅保留 yaml 内置: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadDbServers() {
        try {
            String json = runtimeConfigService.getSection(DB_SECTION);
            if (StrUtil.isBlank(json)) {
                return;
            }
            List<Map<String, Object>> list = objectMapper.readValue(
                    json, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
                    });
            for (Map<String, Object> m : list) {
                String name = String.valueOf(m.get("name"));
                String url = String.valueOf(m.get("url"));
                Object ak = m.get("apiKey");
                String apiKey = ak == null ? null : String.valueOf(ak);
                if (StrUtil.isNotBlank(name) && StrUtil.isNotBlank(url)) {
                    servers.putIfAbsent(name, new ServerConfig(name, url, apiKey));
                }
            }
        } catch (Exception e) {
            log.warn("加载 DB MCP servers 失败: {}", e.getMessage());
        }
    }

    private void saveDbServers() {
        try {
            List<Map<String, Object>> list = servers.values().stream()
                    .map(s -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("name", s.name());
                        m.put("url", s.url());
                        if (StrUtil.isNotBlank(s.apiKey())) {
                            m.put("apiKey", s.apiKey());
                        }
                        return m;
                    })
                    .toList();
            runtimeConfigService.saveSection(DB_SECTION, objectMapper.writeValueAsString(list));
        } catch (Exception e) {
            log.warn("保存 MCP servers 到 DB 失败: {}", e.getMessage());
        }
    }

    // ==================== 运行时管理 ====================

    /**
     * 添加并连接一个 MCP Server（持久化到 DB，重启后仍生效）。
     *
     * @return 工具 ID 列表
     */
    public synchronized List<String> addServer(String name, String url, String apiKey) {
        if (StrUtil.isBlank(name) || StrUtil.isBlank(url)) {
            throw new IllegalArgumentException("server name / url 不能为空");
        }
        ServerConfig cfg = new ServerConfig(name.trim(), url.trim(), StrUtil.trimToNull(apiKey));
        servers.put(name, cfg);
        // 若已存在同名 server，先断开旧连接
        disconnectInternal(name);
        List<String> toolIds = connectInternal(cfg, true);
        saveDbServers();
        return toolIds;
    }

    /**
     * 删除一个 MCP Server（断开 + 注销工具 + 持久化）。
     */
    public synchronized boolean removeServer(String name) {
        if (StrUtil.isBlank(name)) {
            return false;
        }
        if (!servers.containsKey(name)) {
            return false;
        }
        servers.remove(name);
        disconnectInternal(name);
        saveDbServers();
        return true;
    }

    /**
     * 测试连接（不注册工具、不持久化）。
     *
     * @return 工具名列表
     */
    public List<String> testConnection(String name, String url, String apiKey) {
        // 前端不返回 apiKey（出于安全）：若为空则回退到已持久化的 server 配置
        String effectiveKey = StrUtil.isBlank(apiKey) && servers.containsKey(name)
                ? servers.get(name).apiKey() : apiKey;
        McpSyncClient client = null;
        try {
            client = createClient(name, url, StrUtil.trimToNull(effectiveKey));
            ListToolsResult result = client.listTools();
            if (CollUtil.isEmpty(result.tools())) {
                return List.of();
            }
            return result.tools().stream().map(Tool::name).toList();
        } finally {
            if (client != null) {
                try { client.close(); } catch (Exception ignore) { }
            }
        }
    }

    /** 当前所有 server 状态（含未连接的 yaml/DB 配置） */
    public synchronized List<ServerStatus> status() {
        List<ServerStatus> out = new ArrayList<>();
        for (Map.Entry<String, ServerConfig> e : servers.entrySet()) {
            String name = e.getKey();
            ServerConfig cfg = e.getValue();
            boolean connected = clients.containsKey(name);
            int toolCount = serverToolIds.getOrDefault(name, List.of()).size();
            out.add(new ServerStatus(name, cfg.url(), connected, toolCount, errors.get(name)));
        }
        return out;
    }

    // ==================== 内部 ====================

    private List<String> connectInternal(ServerConfig cfg, boolean throwOnFail) {
        McpSyncClient client = null;
        try {
            client = createClient(cfg.name(), cfg.url(), cfg.apiKey());
            ListToolsResult result = client.listTools();
            List<Tool> tools = result.tools() == null ? List.of() : result.tools();

            // 注销旧工具（若重新连接）
            List<String> old = serverToolIds.remove(cfg.name());
            if (CollUtil.isNotEmpty(old)) {
                for (String toolId : old) {
                    toolRegistry.unregister(toolId);
                }
            }

            List<String> toolIds = new ArrayList<>();
            for (Tool tool : tools) {
                McpClientToolExecutor executor = new McpClientToolExecutor(client, tool);
                toolRegistry.register(executor);
                toolIds.add(executor.getToolId());
            }
            clients.put(cfg.name(), client);
            serverToolIds.put(cfg.name(), toolIds);
            errors.remove(cfg.name());
            log.info("MCP Server [{}] 连接成功, 注册 {} 个工具: {}", cfg.name(), toolIds.size(), toolIds);
            return toolIds;
        } catch (Exception e) {
            if (client != null) {
                try { client.close(); } catch (Exception ignore) { }
            }
            errors.put(cfg.name(), e.getMessage());
            if (throwOnFail) {
                throw new IllegalStateException("连接 MCP Server [" + cfg.name() + "] 失败: " + e.getMessage(), e);
            }
            log.error("连接 MCP Server [{}] 失败: {}", cfg.name(), e.getMessage());
            return List.of();
        }
    }

    private void disconnectInternal(String name) {
        McpSyncClient client = clients.remove(name);
        if (client != null) {
            try { client.close(); } catch (Exception ignore) { }
        }
        List<String> toolIds = serverToolIds.remove(name);
        if (CollUtil.isNotEmpty(toolIds)) {
            for (String toolId : toolIds) {
                toolRegistry.unregister(toolId);
            }
        }
        errors.remove(name);
        log.info("MCP Server [{}] 已断开", name);
    }

    /**
     * 规范化 MCP URL：
     * <ul>
     *   <li>自动补 /mcp 后缀（若未以 /mcp 或 /sse 结尾），正确处理 URL 中已有的 query 参数</li>
     * </ul>
     * 例如：https://host → https://host/mcp
     *       https://host/mcp?key=xxx → https://host/mcp?key=xxx（不变，query 保留在末尾）
     *       https://host/sse?key=xxx → https://host/sse?key=xxx（不变）
     */
    private static String normalizeMcpUrl(String url) {
        int qIdx = url.indexOf('?');
        String query = qIdx >= 0 ? url.substring(qIdx) : "";
        String path = qIdx >= 0 ? url.substring(0, qIdx) : url;
        if (!path.endsWith("/mcp") && !path.endsWith("/sse")) {
            path = path.endsWith("/") ? path + "mcp" : path + "/mcp";
        }
        return path + query;
    }

    private McpSyncClient createClient(String name, String url, String apiKey) {
        String mcpUrl = normalizeMcpUrl(url);
        io.modelcontextprotocol.spec.McpClientTransport transport;
        if (mcpUrl.contains("/sse")) {
            // SSE 传输（老式 MCP）：适用于高德 /sse 等端点
            // 拆分 origin 与 sse 路径：baseUri = origin（如 https://host），sseEndpoint = /sse?key=xxx
            int qIdx = mcpUrl.indexOf('?');
            String sseFull = qIdx >= 0 ? mcpUrl.substring(0, qIdx) : mcpUrl;
            String sseQuery = qIdx >= 0 ? mcpUrl.substring(qIdx) : "";
            // origin：scheme://host（不含路径）
            int slashIdx = sseFull.indexOf('/', sseFull.indexOf("//") + 2);
            String origin = slashIdx > 0 ? sseFull.substring(0, slashIdx) : sseFull;
            String ssePath = slashIdx > 0 ? sseFull.substring(slashIdx) : "/sse";
            io.modelcontextprotocol.client.transport.HttpClientSseClientTransport.Builder sseBuilder =
                    io.modelcontextprotocol.client.transport.HttpClientSseClientTransport.builder(origin)
                            .sseEndpoint(ssePath + sseQuery);
            transport = sseBuilder.build();
        } else {
            // Streamable HTTP 传输（默认）
            HttpClientStreamableHttpTransport.Builder streamBuilder =
                    HttpClientStreamableHttpTransport.builder(mcpUrl);
            // 统一注入必要的请求头：
            //  1. Accept: application/json, text/event-stream — 部分服务（如高德）严格要求同时接受两者，否则 406
            //  2. Authorization: Bearer <apiKey> — 需要 Bearer 认证的服务（如 Ahrefs）
            streamBuilder.httpRequestCustomizer((requestBuilder, method, uri, contentType, ctx) -> {
                requestBuilder.header("Accept", "application/json, text/event-stream");
                if (StrUtil.isNotBlank(apiKey)) {
                    requestBuilder.header("Authorization", "Bearer " + apiKey);
                }
            });
            transport = streamBuilder.build();
        }

        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new Implementation("baymd-bootstrap", "1.0.0"))
                .build();
        client.initialize();
        return client;
    }
}
