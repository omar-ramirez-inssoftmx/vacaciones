package com.mx.vacaciones.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * Servicio encargado de calcular días hábiles para vacaciones.
 *
 * <p>
 * Considera como días no laborables:
 * </p>
 * <ul>
 *   <li>Sábados</li>
 *   <li>Domingos</li>
 *   <li>Días festivos oficiales en México</li>
 * </ul>
 */
@Service
public class VacationCalculator {

    /**
     * Calcula los días hábiles entre dos fechas, incluyendo ambas.
     *
     * @param start fecha inicial
     * @param end fecha final
     * @return número de días hábiles
     */
    public int businessDaysInclusive(LocalDate start, LocalDate end) {
        int count = 0;

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            boolean weekend = isWeekend(d);
            boolean holiday = isMexHoliday(d);

            if (!weekend && !holiday) {
                count++;
            }
        }

        return count;
    }

    /**
     * Indica si una fecha está bloqueada para selección
     * por ser fin de semana o día festivo.
     *
     * @param date fecha a validar
     * @return true si la fecha no debe permitirse
     */
    public boolean isBlockedDate(LocalDate date) {
        return isWeekend(date) || isMexHoliday(date);
    }

    /**
     * Indica si una fecha corresponde a sábado o domingo.
     *
     * @param date fecha a validar
     * @return true si es fin de semana
     */
    public boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY
                || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    /**
     * Obtiene los días festivos oficiales de México para un año.
     *
     * @param year año a consultar
     * @return lista de días festivos
     */
    public List<LocalDate> getMexHolidays(int year) {
        List<LocalDate> holidays = new ArrayList<LocalDate>();

        // Fijos
        holidays.add(LocalDate.of(year, 1, 1));
        holidays.add(LocalDate.of(year, 5, 1));
        holidays.add(LocalDate.of(year, 9, 16));
        holidays.add(LocalDate.of(year, 12, 25));

        // Lunes oficiales
        holidays.add(nthMonday(year, 2, 1));   // 1er lunes de febrero
        holidays.add(nthMonday(year, 3, 3));   // 3er lunes de marzo
        holidays.add(nthMonday(year, 11, 3));  // 3er lunes de noviembre

        return holidays;
    }

    /**
     * Obtiene los días festivos oficiales entre un rango de años.
     *
     * @param startYear año inicial
     * @param endYear año final
     * @return lista consolidada de días festivos
     */
    public List<LocalDate> getMexHolidaysBetweenYears(int startYear, int endYear) {
        List<LocalDate> holidays = new ArrayList<LocalDate>();

        for (int year = startYear; year <= endYear; year++) {
            holidays.addAll(getMexHolidays(year));
        }

        return holidays;
    }

    /**
     * Indica si una fecha corresponde a un día festivo oficial en México.
     *
     * @param d fecha a validar
     * @return true si es festivo
     */
    public boolean isMexHoliday(LocalDate d) {
        int y = d.getYear();

        // Fijos
        if (d.equals(LocalDate.of(y, 1, 1))) return true;
        if (d.equals(LocalDate.of(y, 5, 1))) return true;
        if (d.equals(LocalDate.of(y, 9, 16))) return true;
        if (d.equals(LocalDate.of(y, 12, 25))) return true;

        // Lunes oficiales
        if (d.equals(nthMonday(y, 2, 1))) return true;   // Constitución
        if (d.equals(nthMonday(y, 3, 3))) return true;   // Benito Juárez
        if (d.equals(nthMonday(y, 11, 3))) return true;  // Revolución

        return false;
    }

    /**
     * Calcula el enésimo lunes de un mes.
     *
     * @param year año
     * @param month mes
     * @param nth número ordinal del lunes en el mes
     * @return fecha calculada
     */
    private LocalDate nthMonday(int year, int month, int nth) {
        LocalDate first = LocalDate.of(year, month, 1);
        int shift = (DayOfWeek.MONDAY.getValue() - first.getDayOfWeek().getValue() + 7) % 7;
        LocalDate firstMonday = first.plusDays(shift);
        return firstMonday.plusWeeks(nth - 1);
    }
}