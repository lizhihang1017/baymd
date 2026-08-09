package com.zhli.baymd.rag.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_user_episode")
public class UserEpisodeDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String userId;

    private String conversationId;

    private String title;

    private String summary;

    /** PG array: topics TEXT[] */
    @TableField(typeHandler = com.zhli.baymd.rag.dao.handler.PgStringArrayTypeHandler.class)
    private String[] topics;

    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
}
