package com.zhli.baymd.rag.service.pipeline;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.zhli.baymd.framework.convention.ChatMessage;
import com.zhli.baymd.framework.convention.ChatRequest;
import com.zhli.baymd.infra.chat.LLMService;
import com.zhli.baymd.infra.chat.StreamCancellationHandle;
import com.zhli.baymd.rag.core.intent.IntentResolver;
import com.zhli.baymd.rag.core.prompt.PromptConfigService;
import com.zhli.baymd.rag.core.prompt.PromptScenes;
import com.zhli.baymd.rag.core.prompt.PromptTemplateLoader;
import com.zhli.baymd.rag.service.handler.StreamTaskManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.zhli.baymd.rag.constant.RAGConstant.CHAT_SYSTEM_PROMPT_PATH;

/**
 * 系统直接处理执行器 — 纯系统能力（闲聊/问候/简单问答），不走检索管道。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemOnlyExecutor implements ConversationExecutor {

    private final LLMService llmService;
    private final IntentResolver intentResolver;
    private final PromptTemplateLoader promptTemplateLoader;
    private final PromptConfigService promptConfigService;
    private final StreamTaskManager taskManager;

    @Override
    public ExecutionMode getMode() {
        return ExecutionMode.SYSTEM_ONLY;
    }

    @Override
    public boolean supports(StreamChatContext ctx) {
        if (ctx.getSubIntents() == null) return false;
        return ctx.getSubIntents().stream()
                .allMatch(si -> intentResolver.isSystemOnly(si.nodeScores()));
    }

    @Override
    public void execute(StreamChatContext ctx) {
        String customPrompt = ctx.getSubIntents().stream()
                .flatMap(si -> si.nodeScores().stream())
                .map(ns -> ns.getNode().getPromptTemplate())
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse(null);

        String userQuestion = ctx.getRewriteResult() != null
                ? ctx.getRewriteResult().rewrittenQuestion()
                : ctx.getQuestion();
        Map<String, String> slots = Map.of("question", StrUtil.nullToEmpty(userQuestion));

        String systemPrompt = promptConfigService.system(PromptScenes.SYSTEM_ONLY, () ->
                StrUtil.isNotBlank(customPrompt)
                        ? customPrompt
                        : promptTemplateLoader.load(CHAT_SYSTEM_PROMPT_PATH), slots);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        if (CollUtil.isNotEmpty(ctx.getHistory())) {
            messages.addAll(ctx.getHistory());
        }
        messages.add(ChatMessage.user(promptConfigService.user(
                PromptScenes.SYSTEM_ONLY, () -> userQuestion, slots)));

        ChatRequest.ChatRequestBuilder rb = ChatRequest.builder()
                .messages(messages)
                .temperature(0.7)
                .thinking(false);
        promptConfigService.applyParams(PromptScenes.SYSTEM_ONLY, rb);
        ChatRequest req = rb.build();

        StreamCancellationHandle handle = llmService.streamChat(req, ctx.getCallback());
        taskManager.bindHandle(ctx.getTaskId(), handle);
    }
}
