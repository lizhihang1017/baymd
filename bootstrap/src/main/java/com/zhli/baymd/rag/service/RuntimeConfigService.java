package com.zhli.baymd.rag.service;

/**
 * 运行时配置服务 — 配置存 DB，重启后生效。
 *
 * <p>支持分区配置（如 ai / rag）：管理员通过 UI 编辑并保存到 {@code t_app_config}，
 * 重启时 {@code applyConfig()} 将 DB 配置回填到对应 @ConfigurationProperties Bean。</p>
 */
public interface RuntimeConfigService {

    /** 读取分区配置 JSON，无则返回 null */
    String getSection(String section);

    /** 保存分区配置 JSON（按 section upsert） */
    void saveSection(String section, String json);

    /** 启动时把 DB 中所有分区配置回填到对应 Bean */
    void applyConfig();
}
