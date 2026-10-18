package com.inssoft.people.auth.service;

import com.inssoft.people.auth.dto.TokenResponse;
import com.inssoft.people.common.security.JwtConfig;
import com.inssoft.people.common.security.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import javax.crypto.SecretKey;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    private static final String SECRET = "01234567890123456789012345678901";

    @Test
    void issuesAndDecodesTokenWithAuthorities() {
        JwtConfig config = new JwtConfig();
        JwtProperties properties = new JwtProperties(SECRET, "https://inssoft-people.test", 60);
        SecretKey key = config.jwtSecretKey(properties);
        JwtTokenService service = new JwtTokenService(config.jwtEncoder(key), properties);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "omar", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        TokenResponse response = service.issue(authentication);
        Jwt jwt = config.jwtDecoder(key).decode(response.accessToken());

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.username()).isEqualTo("omar");
        assertThat(jwt.getSubject()).isEqualTo("omar");
        assertThat(jwt.getClaimAsStringList("authorities")).containsExactly("ROLE_ADMIN");
        assertThat(jwt.getIssuer().toString()).isEqualTo("https://inssoft-people.test");
    }
}
