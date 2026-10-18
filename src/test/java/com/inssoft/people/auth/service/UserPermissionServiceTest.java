package com.inssoft.people.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPermissionServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private UserPermissionService service;

    @Test
    void loadsPermissionCodes() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("omar")))
                .thenReturn(List.of("EMPLOYEE_VIEW", "ORG_MANAGE"));

        Set<String> permissions = service.permissionsFor("omar");

        assertThat(permissions).containsExactlyInAnyOrder("EMPLOYEE_VIEW", "ORG_MANAGE");
    }

    @Test
    void returnsEmptyWhenRbacTablesAreNotAvailable() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("omar")))
                .thenThrow(new DataAccessResourceFailureException("table not found"));

        assertThat(service.permissionsFor("omar")).isEmpty();
    }

    @Test
    void returnsEmptyForBlankUsername() {
        assertThat(service.permissionsFor(" ")).isEmpty();
    }
}
