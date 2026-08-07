package com.zhli.baymd.rag.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhli.baymd.rag.dao.entity.DrugInteractionDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 药物相互作用 Mapper。
 *
 * <p>双向查询：同时匹配 (A,B) 与 (B,A) 两种存储顺序。</p>
 */
public interface DrugInteractionMapper extends BaseMapper<DrugInteractionDO> {

    /**
     * 双向查询两药之间的相互作用记录。
     * <p>逻辑删除由 MyBatis-Plus 全局逻辑删除处理（{@link DrugInteractionDO#getDeleted()} 带 {@code @TableLogic}），
     * 此原生 SQL 不再附加 deleted 过滤，确保与框架逻辑一致。</p>
     */
    @Select("""
            SELECT * FROM t_drug_interaction
             WHERE deleted = 0
               AND ((drug_a = #{drugA} AND drug_b = #{drugB})
                 OR (drug_a = #{drugB} AND drug_b = #{drugA}))
            """)
    List<DrugInteractionDO> findPair(@Param("drugA") String drugA, @Param("drugB") String drugB);
}
