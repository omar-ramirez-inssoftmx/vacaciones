package com.inssoft.people.calendar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CalendarConfigRequest(
        @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
        String name,

        @Size(max = 64, message = "La zona horaria no puede exceder 64 caracteres")
        String timezone,

        @NotBlank(message = "La semana laboral es obligatoria")
        @Pattern(
                regexp = "(?i)^(MON|TUE|WED|THU|FRI|SAT|SUN)(,(MON|TUE|WED|THU|FRI|SAT|SUN))*$",
                message = "La semana laboral debe usar MON,TUE,WED,THU,FRI,SAT,SUN separados por coma")
        @Size(max = 50, message = "La semana laboral no puede exceder 50 caracteres")
        String workweek
) {
}
