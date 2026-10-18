package com.mx.vacaciones.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.inssoft.people.common.api.ApiResponse;
import com.inssoft.people.common.api.ErrorCode;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.vacation.service.VacationWorkflowService;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.model.VacationRequest;
import com.mx.vacaciones.repository.UserRepository;
import com.mx.vacaciones.repository.VacationRepository;
import com.mx.vacaciones.service.EmailService;

/**
 * Bandeja del líder. La lógica de estado, ownership y ledger vive en
 * {@link VacationWorkflowService}; este controlador solo adapta la UI legada.
 */
@Controller
@RequestMapping("/leader")
public class LeaderController {

    private static final Logger log = LoggerFactory.getLogger(LeaderController.class);

    private final VacationRepository vacationRepository;
    private final UserRepository userRepository;
    private final VacationWorkflowService workflowService;
    private final EmailService emailService;

    public LeaderController(
            VacationRepository vacationRepository,
            UserRepository userRepository,
            VacationWorkflowService workflowService,
            EmailService emailService) {
        this.vacationRepository = vacationRepository;
        this.userRepository = userRepository;
        this.workflowService = workflowService;
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
    public ResponseEntity<ApiResponse<Void>> approveRequest(
            @PathVariable Long id,
            Authentication authentication) {

        User leader = currentLeaderOrNull(authentication);
        if (leader == null) {
            return unauthorized();
        }

        try {
            workflowService.approveByLeader(id, leader);
            return ResponseEntity.ok(ApiResponse.ok("Solicitud aprobada por el líder"));
        } catch (BusinessException ex) {
            return error(ex);
        }
    }

    @PostMapping("/reject/{id}")
    @ResponseBody
    public ResponseEntity<ApiResponse<Void>> rejectRequest(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body,
            Authentication authentication) {

        User leader = currentLeaderOrNull(authentication);
        if (leader == null) {
            return unauthorized();
        }

        String motivo = body == null ? null : body.get("motivo");
        if (motivo == null || motivo.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Debes indicar el motivo del rechazo"));
        }

        try {
            VacationRequest saved = workflowService.rejectByLeader(id, leader, motivo);
            try {
                emailService.notifyUserDecision(saved);
            } catch (Exception ex) {
                log.warn("Solicitud {} rechazada, pero no se pudo enviar correo: {}", id, ex.getMessage());
            }
            return ResponseEntity.ok(ApiResponse.ok("Solicitud rechazada por el líder"));
        } catch (BusinessException ex) {
            return error(ex);
        }
    }

    private User currentLeaderOrNull(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName()).orElse(null);
    }

    private ResponseEntity<ApiResponse<Void>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Autenticación requerida"));
    }

    private ResponseEntity<ApiResponse<Void>> error(BusinessException ex) {
        HttpStatus status = ex.getErrorCode() == ErrorCode.FORBIDDEN
                ? HttpStatus.FORBIDDEN
                : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessage()));
    }
}
