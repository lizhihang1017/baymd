package com.zhli.baymd.rag.controller;

import com.zhli.baymd.framework.convention.Result;
import com.zhli.baymd.framework.context.UserContext;
import com.zhli.baymd.framework.web.Results;
import com.zhli.baymd.rag.dao.entity.FollowUpTaskDO;
import com.zhli.baymd.rag.service.FollowUpTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 主动随访控制器（Phase 2）— 用户查看随访、深链取问题、标记已答、免登录退订。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class FollowUpController {

    private final FollowUpTaskService followUpTaskService;

    /** 用户的随访任务列表 */
    @GetMapping("/followup/list")
    public Result<List<FollowUpTaskDO>> list() {
        return Results.success(followUpTaskService.listByUser(UserContext.getUserId()));
    }

    /** 深链取随访问题（点击后前端预填输入框） */
    @GetMapping("/followup/{id}")
    public Result<FollowUpTaskDO> get(@PathVariable String id) {
        FollowUpTaskDO task = followUpTaskService.getById(id);
        if (task == null) {
            return Results.success(null);
        }
        return Results.success(task);
    }

    /** 用户回答随访后标记已答 */
    @PostMapping("/followup/{id}/answered")
    public Result<Void> answered(@PathVariable String id) {
        followUpTaskService.markAnswered(id);
        return Results.success();
    }

    /** 免登录退订（token 为任务表随机串） */
    @GetMapping("/followup/unsubscribe")
    public Result<Void> unsubscribe(@RequestParam("token") String token) {
        followUpTaskService.unsubscribe(token);
        return Results.success();
    }
}
