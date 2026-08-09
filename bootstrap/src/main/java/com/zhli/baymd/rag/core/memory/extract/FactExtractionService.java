package com.zhli.baymd.rag.core.memory.extract;

import cn.hutool.core.util.StrUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zhli.baymd.framework.convention.ChatMessage;
import com.zhli.baymd.framework.convention.ChatRequest;
import com.zhli.baymd.infra.chat.LLMService;
import com.zhli.baymd.infra.util.LLMResponseCleaner;
import com.zhli.baymd.rag.core.prompt.PromptConfigService;
import com.zhli.baymd.rag.core.prompt.PromptScenes;
import com.zhli.baymd.rag.dao.entity.UserFactDO;
import com.zhli.baymd.rag.dao.mapper.UserFactMapper;
import com.zhli.baymd.rag.dao.mapper.UserFactVectorMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 原子事实提取服务 — 从对话中提取关于用户的事实信息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FactExtractionService {

    private final LLMService llmService;
    private final PromptConfigService promptConfigService;
    private final UserFactMapper factMapper;
    private final UserFactVectorMapper factVectorMapper;
    private static final Gson GSON = new Gson();

    private static final String PROMPT = """
            基于以下对话，提取关于用户的事实信息。每条事实应简短、原子化、可独立理解。

            类型: health(健康)/behavior(行为)/preference(偏好)/goal(目标)
            置信度: 0.0~1.0, <0.5 不提取
            最多5条，无新信息返回空数组

            严格返回 JSON: {"facts":[{"type":"health","fact":"事实内容","confidence":0.9}]}
            """;

    /**
     * 从对话中提取 Facts 并入库。
     */
    public List<UserFactDO> extractAndSave(String question, String answer,
                                            String userId, String messageId) {
        String raw;
        try {
            Map<String, String> slots = Map.of("question", StrUtil.nullToEmpty(question),
                    "answer", StrUtil.nullToEmpty(answer));
            String system = promptConfigService.system(PromptScenes.MEMORY_FACT, () -> PROMPT, slots);
            String user = promptConfigService.user(PromptScenes.MEMORY_FACT, () ->
                    "用户问题: " + question + "\n系统回答: " + answer, slots);
            ChatRequest.ChatRequestBuilder rb = ChatRequest.builder()
                    .messages(List.of(ChatMessage.system(system), ChatMessage.user(user)))
                    .temperature(0.1).maxTokens(512);
            promptConfigService.applyParams(PromptScenes.MEMORY_FACT, rb);
            raw = llmService.chat(rb.build());
        } catch (Exception e) {
            log.warn("Fact 提取 LLM 调用失败", e);
            return List.of();
        }

        List<ExtractedFact> extracted = parseFacts(raw);
        if (extracted.isEmpty()) return List.of();

        List<UserFactDO> saved = new ArrayList<>();
        for (ExtractedFact ef : extracted) {
            String hash = sha256(ef.fact);
            // 去重：同 user + 同 content_hash 不重复插入
            if (isDuplicate(userId, hash)) {
                log.debug("Fact 去重: {}", ef.fact.substring(0, Math.min(30, ef.fact.length())));
                continue;
            }
            UserFactDO fact = UserFactDO.builder()
                    .userId(userId).factType(ef.type).factText(ef.fact)
                    .confidence(ef.confidence).sourceMsgId(messageId)
                    .contentHash(hash).build();
            factMapper.insert(fact);
            saved.add(fact);
        }
        log.info("Fact 提取完成: userId={}, 新提取={}条", userId, saved.size());
        return saved;
    }

    /**
     * 为已保存的 Fact 生成向量（异步调用 Embedding）
     */
    public void embedFact(UserFactDO fact, String vectorStr) {
        factVectorMapper.insert(fact.getId(), vectorStr);
    }

    private boolean isDuplicate(String userId, String hash) {
        return factMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserFactDO>()
                        .eq(UserFactDO::getUserId, userId)
                        .eq(UserFactDO::getContentHash, hash)
                        .last("LIMIT 1")
        ).size() > 0;
    }

    private List<ExtractedFact> parseFacts(String raw) {
        try {
            String cleaned = LLMResponseCleaner.stripMarkdownCodeFence(raw);
            JsonObject obj = GSON.fromJson(cleaned, JsonObject.class);
            if (!obj.has("facts")) return List.of();
            JsonArray arr = obj.getAsJsonArray("facts");
            List<ExtractedFact> facts = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JsonObject f = arr.get(i).getAsJsonObject();
                float conf = f.has("confidence") ? f.get("confidence").getAsFloat() : 1.0f;
                if (conf < 0.5f) continue;
                facts.add(new ExtractedFact(
                        f.get("type").getAsString(),
                        f.get("fact").getAsString(),
                        conf));
            }
            return facts;
        } catch (Exception e) {
            log.warn("Fact JSON 解析失败", e);
            return List.of();
        }
    }

    static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return text; }
    }

    @Data
    public static class ExtractedFact {
        private final String type;
        private final String fact;
        private final Float confidence;
    }
}
