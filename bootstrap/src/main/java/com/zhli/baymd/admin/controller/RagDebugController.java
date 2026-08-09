package com.zhli.baymd.admin.controller;

import cn.hutool.core.util.StrUtil;
import com.zhli.baymd.framework.auth.RequireAdmin;
import com.zhli.baymd.framework.convention.Result;
import com.zhli.baymd.framework.web.Results;
import com.zhli.baymd.rag.core.intent.IntentNode;
import com.zhli.baymd.rag.core.intent.IntentResolver;
import com.zhli.baymd.rag.core.intent.NodeScore;
import com.zhli.baymd.rag.core.retrieve.RetrievalEngine;
import com.zhli.baymd.rag.core.rewrite.MultiQuestionRewriteService;
import com.zhli.baymd.rag.core.rewrite.RewriteResult;
import com.zhli.baymd.rag.dto.RetrievalContext;
import com.zhli.baymd.rag.dto.SubQuestionIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 调试面板（管理员）— 输入问题,展示完整链路: 改写 → 意图打分 → 执行器判定 → 检索证据。
 */
@Slf4j
@RequireAdmin
@RestController
@RequiredArgsConstructor
public class RagDebugController {

    private final MultiQuestionRewriteService rewriteService;
    private final IntentResolver intentResolver;
    private final RetrievalEngine retrievalEngine;

    @PostMapping("/admin/rag/debug")
    public Result<Map<String, Object>> debug(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        if (StrUtil.isBlank(question)) {
            throw new IllegalArgumentException("问题不能为空");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("question", question);

        // 1. 查询改写
        RewriteResult rewrite = rewriteService.rewriteWithSplit(question);
        result.put("rewrittenQuestion", rewrite.rewrittenQuestion());
        result.put("subQuestions", rewrite.subQuestions());

        // 2. 意图分类（保留原始打分）
        List<SubQuestionIntent> subIntents = intentResolver.resolve(rewrite);
        List<Map<String, Object>> intentList = subIntents.stream().map(si -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("subQuestion", si.subQuestion());
            List<Map<String, Object>> scores = si.nodeScores().stream().map(ns -> {
                IntentNode node = ns.getNode();
                Map<String, Object> sm = new LinkedHashMap<>();
                sm.put("id", node.getId());
                sm.put("name", node.getName());
                sm.put("path", node.getFullPath());
                sm.put("kind", node.getKind() == null ? "KB" : node.getKind().name());
                sm.put("score", ns.getScore());
                return sm;
            }).toList();
            m.put("scores", scores);
            return m;
        }).toList();
        result.put("intents", intentList);

        // 3. 执行器判定（模拟 ExecutorRegistry 顺序: 紧急→澄清→系统→Agent→RAG）
        result.put("executor", resolveExecutor(subIntents));

        // 4. 检索证据
        try {
            RetrievalContext rc = retrievalEngine.retrieve(subIntents, 5);
            Map<String, Object> ret = new LinkedHashMap<>();
            ret.put("kbContextLength", StrUtil.length(rc.getKbContext()));
            ret.put("mcpContextLength", StrUtil.length(rc.getMcpContext()));
            ret.put("omittedEvidenceCount", rc.getOmittedEvidenceCount());
            ret.put("kbContextSnippet", StrUtil.maxLength(StrUtil.nullToEmpty(rc.getKbContext()), 500));
            ret.put("mcpContextSnippet", StrUtil.maxLength(StrUtil.nullToEmpty(rc.getMcpContext()), 500));
            result.put("retrieval", ret);
        } catch (Exception e) {
            result.put("retrieval", Map.of("error", e.getMessage()));
            log.warn("调试面板检索失败: {}", e.getMessage());
        }
        return Results.success(result);
    }

    private String resolveExecutor(List<SubQuestionIntent> subIntents) {
        if (subIntents == null || subIntents.isEmpty()) {
            return "CLARIFICATION（无有效意图）";
        }
        boolean allSystem = subIntents.stream()
                .flatMap(si -> si.nodeScores().stream())
                .allMatch(ns -> ns.getNode().getKind() != null && "SYSTEM".equals(ns.getNode().getKind().name()));
        boolean hasMcp = subIntents.stream()
                .flatMap(si -> si.nodeScores().stream())
                .anyMatch(ns -> ns.getNode().getKind() != null && "MCP".equals(ns.getNode().getKind().name()));
        if (allSystem) {
            return "SYSTEM_ONLY（系统直答）";
        }
        if (hasMcp) {
            return "AGENT（含 MCP 意图,Agent 可调工具）";
        }
        return "RAG（知识库问答）";
    }
}
