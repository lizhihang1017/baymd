package com.zhli.baymd.rag.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 主动随访任务。
 *
 * <p>状态机：PENDING →（扫描到期）→ SENT →（用户回答）→ ANSWERED；
 * SENT 后 expireDays 未回答 → EXPIRED；用户退订/取消 → CANCELLED。</p>
 */
@Data
@TableName("t_followup_task")
public class FollowUpTaskDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String userId;

    private String conversationId;

    /** 随访主题（频控去重用） */
    private String topic;

    /** 随访问题（App 内展示，邮件不发送原文） */
    private String question;

    private Date triggerTime;

    /** PENDING / SENT / ANSWERED / CANCELLED / EXPIRED */
    private String status;

    /** 通知通道：email（可插拔 SPI） */
    private String channel;

    /** 退订 token（免登录退订用） */
    private String unsubToken;

    /** 调度分布式锁 */
    private Date lockUntil;

    private Date sentTime;

    private Date answeredTime;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer deleted;
}
