package com.inssoft.people.onboarding.dto;

import java.util.List;

public record OnboardingResponse(
        boolean completed,
        List<TourStep> steps
) {
}
