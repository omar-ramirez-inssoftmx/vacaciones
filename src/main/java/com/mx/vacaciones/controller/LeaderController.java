package com.mx.vacaciones.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.inssoft.people.common.api.ApiResponse;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.model.VacationRequest;
import com.mx.vacaciones.repository.UserRepository;
import com.mx.vacaciones.repository.VacationRepository;
import com.mx.vacaciones.service.EmailService;
import com.mx.vacaciones.service.VacationBalanceService;

/**
 * Bandeja del líder.
 *
 * <p>Corrección de seguridad: el líder solo puede decidir solicitudes donde
 * él es el líder asignado y que estén en estado {@code PENDING_LEADER}.
 * El rechazo deja la solicitud en {@code REJECTED} y libera el saldo.</p>
 */
@Controller
@RequestMapping("/leader")
public class LeaderController {

    private static final Logger log = LoggerFactory.getLogger(LeaderController.class);

    private final VacationRepository vacationRepository;
    private final UserRepository userRepository;
    private final VacationBalanceService vacationBalanceService;
    private final EmailService emailService;

    public LeaderController(
            VacationRepository vacationRepository,
            UserRepository userRepository,
            VacationBalanceService vacationBalanceService,
            EmailService emailService) {
        this.vacationRepository = vacationRepository;
        this.userRepository = userRepository;
        this.vacationBalanceService = vacationBalanceService;
        this.emailService = emailService;
    }

    @GetMapping("/requests")
    public String viewRequests(Authentication authentication, Model model) {

        User leader = currentLeaderOrNull(authentication);
        if (leader == null) {
            return "redirect:/login";
        }

        List<VacationRequest> requests =
                vacationRepository.findByLeaderAndStatus(leader, "PENDING_LEADER");

        model.addAttribute("requests", requests);
        return "leader/requests";
    }

    @PostMapping("/approve/{id}")
    @ResponseBody
    @Transactional
    public ResponseEntity<ApiResponse<Void>> approveRequest(
            @PathVariable Long id,
            Authentication authentication) {

        User leader = currentLeaderOrNull(authentication);
        if (leader == null) {
            return unauthorized();
        }

        VacationRequest request = vacationRepository.findById(id).orElse(null);
        if (request == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Solicitud no encontrada"));
        }

        ResponseEntity<ApiResponse<Void>> ownershipError = validateOwnership(request, leader);
        if (ownershipError != null) {
            return ownershipError;
        }

        if (!"PENDING_LEADER".equals(request.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("La solicitud ya no está pendiente del líder"));
        }

        request.setStatus("PENDING_ADMIN");
        request.setLeaderDecision("APPROVED");
        request.setLeaderDecidedAt(LocalDateTime.now());
        vacationRepository.save(request);

        return ResponseEntity.ok(ApiResponse.ok("Solicitud aprobada por el líder"));
    }

    @PostMapping("/reject/{id}")
    @ResponseBody
    @Transactional
    public ResponseEntity<ApiResponse<Void>> rejectRequest(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body,
            Authentication authentication) {

        User leader = currentLeaderOrNull(authentication);
        if (leader == null) {
            return unauthorized();
        }

        VacationRequest request = vacationRepository.findById(id).orElse(null);
        if (request == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Solicitud no encontrada"));
        }

        ResponseEntity<ApiResponse<Void>> ownershipError = validateOwnership(request, leader);
        if (ownershipError != null) {
            return ownershipError;
        }

        if (!"PENDING_LEADER".equals(request.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("La solicitud ya no está pendiente del líder"));
        }

        String motivo = body == null ? null : body.get("motivo");
        if (motivo == null || motivo.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Debes indicar el motivo del rechazo"));
        }

        request.setStatus("REJECTED");
        request.setLeaderDecision("REJECTED");
        request.setLeaderComment(motivo.trim());
        request.setLeaderDecidedAt(LocalDateTime.now());
        VacationRequest saved = vacationRepository.save(request);

        vacationBalanceService.release(saved.getUser().getId(), saved.getDays());

        try {
            emailService.notifyUserDecision(saved);
        } catch (Exception ex) {
            log.warn("Solicitud {} rechazada, pero no se pudo enviar correo: {}", id, ex.getMessage());
        }

        return ResponseEntity.ok(ApiResponse.ok("Solicitud rechazada por el líder"));
    }

    private User currentLeaderOrNull(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName()).orElse(null);
    }

    private ResponseEntity<ApiResponse<Void>> validateOwnership(VacationRequest request, User leader) {
        if (request.getLeader() == null
                || !request.getLeader().getId().equals(leader.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("No puedes decidir una solicitud que no te fue asignada"));
        }
        return null;
    }

    private ResponseEntity<ApiResponse<Void>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Autenticación requerida"));
    }
}
