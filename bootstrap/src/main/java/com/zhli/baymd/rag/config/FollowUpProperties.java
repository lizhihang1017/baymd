package com.zhli.baymd.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 主动随访配置（{@code rag.followup.*}）。
 *
 * <p>功能默认关闭上线，配置 SMTP 并验证后开启。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag.followup")
public class FollowUpProperties {

    /** 是否启用主动随访 */
    private boolean enabled = false;

    /** 定时扫描间隔（毫秒） */
    private long scanDelayMs = 60_000;

    /** 单用户每日发送上限 */
    private int dailyLimitPerUser = 1;

    /** 同主题去重窗口（天） */
    private int topicDedupDays = 7;

    /** 静默时段（如 "22:00-08:00"），到期任务顺延到静默结束 */
    private String quietHours = "22:00-08:00";

    /** 发送后多少天未回答 → EXPIRED */
    private int expireDays = 3;

    /** 邮件深链基址 */
    private String deepLinkBase = "http://localhost:5173";

    /** 调度批量大小 */
    private int batchSize = 20;

    /** 调度锁时长（秒） */
    private int lockSeconds = 600;
}
