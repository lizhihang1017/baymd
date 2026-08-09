package com.zhli.baymd.rag.core.agent;

import cn.hutool.core.util.StrUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具开关存储（静态）— 统一控制本地 Agent 工具与 MCP 远程工具是否可用。
 * <p>存储"启用工具名集合"；集合为空表示放行全部。启动时由 {@code RuntimeConfigApplier}
 * 从 DB {@code t_app_config.skill} 分区填充，管理后台「工具」tab 可即时切换（热生效）。</p>
 *
 * <p>DB 分区兼容两种格式：</p>
 * <ul>
 *   <li>新格式：{@code ["medical_calculator", "drug_interaction"]} — 启用工具名数组</li>
 *   <li>旧格式：{@code [{"name":"计算器","enabled":true,"tools":["medical_calculator"]}]} — 兼容</li>
 * </ul>
 */
public final class ToolSwitchStore {

    private static volatile Set<String> enabledTools = new HashSet<>();

    private ToolSwitchStore() {
    }

    public static void setEnabledTools(Set<String> tools) {
        enabledTools = tools == null ? Set.of() : tools;
    }

    public static boolean isEnabled(String toolName) {
        // 未配置任何开关时放行全部
        return enabledTools.isEmpty() || (toolName != null && enabledTools.contains(toolName));
    }

    /** 当前启用的工具名快照 */
    public static Set<String> snapshot() {
        return new HashSet<>(enabledTools);
    }

    /**
     * 解析 DB skill 分区 JSON 为启用的工具名集合。
     *
     * @param raw 支持数组字符串（新格式）或对象数组（旧格式），空则放行全部
     */
    public static Set<String> parseEnabledTools(String raw) {
        if (StrUtil.isBlank(raw)) {
            return Set.of();
        }
        try {
            Object parsed = new com.google.gson.Gson().fromJson(raw, Object.class);
            return parseEnabledTools(parsed);
        } catch (Exception e) {
            return Set.of();
        }
    }

    /** 从解析后的 JSON 对象提取启用工具名集合 */
    @SuppressWarnings("unchecked")
    public static Set<String> parseEnabledTools(Object raw) {
        Set<String> tools = new HashSet<>();
        if (raw == null) {
            return tools;
        }
        // 新格式：字符串数组
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof String s) {
                    tools.add(s);
                } else if (o instanceof Map<?, ?> m) {
                    // 旧格式：{name, enabled, tools}
                    if (Boolean.TRUE.equals(m.get("enabled"))) {
                        Object t = m.get("tools");
                        if (t instanceof List<?> tl) {
                            for (Object to : tl) {
                                if (to != null) tools.add(String.valueOf(to));
                            }
                        }
                    }
                }
            }
        }
        return tools;
    }
}
