package com.inssoft.people.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    @Mock
    private AuditLogRepository repository;

    private AuditServiceImpl auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditServiceImpl(repository, new ObjectMapper());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("omar", null, java.util.List.of()));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("User-Agent", "JUnit");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void recordsAuditEntry() {
        auditService.record("ORG_COMPANY_CREATE", "Company", "uuid-1", Map.of("code", "ACME"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());

        AuditLog entry = captor.getValue();
        assertThat(entry.getActorUsername()).isEqualTo("omar");
        assertThat(entry.getAction()).isEqualTo("ORG_COMPANY_CREATE");
        assertThat(entry.getEntity()).isEqualTo("Company");
        assertThat(entry.getEntityId()).isEqualTo("uuid-1");
        assertThat(entry.getNewValue()).contains("ACME");
        assertThat(entry.getIp()).isEqualTo("10.0.0.1");
        assertThat(entry.getUserAgent()).isEqualTo("JUnit");
    }

    @Test
    void auditFailureDoesNotBreakBusinessOperation() {
        when(repository.save(any(AuditLog.class))).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> auditService.record("ACTION", "Entity", "1", "value"))
                .doesNotThrowAnyException();
    }
}
