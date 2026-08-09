package com.zhli.baymd.rag.core.prompt;

import cn.hutool.core.util.StrUtil;

/**
 * 单个 LLM 调用场景的提示词配置 — system / user 提示词 + 超参数（温度/最大token/topP）均可选。
 * <p>来源：DB {@code t_app_config.prompt} 分区，格式
 * {@code {"scene": {"system": "...", "user": "...", "temperature": 0.1, "max_tokens": 512, "top_p": 0.3}}}。
 * system/user 支持模板占位符（如 {@code {question}}），渲染时由调用点传入 slots 填充。
 * 超参为 null 时使用调用点内置默认值。</p>
 */
public record PromptSceneConfig(String system, String user, Double temperature, Integer maxTokens, Double topP) {

    public PromptSceneConfig(String system, String user) {
        this(system, user, null, null, null);
    }

    public boolean hasSystem() {
        return StrUtil.isNotBlank(system);
    }

    public boolean hasUser() {
        return StrUtil.isNotBlank(user);
    }

    public boolean hasParams() {
        return temperature != null || maxTokens != null || topP != null;
    }
}
