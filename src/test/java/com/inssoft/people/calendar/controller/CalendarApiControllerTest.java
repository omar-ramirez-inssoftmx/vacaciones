package com.inssoft.people.calendar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.inssoft.people.calendar.dto.CalendarConfigResponse;
import com.inssoft.people.calendar.dto.CalendarDayResponse;
import com.inssoft.people.calendar.dto.HolidayResponse;
import com.inssoft.people.calendar.dto.OfficialHolidayGenerationResponse;
import com.inssoft.people.calendar.entity.HolidayType;
import com.inssoft.people.calendar.service.CalendarService;
import com.inssoft.people.common.exception.GlobalExceptionHandler;
import com.inssoft.people.common.security.AccessGuard;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.organization.entity.Company;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CalendarApiControllerTest {

    private CalendarService calendarService;
    private EmployeeRepository employeeRepository;
    private UserRepository userRepository;
    private AccessGuard accessGuard;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        calendarService = mock(CalendarService.class);
        employeeRepository = mock(EmployeeRepository.class);
        userRepository = mock(UserRepository.class);
        accessGuard = mock(AccessGuard.class);

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new CalendarApiController(
                        calendarService, employeeRepository, userRepository, accessGuard))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void returnsCalendarDays() throws Exception {
        Company company = company();
        Employee employee = employee(company);
        LocalDate from = LocalDate.of(2026, 8, 3);
        LocalDate to = LocalDate.of(2026, 8, 9);

        when(employeeRepository.findByUser_UsernameAndDeletedAtIsNull("omar"))
                .thenReturn(Optional.of(employee));
        when(calendarService.days(company, from, to)).thenReturn(List.of(
                new CalendarDayResponse(from, true, null, null, null, null)));

        mockMvc.perform(get("/api/v1/calendar/days")
                        .param("from", "2026-08-03")
                        .param("to", "2026-08-09")
                        .principal(principal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].workingDay").value(true));
    }

    @Test
    void returnsCalendarConfig() throws Exception {
        Company company = company();
        when(employeeRepository.findByUser_UsernameAndDeletedAtIsNull("omar"))
                .thenReturn(Optional.of(employee(company)));
        when(calendarService.config(company)).thenReturn(new CalendarConfigResponse(
                UUID.randomUUID(), company.getUuid(), company.getLegalName(),
                "Calendario", "America/Mexico_City", "MON,TUE,WED,THU,FRI", true));

        mockMvc.perform(get("/api/v1/calendar/config").principal(principal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workweek").value("MON,TUE,WED,THU,FRI"));
    }

    @Test
    void createsCompanyHoliday() throws Exception {
        Company company = company();
        UUID companyUuid = company.getUuid();
        User user = user();
        when(calendarService.findCompany(companyUuid)).thenReturn(company);
        when(userRepository.findByUsername("omar")).thenReturn(Optional.of(user));
        when(calendarService.createHoliday(eq(company), any(), eq(user)))
                .thenReturn(new HolidayResponse(
                        UUID.randomUUID(), companyUuid, company.getLegalName(),
                        "Cierre", LocalDate.of(2026, 12, 31), HolidayType.COMPANY,
                        2026, null, true));

        mockMvc.perform(post("/api/v1/calendar/holidays")
                        .principal(principal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyUuid": "%s",
                                  "name": "Cierre",
                                  "date": "2026-12-31",
                                  "type": "COMPANY"
                                }
                                """.formatted(companyUuid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("COMPANY"));
    }

    @Test
    void generatesOfficialHolidays() throws Exception {
        when(calendarService.generateOfficialHolidays(2026))
                .thenReturn(new OfficialHolidayGenerationResponse(2026, 7));

        mockMvc.perform(post("/api/v1/calendar/official-holidays/2026")
                        .principal(principal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(7));
    }

    @Test
    void validatesHolidayBody() throws Exception {
        mockMvc.perform(post("/api/v1/calendar/holidays")
                        .principal(principal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    private UsernamePasswordAuthenticationToken principal() {
        return new UsernamePasswordAuthenticationToken("omar", null, List.of());
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

    private Employee employee(Company company) {
        Employee employee = new Employee();
        employee.setId(10L);
        employee.setUuid(UUID.randomUUID());
        employee.setCompany(company);
        employee.setFirstName("Omar");
        employee.setLastName("Ramírez");
        employee.setEnabled(true);
        return employee;
    }

    private User user() {
        User user = new User();
        user.setId(2L);
        user.setUuid(UUID.randomUUID());
        user.setUsername("omar");
        user.setName("Omar");
        user.setPassword("hash");
        user.setEnabled(true);
        return user;
    }
}
