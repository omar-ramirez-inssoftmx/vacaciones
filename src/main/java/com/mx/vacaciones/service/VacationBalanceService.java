package com.mx.vacaciones.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;

/**
 * Libera días de vacaciones cuando una solicitud es rechazada.
 *
 * <p>Mientras el ledger de vacaciones (Fase 5) no exista, esta es la forma
 * segura de revertir el descuento que el sistema legado hace al crear la
 * solicitud. Usa bloqueo pesimista para evitar condiciones de carrera.</p>
 */
@Service
public class VacationBalanceService {

    private final UserRepository userRepository;

    public VacationBalanceService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void release(Long userId, int days) {
        if (userId == null || days <= 0) {
            return;
        }

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("Usuario no encontrado: " + userId));

        user.setVacationDaysAvailable(user.getVacationDaysAvailable() + days);
    }
}
