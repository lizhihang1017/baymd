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

import com.zhli.baymd.rag.dao.entity.RagTraceNodeDO;
import com.zhli.baymd.rag.dao.entity.RagTraceRunDO;

import java.util.Date;

/**
 * RAG Trace 记录服务
 */
public interface RagTraceRecordService {

    void startRun(RagTraceRunDO run);

    void finishRun(String traceId, String status, String errorMessage, Date endTime, long durationMs);

    void finishRun(String traceId, String status, String errorMessage, Date endTime, long durationMs, String extraData);

    void startNode(RagTraceNodeDO node);

    void finishNode(String traceId, String nodeId, String status, String errorMessage, Date endTime, long durationMs);

    void finishNode(String traceId, String nodeId, String status, String errorMessage, Date endTime, long durationMs, String extraData);

    /**
     * 追加 run 的 extraData（以 JSON 对象合并,如流式完成后补写 answer）
     */
    void appendRunExtraData(String traceId, String extraDataJson);
}
