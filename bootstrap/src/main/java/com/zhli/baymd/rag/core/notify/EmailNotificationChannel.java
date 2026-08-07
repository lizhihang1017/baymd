package com.zhli.baymd.rag.core.notify;

import com.zhli.baymd.user.dao.entity.UserDO;
import com.zhli.baymd.user.dao.mapper.UserMapper;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * 邮件通知通道。
 *
 * <p>仅当 {@code rag.followup.enabled=true} 且配置了 SMTP 时启用。
 * 邮件正文【绝不包含病情细节】，只写通用提醒 + 深链 + 退订。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rag.followup", name = "enabled", havingValue = "true")
public class EmailNotificationChannel implements NotificationChannel {

    private final UserMapper userMapper;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Override
    public String getType() {
        return "email";
    }

    @Override
    public boolean isAvailable(String userId) {
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            return false;
        }
        Integer verified = user.getEmailVerified();
        return user.getEmail() != null && !user.getEmail().isBlank()
                && (verified != null && verified == 1);
    }

    @Override
    public boolean send(NotificationMessage message) {
        if (mailSender == null) {
            log.warn("邮件通道未配置 JavaMailSender，跳过发送: userId={}", message.getUserId());
            return false;
        }
        UserDO user = userMapper.selectById(message.getUserId());
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("用户未绑定邮箱，跳过邮件发送: userId={}", message.getUserId());
            return false;
        }
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setTo(user.getEmail());
            helper.setSubject(message.getTitle());
            helper.setText(buildHtml(message), true);
            mailSender.send(mime);
            log.info("随访邮件已发送: userId={}, to={}", message.getUserId(), user.getEmail());
            return true;
        } catch (Exception e) {
            log.error("随访邮件发送失败: userId={}", message.getUserId(), e);
            return false;
        }
    }

    private String buildHtml(NotificationMessage m) {
        return "<div style=\"font-family:sans-serif;max-width:480px;margin:0 auto;padding:16px\">"
                + "<h2 style=\"color:#5C3D4E\">BayMD 健康随访提醒</h2>"
                + "<p style=\"font-size:14px;color:#444\">" + escape(m.getBody()) + "</p>"
                + "<p style=\"margin:20px 0\">"
                + "<a href=\"" + escape(m.getDeepLink()) + "\" "
                + "style=\"display:inline-block;background:#5C3D4E;color:#fff;padding:10px 20px;"
                + "border-radius:8px;text-decoration:none;font-size:14px\">点击查看随访问题</a></p>"
                + "<p style=\"font-size:12px;color:#8899A6\">"
                + "本邮件不包含任何病情信息。如不想再接收此类提醒，"
                + "<a href=\"" + escape(m.getUnsubscribeLink()) + "\">点此退订</a>。</p>"
                + "</div>";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
