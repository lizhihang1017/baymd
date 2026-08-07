package com.zhli.baymd.infra.vision;

/**
 * 视觉（多模态）解析服务 — 用多模态大模型从图片中提取文本/结构化信息。
 *
 * <p>用于报告解读场景：用户上传化验单/检查报告图片，本服务调用 VL 模型
 * （如 qwen-vl-plus）提取文本内容，再交给 LLM 结构化。</p>
 *
 * <p>实现应为 {@code @ConditionalOnProperty(prefix="ai.vision", name="default-model")}，
 * 未配置视觉模型时 Bean 不存在，调用方需容忍降级。</p>
 */
public interface VisionService {

    /**
     * 从图片提取文本。
     *
     * @param imageBytes 图片二进制
     * @param mimeType   图片 MIME（image/png、image/jpeg 等）
     * @param prompt     提取指令（如"提取这张化验单中的所有指标"）
     * @return 模型返回的文本
     */
    String extractText(byte[] imageBytes, String mimeType, String prompt);
}
