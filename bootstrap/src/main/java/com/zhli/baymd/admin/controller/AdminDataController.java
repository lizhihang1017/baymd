package com.zhli.baymd.admin.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhli.baymd.framework.auth.RequireAdmin;
import com.zhli.baymd.framework.convention.Result;
import com.zhli.baymd.framework.exception.ClientException;
import com.zhli.baymd.framework.web.Results;
import com.zhli.baymd.rag.dao.entity.FollowUpTaskDO;
import com.zhli.baymd.rag.dao.entity.MedicalReportDO;
import com.zhli.baymd.rag.dao.entity.MessageFeedbackDO;
import com.zhli.baymd.rag.dao.mapper.FollowUpTaskMapper;
import com.zhli.baymd.rag.dao.mapper.MedicalReportMapper;
import com.zhli.baymd.rag.dao.mapper.MessageFeedbackMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据管理（管理员）— 随访任务 / 用户反馈 / 体检报告 的查看与操作。
 */
@Slf4j
@RequireAdmin
@RestController
@RequiredArgsConstructor
public class AdminDataController {

    private final FollowUpTaskMapper followUpTaskMapper;
    private final MessageFeedbackMapper feedbackMapper;
    private final MedicalReportMapper reportMapper;
    private final JdbcTemplate jdbcTemplate;

    // ==================== 随访任务 ====================

    /** 随访任务列表（可按状态过滤） */
    @GetMapping("/admin/followup/tasks")
    public Result<List<Map<String, Object>>> followupTasks(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit) {
        String sql = """
                SELECT t.id, t.user_id AS userId, u.username, t.topic, t.question,
                       t.trigger_time AS triggerTime, t.status, t.channel,
                       t.sent_time AS sentTime, t.answered_time AS answeredTime,
                       t.create_time AS createTime
                FROM t_followup_task t
                LEFT JOIN t_user u ON u.id = t.user_id
                WHERE t.deleted = 0 %s
                ORDER BY t.create_time DESC
                LIMIT %d
                """.formatted(StrUtil.isBlank(status) ? "" : "AND t.status = '" + status + "'",
                Math.min(Math.max(limit, 1), 500));
        return Results.success(jdbcTemplate.queryForList(sql));
    }

    /** 随访统计 */
    @GetMapping("/admin/followup/stats")
    public Result<Map<String, Object>> followupStats() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("byStatus", jdbcTemplate.queryForList(
                "SELECT status, COUNT(*) AS cnt FROM t_followup_task WHERE deleted=0 GROUP BY status"));
        r.put("total", jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_followup_task WHERE deleted=0", Long.class));
        return Results.success(r);
    }

    /** 手动触发随访任务（置为可扫描状态并清空锁） */
    @PostMapping("/admin/followup/tasks/{id}/trigger")
    public Result<Void> triggerFollowup(@PathVariable String id) {
        FollowUpTaskDO task = followUpTaskMapper.selectById(id);
        if (task == null) {
            throw new ClientException("随访任务不存在");
        }
        followUpTaskMapper.update(null, new LambdaUpdateWrapper<FollowUpTaskDO>()
                .eq(FollowUpTaskDO::getId, id)
                .set(FollowUpTaskDO::getStatus, "PENDING")
                .set(FollowUpTaskDO::getTriggerTime, new Date())
                .set(FollowUpTaskDO::getLockUntil, null));
        log.info("管理员手动触发随访任务: id={}", id);
        return Results.success();
    }

    /** 取消随访任务 */
    @PostMapping("/admin/followup/tasks/{id}/cancel")
    public Result<Void> cancelFollowup(@PathVariable String id) {
        FollowUpTaskDO task = followUpTaskMapper.selectById(id);
        if (task == null) {
            throw new ClientException("随访任务不存在");
        }
        followUpTaskMapper.update(null, new LambdaUpdateWrapper<FollowUpTaskDO>()
                .eq(FollowUpTaskDO::getId, id)
                .set(FollowUpTaskDO::getStatus, "CANCELLED"));
        log.info("管理员取消随访任务: id={}", id);
        return Results.success();
    }

    /** 删除随访任务 */
    @DeleteMapping("/admin/followup/tasks/{id}")
    public Result<Void> deleteFollowup(@PathVariable String id) {
        followUpTaskMapper.deleteById(id);
        return Results.success();
    }

