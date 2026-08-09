package com.zhli.baymd.core.chunk;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 问答切分配置（Dify 风格）— LLM 从文档提取问答对，每个问答对作为一个块。
 *
 * @param prompt 自定义 QA 提取提示词（可选，覆盖默认）
 */
public record QaOptions(String prompt) implements ChunkingOptions {

    public QaOptions() {
        this(null);
    }

    @Override
    public Map<String, Object> toConfigMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (prompt != null) {
            map.put("prompt", prompt);
        }
        return map;
    }
}
