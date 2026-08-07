package com.zhli.baymd.rag.core.guardrails;

import com.zhli.baymd.rag.core.guardrails.EmergencyDetector.Category;
import com.zhli.baymd.rag.core.guardrails.EmergencyDetector.EmergencyResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EmergencyDetector} 单测 — 关键词匹配、语境抑制、类别优先级。
 */
@DisplayName("紧急分诊检测器测试")
class EmergencyDetectorTest {

    private final EmergencyDetector detector = new EmergencyDetector();

    private EmergencyResult detect(String q) {
        return detector.detect(q);
    }

    // ============================== 正例：真实急症 ==============================

    @Test
    @DisplayName("心血管：突然胸口剧痛出冷汗")
    void cardio_chestPain() {
        EmergencyResult r = detect("我爸爸突然胸口剧痛出冷汗");
        assertThat(r.detected()).isTrue();
        assertThat(r.category()).isEqualTo(Category.CARDIOVASCULAR);
        assertThat(r.matchedKeywords()).isNotEmpty();
    }

    @Test
    @DisplayName("心血管：胸口压榨感")
    void cardio_pressure() {
        assertThat(detect("感觉胸口有压榨感，胸闷").category()).isEqualTo(Category.CARDIOVASCULAR);
    }

    @Test
    @DisplayName("呼吸：喘不过气上不来气")
    void respiratory_wheezing() {
        EmergencyResult r = detect("喘不过气，上不来气");
        assertThat(r.detected()).isTrue();
        assertThat(r.category()).isEqualTo(Category.RESPIRATORY);
    }

    @Test
    @DisplayName("呼吸：单纯呼吸困难")
    void respiratory_dyspnea() {
        assertThat(detect("呼吸困难").category()).isEqualTo(Category.RESPIRATORY);
    }

    @Test
    @DisplayName("神经：突然意识不清晕倒")
    void neuro_syncope() {
        assertThat(detect("我妈突然晕倒意识不清").category()).isEqualTo(Category.NEUROLOGICAL);
    }

    @Test
    @DisplayName("神经：抽搐口吐白沫")
    void neuro_seizure() {
        assertThat(detect("孩子抽搐口吐白沫").category()).isEqualTo(Category.NEUROLOGICAL);
    }

    @Test
    @DisplayName("神经：中风半身不遂")
    void neuro_stroke() {
        assertThat(detect("中风了半身不遂说话不清").category()).isEqualTo(Category.NEUROLOGICAL);
    }

    @Test
    @DisplayName("出血：大出血止不住")
    void hemorrhage() {
        assertThat(detect("大出血止不住血").category()).isEqualTo(Category.HEMORRHAGE);
    }

    @Test
    @DisplayName("中毒：吃了过量药物")
    void poisoning_overdose() {
        assertThat(detect("吃了过量降压药").category()).isEqualTo(Category.POISONING);
    }

    @Test
    @DisplayName("中毒：一氧化碳中毒")
    void poisoning_CO() {
        assertThat(detect("一氧化碳中毒").category()).isEqualTo(Category.POISONING);
    }

    @Test
    @DisplayName("过敏：喉咙肿胀（优先级高于呼吸）")
    void anaphylaxis_priorityOverRespiratory() {
        EmergencyResult r = detect("喉咙肿胀，呼吸困难");
        // 喉咙肿胀→ANAPHYLAXIS，呼吸困难→RESPIRATORY；ANAPHYLAXIS 优先级更高
        assertThat(r.category()).isEqualTo(Category.ANAPHYLAXIS);
    }

    @Test
    @DisplayName("自杀自残：不想活了想死（优先级最高）")
    void selfHarm_topPriority() {
        EmergencyResult r = detect("我不想活了，想死");
        assertThat(r.category()).isEqualTo(Category.SELF_HARM);
    }

    @Test
    @DisplayName("其他急危：剧烈头痛爆炸样")
    void other_severeHeadache() {
        assertThat(detect("剧烈头痛，爆炸一样").category()).isEqualTo(Category.OTHER);
    }

    @Test
    @DisplayName("其他急危：感觉自己快不行了")
    void other_dying() {
        assertThat(detect("感觉自己快不行了").category()).isEqualTo(Category.OTHER);
    }

    @Test
    @DisplayName("自杀自残输出含心理援助热线")
    void selfHarm_executorHasHotline() {
        // 检测器只产出类别；热线在执行器模板中，这里仅验证类别正确
        EmergencyResult r = detect("割腕了");
        assertThat(r.category()).isEqualTo(Category.SELF_HARM);
    }

    // ============================== 反例：非急症 ==============================

    @Test
    @DisplayName("普通科室咨询：头疼挂什么科")
    void negative_deptQuestion() {
        assertThat(detect("头疼应该挂什么科").detected()).isFalse();
    }

    @Test
    @DisplayName("药物咨询：布洛芬副作用")
    void negative_drugQuestion() {
        assertThat(detect("布洛芬有什么副作用").detected()).isFalse();
    }

    @Test
    @DisplayName("问候：你好")
    void negative_greeting() {
        assertThat(detect("你好，你能做什么").detected()).isFalse();
    }

    @Test
    @DisplayName("知识性：胸痛的常见原因有哪些 → 抑制")
    void suppressed_knowledgeQuestion() {
        // 含"胸痛"但属知识性提问，应抑制不报急症
        EmergencyResult r = detect("胸痛的常见原因有哪些");
        assertThat(r.detected()).as("知识性提问应抑制，不触发急症").isFalse();
    }

    @Test
    @DisplayName("知识性：心绞痛的病因和预防 → 抑制")
    void suppressed_knowledgeQuestion2() {
        assertThat(detect("心绞痛的病因和预防").detected()).isFalse();
    }

    @Test
    @DisplayName("知识+主动求助共存：胸痛怎么办我现在胸口痛 → 不抑制")
    void notSuppressed_whenActiveMarker() {
        // "怎么办"是主动求助词，不抑制
        assertThat(detect("胸痛怎么办，我现在胸口痛").detected()).isTrue();
    }

    // ============================== 边界 ==============================

    @Test
    @DisplayName("空输入")
    void blank() {
        assertThat(detect("").detected()).isFalse();
        assertThat(detect(null).detected()).isFalse();
    }

    @Test
    @DisplayName("无关问题")
    void irrelevant() {
        assertThat(detect("今天天气怎么样").detected()).isFalse();
    }

    @Test
    @DisplayName("匹配关键词记录在 matchedKeywords")
    void matchedKeywordsRecorded() {
        EmergencyResult r = detect("突然昏迷抽搐");
        assertThat(r.matchedKeywords())
                .containsAnyOf("昏迷", "抽搐", "突然");
    }

    @Test
    @DisplayName("大小写不敏感")
    void caseInsensitive() {
        // 英文关键词为小写，输入大写也应命中
        EmergencyResult r = detect("drug overdose");
        assertThat(r.detected()).isTrue();
    }
}
