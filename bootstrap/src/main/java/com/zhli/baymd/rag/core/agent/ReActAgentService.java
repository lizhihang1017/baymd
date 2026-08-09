package com.zhli.baymd.rag.core.agent;

import com.zhli.baymd.framework.convention.ChatMessage;
import com.zhli.baymd.infra.chat.LLMService;
import com.zhli.baymd.infra.chat.StreamCallback;
import com.zhli.baymd.rag.config.ReActProperties;
import com.zhli.baymd.rag.core.guardrails.GuardrailsFactory;
import com.zhli.baymd.rag.core.prompt.PromptConfigService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ReAct Agent 服务 — Agent 模式的顶层入口。
 *
 * <p>将 ReAct 循环执行器与 Spring 配置、工具注册表连接。
 * 上层（Controller/Pipeline）调用此服务即可以 Agent 模式处理用户请求。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReActAgentService {

    private final LLMService llmService;
    private final AgentToolRegistry toolRegistry;
    private final GuardrailsFactory guardrailsFactory;
    private final ReActProperties properties;
    private final PromptConfigService promptConfigService;

    @PostConstruct
    void init() {
        log.info("ReAct Agent 服务初始化: maxIterations={}, maxWallClockMs={}ms, tools={}",
                properties.getMaxIterations(), properties.getMaxWallClockMs(),
                toolRegistry.size());
    }

    /**
     * Agent 模式处理用户请求（同步 — 返回完整结果）
     *
     * @param systemPrompt 系统提示词
     * @param history      对话历史
     * @param userQuestion 用户问题
     * @return Agent 执行结果
     */
    public ReActLoop.ReActResult process(String systemPrompt,
                                          List<ChatMessage> history,
                                          String userQuestion) {
        ReActLoop loop = buildLoop();

        List<ChatMessage> messages = new java.util.ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(ChatMessage.system(systemPrompt));
        }
        if (history != null) {
            messages.addAll(history);
        }
        messages.add(ChatMessage.user(userQuestion));

        long start = System.currentTimeMillis();
        ReActLoop.ReActResult result = loop.execute(messages);

        // 检查挂钟时间
        if (result.getTotalElapsedMs() > properties.getMaxWallClockMs()) {
            log.warn("Agent 执行超时: {}ms > {}ms", result.getTotalElapsedMs(), properties.getMaxWallClockMs());
        }

        log.info("Agent 执行完成: {}", result);
        return result;
    }

    /**
     * Agent 模式处理用户请求（流式 — 最终答案通过 callback 实时输出）
     *
     * @param systemPrompt 系统提示词
     * @param history      对话历史
     * @param userQuestion 用户问题
     * @param callback     流式回调
     * @return Agent 执行结果
     */
    public ReActLoop.ReActResult processStreaming(String systemPrompt,
                                                   List<ChatMessage> history,
                                                   String userQuestion,
                                                   StreamCallback callback) {
        ReActLoop loop = buildLoop();

        List<ChatMessage> messages = new java.util.ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(ChatMessage.system(systemPrompt));
        }
        if (history != null) {
            messages.addAll(history);
        }
        messages.add(ChatMessage.user(userQuestion));

        return loop.executeStreaming(messages, callback);
    }

    /**
     * 构建 ReActLoop 实例
     */
    private ReActLoop buildLoop() {
        return ReActLoop.builder()
                .llmService(llmService)
                .toolRegistry(toolRegistry)
                .guardrailsFactory(guardrailsFactory)
                .maxIterations(properties.getMaxIterations())
                .promptConfigService(promptConfigService)
                .build();
    }
}
