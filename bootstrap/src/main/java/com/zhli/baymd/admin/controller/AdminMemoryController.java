package com.zhli.baymd.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhli.baymd.framework.auth.RequireAdmin;
import com.zhli.baymd.framework.convention.Result;
import com.zhli.baymd.framework.exception.ClientException;
import com.zhli.baymd.framework.web.Results;
import com.zhli.baymd.rag.dao.entity.UserEpisodeDO;
import com.zhli.baymd.rag.dao.entity.UserFactDO;
import com.zhli.baymd.rag.dao.mapper.UserEpisodeMapper;
import com.zhli.baymd.rag.dao.mapper.UserFactMapper;
import com.zhli.baymd.user.dao.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户记忆管理（管理员）— 查看/删除所有用户的记忆画像（Fact + Episode）。
 */
@Slf4j
@RequireAdmin
@RestController
@RequiredArgsConstructor
public class AdminMemoryController {

    private final UserFactMapper userFactMapper;
    private final UserEpisodeMapper userEpisodeMapper;
    private final UserMapper userMapper;
    private final JdbcTemplate jdbcTemplate;

    /** 有记忆的用户列表（含 fact/episode 数量） */
    @GetMapping("/admin/memory/users")
    public Result<List<Map<String, Object>>> memoryUsers() {
        String sql = """
                SELECT u.id AS userId, u.username, u.email,
                  (SELECT COUNT(*) FROM t_user_fact f WHERE f.user_id = u.id) AS factCount,
                  (SELECT COUNT(*) FROM t_user_episode e WHERE e.user_id = u.id) AS episodeCount
                FROM t_user u
                WHERE EXISTS (SELECT 1 FROM t_user_fact f WHERE f.user_id = u.id)
                   OR EXISTS (SELECT 1 FROM t_user_episode e WHERE e.user_id = u.id)
                ORDER BY factCount DESC, episodeCount DESC
                """;
        return Results.success(jdbcTemplate.queryForList(sql));
    }

    /** 某用户的 Fact 列表 */
    @GetMapping("/admin/memory/facts")
    public Result<List<UserFactDO>> facts(@RequestParam String userId) {
        return Results.success(userFactMapper.selectList(new LambdaQueryWrapper<UserFactDO>()
                .eq(UserFactDO::getUserId, userId)
                .orderByDesc(UserFactDO::getCreatedAt)));
    }

    /** 某用户的 Episode 列表 */
    @GetMapping("/admin/memory/episodes")
    public Result<List<UserEpisodeDO>> episodes(@RequestParam String userId) {
        return Results.success(userEpisodeMapper.selectList(new LambdaQueryWrapper<UserEpisodeDO>()
                .eq(UserEpisodeDO::getUserId, userId)
                .orderByDesc(UserEpisodeDO::getCreatedAt)));
    }

    /** 删除 Fact（级联删除向量） */
    @DeleteMapping("/admin/memory/fact/{id}")
    public Result<Void> deleteFact(@PathVariable String id) {
        UserFactDO fact = userFactMapper.selectById(id);
        if (fact == null) {
            throw new ClientException("Fact 不存在");
        }
        userFactMapper.deleteById(id); // t_user_fact_vector 外键级联删除
        log.info("管理员删除用户 Fact: id={}, userId={}", id, fact.getUserId());
        return Results.success();
    }

    /** 删除 Episode（级联删除向量） */
    @DeleteMapping("/admin/memory/episode/{id}")
    public Result<Void> deleteEpisode(@PathVariable String id) {
        UserEpisodeDO ep = userEpisodeMapper.selectById(id);
        if (ep == null) {
            throw new ClientException("Episode 不存在");
        }
        userEpisodeMapper.deleteById(id);
        log.info("管理员删除用户 Episode: id={}, userId={}", id, ep.getUserId());
        return Results.success();
    }
}
