package com.zhli.baymd.rag.core.prompt;

import java.util.Map;

/**
 * 场景化提示词配置存储（静态）— 从 DB {@code t_app_config.prompt} 分区加载。
 * <p>key 为 LLM 调用场景名（如 {@code query_rewrite}、{@code intent_classify}），
 * value 为 {@link PromptSceneConfig}（system + user 覆盖）。启动时由
 * {@code RuntimeConfigApplier} 填充，{@code PromptConfigService} 读取。</p>
 */
public final class PromptConfigStore {

    private static volatile Map<String, PromptSceneConfig> scenes = Map.of();

    private PromptConfigStore() {
    }

    public static void setScenes(Map<String, PromptSceneConfig> map) {
        scenes = map == null ? Map.of() : map;
    }

    public static PromptSceneConfig get(String scene) {
        return scenes.get(scene);
    }

    /** 当前所有已配置场景（供前端展示） */
    public static Map<String, PromptSceneConfig> snapshot() {
        return scenes;
    }
}
