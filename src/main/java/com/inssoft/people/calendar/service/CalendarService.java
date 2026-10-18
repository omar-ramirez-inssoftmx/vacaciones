package com.inssoft.people.calendar.service;

import com.inssoft.people.calendar.dto.CalendarConfigRequest;
import com.inssoft.people.calendar.dto.CalendarConfigResponse;
import com.inssoft.people.calendar.dto.CalendarDayResponse;
import com.inssoft.people.calendar.dto.CalendarEventResponse;
import com.inssoft.people.calendar.dto.HolidayRequest;
import com.inssoft.people.calendar.dto.HolidayResponse;
import com.inssoft.people.calendar.dto.OfficialHolidayGenerationResponse;
import com.inssoft.people.calendar.dto.SpecialDayRequest;
import com.inssoft.people.calendar.dto.SpecialDayResponse;
import com.inssoft.people.calendar.entity.CompanyCalendar;
import com.inssoft.people.calendar.entity.Holiday;
import com.inssoft.people.calendar.entity.HolidayType;
import com.inssoft.people.calendar.entity.SpecialDay;
import com.inssoft.people.calendar.entity.SpecialDayType;
import com.inssoft.people.calendar.mapper.CalendarMapper;
import com.inssoft.people.calendar.repository.CompanyCalendarRepository;
import com.inssoft.people.calendar.repository.HolidayRepository;
import com.inssoft.people.calendar.repository.SpecialDayRepository;
import com.inssoft.people.common.audit.AuditService;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.common.exception.ConflictException;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.common.util.CodeNormalizer;
import com.inssoft.people.organization.entity.Company;
import com.inssoft.people.organization.repository.CompanyRepository;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.model.VacationRequest;
import com.mx.vacaciones.repository.VacationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CalendarService {

    private static final String DEFAULT_WORKWEEK = "MON,TUE,WED,THU,FRI";
    private static final int MAX_RANGE_DAYS = 366;

    private final CompanyCalendarRepository calendarRepository;
    private final HolidayRepository holidayRepository;
    private final SpecialDayRepository specialDayRepository;
    private final CompanyRepository companyRepository;
    private final VacationRepository vacationRepository;
    private final AuditService auditService;

    public CalendarService(
            CompanyCalendarRepository calendarRepository,
            HolidayRepository holidayRepository,
            SpecialDayRepository specialDayRepository,
            CompanyRepository companyRepository,
            VacationRepository vacationRepository,
            AuditService auditService) {
        this.calendarRepository = calendarRepository;
        this.holidayRepository = holidayRepository;
        this.specialDayRepository = specialDayRepository;
        this.companyRepository = companyRepository;
        this.vacationRepository = vacationRepository;
        this.auditService = auditService;
    }

    @Transactional
    public CompanyCalendar getOrCreateCalendar(Company company) {
        return calendarRepository.findByCompany_IdAndDeletedAtIsNull(company.getId())
                .orElseGet(() -> {
                    CompanyCalendar calendar = new CompanyCalendar();
                    calendar.setCompany(company);
                    String name = "Calendario " + company.getLegalName();
                    calendar.setName(name.length() > 150 ? name.substring(0, 150) : name);
                    calendar.setTimezone(company.getTimezone() == null
                            ? "America/Mexico_City"
                            : company.getTimezone());
                    calendar.setWorkweek(DEFAULT_WORKWEEK);
                    calendar.setEnabled(true);
                    return calendarRepository.save(calendar);
                });
    }

    @Transactional
    public CalendarConfigResponse config(Company company) {
        return CalendarMapper.toConfigResponse(getOrCreateCalendar(company));
    }

    @Transactional
    public CalendarConfigResponse updateConfig(
            Company company,
            CalendarConfigRequest request,
            User actor) {

        CompanyCalendar calendar = getOrCreateCalendar(company);
        CalendarConfigResponse previous = CalendarMapper.toConfigResponse(calendar);

        if (request.name() != null && !request.name().isBlank()) {
            calendar.setName(request.name().trim());
        }
        if (request.timezone() != null && !request.timezone().isBlank()) {
            calendar.setTimezone(request.timezone().trim());
        }
        String workweek = CodeNormalizer.trimToNull(request.workweek());
        calendar.setWorkweek(CodeNormalizer.normalize(
                workweek == null ? DEFAULT_WORKWEEK : workweek));
        // Valida el formato antes de persistir.
        parseWorkweek(calendar.getWorkweek());

        CompanyCalendar saved = calendarRepository.save(calendar);
        CalendarConfigResponse response = CalendarMapper.toConfigResponse(saved);
        auditService.record("CALENDAR_CONFIG_UPDATE", "CompanyCalendar",
                company.getUuid().toString(), previous, response);
        return response;
    }

    @Transactional(readOnly = true)
    public boolean isWorkingDay(Long companyId, LocalDate date) {
        if (date == null) {
            return false;
        }

        CompanyCalendar calendar = calendarRepository
                .findByCompany_IdAndDeletedAtIsNull(companyId)
                .orElse(null);
        Set<DayOfWeek> workweek = calendar == null
                ? parseWorkweek(DEFAULT_WORKWEEK)
                : parseWorkweek(calendar.getWorkweek());

        if (!workweek.contains(date.getDayOfWeek())) {
            return false;
        }

        if (!holidayRepository.findForCompanyBetween(companyId, date, date).isEmpty()) {
            return false;
        }

        return specialDayRepository.findForCompanyBetween(companyId, date, date).stream()
                .noneMatch(SpecialDay::isAffectsWorkdays);
    }

    @Transactional(readOnly = true)
    public int businessDays(Long companyId, LocalDate start, LocalDate end) {
        validateRange(start, end, MAX_RANGE_DAYS);
        int count = 0;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (isWorkingDay(companyId, date)) {
                count++;
            }
        }
        return count;
    }

    @Transactional(readOnly = true)
    public List<HolidayResponse> holidays(Company company, LocalDate from, LocalDate to) {
        validateRange(from, to, MAX_RANGE_DAYS);
        return holidayRepository.findForCompanyBetween(company.getId(), from, to).stream()
                .map(CalendarMapper::toHolidayResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SpecialDayResponse> specialDays(Company company, LocalDate from, LocalDate to) {
        validateRange(from, to, MAX_RANGE_DAYS);
        return specialDayRepository.findForCompanyBetween(company.getId(), from, to).stream()
                .map(CalendarMapper::toSpecialDayResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CalendarDayResponse> days(Company company, LocalDate from, LocalDate to) {
        validateRange(from, to, MAX_RANGE_DAYS);

        List<Holiday> holidays = holidayRepository.findForCompanyBetween(company.getId(), from, to);
        List<SpecialDay> specialDays = specialDayRepository.findForCompanyBetween(company.getId(), from, to);

        Map<LocalDate, Holiday> holidayByDate = new HashMap<>();
        for (Holiday holiday : holidays) {
            holidayByDate.putIfAbsent(holiday.getHolidayDate(), holiday);
        }

        Map<LocalDate, SpecialDay> specialByDate = new HashMap<>();
        Set<LocalDate> closedDates = new HashSet<>();
        for (SpecialDay specialDay : specialDays) {
            for (LocalDate date = specialDay.getStartDate(); !date.isAfter(specialDay.getEndDate()); date = date.plusDays(1)) {
                specialByDate.putIfAbsent(date, specialDay);
                if (specialDay.isAffectsWorkdays()) {
                    closedDates.add(date);
                }
            }
        }

        Set<DayOfWeek> workweek = parseWorkweek(calendarWorkweek(company.getId()));
        List<CalendarDayResponse> result = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            boolean working = workweek.contains(date.getDayOfWeek())
                    && !holidayByDate.containsKey(date)
                    && !closedDates.contains(date);
            result.add(CalendarMapper.toDayResponse(
                    date, working, holidayByDate.get(date), specialByDate.get(date)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<CalendarEventResponse> events(Company company, LocalDate from, LocalDate to) {
        validateRange(from, to, MAX_RANGE_DAYS);

        List<CalendarEventResponse> events = new ArrayList<>();
        holidayRepository.findForCompanyBetween(company.getId(), from, to)
                .stream()
                .map(CalendarMapper::toHolidayEvent)
                .forEach(events::add);

        specialDayRepository.findForCompanyBetween(company.getId(), from, to)
                .stream()
                .map(CalendarMapper::toSpecialDayEvent)
                .forEach(events::add);

        List<VacationRequest> vacations = vacationRepository
                .findApprovedForCalendarByCompany(company.getId(), from, to);
        vacations.stream()
                .map(CalendarMapper::toVacationEvent)
                .forEach(events::add);

        events.sort(Comparator.comparing(CalendarEventResponse::startDate));
        return events;
    }

    @Transactional(readOnly = true)
    public List<LocalDate> holidayDatesBetween(Long companyId, int startYear, int endYear) {
        if (startYear > endYear || endYear - startYear > 10) {
            throw new BusinessException("Rango de años inválido");
        }
        LocalDate from = LocalDate.of(startYear, 1, 1);
        LocalDate to = LocalDate.of(endYear, 12, 31);

        Set<LocalDate> dates = new HashSet<>();
        holidayRepository.findForCompanyBetween(companyId, from, to)
                .forEach(holiday -> dates.add(holiday.getHolidayDate()));

        specialDayRepository.findForCompanyBetween(companyId, from, to).stream()
                .filter(SpecialDay::isAffectsWorkdays)
                .forEach(specialDay -> {
                    for (LocalDate date = specialDay.getStartDate();
                         !date.isAfter(specialDay.getEndDate());
                         date = date.plusDays(1)) {
                        dates.add(date);
                    }
                });

        return dates.stream().sorted().toList();
    }

    @Transactional
    public HolidayResponse createHoliday(Company company, HolidayRequest request, User actor) {
        HolidayType type = request.type();
        if (type == null) {
            throw new BusinessException("El tipo de festivo es obligatorio");
        }
        validateHolidayCompany(type, company);

        LocalDate date = request.date();
        validateUniqueHoliday(type, company, date, null);

        Holiday holiday = new Holiday();
        holiday.setCompany(company);
        holiday.setName(request.name().trim());
        holiday.setHolidayDate(date);
        holiday.setType(type);
        holiday.setDescription(CodeNormalizer.trimToNull(request.description()));
        holiday.setEnabled(request.enabled() == null || request.enabled());

        Holiday saved = holidayRepository.save(holiday);
        HolidayResponse response = CalendarMapper.toHolidayResponse(saved);
        auditService.record("CALENDAR_HOLIDAY_CREATE", "Holiday",
                saved.getUuid().toString(), response);
        return response;
    }

    @Transactional
    public HolidayResponse updateHoliday(
            UUID uuid,
            Company company,
            HolidayRequest request,
            User actor) {

        Holiday holiday = findHoliday(uuid);
        HolidayResponse previous = CalendarMapper.toHolidayResponse(holiday);

        HolidayType type = request.type();
        if (type == null) {
            throw new BusinessException("El tipo de festivo es obligatorio");
        }
        validateHolidayCompany(type, company);
        validateUniqueHoliday(type, company, request.date(), uuid);

        holiday.setCompany(company);
        holiday.setName(request.name().trim());
        holiday.setHolidayDate(request.date());
        holiday.setType(type);
        holiday.setDescription(CodeNormalizer.trimToNull(request.description()));
        if (request.enabled() != null) {
            holiday.setEnabled(request.enabled());
        }

        Holiday saved = holidayRepository.save(holiday);
        HolidayResponse response = CalendarMapper.toHolidayResponse(saved);
        auditService.record("CALENDAR_HOLIDAY_UPDATE", "Holiday",
                uuid.toString(), previous, response);
        return response;
    }

    @Transactional
    public void deleteHoliday(UUID uuid, User actor) {
        Holiday holiday = findHoliday(uuid);
        HolidayResponse previous = CalendarMapper.toHolidayResponse(holiday);
        holiday.setDeletedAt(java.time.Instant.now());
        holiday.setEnabled(false);
        holidayRepository.save(holiday);
        auditService.record("CALENDAR_HOLIDAY_DELETE", "Holiday",
                uuid.toString(), previous, null);
    }

    @Transactional
    public SpecialDayResponse createSpecialDay(
            Company company,
            SpecialDayRequest request,
            User actor) {

        if (request.type() == null) {
            throw new BusinessException("El tipo de día especial es obligatorio");
        }
        validateSpecialDates(request.startDate(), request.endDate());

        SpecialDay specialDay = new SpecialDay();
        specialDay.setCompany(company);
        specialDay.setName(request.name().trim());
        specialDay.setStartDate(request.startDate());
        specialDay.setEndDate(request.endDate());
        specialDay.setType(request.type());
        specialDay.setAffectsWorkdays(Boolean.TRUE.equals(request.affectsWorkdays()));
        specialDay.setDescription(CodeNormalizer.trimToNull(request.description()));
        specialDay.setEnabled(request.enabled() == null || request.enabled());

        SpecialDay saved = specialDayRepository.save(specialDay);
        SpecialDayResponse response = CalendarMapper.toSpecialDayResponse(saved);
        auditService.record("CALENDAR_SPECIAL_DAY_CREATE", "SpecialDay",
                saved.getUuid().toString(), response);
        return response;
    }

    @Transactional
    public SpecialDayResponse updateSpecialDay(
            UUID uuid,
            Company company,
            SpecialDayRequest request,
            User actor) {

        SpecialDay specialDay = findSpecialDay(uuid);
        SpecialDayResponse previous = CalendarMapper.toSpecialDayResponse(specialDay);
        if (request.type() == null) {
            throw new BusinessException("El tipo de día especial es obligatorio");
        }
        validateSpecialDates(request.startDate(), request.endDate());

        specialDay.setCompany(company);
        specialDay.setName(request.name().trim());
        specialDay.setStartDate(request.startDate());
        specialDay.setEndDate(request.endDate());
        specialDay.setType(request.type());
        if (request.affectsWorkdays() != null) {
            specialDay.setAffectsWorkdays(request.affectsWorkdays());
        }
        specialDay.setDescription(CodeNormalizer.trimToNull(request.description()));
        if (request.enabled() != null) {
            specialDay.setEnabled(request.enabled());
        }

        SpecialDay saved = specialDayRepository.save(specialDay);
        SpecialDayResponse response = CalendarMapper.toSpecialDayResponse(saved);
        auditService.record("CALENDAR_SPECIAL_DAY_UPDATE", "SpecialDay",
                uuid.toString(), previous, response);
        return response;
    }

    @Transactional
    public void deleteSpecialDay(UUID uuid, User actor) {
        SpecialDay specialDay = findSpecialDay(uuid);
        SpecialDayResponse previous = CalendarMapper.toSpecialDayResponse(specialDay);
        specialDay.setDeletedAt(java.time.Instant.now());
        specialDay.setEnabled(false);
        specialDayRepository.save(specialDay);
        auditService.record("CALENDAR_SPECIAL_DAY_DELETE", "SpecialDay",
                uuid.toString(), previous, null);
    }

    @Transactional
    public OfficialHolidayGenerationResponse generateOfficialHolidays(int year) {
        if (year < 2000 || year > 2100) {
            throw new BusinessException("Año inválido para generar festivos oficiales");
        }

        int created = 0;
        created += createOfficialIfMissing(year, "Año Nuevo", LocalDate.of(year, 1, 1));
        created += createOfficialIfMissing(year, "Día de la Constitución", nthMonday(year, 2, 1));
        created += createOfficialIfMissing(year, "Natalicio de Benito Juárez", nthMonday(year, 3, 3));
        created += createOfficialIfMissing(year, "Día del Trabajo", LocalDate.of(year, 5, 1));
        created += createOfficialIfMissing(year, "Día de la Independencia", LocalDate.of(year, 9, 16));
        created += createOfficialIfMissing(year, "Revolución Mexicana", nthMonday(year, 11, 3));
        created += createOfficialIfMissing(year, "Navidad", LocalDate.of(year, 12, 25));

        OfficialHolidayGenerationResponse response =
                new OfficialHolidayGenerationResponse(year, created);
        auditService.record("CALENDAR_OFFICIAL_HOLIDAYS_GENERATE", "Holiday",
                String.valueOf(year), response);
        return response;
    }

    @Transactional(readOnly = true)
    public Company findCompany(UUID companyUuid) {
        return companyRepository.findByUuidAndDeletedAtIsNull(companyUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa", companyUuid));
    }

    @Transactional(readOnly = true)
    public Company defaultCompany() {
        return companyRepository.findFirstByDeletedAtIsNullOrderByIdAsc()
                .orElseThrow(() -> new ResourceNotFoundException("No existe una empresa activa"));
    }

    private String calendarWorkweek(Long companyId) {
        return calendarRepository.findByCompany_IdAndDeletedAtIsNull(companyId)
                .map(CompanyCalendar::getWorkweek)
                .orElse(DEFAULT_WORKWEEK);
    }

    private Holiday findHoliday(UUID uuid) {
        return holidayRepository.findByUuidAndDeletedAtIsNull(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Festivo", uuid));
    }

    private SpecialDay findSpecialDay(UUID uuid) {
        return specialDayRepository.findByUuidAndDeletedAtIsNull(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Día especial", uuid));
    }

    private void validateHolidayCompany(HolidayType type, Company company) {
        if (type == HolidayType.OFFICIAL && company != null) {
            throw new BusinessException("Un festivo oficial no pertenece a una empresa");
        }
        if (type == HolidayType.COMPANY && company == null) {
            throw new BusinessException("Un festivo de empresa requiere empresa");
        }
    }

    private void validateUniqueHoliday(
            HolidayType type, Company company, LocalDate date, UUID currentUuid) {

        if (date == null) {
            throw new BusinessException("La fecha del festivo es obligatoria");
        }

        boolean exists;
        if (company == null) {
            exists = currentUuid == null
                    ? holidayRepository.existsByCompanyIsNullAndHolidayDateAndTypeAndDeletedAtIsNull(date, type)
                    : holidayRepository.existsByCompanyIsNullAndHolidayDateAndTypeAndUuidNotAndDeletedAtIsNull(date, type, currentUuid);
        } else {
            exists = currentUuid == null
                    ? holidayRepository.existsByCompany_IdAndHolidayDateAndTypeAndDeletedAtIsNull(company.getId(), date, type)
                    : holidayRepository.existsByCompany_IdAndHolidayDateAndTypeAndUuidNotAndDeletedAtIsNull(company.getId(), date, type, currentUuid);
        }

        if (exists) {
            throw new ConflictException("Ya existe un festivo de tipo " + type + " para la fecha " + date);
        }
    }

    private void validateSpecialDates(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) {
            throw new BusinessException("El rango de fechas del día especial es inválido");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw new BusinessException("El día especial no puede exceder " + MAX_RANGE_DAYS + " días");
        }
    }

    private void validateRange(LocalDate from, LocalDate to, int maxDays) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new BusinessException("Rango de fechas inválido");
        }
        if (ChronoUnit.DAYS.between(from, to) > maxDays) {
            throw new BusinessException("El rango máximo es " + maxDays + " días");
        }
    }

    private int createOfficialIfMissing(int year, String name, LocalDate date) {
        if (holidayRepository.existsByCompanyIsNullAndHolidayDateAndTypeAndDeletedAtIsNull(
                date, HolidayType.OFFICIAL)) {
            return 0;
        }

        Holiday holiday = new Holiday();
        holiday.setCompany(null);
        holiday.setName(name);
        holiday.setHolidayDate(date);
        holiday.setType(HolidayType.OFFICIAL);
        holiday.setYear(year);
        holiday.setDescription("Feriado oficial México");
        holiday.setEnabled(true);
        holidayRepository.save(holiday);
        return 1;
    }

    private Set<DayOfWeek> parseWorkweek(String value) {
        if (value == null || value.isBlank()) {
            value = DEFAULT_WORKWEEK;
        }

        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String token : value.split(",")) {
            switch (token.trim().toUpperCase()) {
                case "MON" -> days.add(DayOfWeek.MONDAY);
                case "TUE" -> days.add(DayOfWeek.TUESDAY);
                case "WED" -> days.add(DayOfWeek.WEDNESDAY);
                case "THU" -> days.add(DayOfWeek.THURSDAY);
                case "FRI" -> days.add(DayOfWeek.FRIDAY);
                case "SAT" -> days.add(DayOfWeek.SATURDAY);
                case "SUN" -> days.add(DayOfWeek.SUNDAY);
                default -> throw new BusinessException("Día de semana inválido: " + token);
            }
        }

        if (days.isEmpty()) {
            throw new BusinessException("La semana laboral debe tener al menos un día");
        }
        return days;
    }

    private LocalDate nthMonday(int year, int month, int nth) {
        LocalDate first = LocalDate.of(year, month, 1);
        int shift = (DayOfWeek.MONDAY.getValue() - first.getDayOfWeek().getValue() + 7) % 7;
        return first.plusDays(shift).plusWeeks(nth - 1L);
    }
}
