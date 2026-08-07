package com.zhli.baymd.rag.core.guardrails;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 紧急分诊检测器（关键词级，第一级零成本检测）。
 *
 * <p>对原始用户问题做关键词/口语变体匹配，命中即判定为紧急，由 {@code EmergencyExecutor}
 * 短路接管，跳过检索与 Agent，直接输出急救指引。</p>
 *
 * <p>关键词表按类别组织，覆盖心血管/呼吸/意识/出血/中毒/过敏/自杀自残等红旗症状，
 * 含常见口语变体（如"喘不上气""快不行了""胸口压榨"）。"宁可误报"——
 * 遗漏一个红旗症状后果严重，误报只是多一句就医提醒。</p>
 */
@Slf4j
@Component
public class EmergencyDetector {

    /** 紧急类别 */
    public enum Category {
        /** 心血管急症（胸痛/心悸/压榨感） */
        CARDIOVASCULAR,
        /** 呼吸急症（呼吸困难/喘不上气） */
        RESPIRATORY,
        /** 意识/神经急症（昏迷/抽搐/意识不清） */
        NEUROLOGICAL,
        /** 严重出血 */
        HEMORRHAGE,
        /** 中毒/药物过量 */
        POISONING,
        /** 过敏性休克 */
        ANAPHYLAXIS,
        /** 自杀/自残倾向 */
        SELF_HARM,
        /** 其他急危（剧烈头痛/中风征象等） */
        OTHER
    }

    /** 关键词 → 类别 */
    private static final List<KeywordEntry> KEYWORDS = List.of(
            // ---- 心血管 ----
            entry(Category.CARDIOVASCULAR, "胸痛", "胸口痛", "胸口剧痛", "胸部剧痛", "胸口疼",
                    "胸闷", "胸口闷", "压榨", "压榨感", "胸口压榨", "心绞痛", "心脏痛",
                    "心前区痛", "胸骨后疼痛", "放射到左肩", "心悸", "心跳特别快", "心跳过速"),
            // ---- 呼吸 ----
            entry(Category.RESPIRATORY, "呼吸困难", "喘不上气", "喘不过气", "喘不过来气",
                    "喘不上来", "憋气", "窒息", "上不来气", "气短", "呼吸急促",
                    "无法呼吸", "喘息", "口唇发紫", "口唇青紫"),
            // ---- 意识/神经 ----
            entry(Category.NEUROLOGICAL, "意识不清", "意识丧失", "昏迷", "晕厥", "晕倒",
                    "抽搐", "口吐白沫", "翻白眼", "半身不遂", "半边身体不能动",
                    "说话不清", "口眼歪斜", "面瘫", "突然不会说话", "一侧肢体无力"),
            // ---- 出血 ----
            entry(Category.HEMORRHAGE, "大出血", "止不住血", "血流不止", "呕血", "咳血",
                    "便血", "尿血", "阴道大出血", "喷射状出血"),
            // ---- 中毒/过量 ----
            entry(Category.POISONING, "中毒", "药物过量", "吃多了药", "吃了过量",
                    "误食", "一氧化碳中毒", "煤气中毒", "农药中毒", "食物中毒",
                    "overdose", "overdosed", "poisoning", "poisoned"),
            // ---- 过敏性休克 ----
            entry(Category.ANAPHYLAXIS, "过敏性休克", "严重过敏", "喉头水肿", "喉咙肿胀",
                    "全身皮疹伴呼吸困难", "过敏性休克"),
            // ---- 自杀/自残 ----
            entry(Category.SELF_HARM, "自杀", "轻生", "不想活", "想死", "活不下去",
                    "割腕", "割自己", "结束生命", "了结自己", "吃药自杀", "跳楼",
                    "吃药片自杀"),
            // ---- 其他急危 ----
            entry(Category.OTHER, "剧烈头痛", "爆炸样头痛", "雷击样头痛", "突然剧烈头痛",
                    "中风", "脑卒中", "高烧不退", "抽风", "小儿抽风", "惊厥",
                    "宝宝抽搐", "持续抽搐", "瞳孔不等大", "颈项强直",
                    "快不行了", "感觉自己快不行了", "濒死感", "要死了", "快死了")
    );

