package com.zhli.baymd.rag.config;

import com.zhli.baymd.rag.service.RuntimeConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * 运行时配置启动回填器 — 在所有单例 Bean 创建完成后，将 DB 中的分区配置应用到对应 Bean。
 *
 * <p>AI 配置（{@code AIModelProperties}）与 RAG 超参数均为请求期惰性读取，
 * 在此处回填即可在重启后生效。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuntimeConfigApplier implements SmartInitializingSingleton {

    private final RuntimeConfigService runtimeConfigService;

    @Override
    public void afterSingletonsInstantiated() {
        try {
            runtimeConfigService.applyConfig();
        } catch (Exception e) {
            // DB 配置表缺失等场景不应阻塞应用启动，配置回退到 yaml 默认值
            log.warn("运行时配置回填失败，使用 yaml 默认配置: {}", e.getMessage());
        }
    }
}
