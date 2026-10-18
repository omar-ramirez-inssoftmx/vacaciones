package com.inssoft.people.organization.service;

import com.inssoft.people.common.api.PageRequestFactory;
import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.audit.AuditService;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.common.exception.ConflictException;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.common.util.CodeNormalizer;
import com.inssoft.people.organization.dto.BranchRequest;
import com.inssoft.people.organization.dto.BranchResponse;
import com.inssoft.people.organization.entity.Branch;
import com.inssoft.people.organization.entity.Company;
import com.inssoft.people.organization.mapper.BranchMapper;
import com.inssoft.people.organization.repository.BranchRepository;
import com.inssoft.people.organization.repository.CompanyRepository;
import com.inssoft.people.organization.repository.DepartmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class BranchService {

    private final BranchRepository branchRepository;
    private final CompanyRepository companyRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditService auditService;

    public BranchService(
            BranchRepository branchRepository,
            CompanyRepository companyRepository,
            DepartmentRepository departmentRepository,
            AuditService auditService) {
        this.branchRepository = branchRepository;
        this.companyRepository = companyRepository;
        this.departmentRepository = departmentRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<BranchResponse> list(
            UUID companyUuid, String q, Boolean enabled, int page, int size) {

        if (companyUuid != null) {
            findCompany(companyUuid);
        }

        Page<Branch> result = branchRepository.search(
                companyUuid,
                CodeNormalizer.trimToNull(q),
                enabled,
                PageRequestFactory.of(page, size, "name"));

        return PageResponse.from(result, BranchMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public BranchResponse get(UUID uuid) {
        return BranchMapper.toResponse(find(uuid));
    }

    @Transactional
    public BranchResponse create(BranchRequest request) {
        Company company = findCompany(request.companyUuid());
        String code = CodeNormalizer.normalize(request.code());

        if (branchRepository.existsByCompanyIdAndCodeIgnoreCaseAndDeletedAtIsNull(company.getId(), code)) {
            throw new ConflictException("Ya existe una sucursal con el código " + code + " en la empresa");
        }

        Branch branch = new Branch();
        branch.setCompany(company);
        BranchMapper.apply(request, branch);
        branch.setCode(code);

        Branch saved = branchRepository.save(branch);
        BranchResponse response = BranchMapper.toResponse(saved);
        auditService.record("ORG_BRANCH_CREATE", "Branch", saved.getUuid().toString(), response);
        return response;
    }

    @Transactional
    public BranchResponse update(UUID uuid, BranchRequest request) {
        Branch branch = find(uuid);
        Company company = findCompany(request.companyUuid());
        BranchResponse previous = BranchMapper.toResponse(branch);

        String code = CodeNormalizer.normalize(request.code());
        if (branchRepository.existsByCompanyIdAndCodeIgnoreCaseAndUuidNotAndDeletedAtIsNull(
                company.getId(), code, uuid)) {
            throw new ConflictException("Ya existe una sucursal con el código " + code + " en la empresa");
        }

        if (!branch.getCompany().getId().equals(company.getId())
                && departmentRepository.existsByBranchIdAndDeletedAtIsNull(branch.getId())) {
            throw new BusinessException(
                    "No se puede cambiar de empresa una sucursal con departamentos activos");
        }

        if (Boolean.FALSE.equals(request.enabled())
                && departmentRepository.existsByBranchIdAndDeletedAtIsNull(branch.getId())) {
            throw new BusinessException(
                    "No se puede deshabilitar la sucursal porque tiene departamentos activos");
        }

        branch.setCompany(company);
        BranchMapper.apply(request, branch);
        branch.setCode(code);

        Branch saved = branchRepository.save(branch);
        BranchResponse response = BranchMapper.toResponse(saved);
        auditService.record("ORG_BRANCH_UPDATE", "Branch", uuid.toString(), previous, response);
        return response;
    }

    @Transactional
    public void delete(UUID uuid) {
        Branch branch = find(uuid);
        if (departmentRepository.existsByBranchIdAndDeletedAtIsNull(branch.getId())) {
            throw new BusinessException(
                    "No se puede deshabilitar la sucursal porque tiene departamentos activos");
        }

        BranchResponse previous = BranchMapper.toResponse(branch);
        branch.setDeletedAt(Instant.now());
        branch.setEnabled(false);
        branchRepository.save(branch);

        auditService.record("ORG_BRANCH_DELETE", "Branch", uuid.toString(), previous, null);
    }

    private Branch find(UUID uuid) {
        return branchRepository.findByUuidAndDeletedAtIsNull(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal", uuid));
    }

    private Company findCompany(UUID uuid) {
        return companyRepository.findByUuidAndDeletedAtIsNull(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa", uuid));
    }
}
