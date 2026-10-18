package com.inssoft.people.communication.service;

import com.inssoft.people.common.audit.AuditService;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.communication.dto.AnnouncementRequest;
import com.inssoft.people.communication.dto.AnnouncementResponse;
import com.inssoft.people.communication.entity.Announcement;
import com.inssoft.people.communication.repository.AnnouncementRepository;
import com.inssoft.people.organization.entity.Company;
import com.mx.vacaciones.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunicationServiceTest {

    @Mock
    private AnnouncementRepository repository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private CommunicationService service;

    @Test
    void createsAnnouncement() {
        Company company = company(1L);
        User creator = user(2L, "admin");
        when(repository.save(any(Announcement.class))).thenAnswer(invocation -> {
            Announcement announcement = invocation.getArgument(0);
            announcement.setId(1L);
            announcement.setUuid(UUID.randomUUID());
            announcement.setCreatedAt(java.time.Instant.now());
            return announcement;
        });

        AnnouncementResponse response = service.create(company,
                new AnnouncementRequest("Aviso", "Contenido", "IMPORTANT", null), creator);

        assertThat(response.title()).isEqualTo("Aviso");
        assertThat(response.priority()).isEqualTo("IMPORTANT");
        assertThat(response.active()).isTrue();
        verify(auditService).record(eq("COMMUNICATION_PUBLISH"), eq("Announcement"), any(), any());
    }

    @Test
    void listsActiveAnnouncements() {
        Announcement announcement = announcement(company(1L));
        when(repository.findActive(eq(1L), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(announcement)));

        assertThat(service.active(company(1L), 0, 20).content())
                .extracting(AnnouncementResponse::title)
                .containsExactly("Aviso");
    }

    @Test
    void rejectsDeleteFromAnotherCompany() {
        Announcement announcement = announcement(company(1L));
        when(repository.findByUuidAndDeletedAtIsNull(announcement.getUuid()))
                .thenReturn(Optional.of(announcement));

        assertThatThrownBy(() -> service.delete(
                announcement.getUuid(), company(2L), user(2L, "admin")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("otra empresa");
    }

    private Company company(Long id) {
        Company company = new Company();
        company.setId(id);
        company.setUuid(UUID.randomUUID());
        return company;
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("hash");
        return user;
    }

    private Announcement announcement(Company company) {
        Announcement announcement = new Announcement();
        announcement.setId(1L);
        announcement.setUuid(UUID.randomUUID());
        announcement.setCompany(company);
        announcement.setTitle("Aviso");
        announcement.setBody("Contenido");
        announcement.setPriority("NORMAL");
        announcement.setPublishedAt(LocalDateTime.now().minusHours(1));
        announcement.setCreatedBy(user(2L, "admin"));
        announcement.setEnabled(true);
        return announcement;
    }
}
