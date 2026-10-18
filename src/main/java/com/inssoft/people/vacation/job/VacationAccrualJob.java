package com.inssoft.people.vacation.job;

import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.vacation.service.VacationBalanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Acumulación idempotente por aniversario.
 *
 * <p>Para cada empleado activo garantiza que exista el periodo vigente. Si el
 * periodo no existe, se crea con los días que corresponden a la política y se
 * registra el movimiento de acumulación en el ledger. El job puede ejecutarse
 * diariamente sin duplicar movimientos.</p>
 */
@Component
public class VacationAccrualJob {

    private static final Logger log = LoggerFactory.getLogger(VacationAccrualJob.class);

    private final EmployeeRepository employeeRepository;
    private final VacationBalanceService balanceService;

    public VacationAccrualJob(
            EmployeeRepository employeeRepository,
            VacationBalanceService balanceService) {
        this.employeeRepository = employeeRepository;
        this.balanceService = balanceService;
    }

    @Scheduled(cron = "0 20 2 * * *", zone = "America/Mexico_City")
    public void accrueCurrentPeriods() {
        LocalDate today = LocalDate.now();
        List<Employee> employees = employeeRepository.findByDeletedAtIsNullAndEnabledTrue();
        int processed = 0;
        int failed = 0;

        for (Employee employee : employees) {
            if (employee.getHireDate() == null || employee.getHireDate().isAfter(today)) {
                continue;
            }
            try {
                balanceService.ensureCurrentPeriodById(employee.getId());
                processed++;
            } catch (Exception ex) {
                failed++;
                log.warn("No se pudo acumular vacaciones para empleado {}: {}",
                        employee.getUuid(), ex.getMessage());
            }
        }

        log.info("Job de acumulación de vacaciones: {} procesados, {} fallidos", processed, failed);
    }
}
