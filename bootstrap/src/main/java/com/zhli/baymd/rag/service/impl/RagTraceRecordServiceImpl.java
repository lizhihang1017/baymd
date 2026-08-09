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

package com.zhli.baymd.rag.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhli.baymd.rag.dao.entity.RagTraceNodeDO;
import com.zhli.baymd.rag.dao.entity.RagTraceRunDO;
import com.zhli.baymd.rag.dao.mapper.RagTraceNodeMapper;
import com.zhli.baymd.rag.dao.mapper.RagTraceRunMapper;
import com.zhli.baymd.rag.service.RagTraceRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * RAG Trace 记录服务实现
 */
@Service
@RequiredArgsConstructor
public class RagTraceRecordServiceImpl implements RagTraceRecordService {

    private final RagTraceRunMapper runMapper;
    private final RagTraceNodeMapper nodeMapper;

    @Override
    public void startRun(RagTraceRunDO run) {
        runMapper.insert(run);
    }

    @Override
    public void finishRun(String traceId, String status, String errorMessage, Date endTime, long durationMs) {
        finishRun(traceId, status, errorMessage, endTime, durationMs, null);
    }

    @Override
    public void finishRun(String traceId, String status, String errorMessage, Date endTime, long durationMs, String extraData) {
        RagTraceRunDO update = RagTraceRunDO.builder()
                .status(status)
                .errorMessage(errorMessage)
                .endTime(endTime)
                .durationMs(durationMs)
                .extraData(extraData)
                .build();
        runMapper.update(update, Wrappers.lambdaUpdate(RagTraceRunDO.class)
                .eq(RagTraceRunDO::getTraceId, traceId));
    }

    @Override
    public void startNode(RagTraceNodeDO node) {
        nodeMapper.insert(node);
    }

    @Override
    public void appendRunExtraData(String traceId, String extraDataJson) {
        RagTraceRunDO run = runMapper.selectOne(Wrappers.lambdaQuery(RagTraceRunDO.class)
                .eq(RagTraceRunDO::getTraceId, traceId));
        if (run == null) {
            return;
        }
        // 合并 JSON 对象: 保留已有字段（question）,追加新字段（answer）
        String merged;
        try {
            com.fasterxml.jackson.databind.JsonNode base = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                    .readTree(run.getExtraData() == null ? "{}" : run.getExtraData());
            com.fasterxml.jackson.databind.JsonNode extra = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                    .readTree(extraDataJson);
            ((com.fasterxml.jackson.databind.node.ObjectNode) base).setAll((com.fasterxml.jackson.databind.node.ObjectNode) extra);
            merged = base.toString();
        } catch (Exception e) {
            merged = run.getExtraData() == null ? extraDataJson : run.getExtraData() + "," + extraDataJson;
        }
        runMapper.update(RagTraceRunDO.builder().extraData(merged).build(),
                Wrappers.lambdaUpdate(RagTraceRunDO.class).eq(RagTraceRunDO::getTraceId, traceId));
    }

    @Override
    public void finishNode(String traceId, String nodeId, String status, String errorMessage, Date endTime, long durationMs) {
        finishNode(traceId, nodeId, status, errorMessage, endTime, durationMs, null);
    }

    @Override
    public void finishNode(String traceId, String nodeId, String status, String errorMessage, Date endTime, long durationMs, String extraData) {
        RagTraceNodeDO update = RagTraceNodeDO.builder()
                .status(status)
                .errorMessage(errorMessage)
                .endTime(endTime)
                .durationMs(durationMs)
                .extraData(extraData)
                .build();
        nodeMapper.update(update, Wrappers.lambdaUpdate(RagTraceNodeDO.class)
                .eq(RagTraceNodeDO::getTraceId, traceId)
                .eq(RagTraceNodeDO::getNodeId, nodeId));
    }
}
