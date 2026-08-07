package com.zhli.baymd.rag.core.agent.tool;

import com.zhli.baymd.rag.core.agent.AgentTool;
import com.zhli.baymd.rag.core.agent.AgentToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 医学计算器工具 — 提供 BMI / eGFR(CKD-EPI 2021) / BSA(Mosteller) / 最大心率
 * 等常用医学指标计算，结果附带临床解读区间。
 *
 * <p>纯本地计算，无外部依赖、无 LLM 调用，结果确定可单测。</p>
 */
@Slf4j
@Component
public class MedicalCalculatorTool implements AgentTool {

    private static final String TOOL_NAME = "medical_calculator";
    private static final String TOOL_TYPE = "system";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "医学指标计算器。支持：bmi（体质指数，需 weight_kg + height_cm）、"
                + "egfr（肾小球滤过率 CKD-EPI 2021，需 scr 血肌酐 + scr_unit[mg/dL 或 umol/L] + age + sex[male/female]）、"
                + "bsa（体表面积 Mosteller，需 weight_kg + height_cm）、"
                + "heart_rate_max（最大心率估算，需 age）。"
                + "当用户给出身高/体重/年龄/血肌酐等数值并询问指标计算结果时使用。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "calculator", Map.of(
                                "type", "string",
                                "enum", List.of("bmi", "egfr", "bsa", "heart_rate_max"),
                                "description", "计算器类型"
                        ),
                        "params", Map.of(
                                "type", "object",
                                "description", "计算参数：bmi/bsa 需要 weight_kg(数字) 与 height_cm(数字)；"
                                        + "egfr 需要 scr(数字) + scr_unit(mg/dL 或 umol/L) + age(数字) + sex(male/female)；"
                                        + "heart_rate_max 需要 age(数字)"
                        )
                ),
                "required", List.of("calculator", "params")
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
            String calculator = asString(parameters.get("calculator"));
            if (calculator == null || calculator.isBlank()) {
                return AgentToolResult.error(TOOL_NAME, "参数 calculator 不能为空", elapsed(start));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) parameters.get("params");
            if (params == null) {
                params = Map.of();
            }

            String result = switch (calculator.toLowerCase()) {
                case "bmi" -> calcBmi(params);
                case "egfr" -> calcEgfr(params);
                case "bsa" -> calcBsa(params);
                case "heart_rate_max", "heartratemax", "heart_rate" -> calcHeartRateMax(params);
                default -> "不支持的计算器: " + calculator + "。可选: bmi / egfr / bsa / heart_rate_max";
            };
            return AgentToolResult.success(TOOL_NAME, result, 0, elapsed(start));
        } catch (Exception e) {
            log.error("医学计算器执行失败", e);
            return AgentToolResult.error(TOOL_NAME, "计算失败: " + e.getMessage(), elapsed(start));
        }
    }

    // ============================== BMI ==============================

    private String calcBmi(Map<String, Object> params) {
        double weightKg = requireNumber(params, "weight_kg", "体重(weight_kg)");
        double heightCm = requireNumber(params, "height_cm", "身高(height_cm)");
        if (weightKg <= 0 || heightCm <= 0) {
            throw new IllegalArgumentException("体重和身高必须为正数");
        }
        double heightM = heightCm / 100.0;
        double bmi = weightKg / (heightM * heightM);

        String level;
        if (bmi < 18.5) {
            level = "偏瘦";
        } else if (bmi < 24) {
            level = "正常";
        } else if (bmi < 28) {
            level = "超重";
        } else {
            level = "肥胖";
        }
        return String.format("BMI = %.1f（%s，中国标准：<18.5 偏瘦 / 18.5-23.9 正常 / 24-27.9 超重 / ≥28 肥胖）",
                bmi, level);
    }

    // ============================== eGFR (CKD-EPI 2021, race-free) ==============================

    private String calcEgfr(Map<String, Object> params) {
        double scr = requireNumber(params, "scr", "血肌酐(scr)");
        String scrUnit = asString(params.get("scr_unit"));
        if (scrUnit == null || scrUnit.isBlank()) {
            scrUnit = "mg/dL";
        }
        // 统一换算到 mg/dL
        double scrMgDl;
        if (scrUnit.toLowerCase().startsWith("umol") || scrUnit.toLowerCase().startsWith("µmol")) {
            scrMgDl = scr / 88.4;
        } else {
            scrMgDl = scr; // 默认 mg/dL
        }
        double age = requireNumber(params, "age", "年龄(age)");
        String sex = asString(params.get("sex"));
        if (sex == null || sex.isBlank()) {
            throw new IllegalArgumentException("缺少性别参数 sex (male/female)");
        }
        boolean female = sex.toLowerCase().startsWith("f") || sex.toLowerCase().contains("女");

        // CKD-EPI 2021 race-free
        double k = female ? 0.7 : 0.9;
        double alpha = female ? -0.241 : -0.302;
        double scrOverK = scrMgDl / k;
        double minTerm = Math.pow(Math.min(scrOverK, 1.0), alpha);
        double maxTerm = Math.pow(Math.max(scrOverK, 1.0), -1.200);
        double egfr = 142.0 * minTerm * maxTerm * Math.pow(0.9938, age);
        if (female) {
            egfr *= 1.012;
        }

        String stage;
        if (egfr >= 90) {
            stage = "G1 正常或高";
        } else if (egfr >= 60) {
            stage = "G2 轻度降低";
        } else if (egfr >= 45) {
            stage = "G3a 轻中度降低";
        } else if (egfr >= 30) {
            stage = "G3b 中重度降低";
        } else if (egfr >= 15) {
            stage = "G4 重度降低";
        } else {
            stage = "G5 肾衰竭";
        }
        return String.format("eGFR = %.1f mL/min/1.73m²（%s，KDIGO 分期：≥90 G1 / 60-89 G2 / 45-59 G3a / "
                        + "30-44 G3b / 15-29 G4 / <15 G5）。公式：CKD-EPI 2021（不含种族变量），"
                        + "血肌酐 %.2f mg/dL，%s，年龄 %.0f 岁。",
                egfr, stage, scrMgDl, female ? "女性" : "男性", age);
    }

    // ============================== BSA (Mosteller) ==============================

    private String calcBsa(Map<String, Object> params) {
        double weightKg = requireNumber(params, "weight_kg", "体重(weight_kg)");
        double heightCm = requireNumber(params, "height_cm", "身高(height_cm)");
        if (weightKg <= 0 || heightCm <= 0) {
            throw new IllegalArgumentException("体重和身高必须为正数");
        }
        double bsa = Math.sqrt(heightCm * weightKg / 3600.0);
        return String.format("BSA = %.2f m²（Mosteller 公式 = √(身高cm × 体重kg / 3600)）", bsa);
    }

    // ============================== 最大心率 ==============================

    private String calcHeartRateMax(Map<String, Object> params) {
        double age = requireNumber(params, "age", "年龄(age)");
        if (age <= 0 || age > 130) {
            throw new IllegalArgumentException("年龄参数不合理（应在 1-130 之间）");
        }
        int hrMaxClassic = (int) Math.round(220 - age);
        double hrMaxTanaka = 208 - 0.7 * age;
        // 中等强度运动靶心率区间 50-85%
        int lower = (int) Math.round(hrMaxClassic * 0.50);
        int upper = (int) Math.round(hrMaxClassic * 0.85);
        return String.format("最大心率 ≈ %d bpm（经典公式 220-年龄）；"
                        + "Tanaka 公式估算 ≈ %.0f bpm。中等强度运动靶心率区间约 %d-%d bpm（最大心率的 50%%-85%%）。",
                hrMaxClassic, hrMaxTanaka, lower, upper);
    }

    // ============================== 工具方法 ==============================

    private static long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private static double requireNumber(Map<String, Object> params, String key, String displayName) {
        Object v = params.get(key);
        if (v == null) {
            throw new IllegalArgumentException("缺少参数 " + displayName);
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        String s = String.valueOf(v).trim();
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(displayName + " 不是合法数字: " + s);
        }
    }
}
