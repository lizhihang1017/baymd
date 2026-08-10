package com.zhli.baymd.user.controller;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhli.baymd.framework.convention.Result;
import com.zhli.baymd.framework.exception.ClientException;
import com.zhli.baymd.framework.web.Results;
import com.zhli.baymd.rag.dao.entity.UserEpisodeDO;
import com.zhli.baymd.rag.dao.entity.UserFactDO;
import com.zhli.baymd.rag.dao.mapper.UserEpisodeMapper;
import com.zhli.baymd.rag.dao.mapper.UserFactMapper;
import com.zhli.baymd.user.dao.entity.UserDO;
import com.zhli.baymd.user.dao.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 用户自助账户与画像（用户端）— 注册 / 头像 / 密码 / 查看与删除自己的画像。
 * <p>画像数据与管理端 t_user_fact / t_user_episode 同表,天然同步。</p>
 */
@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserAccountController {

    private final UserMapper userMapper;
    private final UserFactMapper userFactMapper;
    private final UserEpisodeMapper userEpisodeMapper;

    @Value("${storage.local.dir:./data/baymd-files}")
    private String localDir;

    // ==================== 注册 ====================

    /** 注册新账号（avatar 为空时前端用默认大白头像） */
    @PostMapping("/auth/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String username = StrUtil.trimToNull(body.get("username"));
        String password = StrUtil.trimToNull(body.get("password"));
        String email = StrUtil.trimToNull(body.get("email"));
        if (username == null || password == null) {
            throw new ClientException("用户名和密码不能为空");
        }
        if (email == null || !email.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")) {
            throw new ClientException("请填写有效的邮箱地址");
        }
        if (username.length() < 2 || username.length() > 20) {
            throw new ClientException("用户名长度需 2-20 个字符");
        }
        if (password.length() < 4) {
            throw new ClientException("密码至少 4 位");
        }
        // 用户名 / 邮箱唯一
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getDeleted, 0)
                .and(w -> w.eq(UserDO::getUsername, username).or().eq(UserDO::getEmail, email)));
        if (exists != null && exists > 0) {
            throw new ClientException("用户名或邮箱已被注册");
        }
        UserDO user = UserDO.builder()
                .id(IdUtil.getSnowflakeNextIdStr())
                .username(username)
                .password(password) // 明文存储(与现有登录一致)
                .email(email)
                .role("user")
                .build();
        userMapper.insert(user);
        log.info("新用户注册: username={}, email={}, id={}", username, email, user.getId());
        return Results.success(Map.of("userId", user.getId(), "username", user.getUsername(), "email", email));
    }

    // ==================== 头像 ====================

    /** 上传头像（保存到本地,返回可访问 URL） */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        String userId = requireUserId();
        if (file == null || file.isEmpty()) {
            throw new ClientException("头像文件不能为空");
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new ClientException("头像不能超过 2MB");
        }
        String ext = resolveExt(file.getOriginalFilename());
        String fileName = userId + "_" + IdUtil.getSnowflakeNextIdStr().substring(0, 8) + ext;
        try {
            Path base = Paths.get(localDir).isAbsolute()
                    ? Paths.get(localDir)
                    : Paths.get(System.getProperty("user.dir"), localDir);
            Path avatarDir = base.resolve("avatars");
            Files.createDirectories(avatarDir);
            Path target = avatarDir.resolve(fileName);
            file.transferTo(target.toFile());
            String url = "/api/baymd/files/avatars/" + fileName;
            userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserDO>()
                    .eq(UserDO::getId, userId).set(UserDO::getAvatar, url));
            log.info("头像已更新: userId={}, url={}", userId, url);
            return Results.success(url);
        } catch (Exception e) {
            log.error("头像上传失败: userId={}", userId, e);
            throw new ClientException("头像上传失败: " + e.getMessage());
        }
    }

    private String resolveExt(String name) {
        if (name == null) return ".png";
        String lower = name.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return ".jpg";
        if (lower.endsWith(".webp")) return ".webp";
        if (lower.endsWith(".gif")) return ".gif";
        return ".png";
    }

    // ==================== 画像（自己的记忆,与管理端同表）====================

    /** 查看自己的画像：facts + episodes */
    @GetMapping("/memory")
    public Result<Map<String, Object>> myMemory() {
        String userId = requireUserId();
        List<UserFactDO> facts = userFactMapper.selectList(new LambdaQueryWrapper<UserFactDO>()
                .eq(UserFactDO::getUserId, userId).orderByDesc(UserFactDO::getCreatedAt));
        List<UserEpisodeDO> episodes = userEpisodeMapper.selectList(new LambdaQueryWrapper<UserEpisodeDO>()
                .eq(UserEpisodeDO::getUserId, userId).orderByDesc(UserEpisodeDO::getCreatedAt));
        return Results.success(Map.of("facts", facts, "episodes", episodes));
    }

    /** 删除自己的画像 Fact（只能删自己的） */
    @DeleteMapping("/memory/fact/{id}")
    public Result<Void> deleteMyFact(@PathVariable String id) {
        String userId = requireUserId();
        UserFactDO fact = userFactMapper.selectById(id);
        if (fact == null || !userId.equals(fact.getUserId())) {
            throw new ClientException("Fact 不存在");
        }
        userFactMapper.deleteById(id);
        return Results.success();
    }

    /** 删除自己的画像 Episode（只能删自己的） */
    @DeleteMapping("/memory/episode/{id}")
    public Result<Void> deleteMyEpisode(@PathVariable String id) {
        String userId = requireUserId();
        UserEpisodeDO ep = userEpisodeMapper.selectById(id);
        if (ep == null || !userId.equals(ep.getUserId())) {
            throw new ClientException("Episode 不存在");
        }
        userEpisodeMapper.deleteById(id);
        return Results.success();
    }

    // ==================== 辅助 ====================

    private String requireUserId() {
        String userId = com.zhli.baymd.framework.context.UserContext.getUserId();
        if (StrUtil.isBlank(userId) || "1".equals(userId)) {
            throw new ClientException("请先登录");
        }
        return userId;
    }
}
