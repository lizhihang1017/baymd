package com.zhli.baymd.rag.core.prompt;

import cn.hutool.core.util.StrUtil;
import com.zhli.baymd.framework.convention.ChatRequest;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 场景化提示词渲染服务。
 *
 * <p>每个 LLM 调用点声明一个场景名（如 {@code query_rewrite}），并在构造 system / user 消息时
 * 通过本服务的 {@link #system} / {@link #user} 方法渲染。若 DB 中配置了该场景的覆盖提示词，
 * 则优先用覆盖内容并填充占位符；否则回退到调用点原有的默认逻辑（.st 模板或内联文本）。</p>
 *
 * <p>典型用法：</p>
 * <pre>
 * String system = promptConfigService.system("query_rewrite",
 *         () -&gt; templateLoader.render(QUERY_REWRITE_PROMPT_PATH, slots), slots);
 * String user = promptConfigService.user("query_rewrite",
 *         () -&gt; question, slots);
 * </pre>
 */
@Service
public class PromptConfigService {

    /**
     * 渲染系统提示词：有场景覆盖则用覆盖（填槽），否则用默认。
     *
     * @param scene          场景名（对应 DB prompt 分区 key）
     * @param defaultSystem  默认系统提示词提供者（无覆盖时调用）
     * @param slots          占位符映射（用于填充覆盖模板中的 {@code {slot}}）
     */
    public String system(String scene, Supplier<String> defaultSystem, Map<String, String> slots) {
        PromptSceneConfig cfg = PromptConfigStore.get(scene);
        if (cfg != null && cfg.hasSystem()) {
            return render(cfg.system(), slots);
        }
        return defaultSystem == null ? "" : defaultSystem.get();
    }

    /**
     * 渲染用户提示词：有场景覆盖则用覆盖（填槽），否则用默认。
     *
     * @param scene        场景名
     * @param defaultUser  默认用户消息提供者（无覆盖时调用）
     * @param slots        占位符映射
     */
    public String user(String scene, Supplier<String> defaultUser, Map<String, String> slots) {
        PromptSceneConfig cfg = PromptConfigStore.get(scene);
        if (cfg != null && cfg.hasUser()) {
            return render(cfg.user(), slots);
        }
        return defaultUser == null ? "" : defaultUser.get();
    }

    /**
     * 应用场景超参覆盖（temperature / maxTokens / topP）到请求构建器。
     * <p>未配置的字段保持调用点内置默认值；已配置的覆盖内置值。</p>
     */
    public void applyParams(String scene, ChatRequest.ChatRequestBuilder builder) {
        PromptSceneConfig cfg = PromptConfigStore.get(scene);
        if (cfg == null) {
            return;
        }
        if (cfg.temperature() != null) {
            builder.temperature(cfg.temperature());
        }
        if (cfg.maxTokens() != null) {
            builder.maxTokens(cfg.maxTokens());
        }
        if (cfg.topP() != null) {
            builder.topP(cfg.topP());
        }
    }

    /**
     * 场景是否配置了 system 覆盖（调用点据此决定是否补发一条 system 消息）。
     */
    public boolean hasSystem(String scene) {
        PromptSceneConfig cfg = PromptConfigStore.get(scene);
        return cfg != null && cfg.hasSystem();
    }

    /**
     * 场景是否配置了 user 覆盖。
     */
    public boolean hasUser(String scene) {
        PromptSceneConfig cfg = PromptConfigStore.get(scene);
        return cfg != null && cfg.hasUser();
    }

    private String render(String template, Map<String, String> slots) {
        String filled = PromptTemplateUtils.fillSlots(template, slots);
        return PromptTemplateUtils.cleanupPrompt(filled);
    }
}
