package com.zhli.baymd.rag.core.memory.extract;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zhli.baymd.framework.convention.ChatMessage;
import com.zhli.baymd.framework.convention.ChatRequest;
import com.zhli.baymd.infra.chat.LLMService;
import com.zhli.baymd.infra.util.LLMResponseCleaner;
import com.zhli.baymd.rag.core.prompt.PromptConfigService;
import com.zhli.baymd.rag.core.prompt.PromptScenes;
import com.zhli.baymd.rag.dao.entity.UserEpisodeDO;
import com.zhli.baymd.rag.dao.mapper.UserEpisodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EpisodeExtractionService {

    private final LLMService llmService;
    private final PromptConfigService promptConfigService;
    private final UserEpisodeMapper episodeMapper;
    private static final Gson GSON = new Gson();

    private static final String PROMPT = """
            基于以下对话，生成情节摘要。title<=15字, summary 1-2句, topics 2-5个关键词。
            严格返回 JSON: {"title":"标题","summary":"摘要","topics":["标签1","标签2"]}
            """;

    public UserEpisodeDO extractAndSave(String question, String answer,
                                         String userId, String conversationId) {
        String raw;
        try {
            Map<String, String> slots = Map.of("question", question == null ? "" : question,
                    "answer", answer == null ? "" : answer);
            String system = promptConfigService.system(PromptScenes.MEMORY_EPISODE, () -> PROMPT, slots);
            String user = promptConfigService.user(PromptScenes.MEMORY_EPISODE, () ->
                    "用户问题: " + question + "\n系统回答: " + answer, slots);
            ChatRequest.ChatRequestBuilder rb = ChatRequest.builder()
                    .messages(List.of(ChatMessage.system(system), ChatMessage.user(user)))
                    .temperature(0.1).maxTokens(256);
            promptConfigService.applyParams(PromptScenes.MEMORY_EPISODE, rb);
            raw = llmService.chat(rb.build());
        } catch (Exception e) {
            log.warn("Episode 提取 LLM 调用失败", e);
            return null;
        }

        try {
            String cleaned = LLMResponseCleaner.stripMarkdownCodeFence(raw);
            JsonObject obj = GSON.fromJson(cleaned, JsonObject.class);
            String[] topics = obj.has("topics") && obj.get("topics").isJsonArray()
                    ? parseTopics(obj.getAsJsonArray("topics")) : new String[0];

            UserEpisodeDO episode = UserEpisodeDO.builder()
                    .userId(userId).conversationId(conversationId)
                    .title(obj.has("title") ? obj.get("title").getAsString() : "")
                    .summary(obj.has("summary") ? obj.get("summary").getAsString() : "")
                    .topics(topics).build();
            episodeMapper.insert(episode);
            log.info("Episode 提取完成: title={}", episode.getTitle());
            return episode;
        } catch (Exception e) {
            log.warn("Episode JSON 解析失败", e);
            return null;
        }
    }

    private String[] parseTopics(com.google.gson.JsonArray arr) {
        String[] ts = new String[Math.min(arr.size(), 5)];
        for (int i = 0; i < ts.length; i++) {
            ts[i] = arr.get(i).getAsString();
        }
        return ts;
    }
}
