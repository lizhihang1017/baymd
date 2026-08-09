package com.zhli.baymd.admin.controller;

import cn.hutool.core.util.IdUtil;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
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

    /** 新增 Fact */
    @PostMapping("/admin/memory/fact")
    public Result<String> createFact(@RequestBody Map<String, Object> body) {
        String userId = String.valueOf(body.get("userId"));
        String factType = String.valueOf(body.get("factType"));
        String factText = String.valueOf(body.get("factText"));
        if (factText == null || factText.isBlank()) {
            throw new ClientException("Fact 内容不能为空");
        }
        float conf = body.get("confidence") == null ? 0.8f : Float.parseFloat(String.valueOf(body.get("confidence")));
        UserFactDO fact = UserFactDO.builder()
                .id(IdUtil.getSnowflakeNextIdStr())
                .userId(userId)
                .factType(factType == null || "null".equals(factType) ? "health" : factType)
                .factText(factText)
                .confidence(conf)
                .build();
        userFactMapper.insert(fact);
        log.info("管理员新增用户 Fact: userId={}, type={}", userId, fact.getFactType());
        return Results.success(fact.getId());
    }

    /** 编辑 Fact */
    @PutMapping("/admin/memory/fact/{id}")
    public Result<Void> updateFact(@PathVariable String id, @RequestBody Map<String, Object> body) {
        UserFactDO fact = userFactMapper.selectById(id);
        if (fact == null) {
            throw new ClientException("Fact 不存在");
        }
        if (body.get("factType") != null) {
            fact.setFactType(String.valueOf(body.get("factType")));
        }
        if (body.get("factText") != null) {
            String text = String.valueOf(body.get("factText"));
            if (text.isBlank()) {
                throw new ClientException("Fact 内容不能为空");
            }
            fact.setFactText(text);
        }
        if (body.get("confidence") != null) {
            fact.setConfidence(Float.parseFloat(String.valueOf(body.get("confidence"))));
        }
        fact.setUpdatedAt(new Date());
        userFactMapper.updateById(fact);
        log.info("管理员编辑用户 Fact: id={}", id);
        return Results.success();
    }

    /** 新增 Episode */
    @PostMapping("/admin/memory/episode")
    public Result<String> createEpisode(@RequestBody Map<String, Object> body) {
        String userId = String.valueOf(body.get("userId"));
        String title = body.get("title") == null ? "" : String.valueOf(body.get("title"));
        String summary = body.get("summary") == null ? "" : String.valueOf(body.get("summary"));
        if (summary.isBlank()) {
            throw new ClientException("Episode 摘要不能为空");
        }
        @SuppressWarnings("unchecked")
        List<String> topics = body.get("topics") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList() : List.of();
        UserEpisodeDO ep = UserEpisodeDO.builder()
                .id(IdUtil.getSnowflakeNextIdStr())
                .userId(userId)
                .title(title)
                .summary(summary)
                .topics(topics.toArray(new String[0]))
                .build();
        userEpisodeMapper.insert(ep);
        log.info("管理员新增用户 Episode: userId={}, title={}", userId, title);
        return Results.success(ep.getId());
    }

    /** 编辑 Episode */
    @PutMapping("/admin/memory/episode/{id}")
    public Result<Void> updateEpisode(@PathVariable String id, @RequestBody Map<String, Object> body) {
        UserEpisodeDO ep = userEpisodeMapper.selectById(id);
        if (ep == null) {
            throw new ClientException("Episode 不存在");
        }
        if (body.get("title") != null) {
            ep.setTitle(String.valueOf(body.get("title")));
        }
        if (body.get("summary") != null) {
            String summary = String.valueOf(body.get("summary"));
            if (summary.isBlank()) {
                throw new ClientException("Episode 摘要不能为空");
            }
            ep.setSummary(summary);
        }
        if (body.get("topics") instanceof List<?> list) {
            ep.setTopics(list.stream().map(String::valueOf).toArray(String[]::new));
        }
        userEpisodeMapper.updateById(ep);
        log.info("管理员编辑用户 Episode: id={}", id);
        return Results.success();
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
