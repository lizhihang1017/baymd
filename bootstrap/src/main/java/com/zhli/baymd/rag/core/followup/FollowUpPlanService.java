package com.zhli.baymd.rag.core.followup;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zhli.baymd.framework.convention.ChatMessage;
import com.zhli.baymd.framework.convention.ChatRequest;
import com.zhli.baymd.infra.chat.LLMService;
import com.zhli.baymd.infra.util.LLMResponseCleaner;
import com.zhli.baymd.rag.constant.RAGConstant;
import com.zhli.baymd.rag.core.prompt.PromptConfigService;
import com.zhli.baymd.rag.core.prompt.PromptScenes;
import com.zhli.baymd.rag.core.prompt.PromptTemplateLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 随访规划服务 — LLM 判断本轮对话是否值得随访。
 *
 * <p>注意：与已有 {@link FollowUpGenerator}（生成回答后的追问建议）用途不同，
 * 本服务产出的是"几天后主动随访用户"的计划。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpPlanService {

    private final LLMService llmService;
    private final PromptTemplateLoader promptTemplateLoader;
    private final PromptConfigService promptConfigService;

    public record FollowUpPlan(boolean shouldFollowUp, String followUpQuestion,
                                int delayDays, String topic) {
        public static FollowUpPlan none() {
            return new FollowUpPlan(false, "", 0, "");
        }
    }

    public FollowUpPlan plan(String question, String answer) {
        try {
            Map<String, String> slots = Map.of("question", question == null ? "" : question,
                    "answer", answer == null ? "" : answer);
            String prompt = promptConfigService.system(PromptScenes.FOLLOWUP_PLAN, () ->
                    promptTemplateLoader.render(RAGConstant.FOLLOWUP_PLAN_PROMPT_PATH, slots), slots);
            String user = promptConfigService.user(PromptScenes.FOLLOWUP_PLAN, () -> "请按规则输出 JSON。", slots);
            ChatRequest.ChatRequestBuilder rb = ChatRequest.builder()
                    .messages(List.of(
                            ChatMessage.system(prompt),
                            ChatMessage.user(user)
                    ))
                    .temperature(0.1)
                    .topP(0.3)
                    .thinking(false);
            promptConfigService.applyParams(PromptScenes.FOLLOWUP_PLAN, rb);
            ChatRequest request = rb.build();
            String raw = llmService.chat(request);
            String cleaned = LLMResponseCleaner.stripMarkdownCodeFence(raw);
            JsonObject obj = JsonParser.parseString(cleaned).getAsJsonObject();
            boolean should = obj.has("shouldFollowUp") && obj.get("shouldFollowUp").getAsBoolean();
            if (!should) {
                return FollowUpPlan.none();
            }
            String q = obj.has("followUpQuestion") ? obj.get("followUpQuestion").getAsString() : "";
            int delay = obj.has("delayDays") ? obj.get("delayDays").getAsInt() : 3;
            String topic = obj.has("topic") ? obj.get("topic").getAsString() : "通用随访";
            if (q == null || q.isBlank() || delay < 1 || delay > 14) {
                return FollowUpPlan.none();
            }
            return new FollowUpPlan(true, q, delay, topic);
        } catch (Exception e) {
            log.warn("随访规划失败: {}", e.getMessage());
            return FollowUpPlan.none();
        }
    }
}
