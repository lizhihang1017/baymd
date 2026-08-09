package com.zhli.baymd.rag.core.prompt;

import java.util.Map;

/**
 * 提示词覆盖存储（静态）— 从 DB {@code t_app_config.prompt} 分区加载，覆盖 .st 模板。
 * <p>启动时由 {@code RuntimeConfigApplier} 填充，{@code PromptTemplateLoader} 读取。
 * key 为模板路径（如 {@code prompt/intent-classifier.st}）。</p>
 */
public final class PromptOverrideStore {

    private static volatile Map<String, String> overrides = Map.of();

    private PromptOverrideStore() {
    }

    public static void setOverrides(Map<String, String> map) {
        overrides = map == null ? Map.of() : map;
    }

    public static String get(String path) {
        return overrides.get(path);
    }
}
