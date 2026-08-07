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

package com.zhli.baymd.rag.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * RAG 对话服务接口
 * 对外暴露流式问答与任务停止能力，屏蔽控制器层之外的实现细节
 */
public interface RAGChatService {

    /**
     * 发起一次 SSE 流式问答（不带报告）
     */
    default void streamChat(String question, String conversationId, Boolean deepThinking, SseEmitter emitter) {
        streamChat(question, conversationId, deepThinking, null, emitter);
    }

    /**
     * 发起一次 SSE 流式问答。
     *
     * @param question       用户问题
     * @param conversationId 会话 ID（可选，空时创建新会话）
     * @param deepThinking   是否开启深度思考模式
     * @param reportId       关联的报告 ID（可选，报告解读场景注入报告上下文）
     * @param emitter        SSE 发射器
     */
    void streamChat(String question, String conversationId, Boolean deepThinking,
                    String reportId, SseEmitter emitter);

    /**
     * 停止指定任务 ID 的流式会话
     *
     * @param taskId 任务 ID
     */
    void stopTask(String taskId);
}
