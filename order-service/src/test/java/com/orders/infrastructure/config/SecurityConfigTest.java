package com.orders.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {
    @Test
    void mapsKeycloakRealmRolesToAuthorities() {
        Jwt jwt = Jwt.withTokenValue("demo")
                .header("alg", "RS256")
                .claim("realm_access", Map.of("roles", List.of("ORDERS_READ", "ORDERS_WRITE")))
                .build();

        Collection<GrantedAuthority> authorities =
                new SecurityConfig.CustomJwtGrantedAuthoritiesConverter().convert(jwt);

        assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ORDERS_READ")));
        assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ORDERS_WRITE")));
    }
}
