package com.zhli.baymd.rag.core.agent.tool;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zhli.baymd.framework.context.UserContext;
import com.zhli.baymd.rag.core.agent.AgentTool;
import com.zhli.baymd.rag.core.agent.AgentToolResult;
import com.zhli.baymd.rag.dao.entity.MedicalReportDO;
import com.zhli.baymd.rag.dao.mapper.MedicalReportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 报告查询工具（联动 Phase 1）— 查询当前用户历史报告的结构化指标，
 * 支持按指标名过滤与时间对比（"我的血糖比上次高吗"）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportQueryTool implements AgentTool {

    private static final String TOOL_NAME = "query_report_indicator";
    private static final String TOOL_TYPE = "system";
    private static final int MAX_REPORTS = 20;

    private final MedicalReportMapper medicalReportMapper;

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "查询用户历史检查报告中的检验指标。当用户询问报告解读、既往指标、指标变化趋势"
                + "（如“我的血糖是多少”“血糖比上次高吗”）时使用。参数 indicator 是指标名（如血糖/白细胞/血压），"
                + "limit 是最多查询的报告份数。无匹配时明确告知未收录。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "indicator", Map.of("type", "string", "description", "指标名（如血糖、白细胞、血压）"),
                        "limit", Map.of("type", "integer", "description", "最多查询报告份数（默认 5）")
                ),
                "required", List.of("indicator")
        );
    }

    @Override
    public String getType() {
        return TOOL_TYPE;
    }

    @Override
    public AgentToolResult execute(Map<String, Object> parameters) {
        long start = System.currentTimeMillis();
        try {
            String indicator = asString(parameters.get("indicator"));
            if (StrUtil.isBlank(indicator)) {
                return AgentToolResult.error(TOOL_NAME, "指标名不能为空", elapsed(start));
            }
            int limit = parameters.get("limit") instanceof Number n
                    ? Math.max(1, Math.min(n.intValue(), MAX_REPORTS)) : 5;

            String userId = UserContext.getUserId();
            List<MedicalReportDO> reports = medicalReportMapper.selectList(
                    new LambdaQueryWrapper<MedicalReportDO>()
                            .eq(MedicalReportDO::getUserId, userId)
                            .eq(MedicalReportDO::getParseStatus, "SUCCESS")
                            .orderByDesc(MedicalReportDO::getCreateTime)
                            .last("LIMIT " + limit));

            List<String> hits = new ArrayList<>();
            for (MedicalReportDO r : reports) {
                if (StrUtil.isBlank(r.getStructured())) {
                    continue;
                }
                try {
                    JsonArray arr = JsonParser.parseString(r.getStructured()).getAsJsonArray();
                    for (JsonElement el : arr) {
                        if (!el.isJsonObject()) continue;
                        JsonObject o = el.getAsJsonObject();
                        String name = o.has("name") ? o.get("name").getAsString() : "";
                        if (name.contains(indicator)) {
                            String value = o.has("value") ? o.get("value").getAsString() : "";
                            String unit = o.has("unit") ? o.get("unit").getAsString() : "";
                            String ref = o.has("refRange") ? o.get("refRange").getAsString() : "";
                            String flag = o.has("flag") ? o.get("flag").getAsString() : "";
                            String date = r.getCreateTime() == null ? ""
                                    : new SimpleDateFormat("yyyy-MM-dd").format(r.getCreateTime());
                            hits.add(String.format("%s %s%s（参考 %s，%s），报告日期 %s",
                                    name, value, StrUtil.blankToDefault(unit, ""),
                                    StrUtil.blankToDefault(ref, "—"),
                                    StrUtil.blankToDefault(flag, "正常"),
                                    StrUtil.blankToDefault(date, "未知")));
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析报告结构化字段失败: reportId={}", r.getId(), e);
                }
            }

            if (hits.isEmpty()) {
                return AgentToolResult.success(TOOL_NAME,
                        String.format("在最近的 %d 份报告中未查询到指标「%s」。", limit, indicator),
                        0, elapsed(start));
            }
            String content = "共 " + hits.size() + " 条「" + indicator + "」记录（时间倒序，最近在前）：\n\n"
                    + String.join("\n", hits);
            return AgentToolResult.success(TOOL_NAME, content, hits.size(), elapsed(start));
        } catch (Exception e) {
            log.error("查询报告指标失败", e);
            return AgentToolResult.error(TOOL_NAME, "查询失败: " + e.getMessage(), elapsed(start));
        }
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private static long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
