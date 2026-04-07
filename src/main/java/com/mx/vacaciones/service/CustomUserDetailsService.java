package com.mx.vacaciones.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;

/**
 * Servicio personalizado de Spring Security para cargar usuarios
 * desde la base de datos durante el proceso de autenticación.
 *
 * <p>
 * Busca al usuario por su nombre de usuario y construye un objeto
 * {@link UserDetails} con su información de acceso, rol y estatus.
 * </p>
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    /**
     * Repositorio de usuarios.
     */
    @Autowired
    private UserRepository userRepo;

    /**
     * Carga un usuario por su username para el proceso de login.
     *
     * @param username nombre de usuario
     * @return detalles del usuario para Spring Security
     * @throws UsernameNotFoundException si el usuario no existe
     */
    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(List.of(new SimpleGrantedAuthority(user.getRole().name())))
                .disabled(!user.getEnabled())
                .build();
    }
}