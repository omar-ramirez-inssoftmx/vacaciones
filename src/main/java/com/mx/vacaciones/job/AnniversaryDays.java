package com.mx.vacaciones.job;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import com.mx.vacaciones.service.VacationPolicyService;

/**
 * Job legado de acumulación por aniversario.
 *
 * <p>Corrección aplicada: se guarda el entitlement anterior antes de
 * actualizarlo; el cálculo anterior siempre daba delta 0. El motor formal de
 * periodos y ledger se implementa en la Fase 5 (módulo vacation).</p>
 */
@Component
public class AnniversaryDays {

    private static final Logger log = LoggerFactory.getLogger(AnniversaryDays.class);

    private final UserRepository userRepository;
    private final VacationPolicyService policy;

    public AnniversaryDays(UserRepository userRepository, VacationPolicyService policy) {
        this.userRepository = userRepository;
        this.policy = policy;
    }

    @Scheduled(cron = "0 10 2 * * *", zone = "America/Mexico_City")
    @Transactional
    public void grantAnniversaryDays() {
        LocalDate today = LocalDate.now();
        List<User> users = userRepository.findAll();
        int updated = 0;

        for (User user : users) {
            if (user.getHireDate() == null) {
                continue;
            }

            int years = policy.yearsOfService(user.getHireDate(), today);
            if (years < 1) {
                // La política formal para empleados con menos de un año se define en Fase 5.
                continue;
            }

            int entitled = policy.entitledDaysByYears(years);
            int previousEntitled = user.getVacationDaysEntitled();

            if (Objects.equals(previousEntitled, entitled)) {
                continue;
            }

            user.setVacationDaysEntitled(entitled);
            int newAvailable = Math.max(0,
                    user.getVacationDaysAvailable() + (entitled - previousEntitled));
            user.setVacationDaysAvailable(newAvailable);
            userRepository.save(user);
            updated++;

            log.info("Aniversario: usuario={} años={} entitled {} -> {} disponible={}",
                    user.getUsername(), years, previousEntitled, entitled, newAvailable);
        }

        log.info("Job de aniversario finalizado. Usuarios actualizados: {}", updated);
    }
}
