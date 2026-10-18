package com.inssoft.people.onboarding.service;

import com.inssoft.people.onboarding.dto.OnboardingResponse;
import com.inssoft.people.onboarding.dto.TourStep;
import com.mx.vacaciones.model.Role;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OnboardingService service;

    @Test
    void employeeTourContainsBasicSteps() {
        List<TourStep> steps = service.stepsFor(user(Role.ROLE_COLABORADOR));

        assertThat(steps)
                .extracting(TourStep::target)
                .contains("[data-tour=\"home-hero\"]",
                        "a[href=\"/vacations/request\"]",
                        "a[href=\"/vacations/status\"]",
                        "a[href=\"/vacations/politicas\"]");
        assertThat(steps)
                .extracting(TourStep::target)
                .doesNotContain("a[href=\"/admin/users\"]");
    }

    @Test
    void leaderTourContainsLeaderStep() {
        List<TourStep> steps = service.stepsFor(user(Role.ROLE_LIDER));

        assertThat(steps)
                .extracting(TourStep::target)
                .contains("a[href=\"/leader/requests\"]",
                        "a[href=\"/admin/requests/history\"]");
    }

    @Test
    void adminTourContainsAdministrativeSteps() {
        List<TourStep> steps = service.stepsFor(user(Role.ROLE_ADMIN));

        assertThat(steps)
                .extracting(TourStep::target)
                .contains("a[href=\"/admin/requests\"]",
                        "a[href=\"/admin/users\"]",
                        "a[href=\"/admin/calendar\"]",
                        "a[href=\"/admin/dashboard\"]");
    }

    @Test
    void statusForReturnsCompletionAndSteps() {
        User user = user(Role.ROLE_COLABORADOR);
        user.setOnboardingCompleted(false);

        OnboardingResponse response = service.statusFor(user);

        assertThat(response.completed()).isFalse();
        assertThat(response.steps()).isNotEmpty();
    }

    @Test
    void completeMarksUser() {
        User user = user(Role.ROLE_COLABORADOR);
        user.setOnboardingCompleted(false);

        service.complete(user);

        assertThat(user.getOnboardingCompleted()).isTrue();
        verify(userRepository).save(user);
    }

    private User user(Role role) {
        User user = new User();
        user.setId(1L);
        user.setUsername("tester");
        user.setName("Tester");
        user.setPassword("hash");
        user.setRole(role);
        user.setEnabled(true);
        user.setOnboardingCompleted(false);
        return user;
    }
}
