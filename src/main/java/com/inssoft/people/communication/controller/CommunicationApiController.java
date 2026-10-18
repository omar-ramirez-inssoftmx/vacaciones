package com.inssoft.people.communication.controller;

import com.inssoft.people.common.api.ApiResponse;
import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.common.security.AccessGuard;
import com.inssoft.people.communication.dto.AnnouncementRequest;
import com.inssoft.people.communication.dto.AnnouncementResponse;
import com.inssoft.people.communication.service.CommunicationService;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.organization.entity.Company;
import com.inssoft.people.organization.repository.CompanyRepository;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/communications")
@Tag(name = "Communications", description = "Comunicados internos")
@SecurityRequirement(name = "bearerAuth")
public class CommunicationApiController {

    private final CommunicationService communicationService;
    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final AccessGuard accessGuard;

    public CommunicationApiController(
            CommunicationService communicationService,
            EmployeeRepository employeeRepository,
            CompanyRepository companyRepository,
            UserRepository userRepository,
            AccessGuard accessGuard) {
        this.communicationService = communicationService;
        this.employeeRepository = employeeRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    @PreAuthorize("@accessGuard.has('COMMUNICATION_VIEW')")
    @Operation(summary = "Lista comunicados activos o todos para administración")
    public ApiResponse<PageResponse<AnnouncementResponse>> list(
            Authentication authentication,
            @RequestParam(required = false) UUID companyUuid,
            @RequestParam(defaultValue = "false") boolean all,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Company company = resolveCompany(companyUuid, authentication);
        if (all && accessGuard.hasAny("COMMUNICATION_CREATE", "COMMUNICATION_PUBLISH")) {
            return ApiResponse.ok("Comunicados obtenidos",
                    communicationService.search(company, q, page, size));
        }
        return ApiResponse.ok("Comunicados obtenidos",
                communicationService.active(company, page, size));
    }

    @PostMapping
    @PreAuthorize("@accessGuard.has('COMMUNICATION_PUBLISH')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Publica un comunicado")
    public ApiResponse<AnnouncementResponse> create(
            Authentication authentication,
            @RequestParam(required = false) UUID companyUuid,
            @Valid @RequestBody AnnouncementRequest request) {
        return ApiResponse.ok("Comunicado publicado", communicationService.create(
                resolveCompany(companyUuid, authentication), request, currentUser(authentication)));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("@accessGuard.has('COMMUNICATION_PUBLISH')")
    @Operation(summary = "Elimina un comunicado")
    public ApiResponse<Void> delete(
            Authentication authentication,
            @PathVariable UUID uuid,
            @RequestParam(required = false) UUID companyUuid) {
        communicationService.delete(uuid, resolveCompany(companyUuid, authentication),
                currentUser(authentication));
        return ApiResponse.ok("Comunicado eliminado");
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", authentication.getName()));
    }

    private Company resolveCompany(UUID companyUuid, Authentication authentication) {
        Employee employee = employeeRepository
                .findByUser_UsernameAndDeletedAtIsNull(authentication.getName())
                .orElse(null);
        Company ownCompany = employee == null ? null : employee.getCompany();

        if (companyUuid == null) {
            if (ownCompany != null) {
                return ownCompany;
            }
            if (accessGuard.has("COMMUNICATION_PUBLISH")) {
                return companyRepository.findFirstByDeletedAtIsNullOrderByIdAsc()
                        .orElseThrow(() -> new ResourceNotFoundException("No existe una empresa activa"));
            }
            throw new ResourceNotFoundException(
                    "No existe una empresa asociada al usuario " + authentication.getName());
        }

        Company requested = companyRepository.findByUuidAndDeletedAtIsNull(companyUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa", companyUuid));
        if (ownCompany != null && ownCompany.getId().equals(requested.getId())) {
            return requested;
        }
        if (accessGuard.has("COMMUNICATION_PUBLISH")) {
            return requested;
        }
        throw new com.inssoft.people.common.exception.BusinessException(
                com.inssoft.people.common.api.ErrorCode.FORBIDDEN,
                "No puedes consultar comunicados de otra empresa");
    }
}
