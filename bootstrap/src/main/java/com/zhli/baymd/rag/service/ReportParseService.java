package com.zhli.baymd.rag.service;

import com.zhli.baymd.rag.dao.entity.MedicalReportDO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 报告解析服务 — 上传报告 → 提取文本（Tika/VL 分流）→ LLM 结构化 → 入库。
 */
public interface ReportParseService {

    /**
     * 解析并存储用户上传的报告。
     *
     * @param file 上传的图片/PDF
     * @return 持久化的报告记录（含结构化结果与解析状态）
     */
    MedicalReportDO parseAndStore(MultipartFile file);

    /**
     * 按 ID 获取报告。
     */
    MedicalReportDO getById(String reportId);

    /**
     * 构造注入对话上下文用的报告文本。
     *
     * @param report 报告记录（可为 null）
     * @return 上下文片段，null 表示无报告
     */
    String buildReportContext(MedicalReportDO report);
}
