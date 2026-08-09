package com.zhli.baymd.rag.core.agent;

import com.zhli.baymd.framework.convention.ChatMessage;
import com.zhli.baymd.framework.convention.ChatRequest;
import com.zhli.baymd.framework.convention.ToolGuardrails;
import com.zhli.baymd.infra.chat.LLMService;
import com.zhli.baymd.infra.chat.StreamCallback;
import com.zhli.baymd.infra.chat.StreamCancellationHandle;
import com.zhli.baymd.rag.core.guardrails.GuardrailsFactory;
import com.zhli.baymd.rag.core.prompt.PromptConfigService;
import com.zhli.baymd.rag.core.prompt.PromptScenes;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReAct 循环执行器 — Agent 的核心。
 *
 * <h3>执行流程</h3>
 * <pre>
 * 1. 将工具定义注入 system prompt
 * 2. 发送消息给 LLM
 * 3. 解析 LLM 响应：
 *    - 包含 &lt;tool_call&gt; → 执行工具 → observation 回灌 → goto 2
 *    - 不含 tool_call → 最终答案 → 结束
 * </pre>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * ReActLoop loop = ReActLoop.builder()
 *         .llmService(llmService)
 *         .toolRegistry(registry)
 *         .guardrailsFactory(guardrailsFactory)
 *         .maxIterations(10)
 *         .build();
 *
 * ReActResult result = loop.execute(messages, callback);
 * }</pre>
 */
@Slf4j
public class ReActLoop {

    // ==================== 配置 ====================

    private final LLMService llmService;
    private final AgentToolRegistry toolRegistry;
    private final GuardrailsFactory guardrailsFactory;
    private final ToolGuardrails toolGuardrails;
    private final int maxIterations;
    private final String systemPromptTemplate;
    private final PromptConfigService promptConfigService;

    // ==================== 解析器 ====================

    /** tool_call XML tag 匹配模式 */
    private static final Pattern TOOL_CALL_PATTERN =
            Pattern.compile("<tool_call>\\s*\\n?(\\{.*?\\})\\s*\\n?</tool_call>",
                    Pattern.DOTALL);

    // ==================== 构造器 ====================

    @Builder
    public ReActLoop(LLMService llmService,
                     AgentToolRegistry toolRegistry,
                     GuardrailsFactory guardrailsFactory,
                     int maxIterations,
                     String systemPromptTemplate,
                     PromptConfigService promptConfigService) {
        this.llmService = llmService;
        this.toolRegistry = toolRegistry;
        this.guardrailsFactory = guardrailsFactory;
        this.toolGuardrails = guardrailsFactory != null
                ? guardrailsFactory.forMcpTool()
                : ToolGuardrails.defaults();
        this.maxIterations = maxIterations > 0 ? maxIterations : 10;
        this.systemPromptTemplate = systemPromptTemplate != null
                ? systemPromptTemplate
                : "你是一个医疗健康助手。请基于工具返回的信息回答用户问题。";
        this.promptConfigService = promptConfigService;
    }

    // ==================== 核心执行 ====================

    /**
     * 执行 ReAct 循环（同步模式 — 返回完整结果）
     *
     * @param messages 初始消息（含 system prompt, history, user question）
     * @return 执行结果
     */
    public ReActResult execute(List<ChatMessage> messages) {
        return executeInternal(messages, null);
    }

    /**
     * 执行 ReAct 循环（流式模式 — 最终答案流式输出到 callback）
     *
     * @param messages 初始消息
     * @param callback 流式回调（用于输出最终答案 + 中间状态）
     * @return 执行结果
     */
    public ReActResult executeStreaming(List<ChatMessage> messages, StreamCallback callback) {
        return executeInternal(messages, callback);
    }

