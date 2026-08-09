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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 事实合并服务 — 同类型多条事实触发 LLM 合并，防止数据膨胀。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FactMergingService {

    private final LLMService llmService;
    private final PromptConfigService promptConfigService;
    private final UserFactMapper factMapper;
    private static final Gson GSON = new Gson();
    private static final int MERGE_THRESHOLD = 10;

    private static final String MERGE_PROMPT = """
            合并以下关于同一用户的事实（去重、解决矛盾、精炼表述）。
            返回 JSON: {"merged": "合并后的事实", "confidence": 0.85}
            若无需合并，返回空: {"merged": "", "confidence": 0}
            """;

    /**
     * 检查是否需要合并（同用户同类型 >= 阈值），异步触发。
     */
    public void mergeIfNeeded(String userId) {
        Map<String, Long> typeCounts = countByType(userId);
        for (var entry : typeCounts.entrySet()) {
            if (entry.getValue() >= MERGE_THRESHOLD) {
                mergeByType(userId, entry.getKey());
            }
        }
    }

    private Map<String, Long> countByType(String userId) {
        List<UserFactDO> all = factMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserFactDO>()
                        .eq(UserFactDO::getUserId, userId));
        return all.stream()
                .collect(Collectors.groupingBy(UserFactDO::getFactType, Collectors.counting()));
    }

    private void mergeByType(String userId, String factType) {
        List<UserFactDO> facts = factMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserFactDO>()
                        .eq(UserFactDO::getUserId, userId)
                        .eq(UserFactDO::getFactType, factType)
                        .orderByDesc(UserFactDO::getConfidence)
                        .last("LIMIT 20"));

        if (facts.size() < 3) return;

        String factList = facts.stream()
                .map(f -> "- " + f.getFactText() + " (可信度:" + String.format("%.0f%%", f.getConfidence() * 100) + ")")
                .collect(Collectors.joining("\n"));

        try {
            Map<String, String> slots = Map.of("fact_list", factList);
            String system = promptConfigService.system(PromptScenes.MEMORY_FACT_MERGE, () -> MERGE_PROMPT, slots);
            String user = promptConfigService.user(PromptScenes.MEMORY_FACT_MERGE, () -> factList, slots);
            ChatRequest.ChatRequestBuilder rb = ChatRequest.builder()
                    .messages(List.of(ChatMessage.system(system), ChatMessage.user(user)))
                    .temperature(0.1).maxTokens(512);
            promptConfigService.applyParams(PromptScenes.MEMORY_FACT_MERGE, rb);
            String raw = llmService.chat(rb.build());

            String cleaned = LLMResponseCleaner.stripMarkdownCodeFence(raw);
            JsonObject obj = GSON.fromJson(cleaned, JsonObject.class);
            if (!obj.has("merged") || obj.get("merged").getAsString().isBlank()) return;

            String merged = obj.get("merged").getAsString();
            float conf = obj.has("confidence") ? obj.get("confidence").getAsFloat() : 0.8f;

            // 插入合并后的事实，删除旧事实
            UserFactDO mergedFact = UserFactDO.builder()
                    .userId(userId).factType(factType).factText(merged)
                    .confidence(conf).build();
            factMapper.insert(mergedFact);

            // 删除已合并的旧事实
            List<String> oldIds = facts.stream().map(UserFactDO::getId).toList();
            factMapper.deleteBatchIds(oldIds);

            log.info("Fact 合并完成: userId={}, type={}, {}条 → 1条", userId, factType, facts.size());

        } catch (Exception e) {
            log.warn("Fact 合并失败: userId={}, type={}", userId, factType, e);
        }
    }
}
