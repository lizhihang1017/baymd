package com.zhli.baymd.rag.core.followup;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zhli.baymd.framework.convention.ChatMessage;
import com.zhli.baymd.framework.convention.ChatRequest;
import com.zhli.baymd.infra.chat.LLMService;
import com.zhli.baymd.infra.util.LLMResponseCleaner;
import com.zhli.baymd.rag.core.prompt.PromptConfigService;
import com.zhli.baymd.rag.core.prompt.PromptScenes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 追问生成器 — 主回答后调用 LLM 生成最多 3 个推荐追问。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpGenerator {

    private final LLMService llmService;
    private final PromptConfigService promptConfigService;

    private static final Gson GSON = new Gson();
    private static final int MAX_FOLLOW_UPS = 3;

    private static final String PROMPT = """
            基于以下对话，生成最多%d个用户可能感兴趣的追问。
            追问应简短（不超过20字）、与上下文相关、引导深入探索。
            严格返回 JSON 数组：["追问1", "追问2", "追问3"]
            如果没有合适的追问，返回空数组 []。
            """.formatted(MAX_FOLLOW_UPS);

    /**
     * 生成追问列表。
     *
     * @param question 用户问题
     * @param answer   系统回答
     * @param history  对话历史
     * @return 追问列表（最多 3 个，可能为空）
     */
    public List<String> generate(String question, String answer, List<ChatMessage> history) {
        try {
            var messages = new java.util.ArrayList<ChatMessage>();
            messages.add(ChatMessage.system(promptConfigService.system(
                    PromptScenes.FOLLOWUP_GENERATE, () -> PROMPT, Map.of())));
            if (history != null && !history.isEmpty()) {
                messages.addAll(history.stream()
                        .filter(m -> m.getRole() == ChatMessage.Role.USER
                                || m.getRole() == ChatMessage.Role.ASSISTANT)
                        .toList());
            }
            messages.add(ChatMessage.user(promptConfigService.user(
                    PromptScenes.FOLLOWUP_GENERATE, () -> "用户问题: " + question,
                    Map.of("question", question == null ? "" : question))));
            messages.add(ChatMessage.assistant(answer));

            ChatRequest.ChatRequestBuilder rb = ChatRequest.builder()
                    .messages(messages).temperature(0.3).maxTokens(256);
            promptConfigService.applyParams(PromptScenes.FOLLOWUP_GENERATE, rb);
            String raw = llmService.chat(rb.build());

            String cleaned = LLMResponseCleaner.stripMarkdownCodeFence(raw);
            List<String> result = GSON.fromJson(cleaned,
                    new TypeToken<List<String>>() {}.getType());

            if (result != null && !result.isEmpty()) {
                return result.stream().limit(MAX_FOLLOW_UPS).toList();
            }
        } catch (Exception e) {
            log.warn("追问生成失败: {}", e.getMessage());
        }
        return List.of();
    }
}
