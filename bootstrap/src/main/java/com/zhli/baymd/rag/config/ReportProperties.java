package com.zhli.baymd.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 报告解读配置（{@code rag.report.*}）。
 *
 * <p>功能默认关闭上线，验证后开启：{@code rag.report.enabled=false} 时
 * 上传接口返回"功能未启用"，避免在 VLM 模型未就绪时产生脏数据。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag.report")
public class ReportProperties {

    /** 是否启用报告解读功能 */
    private boolean enabled = false;

    /** 上传文件大小上限（MB） */
    private int maxFileSizeMb = 10;

    /** 允许的文件类型 */
    private List<String> allowedTypes = List.of("image/jpeg", "image/png", "application/pdf");

    /** 存储桶名（S3 bucket 或本地目录名） */
    private String bucket = "baymd-reports";

    /** Tika 提取文本不足此字符数时判定为扫描件，转 VL 模型 */
    private int scanDetectMinChars = 50;
}
