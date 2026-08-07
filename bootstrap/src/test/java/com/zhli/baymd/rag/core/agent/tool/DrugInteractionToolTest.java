package com.zhli.baymd.rag.core.agent.tool;

import com.zhli.baymd.rag.core.agent.AgentToolResult;
import com.zhli.baymd.rag.dao.entity.DrugInteractionDO;
import com.zhli.baymd.rag.dao.mapper.DrugInteractionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link DrugInteractionTool} 单测 — 归一化、结果格式化、未收录兜底。
 * <p>双向匹配在 SQL 层实现，此处 mock mapper 验证工具调用约定与输出。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("药物相互作用工具测试")
class DrugInteractionToolTest {

    @Mock
    private DrugInteractionMapper mapper;

    @InjectMocks
    private DrugInteractionTool tool;

    private DrugInteractionDO warfarinAspirin() {
        DrugInteractionDO d = new DrugInteractionDO();
        d.setDrugA("华法林");
        d.setDrugB("阿司匹林");
        d.setSeverity("严重");
        d.setDescription("同服显著增加出血风险。");
        return d;
    }

    @Test
    @DisplayName("命中：返回严重程度与描述 + 免责声明")
    void hit_formatsResult() {
        when(mapper.findPair(eq("华法林"), eq("阿司匹林")))
                .thenReturn(List.of(warfarinAspirin()));

        AgentToolResult r = tool.execute(Map.of("drug_a", "华法林", "drug_b", "阿司匹林"));

        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getDocumentCount()).isEqualTo(1);
        assertThat(r.getContent())
                .contains("华法林 + 阿司匹林")
                .contains("严重")
                .contains("出血风险")
                .contains("遵医嘱");
    }

    @Test
    @DisplayName("药名归一化：大小写/空格不影响查询 key")
    void normalize_beforeQuery() {
        when(mapper.findPair(eq("华法林"), eq("阿司匹林")))
                .thenReturn(List.of());

        // 中文小写为原样，但 trim 生效
        tool.execute(Map.of("drug_a", " 华法林 ", "drug_b", "阿司匹林"));

        // 英文归一化
        when(mapper.findPair(eq("warfarin"), eq("aspirin")))
                .thenReturn(List.of());
        tool.execute(Map.of("drug_a", "Warfarin", "drug_b", "ASPIRIN"));
    }

    @Test
    @DisplayName("未收录：明确返回未收录 + 建议咨询药师，不编造")
    void miss_returnSafeMessage() {
        when(mapper.findPair(eq("维生素c"), eq("维生素b12")))
                .thenReturn(List.of());

        AgentToolResult r = tool.execute(Map.of("drug_a", "维生素C", "drug_b", "维生素B12"));

        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getContent())
                .contains("未收录")
                .contains("建议咨询药师")
                .doesNotContain("可以同服", "无相互作用");
    }

    @Test
    @DisplayName("两药相同：跳过查询")
    void sameDrug() {
        AgentToolResult r = tool.execute(Map.of("drug_a", "阿司匹林", "drug_b", "阿司匹林"));
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getContent()).contains("两种药物相同");
    }

    @Test
    @DisplayName("缺少参数：失败")
    void missingParam() {
        AgentToolResult r = tool.execute(Map.of("drug_a", "阿司匹林"));
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getErrorMessage()).contains("药名");
    }

    @Test
    @DisplayName("多条命中：全部列出")
    void multipleHits() {
        DrugInteractionDO d1 = warfarinAspirin();
        DrugInteractionDO d2 = new DrugInteractionDO();
        d2.setDrugA("华法林");
        d2.setDrugB("布洛芬");
        d2.setSeverity("严重");
        d2.setDescription("消化道出血风险升高。");
        when(mapper.findPair(eq("华法林"), eq("布洛芬")))
                .thenReturn(List.of(d1, d2));

        AgentToolResult r = tool.execute(Map.of("drug_a", "华法林", "drug_b", "布洛芬"));
        assertThat(r.getDocumentCount()).isEqualTo(2);
        assertThat(r.getContent()).contains("共 2 条", "布洛芬");
    }

    @Test
    @DisplayName("normalize 工具方法：trim + 小写")
    void normalize_unit() {
        assertThat(DrugInteractionTool.normalize("  Warfarin ")).isEqualTo("warfarin");
        assertThat(DrugInteractionTool.normalize("华法林")).isEqualTo("华法林");
    }

    @Test
    @DisplayName("mapper 异常：返回失败结果，不抛出")
    void mapperThrows_returnsError() {
        when(mapper.findPair(eq("a"), eq("b")))
                .thenThrow(new RuntimeException("DB down"));
        AgentToolResult r = tool.execute(Map.of("drug_a", "A", "drug_b", "B"));
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getErrorMessage()).contains("查询异常");
    }
}
