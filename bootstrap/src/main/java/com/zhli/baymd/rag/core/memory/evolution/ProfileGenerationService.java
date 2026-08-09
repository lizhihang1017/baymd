package com.zhli.baymd.rag.core.memory.evolution;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zhli.baymd.framework.convention.ChatMessage;
import com.zhli.baymd.framework.convention.ChatRequest;
import com.zhli.baymd.infra.chat.LLMService;
import com.zhli.baymd.infra.util.LLMResponseCleaner;
import com.zhli.baymd.rag.core.prompt.PromptConfigService;
import com.zhli.baymd.rag.core.prompt.PromptScenes;
import com.zhli.baymd.rag.dao.entity.UserFactDO;
import com.zhli.baymd.rag.dao.mapper.UserFactMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户画像生成服务 — 累积足够 Fact 后 LLM 生成画像摘要。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileGenerationService {

    private final LLMService llmService;
    private final PromptConfigService promptConfigService;
    private final UserFactMapper factMapper;
    private static final Gson GSON = new Gson();
    private static final int PROFILE_THRESHOLD = 20;

    private static final String PROMPT = """
            基于以下用户事实，生成用户画像摘要（200字以内）。
            包含: 健康状况、行为习惯、偏好倾向、目标诉求。
            返回 JSON: {"profile": "画像摘要文本"}
            """;

    /**
     * 如果用户 Fact 积累到阈值，生成/更新画像。
     */
    public UserProfile generateIfNeeded(String userId) {
        List<UserFactDO> facts = factMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserFactDO>()
                        .eq(UserFactDO::getUserId, userId)
                        .orderByDesc(UserFactDO::getConfidence));

        if (facts.size() < PROFILE_THRESHOLD) return null;

        String factList = facts.stream()
                .map(f -> "[" + f.getFactType() + "] " + f.getFactText())
                .collect(Collectors.joining("\n"));

        try {
            Map<String, String> slots = Map.of("fact_list", factList);
            String system = promptConfigService.system(PromptScenes.MEMORY_PROFILE, () -> PROMPT, slots);
            String user = promptConfigService.user(PromptScenes.MEMORY_PROFILE, () -> factList, slots);
            ChatRequest.ChatRequestBuilder rb = ChatRequest.builder()
                    .messages(List.of(ChatMessage.system(system), ChatMessage.user(user)))
                    .temperature(0.2).maxTokens(512);
            promptConfigService.applyParams(PromptScenes.MEMORY_PROFILE, rb);
            String raw = llmService.chat(rb.build());

            String cleaned = LLMResponseCleaner.stripMarkdownCodeFence(raw);
            JsonObject obj = GSON.fromJson(cleaned, JsonObject.class);
            String profile = obj.has("profile") ? obj.get("profile").getAsString() : "";

            if (!profile.isBlank()) {
                log.info("用户画像已生成: userId={}, facts={}条, profile_len={}",
                        userId, facts.size(), profile.length());
                return new UserProfile(userId, profile, facts.size());
            }
        } catch (Exception e) {
            log.warn("画像生成失败: userId={}", userId, e);
        }
        return null;
    }

    @Data
    public static class UserProfile {
        private final String userId;
        private final String profile;
        private final int sourceFactCount;

        public String toPromptFragment() {
            return "## 用户画像\n" + profile + "\n";
        }
    }
}
