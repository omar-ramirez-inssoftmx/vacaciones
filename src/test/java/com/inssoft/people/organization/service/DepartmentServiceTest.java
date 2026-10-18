package com.inssoft.people.organization.service;

import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.audit.AuditService;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.common.exception.ConflictException;
import com.inssoft.people.organization.dto.DepartmentRequest;
import com.inssoft.people.organization.dto.DepartmentResponse;
import com.inssoft.people.organization.entity.Branch;
import com.inssoft.people.organization.entity.Company;
import com.inssoft.people.organization.entity.Department;
import com.inssoft.people.organization.repository.BranchRepository;
import com.inssoft.people.organization.repository.DepartmentRepository;
import com.inssoft.people.organization.repository.PositionRepository;
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
class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private DepartmentService departmentService;

    @Test
    void createsDepartmentWithParentInSameBranch() {
        Branch branch = branch(1L, "SUC");
        Department parent = department(5L, "TECNOLOGIA", branch);
        when(branchRepository.findByUuidAndDeletedAtIsNull(branch.getUuid())).thenReturn(Optional.of(branch));
        when(departmentRepository.findByUuidAndDeletedAtIsNull(parent.getUuid())).thenReturn(Optional.of(parent));
        when(departmentRepository.existsByBranchIdAndCodeIgnoreCaseAndDeletedAtIsNull(1L, "DESARROLLO"))
                .thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenAnswer(invocation -> {
            Department department = invocation.getArgument(0);
            department.setUuid(UUID.randomUUID());
            department.setCreatedAt(Instant.now());
            return department;
        });

        DepartmentRequest request = new DepartmentRequest(
                branch.getUuid(), parent.getUuid(), "desarrollo", "Desarrollo", null, true);

        DepartmentResponse response = departmentService.create(request);

        assertThat(response.code()).isEqualTo("DESARROLLO");
        assertThat(response.parentUuid()).isEqualTo(parent.getUuid());
        verify(auditService).record(eq("ORG_DEPARTMENT_CREATE"), eq("Department"), anyString(), any());
    }

    @Test
    void rejectsParentFromAnotherBranch() {
        Branch branchA = branch(1L, "SUC-A");
        Branch branchB = branch(2L, "SUC-B");
        Department parent = department(5L, "TECNOLOGIA", branchB);

        when(branchRepository.findByUuidAndDeletedAtIsNull(branchA.getUuid())).thenReturn(Optional.of(branchA));
        when(departmentRepository.findByUuidAndDeletedAtIsNull(parent.getUuid())).thenReturn(Optional.of(parent));

        DepartmentRequest request = new DepartmentRequest(
                branchA.getUuid(), parent.getUuid(), "DESARROLLO", "Desarrollo", null, true);

        assertThatThrownBy(() -> departmentService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("misma sucursal");
    }

    @Test
    void rejectsDuplicateCodeInBranch() {
        Branch branch = branch(1L, "SUC");
        when(branchRepository.findByUuidAndDeletedAtIsNull(branch.getUuid())).thenReturn(Optional.of(branch));
        when(departmentRepository.existsByBranchIdAndCodeIgnoreCaseAndDeletedAtIsNull(1L, "TECNOLOGIA"))
                .thenReturn(true);

        DepartmentRequest request = new DepartmentRequest(
                branch.getUuid(), null, "TECNOLOGIA", "Tecnología", null, true);

        assertThatThrownBy(() -> departmentService.create(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsCycleWhenUpdatingParentToItself() {
        Branch branch = branch(1L, "SUC");
        Department department = department(5L, "TECNOLOGIA", branch);

        when(departmentRepository.findByUuidAndDeletedAtIsNull(department.getUuid()))
                .thenReturn(Optional.of(department));
        when(branchRepository.findByUuidAndDeletedAtIsNull(branch.getUuid())).thenReturn(Optional.of(branch));
        when(departmentRepository.findByUuidAndDeletedAtIsNull(department.getUuid()))
                .thenReturn(Optional.of(department));

        DepartmentRequest request = new DepartmentRequest(
                branch.getUuid(), department.getUuid(), "TECNOLOGIA", "Tecnología", null, true);

        assertThatThrownBy(() -> departmentService.update(department.getUuid(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("propio padre");
    }

    @Test
    void cannotDeleteDepartmentWithPositions() {
        Branch branch = branch(1L, "SUC");
        Department department = department(5L, "TECNOLOGIA", branch);

        when(departmentRepository.findByUuidAndDeletedAtIsNull(department.getUuid()))
                .thenReturn(Optional.of(department));
        when(positionRepository.existsByDepartmentIdAndDeletedAtIsNull(5L)).thenReturn(true);

        assertThatThrownBy(() -> departmentService.delete(department.getUuid()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("puestos");
    }

    @Test
    void listsDepartments() {
        Branch branch = branch(1L, "SUC");
        Department department = department(5L, "TECNOLOGIA", branch);
        when(departmentRepository.search(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(department)));

        PageResponse<DepartmentResponse> response = departmentService.list(null, null, null, 0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).code()).isEqualTo("TECNOLOGIA");
    }

    @Test
    void updatesDepartment() {
        Branch branch = branch(1L, "SUC");
        Department department = department(5L, "TECNOLOGIA", branch);

        when(departmentRepository.findByUuidAndDeletedAtIsNull(department.getUuid()))
                .thenReturn(Optional.of(department));
        when(branchRepository.findByUuidAndDeletedAtIsNull(branch.getUuid())).thenReturn(Optional.of(branch));
        when(departmentRepository.existsByBranchIdAndCodeIgnoreCaseAndUuidNotAndDeletedAtIsNull(
                1L, "SISTEMAS", department.getUuid())).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DepartmentRequest request = new DepartmentRequest(
                branch.getUuid(), null, "sistemas", "Sistemas", null, true);

        DepartmentResponse response = departmentService.update(department.getUuid(), request);

        assertThat(response.code()).isEqualTo("SISTEMAS");
        verify(auditService).record(eq("ORG_DEPARTMENT_UPDATE"), eq("Department"),
                eq(department.getUuid().toString()), any(), any());
    }

    @Test
    void deletesDepartmentLogically() {
        Branch branch = branch(1L, "SUC");
        Department department = department(5L, "TECNOLOGIA", branch);

        when(departmentRepository.findByUuidAndDeletedAtIsNull(department.getUuid()))
                .thenReturn(Optional.of(department));
        when(positionRepository.existsByDepartmentIdAndDeletedAtIsNull(5L)).thenReturn(false);
        when(departmentRepository.existsByParentIdAndDeletedAtIsNull(5L)).thenReturn(false);

        departmentService.delete(department.getUuid());

        assertThat(department.getDeletedAt()).isNotNull();
        assertThat(department.isEnabled()).isFalse();
        verify(auditService).record(eq("ORG_DEPARTMENT_DELETE"), eq("Department"),
                eq(department.getUuid().toString()), any(), any());
    }

    private Branch branch(long id, String code) {
        Company company = new Company();
        company.setId(1L);
        company.setUuid(UUID.randomUUID());
        company.setCode("ACME");
        company.setLegalName("Acme");
        company.setEnabled(true);

        Branch branch = new Branch();
        branch.setId(id);
        branch.setUuid(UUID.randomUUID());
        branch.setCode(code);
        branch.setName(code);
        branch.setCompany(company);
        branch.setEnabled(true);
        return branch;
    }

    private Department department(long id, String code, Branch branch) {
        Department department = new Department();
        department.setId(id);
        department.setUuid(UUID.randomUUID());
        department.setCode(code);
        department.setName(code);
        department.setBranch(branch);
        department.setEnabled(true);
        return department;
    }
}
