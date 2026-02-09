package com.mx.vacaciones.service;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.mx.vacaciones.model.User;
import com.mx.vacaciones.model.VacationRequest;
import com.mx.vacaciones.repository.UserRepository;
import com.mx.vacaciones.repository.VacationRepository;

import jakarta.transaction.Transactional;

@Service
public class VacationService {

    @Autowired
    private final VacationRepository vacationRepository;
    private final UserRepository userRepo;
    private final VacationCalculator calc;


    public VacationService(VacationRepository repository, UserRepository userRepo, VacationCalculator calc) {
        this.vacationRepository = repository;
        this.userRepo = userRepo;
        this.calc = calc;

    }

    public void save(VacationRequest request) {
    	vacationRepository.save(request);
    }
    
    @Transactional
    public VacationRequest createRequest(Long userId, LocalDate start, LocalDate end) {

      if (end.isBefore(start)) throw new IllegalArgumentException("Rango inválido");

      User user = userRepo.findByIdForUpdate(userId)
          .orElseThrow(() -> new IllegalArgumentException("Usuario no existe"));

      int days = calc.businessDaysInclusive(start, end);
      if (days <= 0) throw new IllegalArgumentException("No hay días hábiles en el rango");

      // Descontar saldo
      user.deductVacationDays(days);

      VacationRequest req = new VacationRequest();
      req.setUser(user);
      req.setStartDate(start);
      req.setEndDate(end);
      req.setDays(days);
      req.setStatus("PENDING");

      // guarda ambos (user se guarda por dirty checking)
      return vacationRepository.save(req);
    }
  
}
