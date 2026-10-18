package com.mx.vacaciones.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inssoft.people.onboarding.dto.TourStep;
import com.inssoft.people.onboarding.service.OnboardingService;
import com.mx.vacaciones.model.Role;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import com.mx.vacaciones.repository.VacationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeControllerTest {

    @Mock
    private VacationRepository vacationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OnboardingService onboardingService;

    private HomeController controller;

    @BeforeEach
    void setUp() {
        controller = new HomeController(
                vacationRepository, userRepository, onboardingService, new ObjectMapper());
    }

    @Test
    void homeProvidesOnboardingDataForNewUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setName("Administrador");
        user.setPassword("hash");
        user.setRole(Role.ROLE_ADMIN);
        user.setEnabled(true);
        user.setOnboardingCompleted(false);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(onboardingService.stepsFor(user)).thenReturn(List.of(
                new TourStep("a[href=\"/admin/users\"]", "Usuarios", "Administra usuarios")));
        when(vacationRepository.countByStatusIn(anyList())).thenReturn(3L);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.home(authentication, model);

        assertThat(view).isEqualTo("home");
        assertThat(model.get("onboardingCompleted")).isEqualTo(false);
        assertThat(String.valueOf(model.get("tourStepsJson"))).contains("Usuarios");
        assertThat(model.get("pendingRequestsCount")).isEqualTo(3L);
    }

    @Test
    void resolvesAdminRoleWhenPermissionsComeFirst() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setName("Administrador");
        user.setPassword("hash");
        user.setRole(Role.ROLE_ADMIN);
        user.setEnabled(true);
        user.setOnboardingCompleted(true);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(onboardingService.stepsFor(user)).thenReturn(List.of());
        when(vacationRepository.countByStatusIn(anyList())).thenReturn(2L);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "admin", null, List.of(
                                new SimpleGrantedAuthority("PERM_MASTERDATA_VIEW"),
                                new SimpleGrantedAuthority("PERM_ORG_VIEW"),
                                new SimpleGrantedAuthority("ROLE_ADMIN")));

        ExtendedModelMap model = new ExtendedModelMap();
        controller.home(authentication, model);

        assertThat(model.get("rol")).isEqualTo("ROLE_ADMIN");
        assertThat(model.get("pendingRequestsCount")).isEqualTo(2L);
    }

    @Test
    void resolvesLeaderRoleWhenPermissionsComeFirst() {
        User user = new User();
        user.setId(2L);
        user.setUsername("lider");
        user.setName("Líder");
        user.setPassword("hash");
        user.setRole(Role.ROLE_LIDER);
        user.setEnabled(true);
        user.setOnboardingCompleted(true);

        when(userRepository.findByUsername("lider")).thenReturn(Optional.of(user));
        when(onboardingService.stepsFor(user)).thenReturn(List.of());
        when(vacationRepository.countByStatusIn(anyList())).thenReturn(1L);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "lider", null, List.of(
                                new SimpleGrantedAuthority("PERM_VACATION_APPROVE"),
                                new SimpleGrantedAuthority("ROLE_LIDER")));

        ExtendedModelMap model = new ExtendedModelMap();
        controller.home(authentication, model);

        assertThat(model.get("rol")).isEqualTo("ROLE_LIDER");
    }
}