    // ==================== 用户反馈 ====================

    /** 反馈列表（点赞/踩 + 原因/评论,关联用户与问题） */
    @GetMapping("/admin/feedback")
    public Result<List<Map<String, Object>>> feedback(
            @RequestParam(required = false) Integer vote,
            @RequestParam(defaultValue = "100") int limit) {
        String sql = """
                SELECT f.id, f.user_id AS userId, u.username, f.vote,
                       f.reason, f.comment, f.create_time AS createTime,
                       c.title AS conversationTitle,
                       SUBSTRING(m.content, 1, 200) AS answerSnippet
                FROM t_message_feedback f
                LEFT JOIN t_user u ON u.id = f.user_id
                LEFT JOIN t_conversation c ON c.id = f.conversation_id
                LEFT JOIN t_message m ON m.id = f.message_id
                WHERE f.deleted = 0 %s
                ORDER BY f.create_time DESC
                LIMIT %d
                """.formatted(vote == null ? "" : "AND f.vote = " + vote,
                Math.min(Math.max(limit, 1), 500));
        return Results.success(jdbcTemplate.queryForList(sql));
    }

    /** 反馈统计（赞/踩分布 + 踩的原因聚合） */
    @GetMapping("/admin/feedback/stats")
    public Result<Map<String, Object>> feedbackStats() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("byVote", jdbcTemplate.queryForList(
                "SELECT vote, COUNT(*) AS cnt FROM t_message_feedback WHERE deleted=0 GROUP BY vote"));
        r.put("byReason", jdbcTemplate.queryForList(
                "SELECT reason, COUNT(*) AS cnt FROM t_message_feedback WHERE deleted=0 AND reason IS NOT NULL GROUP BY reason ORDER BY cnt DESC"));
        return Results.success(r);
    }

    /** 删除反馈 */
    @DeleteMapping("/admin/feedback/{id}")
    public Result<Void> deleteFeedback(@PathVariable String id) {
        feedbackMapper.deleteById(id);
        return Results.success();
    }

    // ==================== 体检报告 ====================

    /** 报告列表 */
    @GetMapping("/admin/reports")
    public Result<List<Map<String, Object>>> reports(@RequestParam(defaultValue = "100") int limit) {
        String sql = """
                SELECT r.id, r.user_id AS userId, u.username, r.file_name AS fileName,
                       r.mime_type AS mimeType, r.parse_status AS parseStatus,
                       r.error_message AS errorMessage, r.create_time AS createTime
                FROM t_medical_report r
                LEFT JOIN t_user u ON u.id = r.user_id
                WHERE r.deleted = 0
                ORDER BY r.create_time DESC
                LIMIT %d
                """.formatted(Math.min(Math.max(limit, 1), 500));
        return Results.success(jdbcTemplate.queryForList(sql));
    }

    /** 报告详情（原文 + 结构化 JSON） */
    @GetMapping("/admin/reports/{id}")
    public Result<MedicalReportDO> reportDetail(@PathVariable String id) {
        MedicalReportDO report = reportMapper.selectById(id);
        if (report == null) {
            throw new ClientException("报告不存在");
        }
        return Results.success(report);
    }

    /** 删除报告 */
    @DeleteMapping("/admin/reports/{id}")
    public Result<Void> deleteReport(@PathVariable String id) {
        reportMapper.deleteById(id);
        return Results.success();
    }

    /** 按报告 id 查询关联的追问消息（可选扩展:报告解读会话） */
    @GetMapping("/admin/reports/{id}/conversations")
    public Result<List<Map<String, Object>>> reportConversations(@PathVariable String id) {
        return Results.success(jdbcTemplate.queryForList("""
                SELECT DISTINCT m.conversation_id AS conversationId, c.title,
                       COUNT(*) OVER (PARTITION BY m.conversation_id) AS msgCount
                FROM t_message m
                LEFT JOIN t_conversation c ON c.id = m.conversation_id
                WHERE m.conversation_id IN (
                    SELECT conversation_id FROM t_message
                    WHERE content LIKE '%报告%' AND deleted = 0
                ) AND m.deleted = 0
                LIMIT 20
                """));
    }
}
