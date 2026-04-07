package com.mx.vacaciones.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Manejador personalizado para login exitoso.
 *
 * <p>
 * Después de autenticar correctamente al usuario, valida si su contraseña
 * está marcada como temporal.
 * </p>
 *
 * <ul>
 *     <li>Si la contraseña es temporal, redirige a /change-password</li>
 *     <li>Si no es temporal, continúa al home del sistema</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    /**
     * Repositorio para consultar al usuario autenticado.
     */
    private final UserRepository userRepository;

    /**
     * Se ejecuta cuando el login fue exitoso.
     *
     * @param request request HTTP
     * @param response response HTTP
     * @param authentication autenticación actual
     * @throws IOException en caso de error de redirección
     * @throws ServletException en caso de error del servlet
     */
    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication)
            throws IOException, ServletException {

        String username = authentication.getName();

        User user = userRepository.findByUsername(username).orElse(null);

        if (user != null && Boolean.TRUE.equals(user.getTemporaryPassword())) {
            response.sendRedirect(request.getContextPath() + "/change-password");
            return;
        }

        response.sendRedirect(request.getContextPath() + "/home");
    }
}