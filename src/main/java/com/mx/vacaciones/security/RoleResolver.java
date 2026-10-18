package com.mx.vacaciones.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Resuelve el rol funcional del usuario a partir de sus authorities.
 *
 * <p>Spring ordena las authorities alfabéticamente, por lo que no se puede
 * asumir que la primera sea el rol. Los permisos {@code PERM_*} se cargan
 * desde base de datos y conviven con los roles {@code ROLE_*}.</p>
 */
public final class RoleResolver {

    private RoleResolver() {
    }

    public static String resolve(Authentication authentication) {
        if (authentication == null) {
            return "ROLE_COLABORADOR";
        }
        if (hasAuthority(authentication, "ROLE_ADMIN")
                || hasAuthority(authentication, "ROLE_SUPER_ADMIN")
                || hasAuthority(authentication, "ROLE_HR")) {
            return "ROLE_ADMIN";
        }
        if (hasAuthority(authentication, "ROLE_LIDER")
                || hasAuthority(authentication, "ROLE_LEADER")) {
            return "ROLE_LIDER";
        }
        return "ROLE_COLABORADOR";
    }

    public static boolean hasAuthority(Authentication authentication, String authority) {
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority granted : authentication.getAuthorities()) {
            if (authority.equals(granted.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
