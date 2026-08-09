/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zhli.baymd.rag.core.mcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;

/**
 * MCP 客户端自动配置 — 启动时委托 {@link McpClientManager} 连接 yaml + DB 中配置的 MCP Server。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(McpClientProperties.class)
public class McpClientAutoConfiguration {

    private final McpClientProperties properties;
    private final McpClientManager mcpClientManager;

    @PreDestroy
    public void destroy() {
        // 全部断开由 McpClientManager 持有的 client 管理，此处无需额外处理
    }
}
