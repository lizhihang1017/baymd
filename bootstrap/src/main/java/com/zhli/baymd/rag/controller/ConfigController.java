package com.zhli.baymd.rag.controller;

import com.zhli.baymd.framework.auth.RequireAdmin;
import com.zhli.baymd.framework.convention.Result;
import com.zhli.baymd.framework.web.Results;
import com.zhli.baymd.rag.core.prompt.PromptSceneService;
import com.zhli.baymd.rag.service.RuntimeConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运行时配置控制器（管理员）— 查看/保存 DB 配置分区（ai / rag / prompt / skill），重启后生效。
 */
@Slf4j
@RequireAdmin
@RestController
@RequiredArgsConstructor
public class ConfigController {

    private final RuntimeConfigService runtimeConfigService;
    private final PromptSceneService promptSceneService;

    /** 读取配置分区 JSON */
    @GetMapping("/admin/config/{section}")
    public Result<String> get(@PathVariable String section) {
        return Results.success(runtimeConfigService.getSection(section));
    }

    /** 保存配置分区 JSON（重启后生效） */
    @PutMapping("/admin/config/{section}")
    public Result<Void> save(@PathVariable String section, @RequestBody String json) {
        runtimeConfigService.saveSection(section, json);
        return Results.success();
    }

    /** 场景化提示词目录 — 每个 LLM 调用场景的元信息 + 默认提示词文本 + 默认超参 + 当前覆盖 */
    @GetMapping("/admin/config/prompt-scenes")
    public Result<java.util.List<PromptSceneService.SceneDefaultView>> promptScenes() {
        return Results.success(promptSceneService.listDefaultViews());
    }
}
