package com.zhli.baymd.rag.core.notify;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通知分发器 — 按用户可用通道发送，本期仅 email。
 *
 * <p>用户未配置邮箱时跳过并记日志（任务保留为 PENDING/SENT 由后续过期处理）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final List<NotificationChannel> channels;

    /**
     * 按指定通道发送。返回是否成功。
     */
    public boolean send(String channelType, NotificationMessage message) {
        if (channels == null || channels.isEmpty()) {
            log.warn("无可用通知通道，跳过发送: userId={}, channel={}", message.getUserId(), channelType);
            return false;
        }
        for (NotificationChannel ch : channels) {
            if (ch.getType().equalsIgnoreCase(channelType) && ch.isAvailable(message.getUserId())) {
                return ch.send(message);
            }
        }
        log.info("用户未配置通道或通道不可用: userId={}, channel={}", message.getUserId(), channelType);
        return false;
    }
}
