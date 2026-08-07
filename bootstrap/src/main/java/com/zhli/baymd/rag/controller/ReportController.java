package com.zhli.baymd.rag.controller;

import com.zhli.baymd.framework.convention.Result;
import com.zhli.baymd.framework.errorcode.BaseErrorCode;
import com.zhli.baymd.framework.exception.ClientException;
import com.zhli.baymd.framework.web.Results;
import com.zhli.baymd.rag.config.ReportProperties;
import com.zhli.baymd.rag.dao.entity.MedicalReportDO;
import com.zhli.baymd.rag.service.ReportParseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 报告解读控制器（Phase 1）— 上传化验单/检查报告图片或 PDF，解析后返回结构化预览。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportParseService reportParseService;
    private final ReportProperties reportProperties;

    /**
     * 上传报告并解析。
     */
    @PostMapping(value = "/rag/report/upload")
    public Result<MedicalReportDO> upload(@RequestParam("file") MultipartFile file) {
        if (!reportProperties.isEnabled()) {
            throw new ClientException("报告解读功能未启用", BaseErrorCode.CLIENT_ERROR);
        }
        MedicalReportDO report = reportParseService.parseAndStore(file);
        return Results.success(report);
    }

    /**
     * 查询报告详情。
     */
    @GetMapping("/rag/report/{id}")
    public Result<MedicalReportDO> get(@PathVariable String id) {
        MedicalReportDO report = reportParseService.getById(id);
        if (report == null) {
            throw new ClientException("报告不存在", BaseErrorCode.CLIENT_ERROR);
        }
        return Results.success(report);
    }
}
