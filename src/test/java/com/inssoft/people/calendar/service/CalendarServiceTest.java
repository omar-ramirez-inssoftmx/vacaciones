package com.inssoft.people.calendar.service;

import com.inssoft.people.calendar.dto.CalendarConfigRequest;
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
import com.inssoft.people.calendar.repository.CompanyCalendarRepository;
import com.inssoft.people.calendar.repository.HolidayRepository;
import com.inssoft.people.calendar.repository.SpecialDayRepository;
import com.inssoft.people.common.audit.AuditService;
import com.inssoft.people.common.exception.ConflictException;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.organization.entity.Company;
import com.inssoft.people.organization.repository.CompanyRepository;
import com.mx.vacaciones.model.VacationRequest;
import com.mx.vacaciones.repository.VacationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @Mock
    private CompanyCalendarRepository calendarRepository;

    @Mock
    private HolidayRepository holidayRepository;

    @Mock
    private SpecialDayRepository specialDayRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private VacationRepository vacationRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private CalendarService service;

    @Test
    void mondayIsWorkingDayWhenNoCalendarExists() {
        LocalDate monday = LocalDate.of(2026, 8, 3);
        when(calendarRepository.findByCompany_IdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());
        when(holidayRepository.findForCompanyBetween(1L, monday, monday)).thenReturn(List.of());
        when(specialDayRepository.findForCompanyBetween(1L, monday, monday)).thenReturn(List.of());

        assertThat(service.isWorkingDay(1L, monday)).isTrue();
    }

    @Test
    void saturdayIsNotWorkingDay() {
        assertThat(service.isWorkingDay(1L, LocalDate.of(2026, 8, 8))).isFalse();
        verify(holidayRepository, never()).findForCompanyBetween(anyLong(), any(), any());
    }

    @Test
    void holidayIsNotWorkingDay() {
        LocalDate monday = LocalDate.of(2026, 8, 3);
        when(calendarRepository.findByCompany_IdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());
        when(holidayRepository.findForCompanyBetween(1L, monday, monday))
                .thenReturn(List.of(holiday(company(), monday, HolidayType.COMPANY)));

        assertThat(service.isWorkingDay(1L, monday)).isFalse();
    }

    @Test
    void closureAffectingWorkdaysIsNotWorkingDay() {
        LocalDate monday = LocalDate.of(2026, 8, 3);
        when(calendarRepository.findByCompany_IdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());
        when(holidayRepository.findForCompanyBetween(1L, monday, monday)).thenReturn(List.of());
        when(specialDayRepository.findForCompanyBetween(1L, monday, monday))
                .thenReturn(List.of(specialDay(company(), monday, monday, true)));

        assertThat(service.isWorkingDay(1L, monday)).isFalse();
    }

    @Test
    void countsBusinessDaysForWeek() {
        LocalDate monday = LocalDate.of(2026, 8, 3);
        LocalDate friday = LocalDate.of(2026, 8, 7);
        when(calendarRepository.findByCompany_IdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());
        when(holidayRepository.findForCompanyBetween(eq(1L), any(), any())).thenReturn(List.of());
        when(specialDayRepository.findForCompanyBetween(eq(1L), any(), any())).thenReturn(List.of());

        assertThat(service.businessDays(1L, monday, friday)).isEqualTo(5);
    }

    @Test
    void returnsCalendarDays() {
        Company company = company();
        LocalDate monday = LocalDate.of(2026, 8, 3);
        LocalDate sunday = LocalDate.of(2026, 8, 9);
        Holiday holiday = holiday(company, LocalDate.of(2026, 8, 5), HolidayType.COMPANY);
        SpecialDay closure = specialDay(company, LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 6), true);

        when(calendarRepository.findByCompany_IdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());
        when(holidayRepository.findForCompanyBetween(1L, monday, sunday)).thenReturn(List.of(holiday));
        when(specialDayRepository.findForCompanyBetween(1L, monday, sunday)).thenReturn(List.of(closure));

        List<CalendarDayResponse> days = service.days(company, monday, sunday);

        assertThat(days).hasSize(7);
        assertThat(days.get(0).workingDay()).isTrue();
        assertThat(days.get(2).workingDay()).isFalse();
        assertThat(days.get(3).workingDay()).isFalse();
        assertThat(days.get(5).workingDay()).isFalse();
    }

    @Test
    void returnsEventsIncludingVacations() {
        Company company = company();
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 31);

        when(holidayRepository.findForCompanyBetween(1L, from, to))
                .thenReturn(List.of(holiday(company, LocalDate.of(2026, 8, 5), HolidayType.COMPANY)));
        when(specialDayRepository.findForCompanyBetween(1L, from, to))
                .thenReturn(List.of(specialDay(company, LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 6), true)));
        when(vacationRepository.findApprovedForCalendarByCompany(1L, from, to))
                .thenReturn(List.of(vacation()));

        List<CalendarEventResponse> events = service.events(company, from, to);

        assertThat(events).extracting(CalendarEventResponse::type)
                .containsExactlyInAnyOrder("HOLIDAY", "SPECIAL_DAY", "VACATION");
    }

    @Test
    void createsCompanyHoliday() {
        Company company = company();
        LocalDate date = LocalDate.of(2026, 12, 31);
        HolidayRequest request = new HolidayRequest(
                company.getUuid(), "Cierre de fin de año", date, HolidayType.COMPANY, null, true);

        when(holidayRepository.existsByCompany_IdAndHolidayDateAndTypeAndDeletedAtIsNull(
                1L, date, HolidayType.COMPANY)).thenReturn(false);
        when(holidayRepository.save(any(Holiday.class))).thenAnswer(invocation -> {
            Holiday holiday = invocation.getArgument(0);
            holiday.setId(10L);
            holiday.setUuid(UUID.randomUUID());
            holiday.setYear(date.getYear());
            return holiday;
        });

        HolidayResponse response = service.createHoliday(company, request, null);

        assertThat(response.name()).isEqualTo("Cierre de fin de año");
        assertThat(response.type()).isEqualTo(HolidayType.COMPANY);
        verify(auditService).record(eq("CALENDAR_HOLIDAY_CREATE"), eq("Holiday"),
                any(), any());
    }

    @Test
    void rejectsDuplicateHoliday() {
        Company company = company();
        LocalDate date = LocalDate.of(2026, 12, 31);
        HolidayRequest request = new HolidayRequest(
                company.getUuid(), "Cierre", date, HolidayType.COMPANY, null, true);
        when(holidayRepository.existsByCompany_IdAndHolidayDateAndTypeAndDeletedAtIsNull(
                1L, date, HolidayType.COMPANY)).thenReturn(true);

        assertThatThrownBy(() -> service.createHoliday(company, request, null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createsSpecialDay() {
        Company company = company();
        SpecialDayRequest request = new SpecialDayRequest(
                company.getUuid(), "Cierre corporativo",
                LocalDate.of(2026, 12, 24), LocalDate.of(2026, 12, 31),
                SpecialDayType.CLOSURE, true, null, true);

        when(specialDayRepository.save(any(SpecialDay.class))).thenAnswer(invocation -> {
            SpecialDay day = invocation.getArgument(0);
            day.setId(20L);
            day.setUuid(UUID.randomUUID());
            return day;
        });

        SpecialDayResponse response = service.createSpecialDay(company, request, null);

        assertThat(response.name()).isEqualTo("Cierre corporativo");
        assertThat(response.affectsWorkdays()).isTrue();
    }

    @Test
    void updatesCalendarConfig() {
        Company company = company();
        CompanyCalendar calendar = calendar(company);
        when(calendarRepository.findByCompany_IdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(calendar));
        when(calendarRepository.save(any(CompanyCalendar.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateConfig(
                company,
                new CalendarConfigRequest("Calendario INSSOFT", "America/Mexico_City",
                        "MON,TUE,WED,THU,FRI,SAT"),
                null);

        assertThat(response.workweek()).isEqualTo("MON,TUE,WED,THU,FRI,SAT");
        verify(calendarRepository).save(calendar);
    }

    @Test
    void generatesMissingOfficialHolidays() {
        when(holidayRepository.existsByCompanyIsNullAndHolidayDateAndTypeAndDeletedAtIsNull(
                any(LocalDate.class), eq(HolidayType.OFFICIAL))).thenReturn(false);
        when(holidayRepository.save(any(Holiday.class))).thenAnswer(invocation -> {
            Holiday holiday = invocation.getArgument(0);
            holiday.setId(1L);
            holiday.setUuid(UUID.randomUUID());
            return holiday;
        });

        OfficialHolidayGenerationResponse response = service.generateOfficialHolidays(2026);

        assertThat(response.year()).isEqualTo(2026);
        assertThat(response.created()).isEqualTo(7);
        verify(holidayRepository, times(7)).save(any(Holiday.class));
    }

    @Test
    void returnsHolidayAndClosureDatesForLegacyForm() {
        Company company = company();
        when(holidayRepository.findForCompanyBetween(1L, LocalDate.of(2025, 1, 1), LocalDate.of(2027, 12, 31)))
                .thenReturn(List.of(holiday(company, LocalDate.of(2026, 1, 1), HolidayType.COMPANY)));
        when(specialDayRepository.findForCompanyBetween(1L, LocalDate.of(2025, 1, 1), LocalDate.of(2027, 12, 31)))
                .thenReturn(List.of(specialDay(company, LocalDate.of(2026, 12, 24), LocalDate.of(2026, 12, 31), true)));

        List<LocalDate> dates = service.holidayDatesBetween(1L, 2025, 2027);

        assertThat(dates).contains(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 24));
        assertThat(dates).hasSize(9);
    }

    private Company company() {
        Company company = new Company();
        company.setId(1L);
        company.setUuid(UUID.randomUUID());
        company.setCode("INSSOFT");
        company.setLegalName("INSSOFT");
        company.setTimezone("America/Mexico_City");
        company.setEnabled(true);
        return company;
    }

    private CompanyCalendar calendar(Company company) {
        CompanyCalendar calendar = new CompanyCalendar();
        calendar.setId(5L);
        calendar.setUuid(UUID.randomUUID());
        calendar.setCompany(company);
        calendar.setName("Calendario");
        calendar.setTimezone("America/Mexico_City");
        calendar.setWorkweek("MON,TUE,WED,THU,FRI");
        calendar.setEnabled(true);
        return calendar;
    }

    private Holiday holiday(Company company, LocalDate date, HolidayType type) {
        Holiday holiday = new Holiday();
        holiday.setId(10L);
        holiday.setUuid(UUID.randomUUID());
        holiday.setCompany(company);
        holiday.setName("Festivo");
        holiday.setHolidayDate(date);
        holiday.setType(type);
        holiday.setYear(date.getYear());
        holiday.setEnabled(true);
        return holiday;
    }

    private SpecialDay specialDay(Company company, LocalDate start, LocalDate end, boolean affects) {
        SpecialDay specialDay = new SpecialDay();
        specialDay.setId(20L);
        specialDay.setUuid(UUID.randomUUID());
        specialDay.setCompany(company);
        specialDay.setName("Día especial");
        specialDay.setStartDate(start);
        specialDay.setEndDate(end);
        specialDay.setType(SpecialDayType.CLOSURE);
        specialDay.setAffectsWorkdays(affects);
        specialDay.setEnabled(true);
        return specialDay;
    }

    private VacationRequest vacation() {
        Employee employee = new Employee();
        employee.setFirstName("Omar");
        employee.setLastName("Ramírez");

        VacationRequest request = new VacationRequest();
        request.setUuid(UUID.randomUUID());
        request.setUsername("omar");
        request.setEmployee(employee);
        request.setStartDate(LocalDate.of(2026, 8, 10));
        request.setEndDate(LocalDate.of(2026, 8, 14));
        request.setDays(5);
        request.setStatus("APPROVED");
        return request;
    }
}
