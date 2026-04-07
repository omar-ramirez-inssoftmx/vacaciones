package com.mx.vacaciones.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.mx.vacaciones.model.User;
import com.mx.vacaciones.model.VacationRequest;
import com.mx.vacaciones.repository.UserRepository;
import com.mx.vacaciones.repository.VacationRepository;
import com.mx.vacaciones.service.EmailService;
import com.mx.vacaciones.service.VacationCalculator;

import jakarta.transaction.Transactional;

@Controller
@RequestMapping("/vacations")
public class VacationController {

    private final VacationRepository vacationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final VacationCalculator vacationCalculator;

    public VacationController(
            VacationRepository vacationRepository,
            UserRepository userRepository,
            EmailService emailService,
            VacationCalculator vacationCalculator) {

        this.vacationRepository = vacationRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.vacationCalculator = vacationCalculator;
    }

    // ==========================
    // MOSTRAR FORMULARIO
    // ==========================
    @GetMapping("/request")
    public String showRequestForm(Model model, Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        String username = authentication.getName();

        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("Usuario no encontrado: " + username);
        }

        model.addAttribute("availableDays", user.getVacationDaysAvailable());
        model.addAttribute("vacationRequest", new VacationRequest());

        return "vacations/request";
    }

    // ==========================
    // ENVIAR SOLICITUD
    // ==========================
    @PostMapping("/request")
    @Transactional
    public String submitVacationRequest(
            @RequestParam("startDate") String startDateStr,
            @RequestParam("endDate") String endDateStr,
            Authentication authentication,
            RedirectAttributes ra) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        if (startDateStr == null || startDateStr.isBlank()
                || endDateStr == null || endDateStr.isBlank()) {

            ra.addFlashAttribute("error", "Debes seleccionar fecha inicio y fecha fin.");
            return "redirect:/vacations/request";
        }

        LocalDate startDate;
        LocalDate endDate;

        try {
            startDate = LocalDate.parse(startDateStr);
            endDate   = LocalDate.parse(endDateStr);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Formato de fecha inválido.");
            return "redirect:/vacations/request";
        }

        if (endDate.isBefore(startDate)) {
            ra.addFlashAttribute("error", "La fecha fin no puede ser menor que la fecha inicio.");
            return "redirect:/vacations/request";
        }

        String username = authentication.getName();
        User user = userRepository.findByUsername(username);

        if (user == null) {
            ra.addFlashAttribute("error", "Usuario no encontrado.");
            return "redirect:/vacations/request";
        }

        // Evitar traslapes
        List<String> blockingStatuses = List.of("PENDING", "APPROVED");
        boolean overlap = vacationRepository.existsOverlapping(
                user.getId(),
                startDate,
                endDate,
                blockingStatuses
        );

        if (overlap) {
            ra.addFlashAttribute(
                    "error",
                    "Ya tienes una solicitud que se traslapa con esas fechas."
            );
            return "redirect:/vacations/request";
        }

        // Calcular días hábiles
        int days = vacationCalculator.businessDaysInclusive(startDate, endDate);

        if (days <= 0) {
            ra.addFlashAttribute("error", "El rango no contiene días hábiles.");
            return "redirect:/vacations/request";
        }

        int available = user.getVacationDaysAvailable();
        if (available < days) {
            ra.addFlashAttribute(
                    "error",
                    "No tienes días suficientes. Disponibles: " + available
            );
            return "redirect:/vacations/request";
        }

        // Descontar saldo
        user.setVacationDaysAvailable(available - days);
        userRepository.save(user);

        // Guardar solicitud
        VacationRequest request = new VacationRequest();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setDays(days);
        request.setStatus("PENDING");
        request.setUser(user);
        request.setUsername(username);

        VacationRequest saved = vacationRepository.save(request);

        // Correos (si fallan NO rompen la app)
        try {
            emailService.notifyAdminsVacationRequest(saved);

            String to = user.getEmail();
            if (to != null && !to.isBlank()) {
                emailService.sendVacationRequestEmail(
                        to,
                        user.getUsername(),
                        startDate.toString(),
                        endDate.toString(),
                        days
                );
            }
        } catch (Exception e) {
            System.err.println("⚠ Error enviando correo: " + e.getMessage());
        }

        ra.addFlashAttribute(
                "success",
                "Solicitud enviada. Se descontaron " + days + " días."
        );

        return "redirect:/vacations/request";
    }

    // ==========================
    // ESTATUS DE SOLICITUDES
    // ==========================
    @GetMapping("/status")
    public String vacationStatus(Authentication authentication, Model model) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        String username = authentication.getName();
        List<VacationRequest> requests =
                vacationRepository.findByUserUsernameOrderByIdDesc(username);

        model.addAttribute("requests", requests);
        model.addAttribute("username", username);

        return "vacations/status";
    }

}