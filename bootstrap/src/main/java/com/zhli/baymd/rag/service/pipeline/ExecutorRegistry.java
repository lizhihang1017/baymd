package com.zhli.baymd.rag.service.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 执行器注册表 — 管理所有 {@link ConversationExecutor} 实现。
 *
 * <p>Spring 自动注入所有 ConversationExecutor Bean，按 {@link ExecutionMode} 索引。</p>
 */
@Slf4j
@Component
public class ExecutorRegistry {

    private final Map<ExecutionMode, ConversationExecutor> executors = new EnumMap<>(ExecutionMode.class);

    /**
     * Spring 构造器注入：收集所有 ConversationExecutor 实现
     */
    public ExecutorRegistry(List<ConversationExecutor> executorList) {
        for (ConversationExecutor executor : executorList) {
            executors.put(executor.getMode(), executor);
            log.info("执行器已注册: mode={}, class={}",
                    executor.getMode(), executor.getClass().getSimpleName());
        }
        log.info("执行器注册表初始化完成，共 {} 个执行器", executors.size());
    }

    /**
     * 根据模式获取执行器
     */
    public ConversationExecutor get(ExecutionMode mode) {
        ConversationExecutor executor = executors.get(mode);
        if (executor == null) {
            throw new IllegalStateException("未注册的执行模式: " + mode);
        }
        return executor;
    }

    /**
     * 查找第一个 supports() 返回 true 的执行器（fallback 策略）
     */
    public ConversationExecutor resolve(StreamChatContext ctx) {
        // 按枚举声明顺序（即优先级）：EMERGENCY > CLARIFICATION > SYSTEM_ONLY > AGENT > RAG
        for (ExecutionMode mode : ExecutionMode.values()) {
            ConversationExecutor executor = executors.get(mode);
            if (executor != null && executor.supports(ctx)) {
                return executor;
            }
        }
        // 最终 fallback 到 RAG
        return get(ExecutionMode.RAG);
    }
}
