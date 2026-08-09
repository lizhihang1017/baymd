package com.zhli.baymd.core.chunk;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 父子切分配置（Dify 风格）。
 *
 * @param parentMaxChars 父块最大长度（字符）
 * @param childMaxChars  子块最大长度（字符）
 * @param separator      自定义分隔符（可选，按分隔符切分）
 */
public record ParentChildOptions(
        int parentMaxChars,
        int childMaxChars,
        String separator
) implements ChunkingOptions {

    @Override
    public Map<String, Object> toConfigMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("parentMaxChars", parentMaxChars);
        map.put("childMaxChars", childMaxChars);
        if (separator != null) {
            map.put("separator", separator);
        }
        return map;
    }
}
