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

package com.zhli.baymd.core.chunk;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 固定大小切分配置
 *
 * @param chunkSize   目标块大小（字符数）
 * @param overlapSize 相邻块重叠大小（字符数）
 * @param separator   自定义分隔符（可选）：提供时按分隔符切分后打包到 ≤ chunkSize；空则按固定大小+重叠切分
 */
public record FixedSizeOptions(
        int chunkSize,
        int overlapSize,
        String separator
) implements ChunkingOptions {

    public FixedSizeOptions(int chunkSize, int overlapSize) {
        this(chunkSize, overlapSize, null);
    }

    @Override
    public Map<String, Object> toConfigMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("chunkSize", chunkSize);
        map.put("overlapSize", overlapSize);
        if (separator != null) {
            map.put("separator", separator);
        }
        return map;
    }
}
