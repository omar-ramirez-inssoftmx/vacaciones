package com.inssoft.people.organization.service;

import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.audit.AuditService;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.common.exception.ConflictException;
import com.inssoft.people.organization.dto.BranchRequest;
import com.inssoft.people.organization.dto.BranchResponse;
import com.inssoft.people.organization.entity.Branch;
import com.inssoft.people.organization.entity.Company;
import com.inssoft.people.organization.repository.BranchRepository;
import com.inssoft.people.organization.repository.CompanyRepository;
import com.inssoft.people.organization.repository.DepartmentRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchServiceTest {

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private BranchService branchService;

    @Test
    void createsBranchUnderCompany() {
        Company company = company();
        UUID companyUuid = company.getUuid();
        when(companyRepository.findByUuidAndDeletedAtIsNull(companyUuid)).thenReturn(Optional.of(company));
        when(branchRepository.existsByCompanyIdAndCodeIgnoreCaseAndDeletedAtIsNull(1L, "SUC-NORTE"))
                .thenReturn(false);
        when(branchRepository.save(any(Branch.class))).thenAnswer(invocation -> {
            Branch branch = invocation.getArgument(0);
            branch.setUuid(UUID.randomUUID());
            branch.setCreatedAt(Instant.now());
            return branch;
        });

        BranchRequest request = new BranchRequest(
                companyUuid, "suc-norte", "Sucursal Norte", "Calle 1", "555", true);

        BranchResponse response = branchService.create(request);

        assertThat(response.code()).isEqualTo("SUC-NORTE");
        assertThat(response.companyUuid()).isEqualTo(companyUuid);
        verify(auditService).record(eq("ORG_BRANCH_CREATE"), eq("Branch"), anyString(), any());
    }

    @Test
    void rejectsDuplicateBranchCodeInSameCompany() {
        Company company = company();
        UUID companyUuid = company.getUuid();
        when(companyRepository.findByUuidAndDeletedAtIsNull(companyUuid)).thenReturn(Optional.of(company));
        when(branchRepository.existsByCompanyIdAndCodeIgnoreCaseAndDeletedAtIsNull(1L, "SUC-NORTE"))
                .thenReturn(true);

        BranchRequest request = new BranchRequest(
                companyUuid, "SUC-NORTE", "Sucursal Norte", null, null, true);

        assertThatThrownBy(() -> branchService.create(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cannotDeleteBranchWithDepartments() {
        Branch branch = new Branch();
        branch.setId(10L);
        branch.setUuid(UUID.randomUUID());
        branch.setCode("SUC");
        branch.setName("Sucursal");
        branch.setCompany(company());

        when(branchRepository.findByUuidAndDeletedAtIsNull(branch.getUuid())).thenReturn(Optional.of(branch));
        when(departmentRepository.existsByBranchIdAndDeletedAtIsNull(10L)).thenReturn(true);

        assertThatThrownBy(() -> branchService.delete(branch.getUuid()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("departamentos");
    }

    @Test
    void listsBranches() {
        Branch branch = branchEntity(10L, "SUC-NORTE");
        when(branchRepository.search(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(branch)));

        PageResponse<BranchResponse> response = branchService.list(null, null, null, 0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).code()).isEqualTo("SUC-NORTE");
    }

    @Test
    void updatesBranch() {
        Branch branch = branchEntity(10L, "SUC-NORTE");
        Company company = branch.getCompany();

        when(branchRepository.findByUuidAndDeletedAtIsNull(branch.getUuid())).thenReturn(Optional.of(branch));
        when(companyRepository.findByUuidAndDeletedAtIsNull(company.getUuid())).thenReturn(Optional.of(company));
        when(branchRepository.existsByCompanyIdAndCodeIgnoreCaseAndUuidNotAndDeletedAtIsNull(
                1L, "SUC-SUR", branch.getUuid())).thenReturn(false);
        when(branchRepository.save(any(Branch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BranchRequest request = new BranchRequest(
                company.getUuid(), "suc-sur", "Sucursal Sur", null, null, true);

        BranchResponse response = branchService.update(branch.getUuid(), request);

        assertThat(response.code()).isEqualTo("SUC-SUR");
        verify(auditService).record(eq("ORG_BRANCH_UPDATE"), eq("Branch"), eq(branch.getUuid().toString()), any(), any());
    }

    @Test
    void deletesBranchLogically() {
        Branch branch = branchEntity(10L, "SUC-NORTE");
        when(branchRepository.findByUuidAndDeletedAtIsNull(branch.getUuid())).thenReturn(Optional.of(branch));
        when(departmentRepository.existsByBranchIdAndDeletedAtIsNull(10L)).thenReturn(false);

        branchService.delete(branch.getUuid());

        assertThat(branch.getDeletedAt()).isNotNull();
        assertThat(branch.isEnabled()).isFalse();
        verify(auditService).record(eq("ORG_BRANCH_DELETE"), eq("Branch"), eq(branch.getUuid().toString()), any(), any());
    }

    private Branch branchEntity(long id, String code) {
        Branch branch = new Branch();
        branch.setId(id);
        branch.setUuid(UUID.randomUUID());
        branch.setCode(code);
        branch.setName(code);
        branch.setCompany(company());
        branch.setEnabled(true);
        branch.setCreatedAt(Instant.now());
        return branch;
    }

    private Company company() {
        Company company = new Company();
        company.setId(1L);
        company.setUuid(UUID.randomUUID());
        company.setCode("ACME");
        company.setLegalName("Acme SA de CV");
        company.setEnabled(true);
        return company;
    }
}
