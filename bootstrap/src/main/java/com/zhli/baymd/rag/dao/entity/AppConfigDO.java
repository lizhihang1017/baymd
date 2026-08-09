package com.zhli.baymd.rag.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.zhli.baymd.knowledge.dao.handler.JsonbTypeHandler;
import lombok.Data;

import java.util.Date;

/**
 * 运行时配置（DB 覆盖 yaml，重启生效）。
 *
 * <p>section 为配置分区（如 ai / rag），config_value 存该分区的 JSON，
 * 启动时由 {@code RuntimeConfigService.applyConfig()} 回填到对应 @ConfigurationProperties Bean。</p>
 */
@Data
@TableName("t_app_config")
public class AppConfigDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 配置分区：ai / rag */
    private String section;

    /** 分区 JSON 配置 */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String configValue;

    private Date updatedAt;

    @TableLogic
    private Integer deleted;
}
