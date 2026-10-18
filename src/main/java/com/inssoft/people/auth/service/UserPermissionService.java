package com.inssoft.people.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Carga los permisos efectivos del usuario desde el RBAC de base de datos
 * ({@code sec_user_role → sec_role_permission → sec_permission}).
 *
 * <p>Si las tablas todavía no existen (por ejemplo, antes de aplicar V2),
 * se devuelve un conjunto vacío y {@code AccessGuard} usa el mapa estático
 * de respaldo. Una vez migrada la base, los permisos de BD son la fuente
 * autoritativa.</p>
 *
 * <p>Se usa {@link JdbcTemplate} para que un fallo de lectura no marque como
 * rollback-only la transacción de autenticación.</p>
 */
@Service
public class UserPermissionService {

    private static final Logger log = LoggerFactory.getLogger(UserPermissionService.class);

    private static final String PERMISSIONS_SQL = """
            SELECT DISTINCT p.code
            FROM dbo.sec_user_role ur
            JOIN dbo.sec_role r ON r.id = ur.role_id
            JOIN dbo.sec_role_permission rp ON rp.role_id = r.id
            JOIN dbo.sec_permission p ON p.id = rp.permission_id
            JOIN dbo.users u ON u.id = ur.user_id
            WHERE u.username = ?
              AND r.enabled = 1
              AND p.enabled = 1
            """;

    private final JdbcTemplate jdbcTemplate;

    public UserPermissionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Set<String> permissionsFor(String username) {
        if (username == null || username.isBlank()) {
            return Set.of();
        }

        try {
            List<String> codes = jdbcTemplate.queryForList(PERMISSIONS_SQL, String.class, username);
            return new HashSet<>(codes);
        } catch (DataAccessException ex) {
            log.warn("No se pudieron cargar permisos de BD para {}: {}", username, ex.getMessage());
            return Set.of();
        }
    }
}
