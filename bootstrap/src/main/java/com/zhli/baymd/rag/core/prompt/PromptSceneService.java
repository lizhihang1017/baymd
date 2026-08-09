package com.zhli.baymd.rag.core.prompt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 场景提示词解析服务 — 为管理后台提供每个 LLM 调用场景的：
 * <ul>
 *   <li>默认 system / user 提示词文本（.st 模板内容或内联默认），供前端默认填充</li>
 *   <li>默认超参数（当前代码内置值），供前端展示/修改</li>
 *   <li>当前 DB 覆盖配置</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptSceneService {

    private final PromptTemplateLoader templateLoader;

    /**
     * 场景默认视图：默认提示词文本（未填充槽位的原始模板/内联）+ 默认超参。
     */
    public record SceneDefaultView(String scene, String label, String description, List<String> slots,
                                   String defaultSystem, String defaultUser,
                                   Double defaultTemperature, Integer defaultMaxTokens, Double defaultTopP,
                                   PromptSceneConfig current) {
    }

    public List<SceneDefaultView> listDefaultViews() {
        return PromptSceneCatalog.catalog().stream()
                .map(meta -> toView(meta, PromptConfigStore.get(meta.scene())))
                .toList();
    }

    public SceneDefaultView getDefaultView(String scene) {
        PromptSceneCatalog.SceneMeta meta = PromptSceneCatalog.catalog().stream()
                .filter(m -> m.scene().equals(scene))
                .findFirst()
                .orElse(null);
        if (meta == null) {
            return null;
        }
        return toView(meta, PromptConfigStore.get(scene));
    }

    private SceneDefaultView toView(PromptSceneCatalog.SceneMeta meta, PromptSceneConfig current) {
        String defaultSystem = resolve(meta.defaultSystemTemplate(), meta.inlineSystem());
        String defaultUser = resolve(meta.defaultUserTemplate(), meta.inlineUser());
        return new SceneDefaultView(
                meta.scene(), meta.label(), meta.description(), meta.slots(),
                defaultSystem, defaultUser,
                meta.defaultTemperature(), meta.defaultMaxTokens(), meta.defaultTopP(),
                current);
    }

    private String resolve(String templatePath, String inline) {
        if (templatePath != null) {
            try {
                return templateLoader.load(templatePath);
            } catch (Exception e) {
                log.warn("场景默认提示词模板加载失败: path={}", templatePath, e);
                return "";
            }
        }
        return inline == null ? "" : inline;
    }
}
