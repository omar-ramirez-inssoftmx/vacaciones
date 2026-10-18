package com.inssoft.people.auth.dto;

import java.util.List;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String username,
        List<String> authorities
) {
}
