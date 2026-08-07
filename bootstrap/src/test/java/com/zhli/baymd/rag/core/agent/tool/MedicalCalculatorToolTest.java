package com.zhli.baymd.rag.core.agent.tool;

import com.zhli.baymd.rag.core.agent.AgentToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MedicalCalculatorTool} 公式单测 — 用医学文献标准值验证。
 */
@DisplayName("医学计算器工具公式测试")
class MedicalCalculatorToolTest {

    private final MedicalCalculatorTool tool = new MedicalCalculatorTool();

    private AgentToolResult exec(String calc, Map<String, Object> params) {
        return tool.execute(Map.of("calculator", calc, "params", params));
    }

    // ============================== BMI ==============================

    @Test
    @DisplayName("BMI: 175cm/80kg ≈ 26.14 超重")
    void bmi_overweight() {
        AgentToolResult r = exec("bmi", Map.of("weight_kg", 80, "height_cm", 175));
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getContent()).contains("26.1", "超重");
    }

    @Test
    @DisplayName("BMI: 170cm/55kg ≈ 19.03 正常")
    void bmi_normal() {
        AgentToolResult r = exec("bmi", Map.of("weight_kg", 55, "height_cm", 170));
        assertThat(r.getContent()).contains("19.0", "正常");
    }

    @Test
    @DisplayName("BMI: 160cm/45kg ≈ 17.58 偏瘦")
    void bmi_underweight() {
        AgentToolResult r = exec("bmi", Map.of("weight_kg", 45, "height_cm", 160));
        assertThat(r.getContent()).contains("17.6", "偏瘦");
    }

    @Test
    @DisplayName("BMI: 170cm/90kg ≈ 31.14 肥胖")
    void bmi_obese() {
        AgentToolResult r = exec("bmi", Map.of("weight_kg", 90, "height_cm", 170));
        assertThat(r.getContent()).contains("31.1", "肥胖");
    }

    @Test
    @DisplayName("BMI 接受字符串数字参数")
    void bmi_stringNumbers() {
        AgentToolResult r = exec("bmi", Map.of("weight_kg", "70", "height_cm", "175"));
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getContent()).contains("22.9");
    }

    // ============================== BSA ==============================

    @Test
    @DisplayName("BSA(Mosteller): 175cm/70kg ≈ 1.84 m²")
    void bsa_typical() {
        AgentToolResult r = exec("bsa", Map.of("weight_kg", 70, "height_cm", 175));
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getContent()).contains("1.84");
    }

    // ============================== 最大心率 ==============================

    @Test
    @DisplayName("最大心率: 30岁 → 190 bpm(经典), 靶心率 95-162")
    void heartRateMax_30() {
        AgentToolResult r = exec("heart_rate_max", Map.of("age", 30));
        assertThat(r.getContent()).contains("190 bpm", "95-162");
    }

    // ============================== eGFR (CKD-EPI 2021) ==============================

    @Test
    @DisplayName("eGFR: 40岁男 Scr0.8mg/dL → ~115 G1 正常")
    void egfr_normal_male() {
        AgentToolResult r = exec("egfr", Map.of(
                "scr", 0.8, "scr_unit", "mg/dL", "age", 40, "sex", "male"));
        assertThat(r.isSuccess()).isTrue();
        // 实算约 114.7
        assertThat(r.getContent()).contains("G1", "正常");
        assertThat(extractNumber(r.getContent())).isBetween(110.0, 120.0);
    }

    @Test
    @DisplayName("eGFR: 60岁男 Scr4.0mg/dL → G4 重度降低")
    void egfr_severe_male() {
        AgentToolResult r = exec("egfr", Map.of(
                "scr", 4.0, "scr_unit", "mg/dL", "age", 60, "sex", "male"));
        assertThat(r.getContent()).contains("G4");
        assertThat(extractNumber(r.getContent())).isBetween(15.0, 29.0);
    }

    @Test
    @DisplayName("eGFR: 45岁女 Scr0.7mg/dL → ~109 G1 正常（女性 k=0.7 + ×1.012 系数）")
    void egfr_normal_female() {
        AgentToolResult r = exec("egfr", Map.of(
                "scr", 0.7, "scr_unit", "mg/dL", "age", 45, "sex", "female"));
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getContent()).contains("G1", "正常");
        assertThat(extractNumber(r.getContent())).isGreaterThan(90.0);
    }

    @Test
    @DisplayName("eGFR: 女性 sex 接受中文'女'")
    void egfr_female_chinese_sex() {
        AgentToolResult r = exec("egfr", Map.of(
                "scr", 0.7, "scr_unit", "mg/dL", "age", 45, "sex", "女"));
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getContent()).contains("女性");
    }

    @Test
    @DisplayName("eGFR: µmol/L 单位自动换算为 mg/dL")
    void egfr_umol_unit() {
        // 70.7 µmol/L ≈ 0.8 mg/dL
        AgentToolResult r = exec("egfr", Map.of(
                "scr", 70.7, "scr_unit", "umol/L", "age", 40, "sex", "male"));
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getContent()).contains("G1");
    }

    // ============================== 边界与错误 ==============================

    @Test
    @DisplayName("缺少 calculator → 失败")
    void missing_calculator() {
        AgentToolResult r = tool.execute(Map.of("params", Map.of()));
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getErrorMessage()).contains("calculator");
    }

    @Test
    @DisplayName("不支持的 calculator → 友好提示")
    void unsupported_calculator() {
        AgentToolResult r = exec("xxx", Map.of());
        assertThat(r.getContent()).contains("不支持的计算器");
    }

    @Test
    @DisplayName("BMI 缺少参数 → 失败并指出缺哪个")
    void bmi_missing_param() {
        AgentToolResult r = exec("bmi", Map.of("weight_kg", 70));
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getErrorMessage()).contains("身高");
    }

    @Test
    @DisplayName("eGFR 缺少 sex → 失败")
    void egfr_missing_sex() {
        AgentToolResult r = exec("egfr", Map.of("scr", 1.0, "age", 40));
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getErrorMessage()).contains("性别");
    }

    @Test
    @DisplayName("heart_rate_max 年龄超范围 → 失败")
    void heartRateMax_invalid_age() {
        AgentToolResult r = exec("heart_rate_max", Map.of("age", 200));
        assertThat(r.isSuccess()).isFalse();
    }

    /** 从 "eGFR = 114.7 mL..." 这类文本中提取首个数值 */
    private static double extractNumber(String content) {
        int eq = content.indexOf('=');
        int start = eq + 1;
        // 跳过空格
        while (start < content.length() && !Character.isDigit(content.charAt(start)) && content.charAt(start) != '.') {
            start++;
        }
        int end = start;
        while (end < content.length() && (Character.isDigit(content.charAt(end)) || content.charAt(end) == '.')) {
            end++;
        }
        return Double.parseDouble(content.substring(start, end));
    }
}
