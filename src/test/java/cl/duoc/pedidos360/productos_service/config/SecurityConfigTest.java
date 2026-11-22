package cl.duoc.pedidos360.productos_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Conversión de claims de Azure a autoridades de Spring */
class SecurityConfigTest {

    private static Jwt jwt(Object scp, Object roles) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (scp != null) b.claim("scp", scp);
        if (roles != null) b.claim("roles", roles);
        return b.build();
    }

    private static List<String> autoridades(Jwt jwt) {
        return SecurityConfig.convertidorAutoridades().convert(jwt).stream()
                .map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void scpYRoles_seConviertenEnScopeYRole() {
        assertThat(autoridades(jwt("productos.read productos.write", List.of("Admin"))))
                .containsExactlyInAnyOrder("SCOPE_productos.read", "SCOPE_productos.write", "ROLE_Admin");
    }

    @Test
    void sinRoles_soloScopes() {
        assertThat(autoridades(jwt("productos.read", null))).containsExactly("SCOPE_productos.read");
    }

    @Test
    void audiencias_aceptaConYSinPrefijoApi() {
        assertThat(SecurityConfig.audiencias("abc")).containsExactly("api://abc", "abc");
    }
}
