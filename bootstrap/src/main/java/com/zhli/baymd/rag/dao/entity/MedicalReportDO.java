package com.zhli.baymd.rag.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhli.baymd.knowledge.dao.handler.JsonbTypeHandler;
import lombok.Data;

import java.util.Date;

/**
 * 医学检查报告（用户上传的化验单/检查单图片或 PDF）。
 *
 * <p>独立于知识库摄取流程，是用户个人报告，解析后结构化存储，
 * 供报告解读场景注入对话上下文。</p>
 */
@Data
@TableName("t_medical_report")
public class MedicalReportDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String userId;

    private String fileName;

    /** S3/本地存储对象 key（FileStorageService 返回的 url） */
    private String storageKey;

    private String mimeType;

    /** 提取的原始文本（Tika 或 VL 模型输出） */
    private String rawText;

    /** 结构化指标 JSON 数组：[{name,value,unit,refRange,flag}] */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String structured;

    /** 解析状态：PENDING / SUCCESS / FAILED */
    private String parseStatus;

    /** 解析失败时的错误信息 */
    private String errorMessage;

    private String createBy;

    private String updateBy;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer deleted;
}
