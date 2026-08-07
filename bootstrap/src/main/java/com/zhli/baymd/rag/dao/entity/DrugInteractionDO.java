package com.zhli.baymd.rag.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 药物相互作用记录。
 *
 * <p>每条记录存储一对药物（drug_a / drug_b），查询时双向匹配。
 * 药名归一化存储（trim + 小写），中文药名小写为原样。</p>
 */
@Data
@TableName("t_drug_interaction")
public class DrugInteractionDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 药物 A（归一化：trim + 小写） */
    private String drugA;

    /** 药物 B（归一化：trim + 小写） */
    private String drugB;

    /** 严重程度：严重 / 中度 / 轻度 */
    private String severity;

    /** 相互作用描述与临床建议 */
    private String description;

    private String createBy;

    private String updateBy;

    private Date createTime;

    private Date updateTime;

    /** 逻辑删除 0：正常 1：删除 */
    @TableLogic
    private Integer deleted;
}
