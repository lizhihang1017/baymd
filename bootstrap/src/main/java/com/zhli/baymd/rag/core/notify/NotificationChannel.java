package com.zhli.baymd.rag.core.notify;

/**
 * 通知通道 SPI — 可插拔，为将来短信/微信留位。
 *
 * <p>本期实现邮件通道。通道层只负责"把通用提醒送达用户"，
 * 绝不传输病情细节（隐私红线）。</p>
 */
public interface NotificationChannel {

    /**
     * 通道标识，如 {@code email}。
     */
    String getType();

    /**
     * 判断该用户是否配置了此通道（如是否绑定并验证邮箱）。
     */
    boolean isAvailable(String userId);

    /**
     * 发送通知。
     *
     * @return true 表示发送成功
     */
    boolean send(NotificationMessage message);
}
