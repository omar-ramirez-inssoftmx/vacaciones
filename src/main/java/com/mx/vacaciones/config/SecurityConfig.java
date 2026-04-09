package com.mx.vacaciones.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.mx.vacaciones.security.CustomAuthenticationSuccessHandler;

import lombok.RequiredArgsConstructor;

/**
 * Configuración de seguridad de la aplicación.
 *
 * <p>
 * Define:
 * </p>
 * <ul>
 *     <li>Rutas públicas</li>
 *     <li>Rutas protegidas por rol</li>
 *     <li>Login personalizado</li>
 *     <li>Logout</li>
 *     <li>Codificador de contraseñas</li>
 * </ul>
 *
 * <p>
 * Ajuste realizado:
 * </p>
 * <ul>
 *     <li>Se permite que tanto COLABORADOR como ADMIN accedan a /vacations/**</li>
 *     <li>Se mantiene /admin/** exclusivo para ADMIN</li>
 * </ul>
 *
 * <p>
 * Se agregó un {@link CustomAuthenticationSuccessHandler} para redirigir
 * al usuario a la pantalla de cambio de contraseña cuando su contraseña
 * esté marcada como temporal.
 * </p>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * Manejador personalizado de login exitoso.
     *
     * <p>
     * Valida si el usuario tiene contraseña temporal y, en ese caso,
     * lo redirige a la pantalla de cambio de contraseña.
     * </p>
     */
    private final CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;

    /**
     * Configura la cadena de filtros de seguridad.
     *
     * @param http objeto de configuración de seguridad
     * @return cadena de filtros configurada
     * @throws Exception en caso de error de configuración
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            // CSRF deshabilitado según tu implementación actual.
            // Si después activas CSRF, habría que ajustar formularios y endpoints.
            .csrf(csrf -> csrf.disable())

            .authorizeHttpRequests(auth -> auth
                // =========================
                // RUTAS PÚBLICAS
                // =========================
                .requestMatchers(
                    "/login",
                    "/change-password",
                    "/css/**",
                    "/js/**",
                    "/images/**"
                ).permitAll()

                // =========================
                // RUTAS DE VACACIONES
                // Ahora pueden entrar:
                // - ROLE_COLABORADOR
                // - ROLE_ADMIN
                // =========================
                .requestMatchers("/vacations/**")
                .hasAnyAuthority("ROLE_COLABORADOR", "ROLE_ADMIN")

                // =========================
                // RUTAS DE ADMINISTRACIÓN
                // Exclusivas para admin
                // =========================
                .requestMatchers("/admin/**")
                .hasAuthority("ROLE_ADMIN")

                // Cualquier otra ruta requiere autenticación
                .anyRequest().authenticated()
            )

            .formLogin(form -> form
                .loginPage("/login")
                .failureHandler((request, response, exception) -> {
                    if (exception instanceof org.springframework.security.authentication.DisabledException) {
                        response.sendRedirect("/login?err=INACTIVO");
                    } else {
                        response.sendRedirect("/login?err=LOGIN");
                    }
                })
                .successHandler(customAuthenticationSuccessHandler)
                .permitAll()
            )

            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            );

        return http.build();
    }

    /**
     * Bean para encriptar contraseñas usando BCrypt.
     *
     * @return codificador de contraseñas
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}