package com.inssoft.people.organization.mapper;

import com.inssoft.people.common.util.CodeNormalizer;
import com.inssoft.people.organization.dto.CompanyRequest;
import com.inssoft.people.organization.dto.CompanyResponse;
import com.inssoft.people.organization.entity.Company;

public final class CompanyMapper {

    private CompanyMapper() {
    }

    public static CompanyResponse toResponse(Company company) {
        return new CompanyResponse(
                company.getUuid(),
                company.getCode(),
                company.getLegalName(),
                company.getTradeName(),
                company.getRfc(),
                company.getTimezone(),
                company.isEnabled(),
                company.getCreatedAt(),
                company.getUpdatedAt()
        );
    }

    public static void apply(CompanyRequest request, Company company) {
        company.setCode(CodeNormalizer.normalize(request.code()));
        company.setLegalName(request.legalName().trim());
        company.setTradeName(CodeNormalizer.trimToNull(request.tradeName()));
        company.setRfc(request.rfc() == null ? null : request.rfc().trim().toUpperCase());
        String timezone = CodeNormalizer.trimToNull(request.timezone());
        company.setTimezone(timezone == null ? "America/Mexico_City" : timezone);
        if (request.enabled() != null) {
            company.setEnabled(request.enabled());
        }
    }
}
