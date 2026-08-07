package com.zhli.baymd.user.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhli.baymd.framework.context.UserContext;
import com.zhli.baymd.user.dao.entity.UserDO;
import com.zhli.baymd.user.dao.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 用户邮箱绑定与验证（主动随访前置）。
 *
 * <p>绑定时发送 6 位验证码到邮箱，Redis 存 5 分钟；验证通过写回
 * {@code t_user.email + email_verified=1}，防止把健康提醒发到错误邮箱。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserEmailService {

    private final UserMapper userMapper;
    private final RedissonClient redissonClient;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    private static final String CODE_KEY = "baymd:email:verify:";
    private static final long CODE_TTL_SECONDS = 300;

    /**
     * 发送验证码到指定邮箱。
     */
    public void sendCode(String userId, String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        RBucket<String> bucket = redissonClient.getBucket(CODE_KEY + userId + ":" + email);
        bucket.set(code, java.time.Duration.ofSeconds(CODE_TTL_SECONDS));

        if (mailSender == null) {
            // 开发环境未配置 SMTP：日志输出验证码
            log.warn("未配置 SMTP，邮箱验证码（仅开发用）: userId={}, email={}, code={}", userId, email, code);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(email);
            msg.setSubject("BayMD 邮箱验证码");
            msg.setText("您的 BayMD 邮箱验证码为：" + code + "，5 分钟内有效。如非本人操作请忽略。");
            mailSender.send(msg);
            log.info("邮箱验证码已发送: userId={}, email={}", userId, email);
        } catch (Exception e) {
            log.error("邮箱验证码发送失败: email={}", email, e);
            throw new IllegalStateException("验证码发送失败，请稍后重试");
        }
    }

    /**
     * 验证码校验，通过则绑定邮箱并标记已验证。
     */
    public boolean verifyAndBind(String userId, String email, String code) {
        RBucket<String> bucket = redissonClient.getBucket(CODE_KEY + userId + ":" + email);
        String stored = bucket.get();
        if (stored == null || !stored.equals(code)) {
            return false;
        }
        bucket.delete();
        userMapper.update(null, new LambdaUpdateWrapper<UserDO>()
                .eq(UserDO::getId, userId)
                .set(UserDO::getEmail, email)
                .set(UserDO::getEmailVerified, 1));
        log.info("邮箱绑定成功: userId={}, email={}", userId, email);
        return true;
    }

    /**
     * 当前用户是否已绑定并验证邮箱。
     */
    public boolean emailVerified(String userId) {
        UserDO u = userMapper.selectById(userId);
        return u != null && u.getEmail() != null && !u.getEmail().isBlank()
                && u.getEmailVerified() != null && u.getEmailVerified() == 1;
    }

    public String currentEmail(String userId) {
        UserDO u = userMapper.selectById(userId);
        return u == null ? null : u.getEmail();
    }
}
