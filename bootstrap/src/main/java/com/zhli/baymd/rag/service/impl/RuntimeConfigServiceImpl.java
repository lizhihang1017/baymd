package com.zhli.baymd.rag.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhli.baymd.infra.config.AIModelProperties;
import com.zhli.baymd.rag.config.MemoryProperties;
import com.zhli.baymd.rag.config.RAGConfigProperties;
import com.zhli.baymd.rag.config.ReActProperties;
import com.zhli.baymd.rag.config.SearchChannelProperties;
import com.zhli.baymd.rag.dao.entity.AppConfigDO;
import com.zhli.baymd.rag.dao.mapper.AppConfigMapper;
import com.zhli.baymd.rag.service.RuntimeConfigService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 运行时配置服务实现。
 *
 * <p>支持的配置分区：
 * <ul>
 *   <li>{@code ai} — 模型供应商/候选/默认模型，用 Jackson 原地更新 {@link AIModelProperties} Bean</li>
 *   <li>{@code rag} — 查询改写/记忆/检索通道/ReAct 等关键超参，映射到对应配置 Bean</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuntimeConfigServiceImpl implements RuntimeConfigService {

    private final AppConfigMapper appConfigMapper;
    private final ObjectMapper objectMapper;
    private final AIModelProperties aiModelProperties;
    private final RAGConfigProperties ragConfigProperties;
    private final MemoryProperties memoryProperties;
    private final SearchChannelProperties searchChannelProperties;
    private final ReActProperties reActProperties;

    @Override
    public String getSection(String section) {
        AppConfigDO cfg = findBySection(section);
        return cfg == null ? null : cfg.getConfigValue();
    }

    @Override
    public void saveSection(String section, String json) {
        if (StrUtil.isBlank(section) || StrUtil.isBlank(json)) {
            throw new IllegalArgumentException("配置分区与内容不能为空");
        }
        AppConfigDO existing = findBySection(section);
        if (existing != null) {
            existing.setConfigValue(json);
            existing.setUpdatedAt(new Date());
            appConfigMapper.updateById(existing);
        } else {
            AppConfigDO cfg = new AppConfigDO();
            cfg.setId(IdUtil.getSnowflakeNextIdStr());
            cfg.setSection(section);
            cfg.setConfigValue(json);
            cfg.setUpdatedAt(new Date());
            appConfigMapper.insert(cfg);
        }
        log.info("运行时配置已保存: section={}", section);
    }

    @Override
    public void applyConfig() {
        applyAiSection();
        applyRagSection();
        applyPromptOverrides();
        applySkillOverrides();
    }

    // ============================== prompt / skill 覆盖 ==============================

    private void applyPromptOverrides() {
        String json = getSection("prompt");
        if (StrUtil.isBlank(json)) {
            return;
        }
        try {
            java.util.Map<String, Object> raw = objectMapper.readValue(
                    json, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {
                    });

            // 新格式：{scene: {"system": "...", "user": "..."}} → 场景化覆盖
            java.util.Map<String, com.zhli.baymd.rag.core.prompt.PromptSceneConfig> scenes = new java.util.HashMap<>();
            // 旧格式（向后兼容）：{templatePath: "content"} → 路径覆盖
            java.util.Map<String, String> pathOverrides = new java.util.HashMap<>();

            for (var entry : raw.entrySet()) {
                Object v = entry.getValue();
                if (v instanceof java.util.Map<?, ?> m) {
                    String system = m.get("system") == null ? null : String.valueOf(m.get("system"));
                    String user = m.get("user") == null ? null : String.valueOf(m.get("user"));
                    Double temperature = toDouble(m.get("temperature"));
                    Integer maxTokens = toInteger(m.get("max_tokens"));
                    Double topP = toDouble(m.get("top_p"));
                    scenes.put(entry.getKey(),
                            new com.zhli.baymd.rag.core.prompt.PromptSceneConfig(system, user, temperature, maxTokens, topP));
                } else if (v instanceof String s) {
                    pathOverrides.put(entry.getKey(), s);
                }
            }

            com.zhli.baymd.rag.core.prompt.PromptOverrideStore.setOverrides(pathOverrides);
            com.zhli.baymd.rag.core.prompt.PromptConfigStore.setScenes(scenes);
            log.info("运行时配置已应用: section=prompt, scenes={}, paths={}",
                    scenes.keySet(), pathOverrides.keySet());
        } catch (Exception e) {
            log.warn("prompt 覆盖加载失败: {}", e.getMessage());
        }
    }

    private void applySkillOverrides() {
        String json = getSection("skill");
        if (StrUtil.isBlank(json)) {
            return;
        }
        try {
            java.util.List<java.util.Map<String, Object>> skills =
                    objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {
                    });
            com.zhli.baymd.rag.core.agent.ToolSwitchStore.setEnabledTools(
                    com.zhli.baymd.rag.core.agent.ToolSwitchStore.parseEnabledTools(skills));
            log.info("运行时配置已应用: section=skill, enabledTools={}",
                    com.zhli.baymd.rag.core.agent.ToolSwitchStore.isEnabled("__all__") ? "all" : "filtered");
        } catch (Exception e) {
            log.warn("skill 覆盖加载失败: {}", e.getMessage());
        }
    }

    // ============================== ai 分区 ==============================

    private void applyAiSection() {
        String json = getSection("ai");
        if (StrUtil.isBlank(json)) {
            return;
        }
        try {
            // Jackson 原地更新：只覆盖 JSON 中出现的字段，未出现的保留 yaml 默认值
            objectMapper.readerForUpdating(aiModelProperties).readValue(json);
            log.info("运行时配置已应用: section=ai");
        } catch (Exception e) {
            log.warn("ai 配置应用失败，使用 yaml 默认: {}", e.getMessage());
        }
    }

    // ============================== rag 分区 ==============================

    private void applyRagSection() {
        String json = getSection("rag");
        if (StrUtil.isBlank(json)) {
            return;
        }
        try {
            RagRuntimeConfig cfg = objectMapper.readValue(json, RagRuntimeConfig.class);

            if (cfg.getQueryRewrite() != null && cfg.getQueryRewrite().getEnabled() != null) {
                ragConfigProperties.setQueryRewriteEnabled(cfg.getQueryRewrite().getEnabled());
            }
            if (cfg.getMemory() != null) {
                if (cfg.getMemory().getStrategy() != null) {
                    memoryProperties.setStrategy(cfg.getMemory().getStrategy());
                }
                if (cfg.getMemory().getHistoryKeepTurns() != null) {
                    memoryProperties.setHistoryKeepTurns(cfg.getMemory().getHistoryKeepTurns());
                }
                if (cfg.getMemory().getSummaryStartTurns() != null) {
                    memoryProperties.setSummaryStartTurns(cfg.getMemory().getSummaryStartTurns());
                }
                if (cfg.getMemory().getSummaryEnabled() != null) {
                    memoryProperties.setSummaryEnabled(cfg.getMemory().getSummaryEnabled());
                }
                if (cfg.getMemory().getSummaryMaxChars() != null) {
                    memoryProperties.setSummaryMaxChars(cfg.getMemory().getSummaryMaxChars());
                }
                if (cfg.getMemory().getTitleMaxLength() != null) {
                    memoryProperties.setTitleMaxLength(cfg.getMemory().getTitleMaxLength());
                }
            }
            if (cfg.getSearch() != null) {
                if (cfg.getSearch().getDefaultTopK() != null) {
                    searchChannelProperties.setDefaultTopK(cfg.getSearch().getDefaultTopK());
                }
                if (cfg.getSearch().getVectorGlobalConfidenceThreshold() != null) {
                    searchChannelProperties.getChannels().getVectorGlobal()
                            .setConfidenceThreshold(cfg.getSearch().getVectorGlobalConfidenceThreshold());
                }
                if (cfg.getSearch().getIntentDirectedMinIntentScore() != null) {
                    searchChannelProperties.getChannels().getIntentDirected()
                            .setMinIntentScore(cfg.getSearch().getIntentDirectedMinIntentScore());
                }
            }
            if (cfg.getReact() != null) {
                if (cfg.getReact().getEnabled() != null) {
                    reActProperties.setEnabled(cfg.getReact().getEnabled());
                }
                if (cfg.getReact().getMaxIterations() != null) {
                    reActProperties.setMaxIterations(cfg.getReact().getMaxIterations());
                }
            }
            log.info("运行时配置已应用: section=rag");
        } catch (Exception e) {
            log.warn("rag 配置应用失败，使用 yaml 默认: {}", e.getMessage());
        }
    }

    private AppConfigDO findBySection(String section) {
        return appConfigMapper.selectOne(new LambdaQueryWrapper<AppConfigDO>()
                .eq(AppConfigDO::getSection, section)
                .last("LIMIT 1"));
    }

    private static Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return null; }
    }

    private static Integer toInteger(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return null; }
    }

    // ============================== rag 配置 DTO ==============================

    @Data
    public static class RagRuntimeConfig {
        private QueryRewrite queryRewrite;
        private Memory memory;
        private Search search;
        private React react;
    }

    @Data
    public static class QueryRewrite {
        private Boolean enabled;
    }

    @Data
    public static class Memory {
        private String strategy;
        private Integer historyKeepTurns;
        private Integer summaryStartTurns;
        private Boolean summaryEnabled;
        private Integer summaryMaxChars;
        private Integer titleMaxLength;
    }

    @Data
    public static class Search {
        private Integer defaultTopK;
        private Double vectorGlobalConfidenceThreshold;
        private Double intentDirectedMinIntentScore;
    }

    @Data
    public static class React {
        private Boolean enabled;
        private Integer maxIterations;
    }
}