    private ReActResult executeInternal(List<ChatMessage> initialMessages, StreamCallback callback) {
        long startTime = System.currentTimeMillis();
        String runId = UUID.randomUUID().toString().substring(0, 8);

        // 注入工具定义到 system prompt
        List<ChatMessage> messages = injectToolDefinitions(initialMessages);

        List<ReActStep> steps = new ArrayList<>();
        AtomicBoolean cancelled = new AtomicBoolean(false);

        for (int iteration = 0; iteration < maxIterations && !cancelled.get(); iteration++) {
            log.info("[ReAct:{}] 迭代 {}/{} 开始", runId, iteration + 1, maxIterations);

            // 1. 调用 LLM
            long llmStart = System.currentTimeMillis();
            String llmResponse;
            try {
                if (callback != null) {
                    callback.onThinking(String.format("[思考中... 第%d/%d轮]%n", iteration + 1, maxIterations));
                }
                ChatRequest.ChatRequestBuilder rb = ChatRequest.builder()
                        .messages(messages).temperature(0.3);
                if (promptConfigService != null) {
                    promptConfigService.applyParams(PromptScenes.AGENT_MAIN, rb);
                }
                llmResponse = llmService.chat(rb.build());
            } catch (Exception e) {
                log.error("[ReAct:{}] LLM 调用失败, iteration={}", runId, iteration, e);
                return ReActResult.error(runId, steps, "LLM 调用失败: " + e.getMessage(),
                        System.currentTimeMillis() - startTime);
            }
            long llmElapsed = System.currentTimeMillis() - llmStart;

            // 2. 解析响应 — 判断是工具调用还是最终答案
            Optional<ToolCall> parsedToolCall = parseToolCall(llmResponse);

            if (parsedToolCall.isPresent()) {
                // === 工具调用路径 ===
                ToolCall toolCall = parsedToolCall.get();
                log.info("[ReAct:{}] 检测到工具调用: tool={}, args={}",
                        runId, toolCall.name(), toolCall.arguments());

                if (callback != null) {
                    callback.onThinking(String.format("🔧 正在调用工具: %s...%n", toolCall.name()));
                }

                // 3. 执行工具
                long toolStart = System.currentTimeMillis();
                AgentToolResult toolResult = executeTool(toolCall);
                long toolElapsed = System.currentTimeMillis() - toolStart;

                // 4. 记录步骤
                ReActStep step = new ReActStep(
                        iteration,
                        llmResponse,
                        toolCall,
                        toolResult,
                        llmElapsed,
                        toolElapsed
                );
                steps.add(step);

                // 5. 将工具结果回灌到消息列表
                messages.add(ChatMessage.assistant(llmResponse));
                String observation = formatObservation(toolResult);
                messages.add(ChatMessage.user(observation));

                if (callback != null) {
                    callback.onThinking(String.format("✅ 工具返回: %d 条结果 (%.0fms)%n",
                            toolResult.getDocumentCount(), (double) toolElapsed));
                }

            } else {
                // === 最终答案路径 ===
                log.info("[ReAct:{}] 最终答案, iteration={}, length={}",
                        runId, iteration, llmResponse.length());

                ReActStep finalStep = new ReActStep(
                        iteration,
                        llmResponse,
                        null, null,
                        llmElapsed, 0
                );
                steps.add(finalStep);

                // 流式输出最终答案
                if (callback != null) {
                    callback.onContent(llmResponse);
                    callback.onComplete();
                }

                return ReActResult.success(runId, steps, llmResponse,
                        System.currentTimeMillis() - startTime);
            }
        }

        // 达到最大迭代次数 → 强行让 LLM 总结
        log.warn("[ReAct:{}] 达到最大迭代次数 {}, 请求 LLM 强制总结", runId, maxIterations);

        String summary = forceSummarize(messages, callback);
        return ReActResult.truncated(runId, steps, summary,
                System.currentTimeMillis() - startTime);
    }

    // ==================== 工具调用解析 ====================

