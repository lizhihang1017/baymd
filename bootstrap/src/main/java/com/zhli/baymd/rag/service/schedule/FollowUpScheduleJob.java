package com.zhli.baymd.rag.service.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhli.baymd.rag.config.FollowUpProperties;
import com.zhli.baymd.rag.core.notify.NotificationDispatcher;
import com.zhli.baymd.rag.core.notify.NotificationMessage;
import com.zhli.baymd.rag.dao.entity.FollowUpTaskDO;
import com.zhli.baymd.rag.dao.mapper.FollowUpTaskMapper;
import com.zhli.baymd.rag.service.FollowUpTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * 主动随访定时调度 — 扫描到期 PENDING 任务，CAS 抢锁后发送，发送后置 SENT。
 *
 * <p>复用知识库调度的"扫描 + lock_until 分布式锁"模式（自包含实现，
 * 不依赖 knowledge 模块的 ScheduleLockManager）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowUpScheduleJob {

    private final FollowUpTaskMapper followUpTaskMapper;
    private final FollowUpTaskService followUpTaskService;
    private final NotificationDispatcher notificationDispatcher;
    private final FollowUpProperties properties;

    private final java.util.concurrent.ExecutorService executor =
            Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "followup-schedule");
                t.setDaemon(true);
                return t;
            });

    /**
     * 扫描到期 PENDING 任务。
     */
    @Scheduled(fixedDelayString = "${rag.followup.scan-delay-ms:60000}", initialDelay = 30_000)
    public void scan() {
        if (!properties.isEnabled()) {
            return;
        }
        Date now = new Date();
        // 先回收过期 SENT
        followUpTaskService.expireOverdue(now);

        List<FollowUpTaskDO> tasks = followUpTaskMapper.selectList(new LambdaQueryWrapper<FollowUpTaskDO>()
                .eq(FollowUpTaskDO::getStatus, "PENDING")
                .le(FollowUpTaskDO::getTriggerTime, now)
                .and(w -> w.isNull(FollowUpTaskDO::getLockUntil)
                        .or().lt(FollowUpTaskDO::getLockUntil, now))
                .orderByAsc(FollowUpTaskDO::getTriggerTime)
                .last("LIMIT " + Math.max(properties.getBatchSize(), 1)));

        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        for (FollowUpTaskDO task : tasks) {
            if (!tryClaim(task.getId(), now)) {
                continue;
            }
            try {
                executor.execute(() -> process(task));
            } catch (RejectedExecutionException e) {
                log.error("随访任务提交失败: id={}", task.getId(), e);
                release(task.getId());
            }
        }
    }

    private boolean tryClaim(String id, Date now) {
        Date lockUntil = new Date(now.getTime() + (long) properties.getLockSeconds() * 1000);
        int updated = followUpTaskMapper.update(null, new LambdaUpdateWrapper<FollowUpTaskDO>()
                .eq(FollowUpTaskDO::getId, id)
                .eq(FollowUpTaskDO::getStatus, "PENDING")
                .and(w -> w.isNull(FollowUpTaskDO::getLockUntil)
                        .or().lt(FollowUpTaskDO::getLockUntil, now))
                .set(FollowUpTaskDO::getLockUntil, lockUntil));
        return updated > 0;
    }

    private void release(String id) {
        followUpTaskMapper.update(null, new LambdaUpdateWrapper<FollowUpTaskDO>()
                .eq(FollowUpTaskDO::getId, id)
                .set(FollowUpTaskDO::getLockUntil, null));
    }

    private void process(FollowUpTaskDO task) {
        Date now = new Date();
        try {
            // 静默时段顺延：若触发时间落在静默时段，置回 PENDING 并延后下次扫描
            Date adjusted = followUpTaskService.adjustForQuietHours(task.getTriggerTime());
            if (adjusted.after(now)) {
                followUpTaskMapper.update(null, new LambdaUpdateWrapper<FollowUpTaskDO>()
                        .eq(FollowUpTaskDO::getId, task.getId())
                        .set(FollowUpTaskDO::getLockUntil, adjusted)
                        .set(FollowUpTaskDO::getTriggerTime, adjusted));
                log.info("随访任务因静默时段顺延: id={}, triggerTime={}", task.getId(), adjusted);
                return;
            }

            String deepLink = properties.getDeepLinkBase() + "/?followupId=" + task.getId();
            String unsubLink = properties.getDeepLinkBase() + "/?followupUnsub=" + task.getUnsubToken();
            NotificationMessage msg = NotificationMessage.builder()
                    .userId(task.getUserId())
                    .title("BayMD 健康随访提醒")
                    .body("您前几天在 BayMD 的健康咨询有一条随访提醒，点击查看。")
                    .deepLink(deepLink)
                    .unsubscribeLink(unsubLink)
                    .build();

            boolean sent = notificationDispatcher.send(task.getChannel(), msg);
            if (sent) {
                followUpTaskMapper.update(null, new LambdaUpdateWrapper<FollowUpTaskDO>()
                        .eq(FollowUpTaskDO::getId, task.getId())
                        .set(FollowUpTaskDO::getStatus, "SENT")
                        .set(FollowUpTaskDO::getSentTime, now)
                        .set(FollowUpTaskDO::getLockUntil, null));
                log.info("随访任务已发送: id={}, userId={}", task.getId(), task.getUserId());
            } else {
                // 通道不可用（未绑邮箱），保持 PENDING，设置短 lockUntil 节流重试
                Date next = new Date(now.getTime() + 5 * 60 * 1000);
                followUpTaskMapper.update(null, new LambdaUpdateWrapper<FollowUpTaskDO>()
                        .eq(FollowUpTaskDO::getId, task.getId())
                        .set(FollowUpTaskDO::getLockUntil, next));
                log.info("随访任务通道不可用，节流重试: id={}, userId={}", task.getId(), task.getUserId());
            }
        } catch (Exception e) {
            log.error("随访任务处理异常: id={}", task.getId(), e);
            Date next = new Date(now.getTime() + 5 * 60 * 1000);
            followUpTaskMapper.update(null, new LambdaUpdateWrapper<FollowUpTaskDO>()
                    .eq(FollowUpTaskDO::getId, task.getId())
                    .set(FollowUpTaskDO::getLockUntil, next));
        }
    }
}
