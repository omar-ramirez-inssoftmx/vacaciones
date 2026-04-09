package com.mx.vacaciones.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.mx.vacaciones.repository.VacationRepository;

/**
 * Controlador principal del home.
 *
 * <p>
 * Muestra:
 * </p>
 * <ul>
 *     <li>Nombre del usuario autenticado</li>
 *     <li>Rol del usuario</li>
 *     <li>Cantidad de solicitudes pendientes por revisar para admin</li>
 * </ul>
 *
 * <p>
 * Si el usuario es administrador, se calcula el número de solicitudes
 * con estatus PENDING para mostrarlo como badge en el menú principal.
 * </p>
 */
@Controller
public class HomeController {

    private final VacationRepository vacationRepository;

    /**
     * Inyección del repositorio de vacaciones.
     *
     * @param vacationRepository repositorio de solicitudes de vacaciones
     */
    public HomeController(VacationRepository vacationRepository) {
        this.vacationRepository = vacationRepository;
    }

    /**
     * Pantalla principal del sistema.
     *
     * @param authentication usuario autenticado
     * @param model modelo para la vista
     * @return vista home
     */
    @GetMapping("/home")
    public String home(Authentication authentication, Model model) {

        String username = authentication.getName();

        String rol = authentication.getAuthorities()
                .stream()
                .findFirst()
                .get()
                .getAuthority();

        /*
         * Por defecto el contador va en 0.
         * Solo se consulta si el usuario es admin.
         */
        long pendingRequestsCount = 0L;

        if ("ROLE_ADMIN".equals(rol)) {
            pendingRequestsCount = vacationRepository.countByStatus("PENDING");
        }

        model.addAttribute("usuario", username);
        model.addAttribute("rol", rol);
        model.addAttribute("pendingRequestsCount", pendingRequestsCount);

        return "home";
    }
}