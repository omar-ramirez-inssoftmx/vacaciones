package com.inssoft.people.calendar.controller;

import com.inssoft.people.calendar.dto.CalendarConfigRequest;
import com.inssoft.people.calendar.dto.CalendarConfigResponse;
import com.inssoft.people.calendar.dto.CalendarDayResponse;
import com.inssoft.people.calendar.dto.CalendarEventResponse;
import com.inssoft.people.calendar.dto.HolidayRequest;
import com.inssoft.people.calendar.dto.HolidayResponse;
import com.inssoft.people.calendar.dto.OfficialHolidayGenerationResponse;
import com.inssoft.people.calendar.dto.SpecialDayRequest;
import com.inssoft.people.calendar.dto.SpecialDayResponse;
import com.inssoft.people.calendar.entity.HolidayType;
import com.inssoft.people.calendar.service.CalendarService;
import com.inssoft.people.common.api.ApiResponse;
import com.inssoft.people.common.api.ErrorCode;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.common.security.AccessGuard;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.organization.entity.Company;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/calendar")
@Validated
@Tag(name = "Calendar", description = "Calendario laboral, festivos, cierres y eventos")
@SecurityRequirement(name = "bearerAuth")
public class CalendarApiController {

    private final CalendarService calendarService;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final AccessGuard accessGuard;

    public CalendarApiController(
            CalendarService calendarService,
            EmployeeRepository employeeRepository,
            UserRepository userRepository,
            AccessGuard accessGuard) {
        this.calendarService = calendarService;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/days")
    @PreAuthorize("@accessGuard.has('CALENDAR_VIEW')")
    @Operation(summary = "Días laborales/no laborales de un rango")
    public ApiResponse<List<CalendarDayResponse>> days(
            Authentication authentication,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) UUID companyUuid) {
        Company company = resolveCompany(companyUuid, authentication);
        return ApiResponse.ok("Calendario obtenido", calendarService.days(company, from, to));
    }

