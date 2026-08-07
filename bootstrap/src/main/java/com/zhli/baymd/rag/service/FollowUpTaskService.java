package com.zhli.baymd.rag.service;

import com.zhli.baymd.rag.core.followup.FollowUpPlanService.FollowUpPlan;
import com.zhli.baymd.rag.dao.entity.FollowUpTaskDO;

import java.util.List;

/**
 * 随访任务服务 — 任务 CRUD + 频控 + 静默时段/过期处理。
 */
public interface FollowUpTaskService {

    /**
     * 创建随访任务（含频控校验）。返回创建的任务，频控命中或随访关闭时返回 null。
     */
    FollowUpTaskDO createTask(String userId, String conversationId, FollowUpPlan plan);

    /** 查询用户的随访任务列表 */
    List<FollowUpTaskDO> listByUser(String userId);

    /** 按 ID 获取 */
    FollowUpTaskDO getById(String id);

    /** 按退订 token 获取（深链/退订用） */
    FollowUpTaskDO getByUnsubToken(String token);

    /** 标记为已回答 */
    void markAnswered(String id);

    /** 退订（用户 followup_enabled=0） */
    void unsubscribe(String token);

    /**
     * 判定触发时间是否落在静默时段，是则顺延到静默结束。
     * 纯逻辑，便于单测。
     */
    java.util.Date adjustForQuietHours(java.util.Date triggerTime);

    /**
     * 将已发送超过 expireDays 未回答的任务标记为 EXPIRED。
     */
    int expireOverdue(java.util.Date now);
}
