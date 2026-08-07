package com.zhli.baymd.rag.core.agent.tool;

import com.zhli.baymd.framework.context.LoginUser;
import com.zhli.baymd.framework.context.UserContext;
import com.zhli.baymd.rag.core.agent.AgentToolResult;
import com.zhli.baymd.rag.dao.entity.MedicalReportDO;
import com.zhli.baymd.rag.dao.mapper.MedicalReportMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link ReportQueryTool} 单测。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("报告查询工具测试")
class ReportQueryToolTest {

    @Mock private MedicalReportMapper medicalReportMapper;

    @InjectMocks
    private ReportQueryTool tool;

    @BeforeEach
    void setUp() {
        UserContext.set(LoginUser.builder().userId("u1").build());
    }

    private MedicalReportDO report(String structured) {
        MedicalReportDO r = new MedicalReportDO();
        r.setId("r1");
        r.setUserId("u1");
        r.setParseStatus("SUCCESS");
        r.setStructured(structured);
        return r;
    }

    @Test
    @DisplayName("命中：返回匹配指标记录")
    void hit() {
        when(medicalReportMapper.selectList(any())).thenReturn(List.of(report(
                "[{\"name\":\"血糖\",\"value\":\"8.2\",\"unit\":\"mmol/L\",\"refRange\":\"3.9-6.1\",\"flag\":\"偏高\"}]")));

        AgentToolResult r = tool.execute(Map.of("indicator", "血糖"));

        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getContent()).contains("血糖", "8.2", "mmol/L", "偏高");
    }

    @Test
    @DisplayName("多报告对比：两条血糖记录")
    void multipleReports() {
        when(medicalReportMapper.selectList(any())).thenReturn(List.of(
                report("[{\"name\":\"血糖\",\"value\":\"8.2\",\"unit\":\"mmol/L\",\"refRange\":\"3.9-6.1\",\"flag\":\"偏高\"}]"),
                report("[{\"name\":\"血糖\",\"value\":\"5.6\",\"unit\":\"mmol/L\",\"refRange\":\"3.9-6.1\",\"flag\":\"正常\"}]")
        ));

        AgentToolResult r = tool.execute(Map.of("indicator", "血糖"));

        assertThat(r.getContent()).contains("共 2 条", "8.2", "5.6");
    }

    @Test
    @DisplayName("未命中 → 明确告知")
    void miss() {
        when(medicalReportMapper.selectList(any())).thenReturn(List.of(report(
                "[{\"name\":\"白细胞\",\"value\":\"6.8\"}]")));

        AgentToolResult r = tool.execute(Map.of("indicator", "血糖"));

        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getContent()).contains("未查询到");
    }

    @Test
    @DisplayName("结构化字段非法 → 跳过不报错")
    void invalidStructured_skips() {
        when(medicalReportMapper.selectList(any())).thenReturn(List.of(
                report("[{\"name\":\"白细胞\",\"value\":\"6.8\"}]"),
                report("这不是合法JSON")
        ));

        AgentToolResult r = tool.execute(Map.of("indicator", "白细胞"));

        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getContent()).contains("白细胞");
    }

    @Test
    @DisplayName("缺少 indicator → 失败")
    void missingIndicator() {
        AgentToolResult r = tool.execute(Map.of());
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getErrorMessage()).contains("指标名");
    }
}
