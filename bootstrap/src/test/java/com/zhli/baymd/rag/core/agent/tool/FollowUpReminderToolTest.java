package com.zhli.baymd.rag.core.agent.tool;

import com.zhli.baymd.framework.context.LoginUser;
import com.zhli.baymd.framework.context.UserContext;
import com.zhli.baymd.rag.core.agent.AgentToolResult;
import com.zhli.baymd.rag.core.followup.FollowUpPlanService.FollowUpPlan;
import com.zhli.baymd.rag.dao.entity.FollowUpTaskDO;
import com.zhli.baymd.rag.service.FollowUpTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * {@link FollowUpReminderTool} 单测。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("随访提醒工具测试")
class FollowUpReminderToolTest {

    @Mock private FollowUpTaskService followUpTaskService;

    @InjectMocks
    private FollowUpReminderTool tool;

    @BeforeEach
    void setUp() {
        UserContext.set(LoginUser.builder().userId("u1").build());
    }

    @Test
    @DisplayName("成功创建随访提醒")
    void success_create() {
        FollowUpTaskDO task = new FollowUpTaskDO();
        task.setId("t1");
        when(followUpTaskService.createTask(eq("u1"), isNull(), any(FollowUpPlan.class)))
                .thenReturn(task);

        AgentToolResult r = tool.execute(Map.of(
                "question", "三天后复查测血压",
                "delayDays", 3,
                "topic", "血压复查随访"));

        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getContent()).contains("三天后复查测血压", "3 天后");

        ArgumentCaptor<FollowUpPlan> cap = ArgumentCaptor.forClass(FollowUpPlan.class);
        org.mockito.Mockito.verify(followUpTaskService)
                .createTask(eq("u1"), isNull(), cap.capture());
        assertThat(cap.getValue().delayDays()).isEqualTo(3);
        assertThat(cap.getValue().topic()).isEqualTo("血压复查随访");
    }

    @Test
    @DisplayName("delayDays 越界钳制到 [1,14]")
    void delay_clamped() {
        when(followUpTaskService.createTask(eq("u1"), isNull(), any(FollowUpPlan.class)))
                .thenReturn(new FollowUpTaskDO());

        tool.execute(Map.of("question", "q", "delayDays", 99, "topic", "t"));

        ArgumentCaptor<FollowUpPlan> cap = ArgumentCaptor.forClass(FollowUpPlan.class);
        org.mockito.Mockito.verify(followUpTaskService)
                .createTask(eq("u1"), isNull(), cap.capture());
        assertThat(cap.getValue().delayDays()).isEqualTo(14);
    }

    @Test
    @DisplayName("频控命中（createTask 返回 null）→ 提示未创建")
    void dedup_returnsNull() {
        when(followUpTaskService.createTask(eq("u1"), isNull(), any(FollowUpPlan.class)))
                .thenReturn(null);

        AgentToolResult r = tool.execute(Map.of("question", "q", "delayDays", 3, "topic", "t"));
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getContent()).contains("未能创建");
    }

    @Test
    @DisplayName("缺少 question → 失败")
    void missingQuestion() {
        AgentToolResult r = tool.execute(Map.of("delayDays", 3, "topic", "t"));
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getErrorMessage()).contains("问题");
    }

    @Test
    @DisplayName("缺少 topic → 默认通用随访")
    void defaultTopic() {
        when(followUpTaskService.createTask(eq("u1"), isNull(), any(FollowUpPlan.class)))
                .thenReturn(new FollowUpTaskDO());
        tool.execute(Map.of("question", "q", "delayDays", 1));
        ArgumentCaptor<FollowUpPlan> cap = ArgumentCaptor.forClass(FollowUpPlan.class);
        org.mockito.Mockito.verify(followUpTaskService)
                .createTask(eq("u1"), isNull(), cap.capture());
        assertThat(cap.getValue().topic()).isNotBlank();
    }
}
