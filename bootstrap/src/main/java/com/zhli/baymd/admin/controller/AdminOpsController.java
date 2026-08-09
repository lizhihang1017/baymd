package com.zhli.baymd.admin.controller;

import com.zhli.baymd.framework.auth.RequireAdmin;
import com.zhli.baymd.framework.convention.Result;
import com.zhli.baymd.framework.web.Results;
import com.zhli.baymd.rag.dao.mapper.ConversationMapper;
import com.zhli.baymd.rag.dao.mapper.ConversationMessageMapper;
import com.zhli.baymd.user.dao.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运营看板（管理员）— 在线用户、总量、Token 消耗等统计。
 */
@Slf4j
@RequireAdmin
@RestController
@RequiredArgsConstructor
public class AdminOpsController {

    private final JdbcTemplate jdbcTemplate;
    private final UserMapper userMapper;
    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;

    @GetMapping("/admin/ops/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> r = new LinkedHashMap<>();

        int onlineUsers = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT user_id) FROM t_message WHERE create_time > NOW() - INTERVAL '5 minutes'",
                Integer.class);
        long totalUsers = userMapper.selectCount(null);
        long totalConversations = conversationMapper.selectCount(null);
        long totalMessages = messageMapper.selectCount(null);

        long totalChars = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(LENGTH(content)),0) FROM t_message", Long.class);
        long todayMessages = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_message WHERE create_time >= CURRENT_DATE", Long.class);
        long todayChars = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(LENGTH(content)),0) FROM t_message WHERE create_time >= CURRENT_DATE", Long.class);

        // 中文约 1.5 字符/token，粗略估算
        r.put("onlineUsers", onlineUsers);
        r.put("totalUsers", totalUsers);
        r.put("totalConversations", totalConversations);
        r.put("totalMessages", totalMessages);
        r.put("estimatedTokens", Math.round(totalChars / 1.5));
        r.put("todayMessages", todayMessages);
        r.put("todayTokens", Math.round(todayChars / 1.5));
        return Results.success(r);
    }

    /** 时间趋势：按小时统计消息数与 token 消耗（用于图表） */
    @GetMapping("/admin/ops/trends")
    public Result<java.util.List<java.util.Map<String, Object>>> trends(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "24") int hours) {
        String sql = """
                SELECT to_char(date_trunc('hour', create_time), 'MM-DD HH24:00') AS time,
                       COUNT(*) AS messages,
                       COALESCE(SUM(LENGTH(content)), 0) AS chars
                FROM t_message
                WHERE create_time > NOW() - INTERVAL '%d hours'
                GROUP BY 1 ORDER BY 1
                """.formatted(Math.max(1, Math.min(hours, 168)));
        return Results.success(jdbcTemplate.queryForList(sql));
    }
}
