package com.inssoft.people.calendar.mapper;

import com.inssoft.people.calendar.dto.CalendarConfigResponse;
import com.inssoft.people.calendar.dto.CalendarDayResponse;
import com.inssoft.people.calendar.dto.CalendarEventResponse;
import com.inssoft.people.calendar.dto.HolidayResponse;
import com.inssoft.people.calendar.dto.SpecialDayResponse;
import com.inssoft.people.calendar.entity.CompanyCalendar;
import com.inssoft.people.calendar.entity.Holiday;
import com.inssoft.people.calendar.entity.SpecialDay;
import com.inssoft.people.employee.mapper.EmployeeMapper;
import com.mx.vacaciones.model.VacationRequest;

import java.time.LocalDate;

public final class CalendarMapper {

    private CalendarMapper() {
    }

    public static CalendarConfigResponse toConfigResponse(CompanyCalendar calendar) {
        return new CalendarConfigResponse(
                calendar.getUuid(),
                calendar.getCompany().getUuid(),
                calendar.getCompany().getLegalName(),
                calendar.getName(),
                calendar.getTimezone(),
                calendar.getWorkweek(),
                calendar.isEnabled()
        );
    }

    public static HolidayResponse toHolidayResponse(Holiday holiday) {
        return new HolidayResponse(
                holiday.getUuid(),
                holiday.getCompany() == null ? null : holiday.getCompany().getUuid(),
                holiday.getCompany() == null ? null : holiday.getCompany().getLegalName(),
                holiday.getName(),
                holiday.getHolidayDate(),
                holiday.getType(),
                holiday.getYear(),
                holiday.getDescription(),
                holiday.isEnabled()
        );
    }

    public static SpecialDayResponse toSpecialDayResponse(SpecialDay specialDay) {
        return new SpecialDayResponse(
                specialDay.getUuid(),
                specialDay.getCompany().getUuid(),
                specialDay.getCompany().getLegalName(),
                specialDay.getName(),
                specialDay.getStartDate(),
                specialDay.getEndDate(),
                specialDay.getType(),
                specialDay.isAffectsWorkdays(),
                specialDay.getDescription(),
                specialDay.isEnabled()
        );
    }

    public static CalendarDayResponse toDayResponse(
            LocalDate date,
            boolean workingDay,
            Holiday holiday,
            SpecialDay specialDay) {
        return new CalendarDayResponse(
                date,
                workingDay,
                holiday == null ? null : holiday.getName(),
                holiday == null ? null : holiday.getType().name(),
                specialDay == null ? null : specialDay.getName(),
                specialDay == null ? null : specialDay.getType().name()
        );
    }

    public static CalendarEventResponse toHolidayEvent(Holiday holiday) {
        return new CalendarEventResponse(
                "HOLIDAY",
                holiday.getName(),
                holiday.getHolidayDate(),
                holiday.getHolidayDate(),
                true,
                holiday.getUuid(),
                null
        );
    }

    public static CalendarEventResponse toSpecialDayEvent(SpecialDay specialDay) {
        return new CalendarEventResponse(
                "SPECIAL_DAY",
                specialDay.getName(),
                specialDay.getStartDate(),
                specialDay.getEndDate(),
                specialDay.isAffectsWorkdays(),
                specialDay.getUuid(),
                null
        );
    }

    public static CalendarEventResponse toVacationEvent(VacationRequest request) {
        String employeeName = request.getEmployee() == null
                ? request.getUsername()
                : EmployeeMapper.fullName(request.getEmployee());
        return new CalendarEventResponse(
                "VACATION",
                "Vacaciones: " + employeeName,
                request.getStartDate(),
                request.getEndDate(),
                false,
                request.getUuid(),
                employeeName
        );
    }
}
