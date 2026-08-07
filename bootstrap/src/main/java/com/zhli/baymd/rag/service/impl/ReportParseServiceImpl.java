package com.zhli.baymd.rag.service.impl;

import cn.hutool.core.util.StrUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.zhli.baymd.framework.convention.ChatMessage;
import com.zhli.baymd.framework.convention.ChatRequest;
import com.zhli.baymd.framework.context.UserContext;
import com.zhli.baymd.core.parser.DocumentParser;
import com.zhli.baymd.core.parser.DocumentParserSelector;
import com.zhli.baymd.core.parser.ParserType;
import com.zhli.baymd.infra.chat.LLMService;
import com.zhli.baymd.infra.util.LLMResponseCleaner;
import com.zhli.baymd.rag.config.ReportProperties;
import com.zhli.baymd.rag.constant.RAGConstant;
import com.zhli.baymd.rag.core.prompt.PromptTemplateLoader;
import com.zhli.baymd.rag.dao.entity.MedicalReportDO;
import com.zhli.baymd.rag.dao.mapper.MedicalReportMapper;
import com.zhli.baymd.rag.dto.StoredFileDTO;
import com.zhli.baymd.rag.service.FileStorageService;
import com.zhli.baymd.rag.service.ReportParseService;
import com.zhli.baymd.infra.vision.VisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 报告解析服务实现。
 *
 * <p>解析分流：
 * <ul>
 *   <li>PDF：优先 Tika 提取文本层；文本过短判定为扫描件，转 VL 模型</li>
 *   <li>图片（png/jpg）：直接走 VL 模型</li>
 *   <li>无 VL 模型可用且非文本 PDF：解析失败，明确返回错误，不产生脏数据</li>
 * </ul>
 * LLM 结构化输出 JSON 容错：解析失败时降级为仅保留原文。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportParseServiceImpl implements ReportParseService {

    private final FileStorageService fileStorageService;
    private final DocumentParserSelector documentParserSelector;
    private final LLMService llmService;
    private final PromptTemplateLoader promptTemplateLoader;
    private final MedicalReportMapper medicalReportMapper;
    private final ReportProperties properties;

    /** 视觉模型可选——未配置 ai.vision.default-model 时 Bean 不存在，图片解析降级 */
    @Autowired(required = false)
    private VisionService visionService;

    @Override
    public MedicalReportDO parseAndStore(MultipartFile file) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("报告解读功能未启用");
        }
        validate(file);

        MedicalReportDO report = new MedicalReportDO();
        report.setUserId(UserContext.getUserId());
        report.setFileName(file.getOriginalFilename());
        report.setMimeType(file.getContentType());
        report.setParseStatus("PENDING");
        report.setCreateBy(UserContext.getUserId());
        report.setUpdateBy(UserContext.getUserId());

        try {
            byte[] bytes = file.getBytes();

            // 1. 存储
            StoredFileDTO stored = fileStorageService.upload(
                    properties.getBucket(), bytes,
                    file.getOriginalFilename(), file.getContentType());
            report.setStorageKey(stored.getUrl());

            // 2. 提取文本（分流）
            String rawText = extractText(bytes, file);
            report.setRawText(rawText);

            // 3. LLM 结构化（容错）
            String structured = structure(rawText);
            report.setStructured(structured);

            report.setParseStatus(StrUtil.isNotBlank(rawText) ? "SUCCESS" : "FAILED");
            if (StrUtil.isBlank(rawText)) {
                report.setErrorMessage("未能从报告中提取任何文本");
            }
        } catch (Exception e) {
            log.error("报告解析失败: {}", file.getOriginalFilename(), e);
            report.setParseStatus("FAILED");
            report.setErrorMessage(StrUtil.maxLength(e.getMessage(), 480));
        }

        medicalReportMapper.insert(report);
        log.info("报告解析完成: id={}, status={}, fileName={}",
                report.getId(), report.getParseStatus(), report.getFileName());
        return report;
    }

    @Override
    public MedicalReportDO getById(String reportId) {
        if (StrUtil.isBlank(reportId)) {
            return null;
        }
        return medicalReportMapper.selectById(reportId);
    }

    @Override
    public String buildReportContext(MedicalReportDO report) {
        if (report == null || StrUtil.isBlank(report.getRawText())) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("（用户上传的检查报告，提取文本如下）\n").append(report.getRawText());
        if (StrUtil.isNotBlank(report.getStructured())) {
            sb.append("\n\n结构化指标：\n").append(report.getStructured());
        }
        return sb.toString();
    }

    // ============================== 内部 ==============================

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
        }
        long maxBytes = (long) properties.getMaxFileSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("文件超过上限 " + properties.getMaxFileSizeMb() + "MB");
        }
        String ct = file.getContentType();
        List<String> allowed = properties.getAllowedTypes();
        if (ct == null || !allowed.contains(ct)) {
            throw new IllegalArgumentException("不支持的文件类型: " + ct + "，仅支持 " + allowed);
        }
    }

    private String extractText(byte[] bytes, MultipartFile file) {
        String mime = file.getContentType();
        String fileName = file.getOriginalFilename();
        boolean isPdf = "application/pdf".equalsIgnoreCase(mime)
                || (fileName != null && fileName.toLowerCase().endsWith(".pdf"));

        // PDF：先尝试 Tika 提取文本层
        if (isPdf) {
            String tikaText = tryTikaExtract(bytes, fileName);
            if (StrUtil.isNotBlank(tikaText) && tikaText.length() >= properties.getScanDetectMinChars()) {
                log.info("PDF 文本层提取成功 ({} 字符)，走 Tika 路径", tikaText.length());
                return tikaText;
            }
            log.info("PDF 文本层不足 ({} 字符)，判定为扫描件，转 VL 模型",
                    tikaText == null ? 0 : tikaText.length());
            return extractByVision(bytes, mime);
        }

        // 图片：直接 VL
        if (mime != null && mime.startsWith("image/")) {
            return extractByVision(bytes, mime);
        }

        // 其他类型兜底走 Tika
        return tryTikaExtract(bytes, fileName);
    }

    private String tryTikaExtract(byte[] bytes, String fileName) {
        try {
            DocumentParser parser = documentParserSelector.select(ParserType.TIKA.getType());
            try (InputStream is = new ByteArrayInputStream(bytes)) {
                return parser.extractText(is, fileName);
            }
        } catch (Exception e) {
            log.warn("Tika 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private String extractByVision(byte[] bytes, String mime) {
        if (visionService == null) {
            throw new IllegalStateException("图片/扫描件需视觉模型解析，但未配置 ai.vision.default-model");
        }
        return visionService.extractText(bytes, mime,
                "请提取这张医学检查报告图片中的全部文字信息，保持原始结构、数值与单位准确，按从上到下顺序输出。");
    }

    private String structure(String rawText) {
        if (StrUtil.isBlank(rawText)) {
            return null;
        }
        try {
            String prompt = promptTemplateLoader.render(
                    RAGConstant.REPORT_EXTRACT_PROMPT_PATH,
                    Map.of("report_text", rawText));
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(
                            ChatMessage.system(prompt),
                            ChatMessage.user("请按规则输出 JSON 数组。")
                    ))
                    .temperature(0.1)
                    .topP(0.3)
                    .thinking(false)
                    .build();
            String raw = llmService.chat(request);
            String cleaned = LLMResponseCleaner.stripMarkdownCodeFence(raw);
            // 校验为合法 JSON（数组或对象均可），非法则降级为 null
            JsonElement el = JsonParser.parseString(cleaned);
            if (!el.isJsonArray() && !el.isJsonObject()) {
                log.warn("报告结构化输出非 JSON 数组/对象，降级丢弃: {}", StrUtil.maxLength(cleaned, 200));
                return null;
            }
            return cleaned;
        } catch (Exception e) {
            log.warn("报告结构化失败，降级仅保留原文: {}", e.getMessage());
            return null;
        }
    }
}
