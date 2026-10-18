package com.mx.vacaciones.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.inssoft.people.auth.service.UserPermissionService;
import com.mx.vacaciones.model.Role;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;

/**
 * Carga usuarios desde la base de datos para el login web y para la
 * autenticación usada al emitir el JWT de la API.
 *
 * <p>Incluye el rol legado ({@code ROLE_*}) y los permisos efectivos del
 * RBAC ({@code PERM_*}) para que {@code AccessGuard} use la base de datos
 * como fuente autoritativa.</p>
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepo;
    private final UserPermissionService userPermissionService;

    public CustomUserDetailsService(
            UserRepository userRepo,
            UserPermissionService userPermissionService) {
        this.userRepo = userRepo;
        this.userPermissionService = userPermissionService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        Role role = user.getRole() != null ? user.getRole() : Role.ROLE_COLABORADOR;

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(role.name()));

        Set<String> permissions = userPermissionService.permissionsFor(username);
        permissions.stream()
                .map(permission -> new SimpleGrantedAuthority("PERM_" + permission))
                .forEach(authorities::add);

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(authorities)
                .disabled(!Boolean.TRUE.equals(user.getEnabled()))
                .build();
    }
}
