package com.zhli.baymd.rag.core.agent;

import com.zhli.baymd.framework.convention.ChatMessage;
import com.zhli.baymd.rag.testbase.MockLLMService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReActLoop 单元测试 — 验证 think→tool→observe 循环逻辑。
 */
@DisplayName("ReActLoop 单元测试")
class ReActLoopTest {

    private MockLLMService mockLLM;
    private AgentToolRegistry toolRegistry;
    private ReActLoop loop;

    @BeforeEach
    void setUp() {
        mockLLM = new MockLLMService();
        toolRegistry = new AgentToolRegistry(List.of());

        // 注册一个 mock 知识库检索工具
        toolRegistry.register(new MockSearchTool());

        loop = ReActLoop.builder()
                .llmService(mockLLM)
                .toolRegistry(toolRegistry)
                .guardrailsFactory(null)
                .maxIterations(5)
                .build();
    }

    // ==================== 工具调用解析 ====================

    @Test
    @DisplayName("解析 tool_call — 标准格式")
    void shouldParseToolCall() {
        String response = "我需要搜索知识库。\n"
                + "<tool_call>\n"
                + "{\"name\": \"search_knowledge_base\", \"arguments\": {\"query\": \"高血压饮食\"}}\n"
                + "</tool_call>";

        var result = loop.parseToolCall(response);

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("search_knowledge_base");
        assertThat(result.get().arguments()).containsEntry("query", "高血压饮食");
    }

    @Test
    @DisplayName("解析 tool_call — 无工具调用时应返回空")
    void shouldReturnEmptyWhenNoToolCall() {
        String response = "根据我的知识，高血压患者应该注意...";

        var result = loop.parseToolCall(response);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("解析 tool_call — 数值参数")
    void shouldParseNumericArguments() {
        String response = "<tool_call>\n"
                + "{\"name\": \"search_knowledge_base\", \"arguments\": {\"query\": \"头痛\", \"top_k\": 10}}\n"
                + "</tool_call>";

        var result = loop.parseToolCall(response);

        assertThat(result).isPresent();
        assertThat(result.get().arguments()).containsEntry("top_k", 10);
    }

    // ==================== 单轮（无工具调用） ====================

    @Test
    @DisplayName("无工具调用 — 直接返回最终答案")
    void shouldReturnDirectAnswer() {
        mockLLM.enqueueText("你好！有什么可以帮助你的？");

        List<ChatMessage> messages = List.of(
                ChatMessage.system("你是一个助手"),
                ChatMessage.user("你好")
        );

        ReActLoop.ReActResult result = loop.execute(messages);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFinalAnswer()).contains("帮助");
        assertThat(result.getIterationCount()).isEqualTo(1);
        assertThat(result.getSteps().get(0).toolCall()).isNull(); // 无工具调用
    }

    // ==================== 多轮（含工具调用） ====================

    @Test
    @DisplayName("工具调用 — 一轮工具调用后返回最终答案")
    void shouldExecuteToolAndReturnAnswer() {
        // 第1轮：LLM 返回工具调用
        mockLLM.enqueueText(
                "<tool_call>\n"
                        + "{\"name\": \"mock_search\", \"arguments\": {\"query\": \"test\"}}\n"
                        + "</tool_call>"
        );
        // 第2轮：LLM 基于工具结果返回最终答案
        mockLLM.enqueueText("根据搜索结果，这是一个测试回答。");

        List<ChatMessage> messages = List.of(
                ChatMessage.system("你是一个助手"),
                ChatMessage.user("帮我搜索")
        );

        ReActLoop.ReActResult result = loop.execute(messages);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFinalAnswer()).contains("测试回答");
        assertThat(result.getIterationCount()).isEqualTo(2);
        // 第1步应该有工具调用
        assertThat(result.getSteps().get(0).toolCall()).isNotNull();
        assertThat(result.getSteps().get(0).toolCall().name()).isEqualTo("mock_search");
        // 第2步是最终答案
        assertThat(result.getSteps().get(1).toolCall()).isNull();
    }

    // ==================== 最大迭代 ====================

    @Test
    @DisplayName("达到最大迭代次数 — 强制总结")
    void shouldForceSummarizeAtMaxIterations() {
        // 每轮都返回工具调用（死循环模拟）
        for (int i = 0; i < 5; i++) {
            mockLLM.enqueueText(
                    "<tool_call>\n"
                            + "{\"name\": \"mock_search\", \"arguments\": {\"query\": \"query" + i + "\"}}\n"
                            + "</tool_call>"
            );
        }
        // 最后一轮是强制总结
        mockLLM.enqueueText("已达到能力上限，基于现有信息回答...");

        List<ChatMessage> messages = List.of(
                ChatMessage.system("你是一个助手"),
                ChatMessage.user("帮我查东西")
        );

        ReActLoop.ReActResult result = loop.execute(messages);

        assertThat(result.isTruncated()).isTrue();
        assertThat(result.getIterationCount()).isGreaterThanOrEqualTo(5);
    }

    // ==================== 工具不存在 ====================

    @Test
    @DisplayName("工具不存在 — 返回错误 observation")
    void shouldHandleUnknownTool() {
        mockLLM.enqueueText(
                "<tool_call>\n"
                        + "{\"name\": \"non_existent_tool\", \"arguments\": {\"query\": \"test\"}}\n"
                        + "</tool_call>"
        );
        mockLLM.enqueueText("工具调用失败后，我尽力回答...");

        List<ChatMessage> messages = List.of(
                ChatMessage.system("你是一个助手"),
                ChatMessage.user("帮我")
        );

        ReActLoop.ReActResult result = loop.execute(messages);

        assertThat(result.isSuccess()).isTrue();
        // 工具结果应该是失败的
        var toolStep = result.getSteps().get(0);
        assertThat(toolStep.toolResult().isSuccess()).isFalse();
        assertThat(toolStep.toolResult().getErrorMessage()).contains("不存在");
    }

    // ==================== Mock 工具 ====================

    /**
     * Mock 工具 — 模拟知识库检索
     */
    static class MockSearchTool implements AgentTool {
        @Override
        public String getName() { return "mock_search"; }

        @Override
        public String getDescription() { return "Mock search tool for testing"; }

        @Override
        public Map<String, Object> getParametersSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "query", Map.of("type", "string", "description", "Search query")
                    ),
                    "required", List.of("query")
            );
        }

        @Override
        public AgentToolResult execute(Map<String, Object> parameters) {
            String query = (String) parameters.getOrDefault("query", "");
            return AgentToolResult.success("mock_search",
                    "Mock 搜索结果: 这是关于 \"" + query + "\" 的模拟文档内容。",
                    3, 5);
        }

        @Override
        public String getType() { return "mock"; }
    }
}
