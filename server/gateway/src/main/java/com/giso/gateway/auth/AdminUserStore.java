package com.giso.gateway.auth;

import java.util.List;
import java.util.Map;

/** 管理台账号校验：生产 PostgreSQL，本地配置回退。 */
public interface AdminUserStore {
    /** 校验成功返回 role（admin/viewer），失败 null。 */
    String authenticate(String username, String password) throws Exception;

    /** 是否启用鉴权（未配置任何账号时不鉴权，便于本地开发）。 */
    boolean authEnabled();

    /** 当前有效账号列表（不含密码），供 /admin/api/me 与运维查看。 */
    List<Map<String, Object>> listUsers() throws Exception;

    /** 创建或更新账号；password 为空则不改密码。返回 null 成功，否则错误信息。 */
    String saveUser(String username, String password, String role, String displayName) throws Exception;

    /** 禁用账号；返回 null 成功。 */
    String disableUser(String username) throws Exception;

    /** 修改自己的密码；需校验当前密码。返回 null 成功，否则错误信息。 */
    String changePassword(String username, String currentPassword, String newPassword) throws Exception;
}
