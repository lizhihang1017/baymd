package com.zhli.baymd.framework.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理员权限校验注解。
 *
 * <p>标注在 Controller 类或方法上，要求当前用户角色为 admin。
 * 开发阶段免登录时，{@code UserContextInterceptor} 会注入默认 system 用户（role=admin），
 * 因此管理端接口在开发环境仍可访问；接入真实登录后，非 admin 用户将被拦截。</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAdmin {
}
