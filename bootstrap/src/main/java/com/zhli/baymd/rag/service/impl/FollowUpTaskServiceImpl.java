package com.zhli.baymd.rag.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhli.baymd.rag.config.FollowUpProperties;
import com.zhli.baymd.rag.core.followup.FollowUpPlanService.FollowUpPlan;
import com.zhli.baymd.rag.dao.entity.FollowUpTaskDO;
import com.zhli.baymd.user.dao.entity.UserDO;
import com.zhli.baymd.rag.dao.mapper.FollowUpTaskMapper;
import com.zhli.baymd.rag.service.FollowUpTaskService;
import com.zhli.baymd.user.dao.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * 随访任务服务实现。
 *
 * <p>频控（代码硬校验，不依赖 LLM）：
 * <ul>
 *   <li>同用户同 topic 在 topicDedupDays 内已有未取消任务 → 跳过</li>
 *   <li>同用户当日已发送达到 dailyLimitPerUser → 触发时间顺延到次日</li>
 *   <li>静默时段内到期的任务顺延到静默结束</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpTaskServiceImpl implements FollowUpTaskService {

    private final FollowUpTaskMapper followUpTaskMapper;
    private final UserMapper userMapper;
    private final FollowUpProperties properties;

    @Override
    public FollowUpTaskDO createTask(String userId, String conversationId, FollowUpPlan plan) {
        if (!properties.isEnabled()) {
            return null;
        }
        if (plan == null || !plan.shouldFollowUp()) {
            return null;
        }
        // 用户已退订
        UserDO user = userMapper.selectById(userId);
        if (user != null && (user.getFollowupEnabled() == null || user.getFollowupEnabled() == 0)) {
            log.info("用户已退订随访，跳过: userId={}", userId);
            return null;
        }

        // 频控1：同主题去重
        if (hasRecentTopicTask(userId, plan.topic())) {
            log.info("同主题随访任务 {} 天内已存在，跳过: userId={}, topic={}",
                    properties.getTopicDedupDays(), userId, plan.topic());
            return null;
        }

        // 频控2：当日发送上限 → 顺延到次日
        Date triggerTime = computeTriggerTime(userId, plan.delayDays());

        FollowUpTaskDO task = new FollowUpTaskDO();
        task.setId(IdUtil.getSnowflakeNextIdStr());
        task.setUserId(userId);
        task.setConversationId(conversationId);
        task.setTopic(plan.topic());
        task.setQuestion(plan.followUpQuestion());
        task.setTriggerTime(triggerTime);
        task.setStatus("PENDING");
        task.setChannel("email");
        task.setUnsubToken(IdUtil.fastSimpleUUID());
        task.setCreateTime(new Date());
        task.setUpdateTime(new Date());

        followUpTaskMapper.insert(task);
        log.info("随访任务已创建: id={}, userId={}, topic={}, triggerTime={}",
                task.getId(), userId, plan.topic(), triggerTime);
        return task;
    }

    @Override
    public List<FollowUpTaskDO> listByUser(String userId) {
        return followUpTaskMapper.selectList(new LambdaQueryWrapper<FollowUpTaskDO>()
                .eq(FollowUpTaskDO::getUserId, userId)
                .orderByDesc(FollowUpTaskDO::getCreateTime));
    }

    @Override
    public FollowUpTaskDO getById(String id) {
        return followUpTaskMapper.selectById(id);
    }

    @Override
    public FollowUpTaskDO getByUnsubToken(String token) {
        if (StrUtil.isBlank(token)) {
            return null;
        }
        return followUpTaskMapper.selectOne(new LambdaQueryWrapper<FollowUpTaskDO>()
                .eq(FollowUpTaskDO::getUnsubToken, token)
                .last("LIMIT 1"));
    }

    @Override
    public void markAnswered(String id) {
        followUpTaskMapper.update(null, new LambdaUpdateWrapper<FollowUpTaskDO>()
                .eq(FollowUpTaskDO::getId, id)
                .eq(FollowUpTaskDO::getStatus, "SENT")
                .set(FollowUpTaskDO::getStatus, "ANSWERED")
                .set(FollowUpTaskDO::getAnsweredTime, new Date()));
    }

    @Override
    public void unsubscribe(String token) {
        FollowUpTaskDO task = getByUnsubToken(token);
        if (task == null) {
            return;
        }
        // 关闭用户的全局随访开关
        userMapper.update(null, new LambdaUpdateWrapper<UserDO>()
                .eq(UserDO::getId, task.getUserId())
                .set(UserDO::getFollowupEnabled, 0));
        log.info("用户已退订随访: userId={}", task.getUserId());
    }

    @Override
    public Date adjustForQuietHours(Date triggerTime) {
        if (triggerTime == null || StrUtil.isBlank(properties.getQuietHours())) {
            return triggerTime;
        }
        try {
            String[] parts = properties.getQuietHours().split("-");
            if (parts.length != 2) {
                return triggerTime;
            }
            LocalTime start = LocalTime.parse(parts[0].trim());
            LocalTime end = LocalTime.parse(parts[1].trim());
            LocalDateTime ldt = LocalDateTime.ofInstant(triggerTime.toInstant(), ZoneId.systemDefault());
            LocalTime t = ldt.toLocalTime();

            boolean inQuiet;
            if (start.isBefore(end)) {
                // 同日静默，如 13:00-14:00
                inQuiet = !t.isBefore(start) && t.isBefore(end);
            } else {
                // 跨夜静默，如 22:00-08:00
                inQuiet = !t.isBefore(start) || t.isBefore(end);
            }
            if (!inQuiet) {
                return triggerTime;
            }
            // 顺延到今日 end（若 end 已过则明日 end）
            LocalDateTime resolved = LocalDateTime.of(ldt.toLocalDate(), end);
            if (!resolved.isAfter(ldt)) {
                resolved = resolved.plusDays(1);
            }
            return Date.from(resolved.atZone(ZoneId.systemDefault()).toInstant());
        } catch (Exception e) {
            log.warn("静默时段解析失败: {}, {}", properties.getQuietHours(), e.getMessage());
            return triggerTime;
        }
    }

    @Override
    public int expireOverdue(Date now) {
        int expireDays = Math.max(properties.getExpireDays(), 1);
        Date threshold = new Date(now.getTime() - (long) expireDays * 24 * 3600 * 1000);
        return followUpTaskMapper.update(null, new LambdaUpdateWrapper<FollowUpTaskDO>()
                .eq(FollowUpTaskDO::getStatus, "SENT")
                .lt(FollowUpTaskDO::getSentTime, threshold)
                .set(FollowUpTaskDO::getStatus, "EXPIRED")
                .set(FollowUpTaskDO::getUpdateTime, now));
    }

    // ============================== 内部 ==============================

    private boolean hasRecentTopicTask(String userId, String topic) {
        if (StrUtil.isBlank(topic)) {
            return false;
        }
        Date since = new Date(System.currentTimeMillis()
                - (long) properties.getTopicDedupDays() * 24 * 3600 * 1000);
        Long c = followUpTaskMapper.selectCount(new LambdaQueryWrapper<FollowUpTaskDO>()
                .eq(FollowUpTaskDO::getUserId, userId)
                .eq(FollowUpTaskDO::getTopic, topic)
                .ne(FollowUpTaskDO::getStatus, "CANCELLED")
                .ge(FollowUpTaskDO::getCreateTime, since));
        return c != null && c > 0;
    }

    private int countSentToday(String userId) {
        Date dayStart = Date.from(LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault()).toInstant());
        Long c = followUpTaskMapper.selectCount(new LambdaQueryWrapper<FollowUpTaskDO>()
                .eq(FollowUpTaskDO::getUserId, userId)
                .eq(FollowUpTaskDO::getStatus, "SENT")
                .ge(FollowUpTaskDO::getSentTime, dayStart));
        return c == null ? 0 : c.intValue();
    }

    private Date computeTriggerTime(String userId, int delayDays) {
        int safeDelay = Math.max(1, Math.min(delayDays, 14));
        long base = System.currentTimeMillis() + (long) safeDelay * 24 * 3600 * 1000;
        // 当日发送上限顺延：若用户今日已达上限，顺延到次日
        if (countSentToday(userId) >= properties.getDailyLimitPerUser()) {
            base = Date.from(LocalDate.now().plusDays(1)
                    .atTime(LocalTime.of(9, 0))
                    .atZone(ZoneId.systemDefault()).toInstant()).getTime();
        }
        return new Date(base);
    }
}
