package com.mx.vacaciones.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.inssoft.people.calendar.service.CalendarService;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.vacation.service.VacationWorkflowService;
import com.mx.vacaciones.model.Role;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.model.VacationRequest;
import com.mx.vacaciones.repository.UserRepository;
import com.mx.vacaciones.repository.VacationRepository;
import com.mx.vacaciones.service.EmailService;
import com.mx.vacaciones.service.VacationCalculator;

import jakarta.transaction.Transactional;

/**
 * Controlador encargado del flujo de solicitudes de vacaciones.
 *
 * <p>
 * Soporta dos modos de trabajo:
 * </p>
 * <ul>
 *     <li><b>Colaborador:</b> solicita vacaciones para sí mismo</li>
 *     <li><b>Administrador / RRHH:</b> puede registrar vacaciones para otro usuario</li>
 * </ul>
 *
 * <p>
 * Además, este controlador ya acepta fechas en dos formatos:
 * </p>
 * <ul>
 *     <li><b>yyyy-MM-dd</b> (formato ISO)</li>
 *     <li><b>dd/MM/yyyy</b> (formato visual en español)</li>
 * </ul>
 */
@Controller
@RequestMapping("/vacations")
public class VacationController {

    /**
     * Formato ISO, por ejemplo: 2026-04-08
     */
    private static final DateTimeFormatter ISO_FORMAT =
            DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Formato visual en español, por ejemplo: 08/04/2026
     */
    private static final DateTimeFormatter ES_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("es", "MX"));

    private final VacationRepository vacationRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final EmailService emailService;
    private final VacationCalculator vacationCalculator;
    private final VacationWorkflowService vacationWorkflowService;
    private final CalendarService calendarService;

    public VacationController(
            VacationRepository vacationRepository,
            UserRepository userRepository,
            EmployeeRepository employeeRepository,
            EmailService emailService,
            VacationCalculator vacationCalculator,
            VacationWorkflowService vacationWorkflowService,
            CalendarService calendarService) {

        this.vacationRepository = vacationRepository;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.emailService = emailService;
        this.vacationCalculator = vacationCalculator;
        this.vacationWorkflowService = vacationWorkflowService;
        this.calendarService = calendarService;
    }

    /**
     * Muestra el formulario para registrar una solicitud de vacaciones.
     *
     * <p>
     * Si el usuario autenticado es ADMIN, además carga la lista de usuarios
     * para operar en modo RRHH.
     * </p>
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

        User loggedUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + username));

        boolean isAdmin = hasRole(authentication, "ROLE_ADMIN");

        int currentYear = LocalDate.now().getYear();

        Employee employee = employeeRepository
                .findByUser_UsernameAndDeletedAtIsNull(username)
                .orElse(null);

        List<LocalDate> holidays = employee == null
                ? vacationCalculator.getMexHolidaysBetweenYears(currentYear - 1, currentYear + 1)
                : calendarService.holidayDatesBetween(
                        employee.getCompany().getId(), currentYear - 1, currentYear + 1);

        List<String> holidayDates = holidays.stream()
                .map(LocalDate::toString)
                .collect(Collectors.toList());

        model.addAttribute("availableDays", loggedUser.getVacationDaysAvailable());
        model.addAttribute("vacationRequest", new VacationRequest());
        model.addAttribute("holidayDates", holidayDates);
        boolean isLeader = hasRole(authentication, "ROLE_LIDER");

        String rol = "ROLE_COLABORADOR";

        if (isAdmin) {
            rol = "ROLE_ADMIN";
        } else if (isLeader) {
            rol = "ROLE_LIDER";
        }

        model.addAttribute("rol", rol);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("loggedUserId", loggedUser.getId());
        
        
        List<User> leaders = userRepository.findAll()
                .stream()
                .filter(u -> u.getRole().name().equals("ROLE_LIDER"))
                .filter(u -> !u.getId().equals(loggedUser.getId()))
                .collect(Collectors.toList());

        model.addAttribute("leaders", leaders);
        /*
         * En modo RRHH cargamos todos los usuarios para el combo de selección.
         * Se ordenan por nombre para mejorar la experiencia visual.
         */
        if (isAdmin) {
            List<User> users = userRepository.findAll()
                    .stream()
                    .sorted(Comparator.comparing(
                            user -> user.getName() != null ? user.getName().toLowerCase() : ""
                    ))
                    .collect(Collectors.toList());

            model.addAttribute("users", users);
        }

        return "vacations/request";
    }

    /**
     * Registra una nueva solicitud de vacaciones.
     *
     * <p>
     * Comportamiento:
     * </p>
     * <ul>
     *     <li>Si es colaborador, la solicitud siempre se registra para sí mismo</li>
     *     <li>Si es admin y envía targetUserId, la solicitud se registra para ese usuario</li>
     * </ul>
     *
     * <p>
     * Este método acepta fechas en:
     * </p>
     * <ul>
     *     <li>yyyy-MM-dd</li>
     *     <li>dd/MM/yyyy</li>
     * </ul>
     *
     * @param startDateStr fecha de inicio en texto
     * @param endDateStr fecha de fin en texto
     * @param targetUserId id del usuario objetivo en modo RRHH (opcional)
     * @param authentication usuario autenticado
     * @param ra atributos flash para mensajes
     * @return redirección al formulario
     */
    @PostMapping("/request")
    @Transactional
    public String submitVacationRequest(
            @RequestParam("startDate") String startDateStr,
            @RequestParam("endDate") String endDateStr,
            @RequestParam(value = "targetUserId", required = false) Long targetUserId,
            @RequestParam(value = "leaderId", required = false) Long leaderId,
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
            /*
             * Se intenta parsear ambos formatos:
             * - yyyy-MM-dd
             * - dd/MM/yyyy
             */
            startDate = parseFlexibleDate(startDateStr);
            endDate = parseFlexibleDate(endDateStr);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", "Formato de fecha inválido.");
            return "redirect:/vacations/request";
        }

        if (endDate.isBefore(startDate)) {
            ra.addFlashAttribute("error", "La fecha fin no puede ser menor que la fecha inicio.");
            return "redirect:/vacations/request";
        }

        String loggedUsername = authentication.getName();

        User loggedUser = userRepository.findByUsername(loggedUsername).orElse(null);

        if (loggedUser == null) {
            ra.addFlashAttribute("error", "Usuario autenticado no encontrado.");
            return "redirect:/vacations/request";
        }

        boolean isAdmin = hasRole(authentication, "ROLE_ADMIN");

        /*
         * Usuario objetivo:
         * - colaborador => siempre él mismo
         * - admin => puede elegir otro usuario por id
         */
        User targetUser = loggedUser;

        if (isAdmin && targetUserId != null) {
            targetUser = userRepository.findById(targetUserId).orElse(null);

            if (targetUser == null) {
                ra.addFlashAttribute("error", "El usuario seleccionado no existe.");
                return "redirect:/vacations/request";
            }
        }

        boolean isSelfRequestByAdmin = isAdmin && targetUser.getId().equals(loggedUser.getId());

        Employee employee = employeeRepository.findByUser_IdAndDeletedAtIsNull(targetUser.getId())
                .orElse(null);
        if (employee == null) {
            ra.addFlashAttribute("error",
                    "El usuario no tiene un empleado vinculado. Contacta a Recursos Humanos.");
            return "redirect:/vacations/request";
        }

        User leader = null;

        if (!isSelfRequestByAdmin) {
            leader = leaderId == null ? null : userRepository.findById(leaderId).orElse(null);
            if (leader == null) {
                ra.addFlashAttribute("error", "Debes seleccionar un líder.");
                return "redirect:/vacations/request";
            }
            if (leader.getRole() != Role.ROLE_LIDER) {
                ra.addFlashAttribute("error", "El autorizador seleccionado no tiene rol de líder.");
                return "redirect:/vacations/request";
            }
            if (leader.getId().equals(targetUser.getId())) {
                ra.addFlashAttribute("error", "El líder no puede ser el mismo usuario que solicita.");
                return "redirect:/vacations/request";
            }
        }

        VacationRequest saved;
        try {
            saved = vacationWorkflowService.submit(
                    employee, startDate, endDate, leader, loggedUser, null, !isSelfRequestByAdmin);
        } catch (BusinessException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/vacations/request";
        }        /*
         * Envío de correos:
         * - Siempre se notifica a admins indicando quién capturó la solicitud.
         * - Si el usuario se la registró a sí mismo, se envía correo normal.
         * - Si un admin la registró para otro usuario, se envía correo especial RRHH.
         */
        try {
            emailService.notifyLeaderVacationRequest(saved, leader);
            if (leader != null) {
                emailService.notifyAdminVacationRequest(saved, leader);
            }

            boolean requestCreatedByAdminForAnotherUser =
                    isAdmin && !targetUser.getUsername().equals(loggedUser.getUsername());

            String targetEmail = targetUser.getEmail();

            if (targetEmail != null && !targetEmail.isBlank()) {
                if (requestCreatedByAdminForAnotherUser) {
                    emailService.sendVacationRequestByAdminEmail(
                            targetEmail,
                            safeName(targetUser),
                            targetUser.getUsername(),
                            startDate.format(ES_FORMAT),
                            endDate.format(ES_FORMAT),
                            saved.getDays(),
                            safeName(loggedUser),
                            loggedUser.getUsername()
                    );
                } else {
                    emailService.sendVacationRequestEmail(
                            targetEmail,
                            safeName(targetUser),
                            targetUser.getUsername(),
                            startDate.format(ES_FORMAT),
                            endDate.format(ES_FORMAT),
                            saved.getDays()
                    );
                }
            }

        } catch (Exception e) {
            System.err.println("⚠ Error enviando correo: " + e.getMessage());
            e.printStackTrace();
        }

        /*
         * Mensaje final para pantalla.
         */
        if (isAdmin && !targetUser.getUsername().equals(loggedUser.getUsername())) {
            ra.addFlashAttribute(
                    "success",
                    "Solicitud registrada para " + safeName(targetUser)
                            + ". Se reservaron " + saved.getDays() + " días."
            );
        } else {
            ra.addFlashAttribute(
                    "success",
                    "Solicitud enviada. Se reservaron " + saved.getDays() + " días."
            );
        }

        return "redirect:/vacations/request";
    }

    /**
     * Muestra el historial y estatus de solicitudes del usuario autenticado.
     *
     * <p>
     * Este método mantiene el comportamiento actual:
     * siempre muestra las solicitudes del usuario logueado.
     * </p>
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

    /**
     * Valida si el usuario autenticado tiene el rol indicado.
     *
     * @param authentication autenticación actual
     * @param role rol a validar, por ejemplo ROLE_ADMIN
     * @return true si tiene el rol
     */
    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities()
                .stream()
                .anyMatch(a -> role.equals(a.getAuthority()));
    }

    /**
     * Obtiene un nombre seguro para mensajes y correos.
     *
     * @param user usuario
     * @return nombre o username si no hay nombre
     */
    private String safeName(User user) {
        if (user == null) {
            return "";
        }

        if (user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }

        return user.getUsername();
    }

    /**
     * Intenta convertir una fecha recibida desde la vista.
     *
     * <p>
     * Formatos soportados:
     * </p>
     * <ul>
     *     <li>yyyy-MM-dd</li>
     *     <li>dd/MM/yyyy</li>
     * </ul>
     *
     * @param raw valor recibido desde el formulario
     * @return fecha convertida
     * @throws IllegalArgumentException si no coincide con ningún formato válido
     */
    private LocalDate parseFlexibleDate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Fecha vacía");
        }

        String value = raw.trim();

        try {
            return LocalDate.parse(value, ISO_FORMAT);
        } catch (DateTimeParseException ignored) {
            // Intento siguiente
        }

        try {
            return LocalDate.parse(value, ES_FORMAT);
        } catch (DateTimeParseException ignored) {
            // Intento siguiente
        }

        throw new IllegalArgumentException("Formato de fecha inválido: " + value);
    }
}