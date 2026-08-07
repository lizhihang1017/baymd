package com.zhli.baymd.rag.service.pipeline;

/**
 * 对话执行模式 — 决定使用哪种执行器处理用户请求。
 *
 * <p>{@link ExecutorRegistry#resolve} 按 {@code values()} 声明顺序遍历，
 * 取首个 {@code supports()==true} 者。因此<b>声明顺序即优先级</b>：
 * EMERGENCY &gt; CLARIFICATION &gt; SYSTEM_ONLY &gt; AGENT &gt; RAG。
 * RAG 作为兜底（{@code supports()} 恒 true）必须排在最后。</p>
 */
public enum ExecutionMode {

    /** 紧急分诊 — 红旗症状短路，跳过检索/Agent，直接输出急救指引 */
    EMERGENCY,

    /** 歧义澄清 — 用户问题不够明确，需要追问引导 */
    CLARIFICATION,

    /** 系统直接处理 — 纯系统能力（闲聊、问候等），不走检索 */
    SYSTEM_ONLY,

    /** Agent 模式 — LLM 自主决定多步工具调用（ReAct 循环） */
    AGENT,

    /** RAG 知识问答 — 检索 + LLM 生成（兜底） */
    RAG
}
