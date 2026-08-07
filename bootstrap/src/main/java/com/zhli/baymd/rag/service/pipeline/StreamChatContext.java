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
import com.zhli.baymd.infra.chat.StreamCallback;
import com.zhli.baymd.rag.core.guardrails.EmergencyDetector;
import com.zhli.baymd.rag.core.rewrite.RewriteResult;
import com.zhli.baymd.rag.dto.SubQuestionIntent;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 流式对话上下文
 */
@Getter
@Builder
public class StreamChatContext {

    // ==================== 不可变输入参数 ====================

    private final String question;
    private final String conversationId;
    private final String taskId;
    private final boolean deepThinking;
    private final String userId;
    private final StreamCallback callback;
    /** 用户上传的报告 ID（可选，报告解读场景） */
    private final String reportId;

    // ==================== 管道中填充的中间状态 ====================

    @Setter
    private List<ChatMessage> history;

    @Setter
    private RewriteResult rewriteResult;

    @Setter
    private List<SubQuestionIntent> subIntents;

    /** 紧急分诊检测结果（预处理最前阶段填充，{@code null} 表示非紧急） */
    @Setter
    private EmergencyDetector.EmergencyResult emergency;

    /** 报告解读上下文（由 reportId 加载的结构化/原文内容，注入 Prompt） */
    @Setter
    private String reportContext;
}
