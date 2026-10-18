package com.inssoft.people.auth.service;

import com.inssoft.people.auth.dto.LoginRequest;
import com.inssoft.people.auth.dto.TokenResponse;
import com.inssoft.people.common.audit.AuditService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final AuditService auditService;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtTokenService jwtTokenService,
            AuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.auditService = auditService;
    }

    public TokenResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            auditService.record("LOGIN_SUCCESS", "User", request.username(), null, null, "SUCCESS");
            return jwtTokenService.issue(authentication);
        } catch (RuntimeException ex) {
            auditService.record("LOGIN_FAILURE", "User", request.username(), null, null, "FAILED");
            throw ex;
        }
    }
}