    @GetMapping("/events")
    @PreAuthorize("@accessGuard.has('CALENDAR_VIEW')")
    @Operation(summary = "Festivos, días especiales y vacaciones aprobadas")
    public ApiResponse<List<CalendarEventResponse>> events(
            Authentication authentication,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) UUID companyUuid) {
        Company company = resolveCompany(companyUuid, authentication);
        return ApiResponse.ok("Eventos obtenidos", calendarService.events(company, from, to));
    }

    @GetMapping("/holidays")
    @PreAuthorize("@accessGuard.has('CALENDAR_VIEW')")
    @Operation(summary = "Lista festivos oficiales y de empresa")
    public ApiResponse<List<HolidayResponse>> holidays(
            Authentication authentication,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) UUID companyUuid) {
        Company company = resolveCompany(companyUuid, authentication);
        return ApiResponse.ok("Festivos obtenidos", calendarService.holidays(company, from, to));
    }

    @GetMapping("/special-days")
    @PreAuthorize("@accessGuard.has('CALENDAR_VIEW')")
    @Operation(summary = "Lista días especiales, eventos y cierres")
    public ApiResponse<List<SpecialDayResponse>> specialDays(
            Authentication authentication,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) UUID companyUuid) {
        Company company = resolveCompany(companyUuid, authentication);
        return ApiResponse.ok("Días especiales obtenidos",
                calendarService.specialDays(company, from, to));
    }

    @GetMapping("/config")
    @PreAuthorize("@accessGuard.has('CALENDAR_VIEW')")
    @Operation(summary = "Configuración del calendario de la empresa")
    public ApiResponse<CalendarConfigResponse> config(
            Authentication authentication,
            @RequestParam(required = false) UUID companyUuid) {
        Company company = resolveCompany(companyUuid, authentication);
        return ApiResponse.ok("Configuración obtenida", calendarService.config(company));
    }

    @PutMapping("/config")
    @PreAuthorize("@accessGuard.has('CALENDAR_MANAGE')")
    @Operation(summary = "Actualiza la semana laboral y zona horaria")
    public ApiResponse<CalendarConfigResponse> updateConfig(
            Authentication authentication,
            @RequestParam(required = false) UUID companyUuid,
            @Valid @RequestBody CalendarConfigRequest request) {
        Company company = resolveCompany(companyUuid, authentication);
        return ApiResponse.ok("Configuración actualizada",
                calendarService.updateConfig(company, request, currentUser(authentication)));
    }

    @PostMapping("/holidays")
    @PreAuthorize("@accessGuard.has('CALENDAR_MANAGE')")
    @Operation(summary = "Crea un festivo oficial o de empresa")
    public ApiResponse<HolidayResponse> createHoliday(
            Authentication authentication,
            @Valid @RequestBody HolidayRequest request) {
        Company company = resolveHolidayCompany(request);
        return ApiResponse.ok("Festivo creado",
                calendarService.createHoliday(company, request, currentUser(authentication)));
    }

    @PutMapping("/holidays/{uuid}")
    @PreAuthorize("@accessGuard.has('CALENDAR_MANAGE')")
    @Operation(summary = "Actualiza un festivo")
    public ApiResponse<HolidayResponse> updateHoliday(
            Authentication authentication,
            @PathVariable UUID uuid,
            @Valid @RequestBody HolidayRequest request) {
        Company company = resolveHolidayCompany(request);
        return ApiResponse.ok("Festivo actualizado",
                calendarService.updateHoliday(uuid, company, request, currentUser(authentication)));
    }

    @DeleteMapping("/holidays/{uuid}")
    @PreAuthorize("@accessGuard.has('CALENDAR_MANAGE')")
    @Operation(summary = "Deshabilita lógicamente un festivo")
    public ApiResponse<Void> deleteHoliday(
            Authentication authentication,
            @PathVariable UUID uuid) {
        calendarService.deleteHoliday(uuid, currentUser(authentication));
        return ApiResponse.ok("Festivo deshabilitado");
    }

    @PostMapping("/special-days")
    @PreAuthorize("@accessGuard.has('CALENDAR_MANAGE')")
    @Operation(summary = "Crea un día especial, evento o cierre")
    public ApiResponse<SpecialDayResponse> createSpecialDay(
            Authentication authentication,
            @Valid @RequestBody SpecialDayRequest request) {
        Company company = resolveCompany(request.companyUuid(), authentication);
        return ApiResponse.ok("Día especial creado",
                calendarService.createSpecialDay(company, request, currentUser(authentication)));
    }

    @PutMapping("/special-days/{uuid}")
    @PreAuthorize("@accessGuard.has('CALENDAR_MANAGE')")
    @Operation(summary = "Actualiza un día especial")
    public ApiResponse<SpecialDayResponse> updateSpecialDay(
            Authentication authentication,
            @PathVariable UUID uuid,
            @Valid @RequestBody SpecialDayRequest request) {
        Company company = resolveCompany(request.companyUuid(), authentication);
        return ApiResponse.ok("Día especial actualizado",
                calendarService.updateSpecialDay(uuid, company, request, currentUser(authentication)));
    }

    @DeleteMapping("/special-days/{uuid}")
    @PreAuthorize("@accessGuard.has('CALENDAR_MANAGE')")
    @Operation(summary = "Deshabilita lógicamente un día especial")
    public ApiResponse<Void> deleteSpecialDay(
            Authentication authentication,
            @PathVariable UUID uuid) {
        calendarService.deleteSpecialDay(uuid, currentUser(authentication));
        return ApiResponse.ok("Día especial deshabilitado");
    }

    @PostMapping("/official-holidays/{year}")
    @PreAuthorize("@accessGuard.has('CALENDAR_MANAGE')")
    @Operation(summary = "Genera los festivos oficiales de México para un año")
    public ApiResponse<OfficialHolidayGenerationResponse> generateOfficialHolidays(
            @PathVariable int year) {
        return ApiResponse.ok("Festivos oficiales generados",
                calendarService.generateOfficialHolidays(year));
    }

    private Company resolveHolidayCompany(HolidayRequest request) {
        if (request.type() == HolidayType.OFFICIAL) {
            if (request.companyUuid() != null) {
                throw new BusinessException("Un festivo oficial es global y no lleva empresa");
            }
            return null;
        }
        if (request.companyUuid() == null) {
            throw new BusinessException("Un festivo de empresa requiere companyUuid");
        }
        return calendarService.findCompany(request.companyUuid());
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
            if (accessGuard.has("CALENDAR_MANAGE")) {
                return calendarService.defaultCompany();
            }
            throw new ResourceNotFoundException(
                    "No existe una empresa asociada al usuario " + authentication.getName());
        }

        Company requested = calendarService.findCompany(companyUuid);
        if (ownCompany != null && ownCompany.getId().equals(requested.getId())) {
            return requested;
        }
        if (accessGuard.has("CALENDAR_MANAGE")) {
            return requested;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN,
                "No tienes permisos para consultar el calendario de otra empresa");
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", authentication.getName()));
    }
}
