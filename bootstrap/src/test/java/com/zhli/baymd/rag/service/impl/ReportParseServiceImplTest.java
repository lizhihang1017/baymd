package com.zhli.baymd.rag.service.impl;

import com.zhli.baymd.core.parser.DocumentParser;
import com.zhli.baymd.core.parser.DocumentParserSelector;
import com.zhli.baymd.framework.convention.ChatMessage;
import com.zhli.baymd.framework.convention.ChatRequest;
import com.zhli.baymd.framework.context.LoginUser;
import com.zhli.baymd.framework.context.UserContext;
import com.zhli.baymd.infra.chat.LLMService;
import com.zhli.baymd.infra.vision.VisionService;
import com.zhli.baymd.rag.config.ReportProperties;
import com.zhli.baymd.rag.core.prompt.PromptTemplateLoader;
import com.zhli.baymd.rag.dao.entity.MedicalReportDO;
import com.zhli.baymd.rag.dao.mapper.MedicalReportMapper;
import com.zhli.baymd.rag.dto.StoredFileDTO;
import com.zhli.baymd.rag.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ReportParseServiceImpl} 单测 — 路由分流、结构化容错、上下文构造。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("报告解析服务测试")
class ReportParseServiceImplTest {

    @Mock private FileStorageService fileStorageService;
    @Mock private DocumentParserSelector documentParserSelector;
    @Mock private DocumentParser documentParser;
    @Mock private LLMService llmService;
    @Mock private PromptTemplateLoader promptTemplateLoader;
    @Mock private MedicalReportMapper medicalReportMapper;
    @Mock private VisionService visionService;

    @InjectMocks
    private ReportParseServiceImpl service;

    private ReportProperties props;

