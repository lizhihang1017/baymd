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

package com.zhli.baymd.rag.trace;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.zhli.baymd.framework.context.UserContext;
import com.zhli.baymd.framework.trace.RagTraceContext;
import com.zhli.baymd.infra.chat.StreamCallback;
import com.zhli.baymd.rag.config.RagTraceProperties;
import com.zhli.baymd.rag.dao.entity.RagTraceRunDO;
import com.zhli.baymd.rag.service.RagTraceRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 流式对话 Trace 包装器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamChatTraceRunner {

    private static final String ENTRY_METHOD = "RAGChatService#streamChat";
    private static final String TRACE_NAME = "rag-stream-chat";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_ERROR = "ERROR";

    private final RagTraceProperties traceProperties;
    private final RagTraceRecordService traceRecordService;

    public void run(String question,
                    String conversationId,
                    String taskId,
                    StreamCallback callback,
                    Runnable businessLogic) {
        if (!traceProperties.isEnabled()) {
            runWithoutTrace(conversationId, taskId, callback, businessLogic);
            return;
        }

        String traceId = IdUtil.getSnowflakeNextIdStr();
        long startMillis = System.currentTimeMillis();
        // 记录 traceId 供 wrapCallback 使用（onComplete 补写 answer 时定位 run）
        CURRENT_TRACE_ID.set(traceId);

        traceRecordService.startRun(RagTraceRunDO.builder()
                .traceId(traceId)
                .traceName(TRACE_NAME)
                .entryMethod(ENTRY_METHOD)
                .conversationId(conversationId)
                .taskId(taskId)
                .userId(UserContext.getUserId())
                .status(STATUS_RUNNING)
                .startTime(new Date())
                .extraData(StrUtil.format("{\"question\":{}}",
                        escapeJson(StrUtil.maxLength(question, 2000))))
                .build());

        RagTraceContext.setTraceId(traceId);
        RagTraceContext.setTaskId(taskId);
        try {
            businessLogic.run();
            traceRecordService.finishRun(
                    traceId,
                    STATUS_SUCCESS,
                    null,
                    new Date(),
                    System.currentTimeMillis() - startMillis
            );
        } catch (Throwable ex) {
            traceRecordService.finishRun(
                    traceId,
                    STATUS_ERROR,
                    truncateError(ex),
                    new Date(),
                    System.currentTimeMillis() - startMillis
            );
            log.warn("执行流式对话失败，会话ID：{}，任务ID：{}", conversationId, taskId, ex);
            callback.onError(ex);
        } finally {
            RagTraceContext.clear();
            CURRENT_TRACE_ID.remove();
        }
    }

    /** 当前正在执行的 traceId（供 {@link #wrapCallback} 使用）— TTL 透传到异步回调线程 */
    private static final com.alibaba.ttl.TransmittableThreadLocal<String> CURRENT_TRACE_ID =
            new com.alibaba.ttl.TransmittableThreadLocal<>();

    /**
     * 包装流式回调：收集最终回答,在 onComplete 时补写到 run.extra_data。
     * <p>必须在 {@link #run} 之后、业务逻辑执行前调用,且返回的包装回调需传给 ctx。</p>
     */
    public StreamCallback wrapCallback(StreamCallback callback) {
        StringBuilder answerBuffer = new StringBuilder();
        return new StreamCallback() {
            @Override
            public void onThinking(String content) {
                callback.onThinking(content);
            }

            @Override
            public void onContent(String content) {
                answerBuffer.append(content);
                callback.onContent(content);
            }

            @Override
            public void onComplete() {
                try {
                    String traceId = CURRENT_TRACE_ID.get();
                    if (traceId != null) {
                        traceRecordService.appendRunExtraData(traceId,
                                StrUtil.format("{\"answer\":{}}",
                                        escapeJson(StrUtil.maxLength(answerBuffer.toString(), 20000))));
                    }
                } catch (Exception e) {
                    log.warn("补写 trace answer 失败: {}", e.getMessage());
                }
                callback.onComplete();
            }

            @Override
            public void onError(Throwable error) {
                callback.onError(error);
            }
        };
    }

    /** JSON 字符串转义（引号/反斜杠/换行/控制字符） */
    private static String escapeJson(String s) {
        if (s == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append("\"").toString();
    }

    private void runWithoutTrace(String conversationId,
                                 String taskId,
                                 StreamCallback callback,
                                 Runnable businessLogic) {
        try {
            businessLogic.run();
        } catch (Throwable ex) {
            log.warn("执行流式对话失败，会话ID：{}，任务ID：{}", conversationId, taskId, ex);
            callback.onError(ex);
        }
    }

    private String truncateError(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        String message = throwable.getClass().getSimpleName() + ": " + StrUtil.blankToDefault(throwable.getMessage(), "");
        int max = traceProperties.getMaxErrorLength();
        return message.length() <= max ? message : message.substring(0, max);
    }
}
