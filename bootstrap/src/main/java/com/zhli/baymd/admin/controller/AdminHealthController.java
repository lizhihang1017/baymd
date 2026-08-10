package com.zhli.baymd.admin.controller;

import cn.hutool.core.util.StrUtil;
import com.zhli.baymd.framework.auth.RequireAdmin;
import com.zhli.baymd.framework.convention.ChatMessage;
import com.zhli.baymd.framework.convention.ChatRequest;
import com.zhli.baymd.framework.convention.Result;
import com.zhli.baymd.framework.web.Results;
import com.zhli.baymd.infra.chat.LLMService;
import com.zhli.baymd.infra.embedding.EmbeddingService;
import com.zhli.baymd.infra.rerank.RerankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统健康检查（管理员）— 一键检测 DB / Redis / 模型(chat/embedding/rerank),显示版本与运行时长。
 */
@Slf4j
@RequireAdmin
@RestController
@RequiredArgsConstructor
public class AdminHealthController {

    private final JdbcTemplate jdbcTemplate;
    private final RedissonClient redissonClient;
    private final LLMService llmService;
    private final EmbeddingService embeddingService;
    private final RerankService rerankService;

    @GetMapping("/admin/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 系统信息
        Map<String, Object> sys = new LinkedHashMap<>();
        sys.put("name", "BayMD");
        sys.put("startTime", ManagementFactory.getRuntimeMXBean().getStartTime());
        sys.put("uptimeSeconds", (System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime()) / 1000);
        sys.put("javaVersion", System.getProperty("java.version"));
        sys.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        result.put("system", sys);

        // 组件健康
        List<Map<String, Object>> checks = new ArrayList<>();
        checks.add(check("PostgreSQL", () -> {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return "OK";
        }));
        checks.add(check("Redis", () -> {
            redissonClient.getKeys().count();
            return "OK";
        }));
        checks.add(check("LLM Chat", () -> {
            String resp = llmService.chat(ChatRequest.builder()
                    .messages(List.of(ChatMessage.user("回复 OK 两个字")))
                    .temperature(0.0)
                    .maxTokens(10)
                    .build());

            return StrUtil.isBlank(resp) ? "空响应" : "OK";
        }));
        checks.add(check("Embedding", () -> {
            var vectors = embeddingService.embedBatch(List.of("健康检查测试"));
            return (vectors != null && !vectors.isEmpty()) ? "OK" : "空向量";
        }));
        checks.add(check("Rerank", () -> {
            var resultR = rerankService.rerank("测试", List.of(), 1);
            return "OK";
        }));
        result.put("checks", checks);

        boolean allOk = checks.stream().allMatch(c -> "OK".equals(c.get("status")));
        result.put("overall", allOk ? "HEALTHY" : "DEGRADED");
        return Results.success(result);
    }

    private Map<String, Object> check(String name, ThrowingSupplier supplier) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        long start = System.currentTimeMillis();
        try {
            String detail = supplier.get();
            m.put("status", "OK");
            m.put("detail", detail);
        } catch (Exception e) {
            m.put("status", "ERROR");
            m.put("detail", StrUtil.maxLength(e.getMessage(), 200));
            log.warn("健康检查失败: {} - {}", name, e.getMessage());
        }
        m.put("latencyMs", System.currentTimeMillis() - start);
        return m;
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        String get() throws Exception;
    }
}
