package com.giso.gateway.auth;

/** 单次管理 API 请求解析出的登录身份（避免重复 BCrypt / 查库）。 */
public record AuthContext(String username, String role) { }
