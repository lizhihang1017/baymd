package com.zhli.baymd.rag.service.pipeline;

import com.zhli.baymd.framework.convention.ChatMessage;
import com.zhli.baymd.framework.convention.ChatRequest;
import com.zhli.baymd.infra.chat.LLMService;
import com.zhli.baymd.infra.chat.StreamCallback;
import com.zhli.baymd.infra.chat.StreamCancellationHandle;
import com.zhli.baymd.rag.config.SearchChannelProperties;
import com.zhli.baymd.rag.core.eval.QualityEvaluator;
import com.zhli.baymd.rag.core.followup.FollowUpGenerator;
import com.zhli.baymd.rag.core.memory.extract.MemoryExtractionOrchestrator;
import com.zhli.baymd.rag.core.intent.IntentResolver;
import com.zhli.baymd.rag.core.prompt.EvidenceBudgetService;
import com.zhli.baymd.rag.core.prompt.PromptConfigService;
import com.zhli.baymd.rag.core.prompt.PromptContext;
import com.zhli.baymd.rag.core.prompt.PromptScenes;
import com.zhli.baymd.rag.core.prompt.RAGPromptService;
import com.zhli.baymd.rag.core.retrieve.RetrievalEngine;
import com.zhli.baymd.rag.dto.IntentGroup;
import com.zhli.baymd.rag.dto.RetrievalContext;
import com.zhli.baymd.rag.service.handler.EnrichedStreamCallback;
import com.zhli.baymd.rag.service.handler.StreamTaskManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RAG 知识问答执行器 — 检索 + LLM 生成的经典 RAG 模式。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagExecutor implements ConversationExecutor {

    private final SearchChannelProperties searchProperties;
    private final RetrievalEngine retrievalEngine;
    private final LLMService llmService;
    private final RAGPromptService promptBuilder;
    private final PromptConfigService promptConfigService;
    private final IntentResolver intentResolver;
    private final EvidenceBudgetService evidenceBudgetService;
    private final StreamTaskManager taskManager;
    private final FollowUpGenerator followUpGenerator;
    private final QualityEvaluator qualityEvaluator;
    private final MemoryExtractionOrchestrator memoryOrchestrator;

    @Override
    public ExecutionMode getMode() {
        return ExecutionMode.RAG;
    }

    @Override
    public boolean supports(StreamChatContext ctx) {
        // RAG 是 fallback — 总是返回 true
        return true;
    }

    @Override
    public void execute(StreamChatContext ctx) {
        // 1. 检索
        RetrievalContext retrievalCtx = retrievalEngine.retrieve(
                ctx.getSubIntents(),
                searchProperties.getDefaultTopK()
        );

        // 2. 空检索 / 低置信度短路
        if (retrievalCtx.isEmpty()) {
            ctx.getCallback().onContent(evidenceBudgetService.getNoEvidenceResponse());
            ctx.getCallback().onComplete();
            return;
        }
        if (!evidenceBudgetService.hasValidEvidence(retrievalCtx.getIntentChunks())) {
            log.info("RAG 执行器: 证据置信度不足，短路返回");
            ctx.getCallback().onContent(evidenceBudgetService.getNoEvidenceResponse());
            ctx.getCallback().onComplete();
            return;
        }

        // 3. 构建 Prompt 并流式生成
        String baseQuestion = ctx.getRewriteResult() != null
                ? ctx.getRewriteResult().rewrittenQuestion()
                : ctx.getQuestion();
        // 若用户上传了报告，将报告内容拼入用户问题供 LLM 参考（检索已在上游完成，不影响召回）
        String effectiveQuestion = ctx.getReportContext() != null && !ctx.getReportContext().isBlank()
                ? ctx.getReportContext() + "\n\n用户问题：" + baseQuestion
                : baseQuestion;

        IntentGroup mergedGroup = intentResolver.mergeIntentGroup(ctx.getSubIntents());
        PromptContext promptContext = PromptContext.builder()
                .question(effectiveQuestion)
                .mcpContext(retrievalCtx.getMcpContext())
                .kbContext(retrievalCtx.getKbContext())
                .mcpIntents(mergedGroup.mcpIntents())
                .kbIntents(mergedGroup.kbIntents())
                .intentChunks(retrievalCtx.getIntentChunks())
                .omittedEvidenceCount(retrievalCtx.getOmittedEvidenceCount())
                .build();

        List<ChatMessage> messages = promptBuilder.buildStructuredMessages(
                promptContext,
                ctx.getHistory(),
                effectiveQuestion,
                ctx.getRewriteResult() != null
                        ? ctx.getRewriteResult().subQuestions()
                        : List.of()
        );

        ChatRequest.ChatRequestBuilder rb = ChatRequest.builder()
                .messages(messages)
                .thinking(ctx.isDeepThinking())
                .temperature(retrievalCtx.hasMcp() ? 0.3 : 0.0)
                .topP(retrievalCtx.hasMcp() ? 0.8 : 1.0);
        promptConfigService.applyParams(PromptScenes.RAG_ANSWER, rb);
        ChatRequest chatRequest = rb.build();

        // 用增强回调包裹：自动收集引用、生成追问、异步评估质量
        String question = ctx.getRewriteResult() != null
                ? ctx.getRewriteResult().rewrittenQuestion()
                : ctx.getQuestion();
        StreamCallback enriched = new EnrichedStreamCallback(
                ctx.getCallback(), followUpGenerator, qualityEvaluator,
                memoryOrchestrator,
                question, ctx.getUserId(), ctx.getConversationId(),
                retrievalCtx.getIntentChunks());

        StreamCancellationHandle handle = llmService.streamChat(chatRequest, enriched);
        taskManager.bindHandle(ctx.getTaskId(), handle);
    }
}
