package com.inssoft.people.organization.service;

import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.audit.AuditService;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.common.exception.ConflictException;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.organization.dto.CompanyRequest;
import com.inssoft.people.organization.dto.CompanyResponse;
import com.inssoft.people.organization.entity.Company;
import com.inssoft.people.organization.repository.BranchRepository;
import com.inssoft.people.organization.repository.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private CompanyService companyService;

    @Test
    void createsCompanyNormalizingCodeAndAuditing() {
        CompanyRequest request = new CompanyRequest(
                "acme", "Acme SA de CV", null, null, "America/Mexico_City", true);

        when(companyRepository.existsByCodeIgnoreCaseAndDeletedAtIsNull("ACME")).thenReturn(false);
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> {
            Company company = invocation.getArgument(0);
            company.setUuid(UUID.randomUUID());
            company.setCreatedAt(Instant.now());
            return company;
        });

        CompanyResponse response = companyService.create(request);

        assertThat(response.code()).isEqualTo("ACME");
        assertThat(response.legalName()).isEqualTo("Acme SA de CV");
        assertThat(response.uuid()).isNotNull();
        verify(auditService).record(eq("ORG_COMPANY_CREATE"), eq("Company"), anyString(), any());
    }

    @Test
    void rejectsDuplicateCompanyCode() {
        when(companyRepository.existsByCodeIgnoreCaseAndDeletedAtIsNull("ACME")).thenReturn(true);

        CompanyRequest request = new CompanyRequest(
                "ACME", "Acme SA de CV", null, null, null, true);

        assertThatThrownBy(() -> companyService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ACME");
    }

    @Test
    void cannotDeleteCompanyWithBranches() {
        UUID uuid = UUID.randomUUID();
        Company company = new Company();
        company.setId(1L);
        company.setUuid(uuid);
        company.setCode("ACME");
        company.setLegalName("Acme SA de CV");
        company.setEnabled(true);

        when(companyRepository.findByUuidAndDeletedAtIsNull(uuid)).thenReturn(Optional.of(company));
        when(branchRepository.existsByCompanyIdAndDeletedAtIsNull(1L)).thenReturn(true);

        assertThatThrownBy(() -> companyService.delete(uuid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("sucursales");
    }

    @Test
    void getReturnsNotFound() {
        UUID uuid = UUID.randomUUID();
        when(companyRepository.findByUuidAndDeletedAtIsNull(uuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.get(uuid))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listsCompaniesWithPagination() {
        Company company = companyEntity(1L, "ACME");
        when(companyRepository.search(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(company)));

        PageResponse<CompanyResponse> response = companyService.list(null, null, 0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).code()).isEqualTo("ACME");
    }

    @Test
    void updatesCompany() {
        UUID uuid = UUID.randomUUID();
        Company company = companyEntity(1L, "ACME");
        company.setUuid(uuid);

        when(companyRepository.findByUuidAndDeletedAtIsNull(uuid)).thenReturn(Optional.of(company));
        when(companyRepository.existsByCodeIgnoreCaseAndUuidNotAndDeletedAtIsNull("NEW", uuid))
                .thenReturn(false);
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyRequest request = new CompanyRequest(
                "new", "Nueva SA", "Nueva", null, "America/Mexico_City", true);

        CompanyResponse response = companyService.update(uuid, request);

        assertThat(response.code()).isEqualTo("NEW");
        assertThat(response.legalName()).isEqualTo("Nueva SA");
        verify(auditService).record(eq("ORG_COMPANY_UPDATE"), eq("Company"), eq(uuid.toString()), any(), any());
    }

    @Test
    void deletesCompanyLogically() {
        UUID uuid = UUID.randomUUID();
        Company company = companyEntity(1L, "ACME");
        company.setUuid(uuid);

        when(companyRepository.findByUuidAndDeletedAtIsNull(uuid)).thenReturn(Optional.of(company));
        when(branchRepository.existsByCompanyIdAndDeletedAtIsNull(1L)).thenReturn(false);

        companyService.delete(uuid);

        assertThat(company.getDeletedAt()).isNotNull();
        assertThat(company.isEnabled()).isFalse();
        verify(auditService).record(eq("ORG_COMPANY_DELETE"), eq("Company"), eq(uuid.toString()), any(), isNull());
    }

    private Company companyEntity(long id, String code) {
        Company company = new Company();
        company.setId(id);
        company.setUuid(UUID.randomUUID());
        company.setCode(code);
        company.setLegalName("Acme SA de CV");
        company.setTimezone("America/Mexico_City");
        company.setEnabled(true);
        company.setCreatedAt(Instant.now());
        return company;
    }
}
