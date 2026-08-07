package com.zhli.baymd.rag.service.pipeline;

import com.zhli.baymd.rag.config.ReActProperties;
import com.zhli.baymd.rag.core.agent.ReActAgentService;
import com.zhli.baymd.rag.core.agent.ReActLoop;
import com.zhli.baymd.rag.core.prompt.PromptTemplateLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.zhli.baymd.rag.constant.RAGConstant.CHAT_SYSTEM_PROMPT_PATH;

/**
 * Agent 执行器 — LLM 自主多步推理（ReAct 循环）。
 *
 * <p>用户请求被路由到此执行器时，Agent 可以自主决定：</p>
 * <ul>
 *   <li>是否需要查知识库（调用 search_knowledge_base 工具）</li>
 *   <li>是否需要调用 MCP 工具</li>
 *   <li>多步推理后综合给出答案</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentExecutor implements ConversationExecutor {

    private final ReActAgentService agentService;
    private final PromptTemplateLoader promptTemplateLoader;
    private final ReActProperties reActProperties;

    @Override
    public ExecutionMode getMode() {
        return ExecutionMode.AGENT;
    }

    @Override
    public boolean supports(StreamChatContext ctx) {
        // Agent 模式总开关
        if (!reActProperties.isEnabled()) {
            return false;
        }
        // 深度思考模式 → Agent
        if (ctx.isDeepThinking()) {
            return true;
        }
        // MCP 意图（需要外部实时数据）→ Agent
        if (ctx.getSubIntents() != null) {
            return ctx.getSubIntents().stream()
                    .flatMap(si -> si.nodeScores().stream())
                    .anyMatch(ns -> ns.getNode().getKind() != null
                            && ns.getNode().getKind().name().equals("MCP"));
        }
        return false;
    }

    @Override
    public void execute(StreamChatContext ctx) {
        String systemPrompt = promptTemplateLoader.load(CHAT_SYSTEM_PROMPT_PATH);
        String baseQuestion = ctx.getRewriteResult() != null
                ? ctx.getRewriteResult().rewrittenQuestion()
                : ctx.getQuestion();
        // 若用户上传了报告，将报告内容拼入用户问题供 Agent 参考
        String question = ctx.getReportContext() != null && !ctx.getReportContext().isBlank()
                ? ctx.getReportContext() + "\n\n用户问题：" + baseQuestion
                : baseQuestion;

        log.info("Agent 执行器启动: question={}", baseQuestion);

        ReActLoop.ReActResult result = agentService.processStreaming(
                systemPrompt,
                ctx.getHistory(),
                question,
                ctx.getCallback()
        );

        log.info("Agent 执行器完成: iterations={}, tools={}, elapsed={}ms, success={}",
                result.getIterationCount(),
                result.getSteps().stream().filter(s -> s.toolCall() != null).count(),
                result.getTotalElapsedMs(),
                result.isSuccess());
    }
}
