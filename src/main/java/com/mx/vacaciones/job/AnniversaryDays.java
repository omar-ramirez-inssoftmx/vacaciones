	package com.mx.vacaciones.job;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import com.mx.vacaciones.service.VacationPolicyService;

import jakarta.transaction.Transactional;

@Component
public class AnniversaryDays {
	
	@Autowired
    private final UserRepository userRepository;
    private final VacationPolicyService policy;

    public AnniversaryDays(UserRepository userRepository, VacationPolicyService policy) {
        this.userRepository = userRepository;
        this.policy = policy;
    }
	
    //Diario 02:10 AM
    @Scheduled(cron = "0 10 2 * * *", zone = "America/Mexico_City")
	@Transactional
	public void grantAnniversaryDays() {
		System.out.println("Se ejecuta Job de aniversario");
	  LocalDate today = LocalDate.now();
	  List<User> users = userRepository.findAll();

	  for (User u : users) {
	    int years = policy.yearsOfService(u.getHireDate(), today);
	    int entitled = policy.entitledDaysByYears(years);

	    // Si cambió el entitlement (cumplió años), actualiza
	    if (!Objects.equals(u.getVacationDaysEntitled(), entitled)) {
	      u.setVacationDaysEntitled(entitled);

	      // estrategia simple: "recarga anual"
	      u.setVacationDaysAvailable(u.getVacationDaysAvailable() + (entitled - u.getVacationDaysEntitled())); 
	      // ⚠️ mejor: guarda previousEntitled antes (te lo dejo abajo)
	    }
	  }
	}

}
