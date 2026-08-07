package com.zhli.baymd.rag.core.agent.tool;

import com.zhli.baymd.framework.context.UserContext;
import com.zhli.baymd.rag.core.agent.AgentTool;
import com.zhli.baymd.rag.core.agent.AgentToolResult;
import com.zhli.baymd.rag.core.followup.FollowUpPlanService.FollowUpPlan;
import com.zhli.baymd.rag.dao.entity.FollowUpTaskDO;
import com.zhli.baymd.rag.service.FollowUpTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 随访提醒工具（联动 Phase 2）— Agent 可在对话中主动为用户创建随访任务，
 * 如"三天后提醒我复查测血压"。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowUpReminderTool implements AgentTool {

    private static final String TOOL_NAME = "create_followup_reminder";
    private static final String TOOL_TYPE = "system";

    private final FollowUpTaskService followUpTaskService;

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "为用户创建主动随访提醒任务。当用户提出类似“三天后提醒我复查测血压”“明天提醒我吃药”"
                + "等希望日后被主动提醒的健康诉求时使用。参数 question 是随访问题，delayDays 是几天后提醒，"
                + "topic 是提醒主题。受频控限制：同主题 7 天内不重复创建。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "question", Map.of("type", "string", "description", "随访问题，如“三天后复查测血压”"),
                        "delayDays", Map.of("type", "integer", "description", "几天后提醒（1-14）"),
                        "topic", Map.of("type", "string", "description", "提醒主题，如“血压复查随访”")
                ),
                "required", List.of("question", "delayDays", "topic")
        );
    }

    @Override
    public String getType() {
        return TOOL_TYPE;
    }

    @Override
    public AgentToolResult execute(Map<String, Object> parameters) {
        long start = System.currentTimeMillis();
        try {
            String question = asString(parameters.get("question"));
            String topic = asString(parameters.get("topic"));
            int delayDays = parameters.get("delayDays") instanceof Number n
                    ? n.intValue() : 3;
            if (question == null || question.isBlank()) {
                return AgentToolResult.error(TOOL_NAME, "随访问题不能为空", elapsed(start));
            }
            if (topic == null || topic.isBlank()) {
                topic = "健康随访";
            }
            delayDays = Math.max(1, Math.min(delayDays, 14));

            FollowUpPlan plan = new FollowUpPlan(true, question, delayDays, topic);
            FollowUpTaskDO task = followUpTaskService.createTask(
                    UserContext.getUserId(), null, plan);

            if (task == null) {
                return AgentToolResult.success(TOOL_NAME,
                        "未能创建随访提醒：可能随访功能未开启、同主题 7 天内已有任务、或用户已退订。",
                        0, elapsed(start));
            }
            return AgentToolResult.success(TOOL_NAME,
                    String.format("已为用户创建随访提醒：%s（%d 天后触发，主题：%s）",
                            question, delayDays, topic),
                    1, elapsed(start));
        } catch (Exception e) {
            log.error("创建随访提醒失败", e);
            return AgentToolResult.error(TOOL_NAME, "创建随访提醒失败: " + e.getMessage(), elapsed(start));
        }
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private static long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
