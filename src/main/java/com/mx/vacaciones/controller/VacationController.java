package com.mx.vacaciones.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

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

/**
 * Controlador encargado del flujo de solicitudes de vacaciones del colaborador.
 *
 * <p>
 * Permite:
 * </p>
 * <ul>
 *     <li>Mostrar formulario de solicitud</li>
 *     <li>Registrar una nueva solicitud</li>
 *     <li>Consultar el estatus de solicitudes realizadas</li>
 * </ul>
 */
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

    /**
     * Muestra el formulario para registrar una solicitud de vacaciones.
     *
     * @param model modelo para la vista
     * @param authentication usuario autenticado
     * @return vista del formulario o redirección a login
     */
    @GetMapping("/request")
    public String showRequestForm(Model model, Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        String username = authentication.getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + username));

        int currentYear = LocalDate.now().getYear();

        List<String> holidayDates = vacationCalculator
                .getMexHolidaysBetweenYears(currentYear - 1, currentYear + 1)
                .stream()
                .map(LocalDate::toString)
                .collect(Collectors.toList());

        model.addAttribute("availableDays", user.getVacationDaysAvailable());
        model.addAttribute("vacationRequest", new VacationRequest());
        model.addAttribute("holidayDates", holidayDates);

        return "vacations/request";
    }

    /**
     * Registra una nueva solicitud de vacaciones.
     *
     * <p>
     * Valida:
     * </p>
     * <ul>
     *     <li>Autenticación</li>
     *     <li>Formato de fechas</li>
     *     <li>Rango válido</li>
     *     <li>Fechas no bloqueadas (fin de semana o festivo)</li>
     *     <li>Traslapes con otras solicitudes</li>
     *     <li>Saldo suficiente de días</li>
     * </ul>
     *
     * @param startDateStr fecha de inicio en texto
     * @param endDateStr fecha de fin en texto
     * @param authentication usuario autenticado
     * @param ra atributos flash para mensajes
     * @return redirección al formulario
     */
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
            endDate = LocalDate.parse(endDateStr);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Formato de fecha inválido.");
            return "redirect:/vacations/request";
        }

        if (endDate.isBefore(startDate)) {
            ra.addFlashAttribute("error", "La fecha fin no puede ser menor que la fecha inicio.");
            return "redirect:/vacations/request";
        }

        if (vacationCalculator.isBlockedDate(startDate)) {
            ra.addFlashAttribute(
                    "error",
                    "La fecha de inicio no puede ser sábado, domingo o día festivo."
            );
            return "redirect:/vacations/request";
        }

        if (vacationCalculator.isBlockedDate(endDate)) {
            ra.addFlashAttribute(
                    "error",
                    "La fecha de fin no puede ser sábado, domingo o día festivo."
            );
            return "redirect:/vacations/request";
        }

        String username = authentication.getName();

        User user = userRepository.findByUsername(username).orElse(null);

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
                        user.getName(),
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

    /**
     * Muestra el historial y estatus de solicitudes del usuario autenticado.
     *
     * @param authentication usuario autenticado
     * @param model modelo para la vista
     * @return vista de estatus o redirección a login
     */
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