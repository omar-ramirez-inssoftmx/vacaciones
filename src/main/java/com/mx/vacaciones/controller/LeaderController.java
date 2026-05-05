package com.mx.vacaciones.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.mx.vacaciones.model.User;
import com.mx.vacaciones.model.VacationRequest;
import com.mx.vacaciones.repository.UserRepository;
import com.mx.vacaciones.repository.VacationRepository;

@Controller
@RequestMapping("/leader")
public class LeaderController {

    private final VacationRepository vacationRepository;
    private final UserRepository userRepository;

    public LeaderController(
            VacationRepository vacationRepository,
            UserRepository userRepository) {
        this.vacationRepository = vacationRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/requests")
    public String viewRequests(Authentication authentication, Model model) {

        String username = authentication.getName();

        User leader = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Líder no encontrado"));

        List<VacationRequest> requests =
                vacationRepository.findByLeaderAndStatus(
                        leader,
                        "PENDING_LEADER"
                );

        model.addAttribute("requests", requests);

        return "leader/requests";
    }

    @PostMapping("/approve/{id}")
    @ResponseBody
    public void approveRequest(@PathVariable Long id) {

        VacationRequest request = vacationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

        request.setStatus("PENDING_ADMIN");
        request.setLeaderDecision("APPROVED");
        request.setLeaderDecidedAt(LocalDateTime.now());

        vacationRepository.save(request);
    }
    
    @PostMapping("/reject/{id}")
    @ResponseBody
    public String rejectRequest(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body) {

        VacationRequest request = vacationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

        String motivo = body.get("motivo");

        request.setStatus("PENDING_ADMIN");
        request.setLeaderDecision("REJECTED");
        request.setLeaderComment(motivo);
        request.setLeaderDecidedAt(LocalDateTime.now());

        vacationRepository.save(request);

        return "ok";
    }
}