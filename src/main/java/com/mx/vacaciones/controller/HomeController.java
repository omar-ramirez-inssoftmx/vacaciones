package com.mx.vacaciones.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inssoft.people.onboarding.dto.TourStep;
import com.inssoft.people.onboarding.service.OnboardingService;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import com.mx.vacaciones.repository.VacationRepository;

/**
 * Controlador principal del home.
 *
 * <p>Además del resumen del usuario, entrega al cliente los pasos del
 * recorrido inicial (onboarding) según su rol y si ya lo completó.</p>
 */
@Controller
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    private final VacationRepository vacationRepository;
    private final UserRepository userRepository;
    private final OnboardingService onboardingService;
    private final ObjectMapper objectMapper;

    public HomeController(
            VacationRepository vacationRepository,
            UserRepository userRepository,
            OnboardingService onboardingService,
            ObjectMapper objectMapper) {
        this.vacationRepository = vacationRepository;
        this.userRepository = userRepository;
        this.onboardingService = onboardingService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/home")
    public String home(Authentication authentication, Model model) {

        String username = authentication.getName();

        String rol = authentication.getAuthorities()
                .stream()
                .findFirst()
                .map(authority -> authority.getAuthority())
                .orElse("ROLE_COLABORADOR");

        long pendingRequestsCount = 0L;

        if ("ROLE_ADMIN".equals(rol) || "ROLE_LIDER".equals(rol)) {
            pendingRequestsCount = vacationRepository.countByStatusIn(
                    List.of("PENDING_LEADER", "PENDING_ADMIN"));
        }

        User user = userRepository.findByUsername(username).orElse(null);
        boolean onboardingCompleted = user != null
                && Boolean.TRUE.equals(user.getOnboardingCompleted());

        List<TourStep> tourSteps = user == null
                ? List.of()
                : onboardingService.stepsFor(user);

        String tourStepsJson = "[]";
        try {
            tourStepsJson = objectMapper.writeValueAsString(tourSteps);
        } catch (Exception ex) {
            log.warn("No se pudo serializar el recorrido inicial para {}", username, ex);
        }

        model.addAttribute("usuario", username);
        model.addAttribute("rol", rol);
        model.addAttribute("pendingRequestsCount", pendingRequestsCount);
        model.addAttribute("onboardingCompleted", onboardingCompleted);
        model.addAttribute("tourStepsJson", tourStepsJson);

        return "home";
    }
}
