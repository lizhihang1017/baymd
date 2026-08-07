package com.zhli.baymd.rag.core.agent.tool;

import com.zhli.baymd.rag.core.agent.AgentTool;
import com.zhli.baymd.rag.core.agent.AgentToolResult;
import com.zhli.baymd.rag.dao.entity.DrugInteractionDO;
import com.zhli.baymd.rag.dao.mapper.DrugInteractionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 药物相互作用查询工具。
 *
 * <p>两药名标准化（trim + 小写）后双向查表，命中返回严重程度与临床建议；
 * 查不到明确返回"未收录，建议咨询药师"，绝不编造相互作用。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DrugInteractionTool implements AgentTool {

    private static final String TOOL_NAME = "drug_interaction";
    private static final String TOOL_TYPE = "system";

    private final DrugInteractionMapper drugInteractionMapper;

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "查询两种药物之间的相互作用。当用户询问某两种药能否同服、是否有冲突、"
                + "能否一起吃时使用。参数 drug_a 与 drug_b 为两个药名（通用名/商品名均可）。"
                + "仅能查询已收录的相互作用组合，未收录时不编造，建议咨询药师。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "drug_a", Map.of("type", "string", "description", "第一个药名"),
                        "drug_b", Map.of("type", "string", "description", "第二个药名")
                ),
                "required", List.of("drug_a", "drug_b")
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
            String rawA = asString(parameters.get("drug_a"));
            String rawB = asString(parameters.get("drug_b"));
            if (rawA == null || rawA.isBlank() || rawB == null || rawB.isBlank()) {
                return AgentToolResult.error(TOOL_NAME, "两个药名都不能为空", elapsed(start));
            }
            String a = normalize(rawA);
            String b = normalize(rawB);
            if (a.equals(b)) {
                return AgentToolResult.success(TOOL_NAME,
                        "两种药物相同，无需查询相互作用。", 0, elapsed(start));
            }

            log.info("Agent 查询药物相互作用: {} + {}", rawA, rawB);
            List<DrugInteractionDO> hits = drugInteractionMapper.findPair(a, b);
            if (hits == null || hits.isEmpty()) {
                return AgentToolResult.success(TOOL_NAME,
                        String.format("未收录 %s 与 %s 的相互作用记录，无法判断。"
                                + "建议咨询药师或医生确认能否同服。", rawA, rawB),
                        0, elapsed(start));
            }

            return AgentToolResult.success(TOOL_NAME, formatHits(rawA, rawB, hits),
                    hits.size(), elapsed(start));
        } catch (Exception e) {
            log.error("药物相互作用查询失败", e);
            return AgentToolResult.error(TOOL_NAME, "查询异常: " + e.getMessage(), elapsed(start));
        }
    }

    private String formatHits(String rawA, String rawB, List<DrugInteractionDO> hits) {
        StringBuilder sb = new StringBuilder();
        sb.append("查到 ").append(rawA).append(" 与 ").append(rawB)
                .append(" 的相互作用记录共 ").append(hits.size()).append(" 条：\n\n");
        for (int i = 0; i < hits.size(); i++) {
            DrugInteractionDO h = hits.get(i);
            sb.append("### 一、").append(h.getDrugA()).append(" + ").append(h.getDrugB())
                    .append("（严重程度：").append(h.getSeverity()).append("）\n\n");
            sb.append(h.getDescription()).append("\n\n");
        }
        sb.append("> 注：以上信息仅供参考，具体用药请遵医嘱。");
        return sb.toString().trim();
    }

    /** 归一化：trim + 小写（中文药名小写为原样） */
    static String normalize(String drugName) {
        return drugName.trim().toLowerCase();
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private static long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