    /**
     * 从 LLM 响应中解析 &lt;tool_call&gt; 标签
     */
    Optional<ToolCall> parseToolCall(String llmResponse) {
        Matcher matcher = TOOL_CALL_PATTERN.matcher(llmResponse);
        if (!matcher.find()) {
            return Optional.empty();
        }

        String json = matcher.group(1).trim();
        try {
            // 使用简单的 JSON 解析（避免引入额外依赖）
            return Optional.of(ToolCall.fromJson(json));
        } catch (Exception e) {
            log.warn("解析 tool_call JSON 失败: {}", json, e);
            return Optional.empty();
        }
    }

    /**
     * 执行工具调用
     */
    AgentToolResult executeTool(ToolCall toolCall) {
        Optional<AgentTool> toolOpt = toolRegistry.getTool(toolCall.name());
        if (toolOpt.isEmpty()) {
            return AgentToolResult.error(toolCall.name(),
                    "工具不存在: " + toolCall.name(), 0);
        }

        // 使用护栏包裹工具执行（超时不重试，失败用降级值）
        return toolGuardrails.callWithFallback(
                () -> toolOpt.get().execute(toolCall.arguments()),
                ex -> !(ex instanceof ToolGuardrails.ToolTimeoutException),
                AgentToolResult.error(toolCall.name(), "工具调用失败（已重试）", 0)
        );
    }

    // ==================== 辅助方法 ====================

    /**
     * 将工具定义注入 system prompt
     */
    private List<ChatMessage> injectToolDefinitions(List<ChatMessage> messages) {
        List<ChatMessage> result = new ArrayList<>();
        boolean hasSystem = false;

        for (ChatMessage msg : messages) {
            if (msg.getRole() == ChatMessage.Role.SYSTEM) {
                String toolSection = toolRegistry.buildToolPromptSection();
                String enhanced = msg.getContent() + "\n" + toolSection;
                result.add(ChatMessage.system(enhanced));
                hasSystem = true;
            } else {
                result.add(msg);
            }
        }

        // 如果没有 system 消息，创建一个
        if (!hasSystem) {
            String toolSection = toolRegistry.buildToolPromptSection();
            result.add(0, ChatMessage.system(systemPromptTemplate + "\n" + toolSection));
        }

        return result;
    }

    /**
     * 将工具结果格式化为 observation
     */
    private String formatObservation(AgentToolResult result) {
        if (result.isSuccess()) {
            return "<tool_result>\n"
                    + "工具: " + result.getToolName() + "\n"
                    + "状态: 成功\n"
                    + (result.getDocumentCount() > 0
                            ? "找到 " + result.getDocumentCount() + " 条结果\n" : "")
                    + "结果:\n" + result.getContent() + "\n"
                    + "</tool_result>\n\n"
                    + "请基于以上工具返回的信息回答用户问题。如果信息不足，请据实告知用户。";
        } else {
            return "<tool_result>\n"
                    + "工具: " + result.getToolName() + "\n"
                    + "状态: 失败\n"
                    + "错误: " + result.getErrorMessage() + "\n"
                    + "</tool_result>\n\n"
                    + "工具调用失败，请尝试其他方式获取信息或告知用户当前无法完成此操作。";
        }
    }

    /**
     * 达到最大迭代次数时，强制 LLM 基于已有信息总结
     */
    private String forceSummarize(List<ChatMessage> messages, StreamCallback callback) {
        messages.add(ChatMessage.user(
                "你已经达到了最大工具调用次数。请基于以上所有工具返回的信息，"
                        + "给出你当前能给出的最佳回答。如果信息不足，请据实告知。"
        ));

        try {
            ChatRequest.ChatRequestBuilder rb = ChatRequest.builder()
                    .messages(messages)
                    .temperature(0.3);
            if (promptConfigService != null) {
                promptConfigService.applyParams(PromptScenes.AGENT_MAIN, rb);
            }
            String summary = llmService.chat(rb.build());
            if (callback != null) {
                callback.onContent(summary);
                callback.onComplete();
            }
            return summary;
        } catch (Exception e) {
            log.error("强制总结失败", e);
            String fallback = "抱歉，处理您的问题时遇到了困难，请稍后再试。";
            if (callback != null) {
                callback.onContent(fallback);
                callback.onComplete();
            }
            return fallback;
        }
    }

