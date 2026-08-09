package com.zhli.baymd.core.chunk.strategy;

import cn.hutool.core.util.IdUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zhli.baymd.core.chunk.ChunkingMode;
import com.zhli.baymd.core.chunk.ChunkingOptions;
import com.zhli.baymd.core.chunk.ChunkingStrategy;
import com.zhli.baymd.core.chunk.QaOptions;
import com.zhli.baymd.core.chunk.VectorChunk;
import com.zhli.baymd.framework.convention.ChatMessage;
import com.zhli.baymd.framework.convention.ChatRequest;
import com.zhli.baymd.infra.chat.LLMService;
import com.zhli.baymd.infra.util.LLMResponseCleaner;
import com.zhli.baymd.rag.core.prompt.PromptConfigService;
import com.zhli.baymd.rag.core.prompt.PromptScenes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 问答切块（Dify 风格）：LLM 从文档提取问答对，每个问答对一个块。
 * <p>块内容格式：{@code Q: 问题\nA: 答案}，自包含可独立检索。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QaChunker implements ChunkingStrategy {

    private static final int MAX_QA_PAIRS = 20;
    private static final String PROMPT = """
            # 角色
            你是医学文档问答对提取器。从给定的文档中提取独立的问答对。

            # 规则
            1. 只提取文档中明确存在、独立成对的问题与答案
            2. 每个问答对应自包含、可独立理解（不要引用"如上/前文"）
            3. 最多返回 %d 对；无法提取时返回空数组 []
            4. 严格返回 JSON 数组，不要任何解释或 markdown 代码块：
            [{"question":"问题","answer":"答案"}]

            # 文档
            {doc}
            """;

    private final LLMService llmService;
    private final PromptConfigService promptConfigService;

    @Override
    public ChunkingMode getType() {
        return ChunkingMode.QA;
    }

    @Override
    public List<VectorChunk> chunk(String text, ChunkingOptions config) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        // 自定义 QA 提示词（可选）覆盖默认
        String custom = (config instanceof QaOptions qa && qa.prompt() != null && !qa.prompt().isBlank())
                ? qa.prompt() : null;
        String truncated = truncate(text, 8000);
        String defaultPrompt = (custom != null ? custom : PROMPT.formatted(MAX_QA_PAIRS))
                .replace("{doc}", truncated);
        Map<String, String> slots = Map.of("doc", truncated);
        String userPrompt = promptConfigService.user(PromptScenes.QA_CHUNK, () -> defaultPrompt, slots);

        List<ChatMessage> messages = new ArrayList<>();
        if (promptConfigService.hasSystem(PromptScenes.QA_CHUNK)) {
            messages.add(ChatMessage.system(promptConfigService.system(
                    PromptScenes.QA_CHUNK, () -> "", slots)));
        }
        messages.add(ChatMessage.user(userPrompt));
        ChatRequest.ChatRequestBuilder rb = ChatRequest.builder()
                .messages(messages)
                .temperature(0.1)
                .topP(0.3)
                .thinking(false);
        promptConfigService.applyParams(PromptScenes.QA_CHUNK, rb);
        ChatRequest request = rb.build();
        String raw = llmService.chat(request);
        List<VectorChunk> chunks = parsePairs(raw, text);
        if (chunks.isEmpty()) {
            // LLM 未提取到问答对时，退化为整篇一个块，避免文档丢失
            log.warn("QA 切块未提取到问答对，退化为整篇: len={}", text.length());
            chunks.add(VectorChunk.builder()
                    .chunkId(IdUtil.getSnowflakeNextIdStr())
                    .index(0)
                    .content(text.trim())
                    .build());
        }
        return chunks;
    }

    private List<VectorChunk> parsePairs(String raw, String fallbackText) {
        List<VectorChunk> out = new ArrayList<>();
        try {
            String cleaned = LLMResponseCleaner.stripMarkdownCodeFence(raw);
            JsonElement root = JsonParser.parseString(cleaned);
            JsonArray arr = root.isJsonArray() ? root.getAsJsonArray()
                    : root.isJsonObject() && root.getAsJsonObject().has("results")
                        ? root.getAsJsonObject().getAsJsonArray("results") : null;
            if (arr == null) {
                return out;
            }
            int index = 0;
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                String q = obj.has("question") ? obj.get("question").getAsString() : "";
                String a = obj.has("answer") ? obj.get("answer").getAsString() : "";
                if (q.isBlank() || a.isBlank()) continue;
                String content = "Q: " + q.trim() + "\nA: " + a.trim();
                out.add(VectorChunk.builder()
                        .chunkId(IdUtil.getSnowflakeNextIdStr())
                        .index(index++)
                        .content(content)
                        .build());
            }
        } catch (Exception e) {
            log.warn("QA 切块解析失败: {}", e.getMessage());
        }
        return out;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
