package com.zhli.baymd.rag.service.impl;

import com.zhli.baymd.rag.config.FollowUpProperties;
import com.zhli.baymd.rag.core.followup.FollowUpPlanService.FollowUpPlan;
import com.zhli.baymd.rag.dao.entity.FollowUpTaskDO;
import com.zhli.baymd.user.dao.entity.UserDO;
import com.zhli.baymd.rag.dao.mapper.FollowUpTaskMapper;
import com.zhli.baymd.user.dao.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FollowUpTaskServiceImpl} 单测 — 频控、静默时段、退订、过期。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("随访任务服务测试")
class FollowUpTaskServiceImplTest {

    @Mock private FollowUpTaskMapper followUpTaskMapper;
    @Mock private UserMapper userMapper;

    @InjectMocks
    private FollowUpTaskServiceImpl service;

    private FollowUpProperties props;

    @BeforeEach
    void setUp() {
        props = new FollowUpProperties();
        props.setEnabled(true);
        props.setTopicDedupDays(7);
        props.setDailyLimitPerUser(1);
        props.setQuietHours("22:00-08:00");
        props.setExpireDays(3);
        try {
            var f = FollowUpTaskServiceImpl.class.getDeclaredField("properties");
            f.setAccessible(true);
            f.set(service, props);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private FollowUpPlan plan(String topic) {
        return new FollowUpPlan(true, "这几天好些了吗？", 3, topic);
    }

    private UserDO user(boolean unsubscribed) {
        UserDO u = new UserDO();
        u.setId("u1");
        u.setEmail("u@x.com");
        u.setEmailVerified(1);
        u.setFollowupEnabled(unsubscribed ? 0 : 1);
        return u;
    }

    // ============================== createTask 频控 ==============================

    @Test
    @DisplayName("功能未启用 → 不创建")
    void disabled_returnsNull() {
        props.setEnabled(false);
        assertThat(service.createTask("u1", "c1", plan("t"))).isNull();
    }

    @Test
    @DisplayName("计划不应随访 → 不创建")
    void planNone_returnsNull() {
        assertThat(service.createTask("u1", "c1", FollowUpPlan.none())).isNull();
    }

    @Test
    @DisplayName("用户已退订 → 不创建")
    void unsubscribedUser_returnsNull() {
        when(userMapper.selectById(anyString())).thenReturn(user(true));
        assertThat(service.createTask("u1", "c1", plan("t"))).isNull();
    }

    @Test
    @DisplayName("同主题 7 天内已有任务 → 跳过")
    void topicDedup_returnsNull() {
        when(userMapper.selectById(anyString())).thenReturn(user(false));
        when(followUpTaskMapper.selectCount(any())).thenReturn(1L);
        assertThat(service.createTask("u1", "c1", plan("头疼随访"))).isNull();
    }

    @Test
    @DisplayName("正常创建：triggerTime = now + delayDays")
    void normal_create() {
        when(userMapper.selectById(anyString())).thenReturn(user(false));
        when(followUpTaskMapper.selectCount(any())).thenReturn(0L);

        FollowUpTaskDO task = service.createTask("u1", "c1", plan("头疼随访"));

        assertThat(task).isNotNull();
        assertThat(task.getStatus()).isEqualTo("PENDING");
        assertThat(task.getTopic()).isEqualTo("头疼随访");
        assertThat(task.getChannel()).isEqualTo("email");
        assertThat(task.getUnsubToken()).isNotBlank();
        // triggerTime ≈ now + 3 天
        long diffMs = task.getTriggerTime().getTime() - System.currentTimeMillis();
        assertThat(diffMs).isBetween(2L * 24 * 3600 * 1000, 4L * 24 * 3600 * 1000);

        ArgumentCaptor<FollowUpTaskDO> cap = ArgumentCaptor.forClass(FollowUpTaskDO.class);
        verify(followUpTaskMapper).insert(cap.capture());
        assertThat(cap.getValue().getQuestion()).contains("好些了吗");
    }

    @Test
    @DisplayName("delayDays 越界钳制到 [1,14]")
    void delayClamped() {
        when(userMapper.selectById(anyString())).thenReturn(user(false));
        when(followUpTaskMapper.selectCount(any())).thenReturn(0L);

        FollowUpTaskDO task = service.createTask("u1", "c1", new FollowUpPlan(true, "q", 99, "t"));
        long diffMs = task.getTriggerTime().getTime() - System.currentTimeMillis();
        // 14 天
        assertThat(diffMs).isBetween(13L * 24 * 3600 * 1000, 15L * 24 * 3600 * 1000);
    }

    // ============================== 静默时段 ==============================

    @Test
    @DisplayName("静默时段 22:00-08:00：23:00 触发 → 顺延次日 08:00")
    void quiet_night() {
        Date t = at("2026-08-07T23:00:00");
        Date adjusted = service.adjustForQuietHours(t);
        LocalDateTime ldt = toLdt(adjusted);
        assertThat(ldt.toLocalDate()).isEqualTo(LocalDateTime.ofInstant(t.toInstant(), ZoneId.systemDefault()).toLocalDate().plusDays(1));
        assertThat(ldt.toLocalTime()).hasHour(8).hasMinute(0);
    }

    @Test
    @DisplayName("静默时段 22:00-08:00：凌晨 03:00 触发 → 顺延当日 08:00")
    void quiet_earlyMorning() {
        Date t = at("2026-08-07T03:00:00");
        Date adjusted = service.adjustForQuietHours(t);
        LocalDateTime ldt = toLdt(adjusted);
        assertThat(ldt.toLocalTime()).hasHour(8).hasMinute(0);
        // 当日（03:00 在 08:00 之前，属当夜静默窗口）
        assertThat(ldt.toLocalDate()).isEqualTo(LocalDateTime.ofInstant(t.toInstant(), ZoneId.systemDefault()).toLocalDate());
    }

    @Test
    @DisplayName("非静默时段 14:00 → 不调整")
    void quiet_notInQuiet() {
        Date t = at("2026-08-07T14:00:00");
        assertThat(service.adjustForQuietHours(t)).isEqualTo(t);
    }

    @Test
    @DisplayName("静默时段为空 → 不调整")
    void quiet_empty() {
        props.setQuietHours("");
        Date t = at("2026-08-07T23:00:00");
        assertThat(service.adjustForQuietHours(t)).isEqualTo(t);
    }

    // ============================== 退订 ==============================

    @Test
    @DisplayName("退订：token 对应任务不存在 → 无副作用")
    void unsubscribe_unknownToken() {
        when(followUpTaskMapper.selectOne(any())).thenReturn(null);
        service.unsubscribe("nope");
        // 任务为 null 直接返回，不应触发任何用户更新
        verify(userMapper, org.mockito.Mockito.never()).update(any(), any());
    }

    private static Date at(String iso) {
        return Date.from(LocalDateTime.parse(iso).atZone(ZoneId.systemDefault()).toInstant());
    }

    private static LocalDateTime toLdt(Date d) {
        return LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
    }
}