    @BeforeEach
    void setUp() {
        props = new ReportProperties();
        props.setEnabled(true);
        props.setMaxFileSizeMb(10);
        props.setAllowedTypes(List.of("image/jpeg", "image/png", "application/pdf"));
        props.setScanDetectMinChars(50);
        // 注入 properties（@InjectMocks 不会注入非 @Mock 的 final，需手工）
        try {
            var f = ReportParseServiceImpl.class.getDeclaredField("properties");
            f.setAccessible(true);
            f.set(service, props);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // visionService 是 @Autowired(required=false) 字段，构造器注入后 Mockito 不会再做字段注入，需手工注入
        try {
            var f = ReportParseServiceImpl.class.getDeclaredField("visionService");
            f.setAccessible(true);
            f.set(service, visionService);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        UserContext.set(LoginUser.builder().userId("u1").build());
    }

    // ============================== 路由分流 ==============================

    @Test
    @DisplayName("PDF 文本层充足 → 走 Tika，不调 VL")
    void pdf_withTextLayer_usesTika() throws Exception {
        String textLayer = "白细胞 6.8 10^9/L 参考范围 3.5-9.5 血糖 5.1 mmol/L 参考范围 3.9-6.1".repeat(3);
        when(fileStorageService.upload(anyString(), any(byte[].class), anyString(), anyString()))
                .thenReturn(stored("local://baymd-reports/r.pdf"));
        when(documentParserSelector.select(anyString())).thenReturn(documentParser);
        when(documentParser.extractText(any(InputStream.class), anyString())).thenReturn(textLayer);
        when(llmService.chat(any(ChatRequest.class))).thenReturn("[]");
        when(promptTemplateLoader.render(anyString(), any())).thenReturn("sys");

        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", new byte[]{1, 2, 3});

        MedicalReportDO result = service.parseAndStore(file);

        assertThat(result.getParseStatus()).isEqualTo("SUCCESS");
        assertThat(result.getRawText()).contains("白细胞");
        // 关键：未调用 VL 模型
        verify(visionService, never()).extractText(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("图片 → 走 VL 模型")
    void image_usesVision() throws Exception {
        when(fileStorageService.upload(anyString(), any(byte[].class), anyString(), anyString()))
                .thenReturn(stored("local://baymd-reports/r.png"));
        when(visionService.extractText(any(byte[].class), eq("image/png"), anyString()))
                .thenReturn("血红蛋白 110 g/L");
        when(llmService.chat(any(ChatRequest.class))).thenReturn("[]");
        when(promptTemplateLoader.render(anyString(), any())).thenReturn("sys");

        MockMultipartFile file = new MockMultipartFile("file", "r.png", "image/png", new byte[]{9, 9});

        MedicalReportDO result = service.parseAndStore(file);

        assertThat(result.getParseStatus()).isEqualTo("SUCCESS");
        assertThat(result.getRawText()).isEqualTo("血红蛋白 110 g/L");
        verify(documentParser, never()).extractText(any(), anyString());
    }

    @Test
    @DisplayName("LLM 结构化输出非法 JSON → 降级丢弃 structured，仍保留原文")
    void structure_invalidJson_degrades() throws Exception {
        when(fileStorageService.upload(anyString(), any(byte[].class), anyString(), anyString()))
                .thenReturn(stored("local://r.png"));
        when(visionService.extractText(any(byte[].class), anyString(), anyString()))
                .thenReturn("白细胞 6.8");
        when(promptTemplateLoader.render(anyString(), any())).thenReturn("sys");
        when(llmService.chat(any(ChatRequest.class))).thenReturn("这不是JSON");

        MockMultipartFile file = new MockMultipartFile("file", "r.png", "image/png", new byte[]{1});

        MedicalReportDO result = service.parseAndStore(file);

        assertThat(result.getParseStatus()).isEqualTo("SUCCESS");
        assertThat(result.getRawText()).isEqualTo("白细胞 6.8");
        assertThat(result.getStructured()).isNull(); // 降级丢弃
    }

    @Test
    @DisplayName("图片但未配置 VL → 解析失败，不产生脏数据")
    void image_noVision_fails() throws Exception {
        // visionService 为 null（未配置）—— 通过反射置空
        try {
            var f = ReportParseServiceImpl.class.getDeclaredField("visionService");
            f.setAccessible(true);
            f.set(service, null);
        } catch (Exception ignored) {}

        when(fileStorageService.upload(anyString(), any(byte[].class), anyString(), anyString()))
                .thenReturn(stored("local://r.png"));

        MockMultipartFile file = new MockMultipartFile("file", "r.png", "image/png", new byte[]{1});

        MedicalReportDO result = service.parseAndStore(file);

        assertThat(result.getParseStatus()).isEqualTo("FAILED");
        assertThat(result.getErrorMessage()).contains("视觉模型");
    }

    @Test
    @DisplayName("功能未启用 → 抛异常")
    void disabled_throws() {
        props.setEnabled(false);
        MockMultipartFile file = new MockMultipartFile("file", "r.png", "image/png", new byte[]{1});
        try {
            service.parseAndStore(file);
            assertThat(false).as("应抛异常").isTrue();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).contains("未启用");
        }
    }

    @Test
    @DisplayName("文件类型不允许 → 抛异常")
    void badType_throws() {
        MockMultipartFile file = new MockMultipartFile("file", "r.txt", "text/plain", new byte[]{1});
        try {
            service.parseAndStore(file);
            assertThat(false).as("应抛异常").isTrue();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("不支持");
        }
    }

    // ============================== buildReportContext（纯逻辑） ==============================

    @Test
    @DisplayName("buildReportContext: null → null")
    void context_null() {
        assertThat(service.buildReportContext(null)).isNull();
    }

    @Test
    @DisplayName("buildReportContext: rawText 空 → null")
    void context_empty() {
        MedicalReportDO r = new MedicalReportDO();
        r.setRawText("");
        assertThat(service.buildReportContext(r)).isNull();
    }

    @Test
    @DisplayName("buildReportContext: 仅 rawText")
    void context_rawOnly() {
        MedicalReportDO r = new MedicalReportDO();
        r.setRawText("血糖 8.2 mmol/L");
        String ctx = service.buildReportContext(r);
        assertThat(ctx).contains("用户上传的检查报告", "血糖 8.2 mmol/L");
        assertThat(ctx).doesNotContain("结构化指标");
    }

    @Test
    @DisplayName("buildReportContext: rawText + structured")
    void context_rawAndStructured() {
        MedicalReportDO r = new MedicalReportDO();
        r.setRawText("血糖 8.2 mmol/L");
        r.setStructured("[{\"name\":\"血糖\",\"value\":\"8.2\"}]");
        String ctx = service.buildReportContext(r);
        assertThat(ctx).contains("用户上传的检查报告", "血糖 8.2 mmol/L", "结构化指标", "8.2");
    }

    private StoredFileDTO stored(String url) {
        StoredFileDTO dto = new StoredFileDTO();
        dto.setUrl(url);
        dto.setOriginalFilename("r");
        return dto;
    }
}
