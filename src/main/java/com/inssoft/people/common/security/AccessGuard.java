package com.inssoft.people.common.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Autorización por permisos de negocio.
 *
 * <p>Fuente autoritativa: permisos {@code PERM_*} cargados desde la base de
 * datos ({@code sec_role_permission}) al autenticar. Si el usuario no tiene
 * permisos de BD (por ejemplo, antes de aplicar la migración V2), se usa un
 * mapa estático de respaldo por rol para no romper el sistema legado.</p>
 */
@Component("accessGuard")
public class AccessGuard {

    private static final Set<String> EMPLOYEE_PERMISSIONS = Set.of(
            "MASTERDATA_VIEW",
            "ORG_VIEW",
            "CALENDAR_VIEW",
            "COMMUNICATION_VIEW",
            "EMPLOYEE_VIEW_SELF",
            "VACATION_CREATE",
            "VACATION_CREATE_SELF",
            "VACATION_VIEW_SELF",
            "VACATION_CANCEL",
            "VACATION_CANCEL_SELF",
            "PAYROLL_VIEW_SELF",
            "DOCUMENT_VIEW_SELF",
            "TELEWORK_VIEW_SELF",
            "WORKPLAN_VIEW_SELF",
            "INCIDENT_VIEW_SELF",
            "INCIDENT_CREATE",
            "INCIDENT_CREATE_SELF",
            "NOTIFICATION_VIEW_SELF",
            "DASHBOARD_VIEW_SELF"
    );

    private static final Set<String> EMPLOYEE_SELF_PERMISSIONS = union(
            EMPLOYEE_PERMISSIONS, Set.of("EMPLOYEE_VIEW_SENSITIVE"));

    private static final Set<String> LEADER_PERMISSIONS = union(EMPLOYEE_PERMISSIONS, Set.of(
            "EMPLOYEE_VIEW_TEAM",
            "VACATION_VIEW_TEAM",
            "VACATION_APPROVE",
            "VACATION_APPROVE_TEAM",
            "INCIDENT_VIEW",
            "INCIDENT_VIEW_TEAM",
            "INCIDENT_CREATE_TEAM",
            "INCIDENT_APPROVE",
            "WORKPLAN_VIEW",
            "WORKPLAN_VIEW_TEAM",
            "TELEWORK_VIEW",
            "TELEWORK_VIEW_TEAM",
            "DASHBOARD_VIEW",
            "DASHBOARD_VIEW_TEAM"
    ));

    private static final Set<String> HR_PERMISSIONS = union(LEADER_PERMISSIONS, Set.of(
            "MASTERDATA_MANAGE",
            "ORG_MANAGE",
            "EMPLOYEE_VIEW_SENSITIVE",
            "EMPLOYEE_VIEW_TEAM",
            "EMPLOYEE_VIEW_ALL",
            "EMPLOYEE_CREATE",
            "EMPLOYEE_UPDATE",
            "EMPLOYEE_TERMINATE",
            "EMPLOYEE_DISABLE",
            "VACATION_ADJUST",
            "VACATION_MANAGE_ALL",
            "VACATION_POLICY_MANAGE",
            "CALENDAR_MANAGE",
            "DOCUMENT_VIEW",
            "DOCUMENT_VIEW_TEAM",
            "DOCUMENT_MANAGE",
            "DOCUMENT_UPLOAD",
            "DOCUMENT_DELETE",
            "PAYROLL_VIEW",
            "PAYROLL_VIEW_TEAM",
            "PAYROLL_MANAGE",
            "PAYROLL_UPLOAD",
            "COMMUNICATION_CREATE",
            "COMMUNICATION_PUBLISH",
            "INCIDENT_CREATE",
            "TELEWORK_MANAGE",
            "WORKPLAN_MANAGE",
            "NOTIFICATION_MANAGE",
            "AUDIT_VIEW",
            "DASHBOARD_VIEW"
    ));

    private static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.of(
            "ROLE_SUPER_ADMIN", Set.of("*"),
            "ROLE_ADMIN", Set.of("*"),
            "ROLE_HR", HR_PERMISSIONS,
            "ROLE_LIDER", LEADER_PERMISSIONS,
            "ROLE_LEADER", LEADER_PERMISSIONS,
            "ROLE_COLABORADOR", EMPLOYEE_SELF_PERMISSIONS,
            "ROLE_EMPLOYEE", EMPLOYEE_SELF_PERMISSIONS
    );

    public boolean has(String permission) {
        if (permission == null || permission.isBlank()) {
            return false;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }

        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        boolean hasDatabasePermissions = authorities.stream()
                .anyMatch(authority -> authority.startsWith("PERM_"));

        if (hasDatabasePermissions) {
            return authorities.contains("PERM_" + permission);
        }

        for (String authority : authorities) {
            Set<String> permissions = ROLE_PERMISSIONS.get(authority);
            if (permissions == null) {
                continue;
            }
            if (permissions.contains("*") || permissions.contains(permission)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAny(String... permissions) {
        for (String permission : permissions) {
            if (has(permission)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> union(Set<String> base, Set<String> extra) {
        Set<String> result = new HashSet<>(base);
        result.addAll(extra);
        return Set.copyOf(result);
    }
}
