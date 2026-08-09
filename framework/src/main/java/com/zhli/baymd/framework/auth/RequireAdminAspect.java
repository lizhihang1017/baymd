package com.zhli.baymd.framework.auth;

import com.zhli.baymd.framework.context.UserContext;
import com.zhli.baymd.framework.exception.ClientException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 管理员权限切面 — 拦截 {@link RequireAdmin} 标注的方法/类，校验当前用户为 admin。
 *
 * <p>开发阶段免登录时 UserContext 为默认 system（admin），管理端可用；
 * 真实登录后按用户角色校验，非 admin 返回 {@code ClientException}（需管理员权限）。</p>
 */
@Slf4j
@Aspect
@Component
public class RequireAdminAspect {

    @Around("@annotation(com.zhli.baymd.framework.auth.RequireAdmin)"
            + " || @within(com.zhli.baymd.framework.auth.RequireAdmin)")
    public Object checkAdmin(ProceedingJoinPoint pjp) throws Throwable {
        String role = UserContext.getRole();
        if (!"admin".equals(role)) {
            log.warn("非管理员访问受限接口被拦截: role={}, signature={}", role, pjp.getSignature().toShortString());
            throw new ClientException("需要管理员权限");
        }
        return pjp.proceed();
    }
}
