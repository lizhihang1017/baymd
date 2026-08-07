package com.zhli.baymd.rag.core.notify;

import lombok.Builder;
import lombok.Data;

/**
 * 通知消息（与通道无关）。邮件正文【绝不包含病情细节】，仅通用提醒 + 深链。
 */
@Data
@Builder
public class NotificationMessage {

    private String userId;

    private String title;

    /** 通用提醒正文（不含病情），如"您有一条健康随访提醒" */
    private String body;

    /** App 深链（携带 followupId，点击后回流回答） */
    private String deepLink;

    /** 退订链接 */
    private String unsubscribeLink;
}