    private record KeywordEntry(Category category, List<String> keywords) {}

    private static KeywordEntry entry(Category c, String... kws) {
        return new KeywordEntry(c, Arrays.asList(kws));
    }

    /** 知识性提问标记词（出现这些词更可能是询问知识而非正在发病） */
    private static final List<String> KNOWLEDGE_MARKERS = List.of(
            "原因", "是什么", "什么是", "常见", "有哪些", "为什么", "如何预防",
            "怎么预防", "预防", "病因", "区别", "表现", "如何治疗", "怎么治疗",
            "治疗方法", "科普"
    );

    /** 主动求助/正在发病标记词（出现这些词表明是真实急症而非知识询问） */
    private static final List<String> ACTIVE_MARKERS = List.of(
            "突然", "现在", "正在", "感觉", "难受", "不舒服", "我已经",
            "我正", "刚刚", "刚才", "救命", "怎么办", "急", "我妈", "我爸",
            "我孩", "宝宝", "我老", "家人"
    );

    /**
     * 检测用户问题是否含红旗症状。
     *
     * @param question 原始用户问题（未改写）
     * @return 检测结果，{@code detected=false} 表示非紧急
     */
    public EmergencyResult detect(String question) {
        if (question == null || question.isBlank()) {
            return EmergencyResult.none();
        }
        String text = question.toLowerCase();
        Set<Category> hitCategories = new LinkedHashSet<>();
        List<String> matched = new ArrayList<>();
        for (KeywordEntry e : KEYWORDS) {
            for (String kw : e.keywords) {
                if (text.contains(kw.toLowerCase())) {
                    hitCategories.add(e.category);
                    matched.add(kw);
                }
            }
        }
        if (hitCategories.isEmpty()) {
            return EmergencyResult.none();
        }
        // 语境抑制：若问题明显是知识性提问（含知识标记词）且无主动求助信号，
        // 判定为询问知识而非真实急症，避免"胸痛的常见原因有哪些"误报为紧急。
        if (containsAny(text, KNOWLEDGE_MARKERS) && !containsAny(text, ACTIVE_MARKERS)) {
            log.info("红旗关键词命中但判定为知识性提问（抑制）: matched={}, question={}",
                    matched, truncate(question, 60));
            return EmergencyResult.none();
        }
        // 多类别命中时优先取风险最高的类别（自杀自残 > 心血管 > 呼吸 > 神经 > 出血 > 中毒 > 过敏 > 其他）
        Category primary = priorityCategory(hitCategories);
        log.warn("检测到红旗症状: primary={}, categories={}, matched={}, question={}",
                primary, hitCategories, matched, truncate(question, 60));
        return new EmergencyResult(true, primary, matched);
    }

    private boolean containsAny(String text, List<String> markers) {
        for (String m : markers) {
            if (text.contains(m.toLowerCase())) return true;
        }
        return false;
    }

    private Category priorityCategory(Set<Category> cats) {
        Category[] order = {
                Category.SELF_HARM, Category.ANAPHYLAXIS, Category.CARDIOVASCULAR,
                Category.RESPIRATORY, Category.NEUROLOGICAL, Category.HEMORRHAGE,
                Category.POISONING, Category.OTHER
        };
        for (Category c : order) {
            if (cats.contains(c)) return c;
        }
        return Category.OTHER;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /**
     * 紧急检测结果
     */
    public record EmergencyResult(boolean detected, Category category, List<String> matchedKeywords) {
        public static EmergencyResult none() {
            return new EmergencyResult(false, null, List.of());
        }
    }
}
