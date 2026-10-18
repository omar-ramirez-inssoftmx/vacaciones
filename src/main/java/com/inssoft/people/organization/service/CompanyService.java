package com.inssoft.people.organization.service;

import com.inssoft.people.common.api.PageRequestFactory;
import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.audit.AuditService;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.common.exception.ConflictException;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.common.util.CodeNormalizer;
import com.inssoft.people.organization.dto.CompanyRequest;
import com.inssoft.people.organization.dto.CompanyResponse;
import com.inssoft.people.organization.entity.Company;
import com.inssoft.people.organization.mapper.CompanyMapper;
import com.inssoft.people.organization.repository.BranchRepository;
import com.inssoft.people.organization.repository.CompanyRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final BranchRepository branchRepository;
    private final AuditService auditService;

    public CompanyService(
            CompanyRepository companyRepository,
            BranchRepository branchRepository,
            AuditService auditService) {
        this.companyRepository = companyRepository;
        this.branchRepository = branchRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<CompanyResponse> list(String q, Boolean enabled, int page, int size) {
        String query = CodeNormalizer.trimToNull(q);
        Page<Company> result = companyRepository.search(
                query, enabled, PageRequestFactory.of(page, size, "legalName"));
        return PageResponse.from(result, CompanyMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CompanyResponse get(UUID uuid) {
        return CompanyMapper.toResponse(find(uuid));
    }

    @Transactional
    public CompanyResponse create(CompanyRequest request) {
        String code = CodeNormalizer.normalize(request.code());
        if (companyRepository.existsByCodeIgnoreCaseAndDeletedAtIsNull(code)) {
            throw new ConflictException("Ya existe una empresa con el código " + code);
        }

        Company company = new Company();
        CompanyMapper.apply(request, company);
        company.setCode(code);

        Company saved = companyRepository.save(company);
        CompanyResponse response = CompanyMapper.toResponse(saved);
        auditService.record("ORG_COMPANY_CREATE", "Company", saved.getUuid().toString(), response);
        return response;
    }

    @Transactional
    public CompanyResponse update(UUID uuid, CompanyRequest request) {
        Company company = find(uuid);
        CompanyResponse previous = CompanyMapper.toResponse(company);

        String code = CodeNormalizer.normalize(request.code());
        if (companyRepository.existsByCodeIgnoreCaseAndUuidNotAndDeletedAtIsNull(code, uuid)) {
            throw new ConflictException("Ya existe una empresa con el código " + code);
        }

        if (Boolean.FALSE.equals(request.enabled())
                && branchRepository.existsByCompanyIdAndDeletedAtIsNull(company.getId())) {
            throw new BusinessException(
                    "No se puede deshabilitar la empresa porque tiene sucursales activas");
        }

        CompanyMapper.apply(request, company);
        company.setCode(code);

        Company saved = companyRepository.save(company);
        CompanyResponse response = CompanyMapper.toResponse(saved);
        auditService.record("ORG_COMPANY_UPDATE", "Company", uuid.toString(), previous, response);
        return response;
    }

    @Transactional
    public void delete(UUID uuid) {
        Company company = find(uuid);
        if (branchRepository.existsByCompanyIdAndDeletedAtIsNull(company.getId())) {
            throw new BusinessException(
                    "No se puede deshabilitar la empresa porque tiene sucursales activas");
        }

        CompanyResponse previous = CompanyMapper.toResponse(company);
        company.setDeletedAt(Instant.now());
        company.setEnabled(false);
        companyRepository.save(company);

        auditService.record("ORG_COMPANY_DELETE", "Company", uuid.toString(), previous, null);
    }

    private Company find(UUID uuid) {
        return companyRepository.findByUuidAndDeletedAtIsNull(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa", uuid));
    }
}
