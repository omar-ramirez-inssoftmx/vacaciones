package com.inssoft.people.common.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Autorización por permisos de negocio.
 *
 * <p>Fase 1: el mapa rol→permisos vive en código para no acoplar la fundación
 * a un esquema de RBAC que aún no está migrado. En la Fase 4 se reemplaza por
 * consultas a {@code sec_role_permission} sin cambiar las anotaciones
 * {@code @PreAuthorize("@accessGuard.has('...')")} de los controladores.</p>
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
            "VACATION_VIEW_SELF",
            "VACATION_CANCEL",
            "PAYROLL_VIEW_SELF",
            "DOCUMENT_VIEW_SELF",
            "TELEWORK_VIEW_SELF",
            "WORKPLAN_VIEW_SELF",
            "INCIDENT_VIEW_SELF",
            "INCIDENT_CREATE",
            "NOTIFICATION_VIEW_SELF",
            "DASHBOARD_VIEW_SELF"
    );

    private static final Set<String> LEADER_PERMISSIONS = union(EMPLOYEE_PERMISSIONS, Set.of(
            "EMPLOYEE_VIEW",
            "VACATION_VIEW",
            "VACATION_APPROVE",
            "INCIDENT_VIEW",
            "INCIDENT_APPROVE",
            "WORKPLAN_VIEW",
            "TELEWORK_VIEW",
            "DOCUMENT_VIEW",
            "DASHBOARD_VIEW"
    ));

    private static final Set<String> HR_PERMISSIONS = union(LEADER_PERMISSIONS, Set.of(
            "MASTERDATA_MANAGE",
            "ORG_MANAGE",
            "EMPLOYEE_CREATE",
            "EMPLOYEE_UPDATE",
            "EMPLOYEE_TERMINATE",
            "VACATION_ADJUST",
            "CALENDAR_MANAGE",
            "DOCUMENT_UPLOAD",
            "DOCUMENT_DELETE",
            "PAYROLL_VIEW",
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
            "ROLE_COLABORADOR", EMPLOYEE_PERMISSIONS,
            "ROLE_EMPLOYEE", EMPLOYEE_PERMISSIONS
    );

    public boolean has(String permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            Set<String> permissions = ROLE_PERMISSIONS.get(authority.getAuthority());
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
        Set<String> result = new java.util.HashSet<>(base);
        result.addAll(extra);
        return Set.copyOf(result);
    }
}
