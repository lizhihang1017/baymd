/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zhli.baymd.rag.service.pipeline;

import com.zhli.baymd.framework.convention.ChatMessage;
import com.zhli.baymd.rag.core.guardrails.EmergencyDetector;
import com.zhli.baymd.rag.core.intent.IntentResolver;
import com.zhli.baymd.rag.core.memory.ConversationMemoryService;
import com.zhli.baymd.rag.core.rewrite.QueryRewriteService;
import com.zhli.baymd.rag.core.rewrite.RewriteResult;
import com.zhli.baymd.rag.dao.entity.MedicalReportDO;
import com.zhli.baymd.rag.dto.SubQuestionIntent;
import com.zhli.baymd.rag.service.ReportParseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 流式对话流水线
 * <p>
 * 承载从 RAGChatServiceImpl 提取的业务编排逻辑：
 * 紧急检测 -> 报告加载 -> 记忆加载 -> 改写拆分 -> 意图解析 -> 执行器分发
 * （EMERGENCY / Clarification / SystemOnly / Agent / RAG）
 * <p>
 * 流水线模式：公共预处理阶段 → {@link ExecutorRegistry} 选择执行器 → 委托执行。
 * 相比旧版三段 if-else，新架构支持任意新增执行模式而无需修改管道代码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamChatPipeline {

    private final EmergencyDetector emergencyDetector;
    private final ReportParseService reportParseService;
    private final ConversationMemoryService memoryService;
    private final QueryRewriteService queryRewriteService;
    private final IntentResolver intentResolver;
    private final ExecutorRegistry executorRegistry;

    /**
     * 执行流式对话管道
     */
    public void execute(StreamChatContext ctx) {
        // === 公共预处理 ===
        // 紧急分诊检测必须在最前（基于原始问题，零成本关键词匹配），
        // 命中则 EmergencyExecutor 会短路接管，跳过后续检索/Agent。
        detectEmergency(ctx);
        loadReportContext(ctx);
        loadMemory(ctx);
        rewriteQuery(ctx);
        resolveIntents(ctx);

        // === 执行器分发 ===
        ConversationExecutor executor = executorRegistry.resolve(ctx);
        log.info("管道分发: mode={}, executor={}, question={}",
                executor.getMode(), executor.getClass().getSimpleName(),
                ctx.getQuestion().substring(0, Math.min(50, ctx.getQuestion().length())));

        executor.execute(ctx);
    }

    // ==================== 公共预处理 ====================

    private void detectEmergency(StreamChatContext ctx) {
        EmergencyDetector.EmergencyResult result = emergencyDetector.detect(ctx.getQuestion());
        ctx.setEmergency(result);
    }

    private void loadReportContext(StreamChatContext ctx) {
        if (ctx.getReportId() == null || ctx.getReportId().isBlank()) {
            return;
        }
        try {
            MedicalReportDO report = reportParseService.getById(ctx.getReportId());
            ctx.setReportContext(reportParseService.buildReportContext(report));
        } catch (Exception e) {
            log.warn("加载报告上下文失败: reportId={}, {}", ctx.getReportId(), e.getMessage());
        }
    }

    // ==================== 公共预处理（保持不变） ====================

    private void loadMemory(StreamChatContext ctx) {
        List<ChatMessage> history = memoryService.loadAndAppend(
                ctx.getConversationId(),
                ctx.getUserId(),
                ChatMessage.user(ctx.getQuestion())
        );
        ctx.setHistory(history);
    }

    private void rewriteQuery(StreamChatContext ctx) {
        RewriteResult rewriteResult = queryRewriteService.rewriteWithSplit(
                ctx.getQuestion(), ctx.getHistory());
        ctx.setRewriteResult(rewriteResult);
    }

    private void resolveIntents(StreamChatContext ctx) {
        List<SubQuestionIntent> subIntents = intentResolver.resolve(ctx.getRewriteResult());
        ctx.setSubIntents(subIntents);
    }
}
