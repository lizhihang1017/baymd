package com.zhli.baymd.rag.core.prompt;

/**
 * LLM 调用场景名常量 — 对应 DB {@code t_app_config.prompt} 分区的 key。
 *
 * <p>每个常量代表一次独立的 LLM 调用，可在管理后台单独配置其 system / user 提示词。
 * 命名规约：{@code <域>_<动作>}。</p>
 */
public final class PromptScenes {

    private PromptScenes() {
    }

    // ==================== RAG 流水线 ====================

    /** 查询改写 + 多问句拆分 */
    public static final String QUERY_REWRITE = "query_rewrite";

    /** 意图分类 */
    public static final String INTENT_CLASSIFY = "intent_classify";

    /** 歧义二次确认（LLM 判断是否存在品类歧义） */
    public static final String AMBIGUITY_CHECK = "ambiguity_check";

    /** RAG 检索后回答（KB 单意图 / MCP / 混合） */
    public static final String RAG_ANSWER = "rag_answer";

    /** 纯系统闲聊直答（SYSTEM_ONLY 执行器） */
    public static final String SYSTEM_ONLY = "system_only";

    /** 引导式问答选项生成 */
    public static final String GUIDANCE = "guidance";

    // ==================== Agent ====================

    /** ReAct Agent 主循环（系统提示词 = 角色 + 工具列表 + 规则） */
    public static final String AGENT_MAIN = "agent_main";

    /** MCP 工具参数提取 */
    public static final String MCP_PARAM_EXTRACT = "mcp_param_extract";

    // ==================== 记忆 / 摘要 ====================

    /** 对话长时摘要生成 */
    public static final String CONVERSATION_SUMMARY = "conversation_summary";

    /** 会话标题生成 */
    public static final String CONVERSATION_TITLE = "conversation_title";

    /** 事实抽取（用户记忆） */
    public static final String MEMORY_FACT = "memory_fact";

    /** 情节抽取（用户记忆） */
    public static final String MEMORY_EPISODE = "memory_episode";

    /** 事实合并去重（记忆演进） */
    public static final String MEMORY_FACT_MERGE = "memory_fact_merge";

    /** 用户画像生成 */
    public static final String MEMORY_PROFILE = "memory_profile";

    // ==================== 报告 / 随访 ====================

    /** 体检报告解析 */
    public static final String REPORT_EXTRACT = "report_extract";

    /** 随访方案生成 */
    public static final String FOLLOWUP_PLAN = "followup_plan";

    /** 随访邮件内容生成 */
    public static final String FOLLOWUP_GENERATE = "followup_generate";

    /** 问答质量评估（eval） */
    public static final String QUALITY_EVALUATE = "quality_evaluate";

    /** PDF 解析前格式校验 */
    public static final String PDF_FORMAT_GUARD = "pdf_format_guard";

    // ==================== 摄取 ====================

    /** QA 切块（LLM 提取问答对） */
    public static final String QA_CHUNK = "qa_chunk";

    /** 增强节点（LLM 元数据提取） */
    public static final String ENRICHER = "enricher";
}
