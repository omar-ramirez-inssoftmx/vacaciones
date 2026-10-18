package com.inssoft.people.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccessGuardTest {

    private final AccessGuard accessGuard = new AccessGuard();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void employeeCanReadMasterDataButCannotManageOrganization() {
        authenticate("ROLE_EMPLOYEE");

        assertThat(accessGuard.has("MASTERDATA_VIEW")).isTrue();
        assertThat(accessGuard.has("ORG_VIEW")).isTrue();
        assertThat(accessGuard.has("ORG_MANAGE")).isFalse();
        assertThat(accessGuard.has("USER_MANAGE")).isFalse();
    }

    @Test
    void hrCanManageMasterDataAndEmployees() {
        authenticate("ROLE_HR");

        assertThat(accessGuard.has("MASTERDATA_MANAGE")).isTrue();
        assertThat(accessGuard.has("ORG_MANAGE")).isTrue();
        assertThat(accessGuard.has("EMPLOYEE_CREATE")).isTrue();
        assertThat(accessGuard.has("USER_MANAGE")).isFalse();
    }

    @Test
    void leaderCanApproveVacations() {
        authenticate("ROLE_LIDER");

        assertThat(accessGuard.has("VACATION_APPROVE")).isTrue();
        assertThat(accessGuard.has("ORG_MANAGE")).isFalse();
    }

    @Test
    void adminHasAllPermissions() {
        authenticate("ROLE_ADMIN");

        assertThat(accessGuard.has("USER_MANAGE")).isTrue();
        assertThat(accessGuard.has("ANY_FUTURE_PERMISSION")).isTrue();
    }

    @Test
    void anonymousHasNoPermissions() {
        assertThat(accessGuard.has("MASTERDATA_VIEW")).isFalse();
    }

    @Test
    void databasePermissionsAreAuthoritativeWhenPresent() {
        authenticate("ROLE_ADMIN", "PERM_EMPLOYEE_VIEW");

        assertThat(accessGuard.has("EMPLOYEE_VIEW")).isTrue();
        // Aunque ROLE_ADMIN tenga '*' en el mapa estático, la BD manda.
        assertThat(accessGuard.has("USER_MANAGE")).isFalse();
    }

    private void authenticate(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "tester", null,
                        java.util.Arrays.stream(authorities)
                                .map(SimpleGrantedAuthority::new)
                                .toList()));
    }
}