    // ==================== 内部类型 ====================

    /**
     * ReAct 单步记录
     */
    public record ReActStep(
            int iteration,
            String llmResponse,
            ToolCall toolCall,
            AgentToolResult toolResult,
            long llmElapsedMs,
            long toolElapsedMs
    ) {}

    /**
     * 解析后的工具调用
     */
    public record ToolCall(String name, Map<String, Object> arguments) {
        /**
         * 从简单 JSON 字符串解析（仅支持单层嵌套）
         */
        static ToolCall fromJson(String json) {
            // 手动解析，避免引入 Gson/Jackson 依赖到核心层
            String name = extractString(json, "name");
            Map<String, Object> args = extractArguments(json);
            return new ToolCall(name, args);
        }

        private static String extractString(String json, String key) {
            Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
            Matcher m = p.matcher(json);
            return m.find() ? m.group(1) : "";
        }
    }

    /**
     * 从 JSON 中提取 arguments 对象
     */
    static Map<String, Object> extractArguments(String json) {
        // 提取 "arguments" 后面的 JSON 对象
        Pattern p = Pattern.compile("\"arguments\"\\s*:\\s*(\\{[^}]*\\})");
        Matcher m = p.matcher(json);
        if (!m.find()) return Map.of();

        String argsJson = m.group(1);
        Map<String, Object> args = new java.util.LinkedHashMap<>();

        // 解析每个键值对（仅支持字符串值）
        Pattern kvPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");
        Matcher kvMatcher = kvPattern.matcher(argsJson);
        while (kvMatcher.find()) {
            args.put(kvMatcher.group(1), kvMatcher.group(2));
        }

        // 也处理数值
        Pattern numPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\\d+)");
        Matcher numMatcher = numPattern.matcher(argsJson);
        while (numMatcher.find()) {
            try {
                args.put(numMatcher.group(1), Integer.parseInt(numMatcher.group(2)));
            } catch (NumberFormatException ignored) {
            }
        }

        return args;
    }

    // ==================== 结果类型 ====================

    /**
     * ReAct 循环执行结果
     */
    @Getter
    public static class ReActResult {
        private final String runId;
        private final List<ReActStep> steps;
        private final String finalAnswer;
        private final long totalElapsedMs;
        private final boolean success;
        private final boolean truncated;
        private final String errorMessage;

        ReActResult(String runId, List<ReActStep> steps, String finalAnswer,
                     long totalElapsedMs, boolean success, boolean truncated, String errorMessage) {
            this.runId = runId;
            this.steps = List.copyOf(steps);
            this.finalAnswer = finalAnswer;
            this.totalElapsedMs = totalElapsedMs;
            this.success = success;
            this.truncated = truncated;
            this.errorMessage = errorMessage;
        }

        static ReActResult success(String runId, List<ReActStep> steps,
                                    String finalAnswer, long elapsedMs) {
            return new ReActResult(runId, steps, finalAnswer, elapsedMs, true, false, null);
        }

        static ReActResult truncated(String runId, List<ReActStep> steps,
                                      String finalAnswer, long elapsedMs) {
            return new ReActResult(runId, steps, finalAnswer, elapsedMs, true, true, null);
        }

        static ReActResult error(String runId, List<ReActStep> steps,
                                  String errorMessage, long elapsedMs) {
            return new ReActResult(runId, steps, "", elapsedMs, false, false, errorMessage);
        }

        public int getIterationCount() {
            return steps.size();
        }

        public long getTotalToolCallMs() {
            return steps.stream()
                    .filter(s -> s.toolResult() != null)
                    .mapToLong(ReActStep::toolElapsedMs)
                    .sum();
        }

        @Override
        public String toString() {
            return String.format("ReActResult{runId=%s, iterations=%d, tools=%d, elapsed=%dms, success=%s}",
                    runId, getIterationCount(),
                    steps.stream().filter(s -> s.toolCall() != null).count(),
                    totalElapsedMs, success);
        }
    }
}
