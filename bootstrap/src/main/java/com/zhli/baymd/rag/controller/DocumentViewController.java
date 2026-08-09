package com.zhli.baymd.rag.controller;

import com.zhli.baymd.core.chunk.ChunkingMode;
import com.zhli.baymd.core.chunk.ChunkingOptions;
import com.zhli.baymd.core.chunk.ChunkingStrategy;
import com.zhli.baymd.core.chunk.ChunkingStrategyFactory;
import com.zhli.baymd.core.chunk.VectorChunk;
import com.zhli.baymd.core.parser.DocumentParser;
import com.zhli.baymd.core.parser.DocumentParserSelector;
import com.zhli.baymd.core.parser.ParserType;
import com.zhli.baymd.framework.auth.RequireAdmin;
import com.zhli.baymd.framework.convention.Result;
import com.zhli.baymd.framework.web.Results;
import com.zhli.baymd.knowledge.dao.entity.KnowledgeDocumentDO;
import com.zhli.baymd.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhli.baymd.rag.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档查看与分块预览（管理员）。
 */
@Slf4j
@RequireAdmin
@RestController
@RequiredArgsConstructor
public class DocumentViewController {

    private static final int PREVIEW_CHUNKS = 10;
    private static final int CHUNK_PREVIEW_LEN = 300;
    private static final int CONTENT_MAX_LEN = 20_000;

    private final DocumentParserSelector parserSelector;
    private final ChunkingStrategyFactory chunkingStrategyFactory;
    private final FileStorageService fileStorageService;
    private final KnowledgeDocumentMapper documentMapper;
    private final ObjectMapper objectMapper;

    /**
     * 分块预览：按所选策略/配置对文件内容分块，返回前 N 块（不落库）。
     */
    @PostMapping("/knowledge-base/chunk-preview")
    public Result<Map<String, Object>> preview(@RequestParam("file") MultipartFile file,
                                               @RequestParam(value = "chunkStrategy", defaultValue = "fixed_size") String chunkStrategy,
                                               @RequestParam(value = "chunkConfig", required = false) String chunkConfig) {
        try {
            byte[] bytes = file.getBytes();
            String text = extractText(bytes, file.getOriginalFilename());
            if (text == null || text.isBlank()) {
                throw new com.zhli.baymd.framework.exception.ClientException("无法从文件提取文本");
            }

            ChunkingMode mode = ChunkingMode.fromValue(chunkStrategy);
            if (mode == null) {
                mode = ChunkingMode.FIXED_SIZE;
            }
            ChunkingOptions options = mode.createOptions(parseConfig(chunkConfig));
            ChunkingStrategy strategy = chunkingStrategyFactory.requireStrategy(mode);
            List<VectorChunk> chunks = strategy.chunk(text, options);

            List<Map<String, Object>> previews = chunks.stream()
                    .limit(PREVIEW_CHUNKS)
                    .map(c -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("index", c.getIndex());
                        m.put("content", truncate(c.getContent(), CHUNK_PREVIEW_LEN));
                        return m;
                    })
                    .toList();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalChunks", chunks.size());
            result.put("previews", previews);
            return Results.success(result);
        } catch (Exception e) {
            log.warn("分块预览失败: {}", e.getMessage());
            throw new com.zhli.baymd.framework.exception.ClientException("分块预览失败: " + e.getMessage());
        }
    }

    /**
     * 查看文档实际内容（提取后的文本）。
     */
    @GetMapping("/knowledge-base/docs/{docId}/content")
    public Result<String> content(@PathVariable String docId) {
        KnowledgeDocumentDO doc = documentMapper.selectById(docId);
        if (doc == null || doc.getFileUrl() == null) {
            throw new com.zhli.baymd.framework.exception.ClientException("文档不存在");
        }
        try (InputStream is = fileStorageService.openStream(doc.getFileUrl())) {
            DocumentParser parser = parserSelector.select(ParserType.TIKA.getType());
            String text = parser.extractText(is, doc.getDocName());
            return Results.success(text == null ? "" : truncate(text, CONTENT_MAX_LEN));
        } catch (Exception e) {
            log.warn("读取文档内容失败: docId={}, {}", docId, e.getMessage());
            throw new com.zhli.baymd.framework.exception.ClientException("读取文档内容失败: " + e.getMessage());
        }
    }

    // ===================== 内部 =====================

    private String extractText(byte[] bytes, String fileName) {
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            DocumentParser parser = parserSelector.select(ParserType.TIKA.getType());
            return parser.extractText(is, fileName);
        } catch (Exception e) {
            log.warn("文档文本提取失败: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String chunkConfig) {
        if (chunkConfig == null || chunkConfig.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(chunkConfig, Map.class);
        } catch (Exception e) {
            log.warn("chunkConfig 解析失败，使用默认: {}", e.getMessage());
            return Map.of();
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
