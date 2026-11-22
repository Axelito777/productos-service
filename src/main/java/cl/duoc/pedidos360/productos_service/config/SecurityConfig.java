package cl.duoc.pedidos360.productos_service.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Valida el JWT de Azure Entra ID dentro del servicio (defensa en profundidad):
 * aunque alguien llegue al puerto 8081 saltándose el API Gateway, sin un token válido no entra.
 */
@Configuration
public class SecurityConfig {

    private static final String LOGIN_AZURE = "https://login.microsoftonline.com/";

    /** Reglas por ruta: el mismo scope que exige el Gateway en cada endpoint */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // API REST con token en cada petición: no hay sesión ni cookies, por eso CSRF no aplica
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Dispatch interno de errores de Spring: sin esto un 500 llegaría como 401/403
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/productos").hasAuthority("SCOPE_productos.read")
                        .requestMatchers(HttpMethod.POST, "/productos").hasAuthority("SCOPE_productos.write")
                        .requestMatchers(HttpMethod.GET, "/productos/{id}").hasAuthority("SCOPE_productos.read")
                        .requestMatchers(HttpMethod.PUT, "/productos/{id}").hasAuthority("SCOPE_productos.write")
                        .requestMatchers(HttpMethod.DELETE, "/productos/{id}").hasAuthority("SCOPE_productos.write")
                        // Todo lo que no está listado (incluida /h2-console) queda bloqueado
                        .anyRequest().denyAll())
                // Resource server: lee el Bearer, lo valida con el JwtDecoder y responde 401/403 estándar
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * Decodificador con la URL de llaves públicas (JWKS) de Azure.
     * No usamos issuer-uri porque obliga a ir a internet al arrancar; las llaves se descargan
     * recién con el primer token y Nimbus las deja en caché.
     */
    @Bean
    public JwtDecoder jwtDecoder(@Value("${azure.tenant-id}") String tenantId,
                                 @Value("${azure.client-id}") String clientId) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(LOGIN_AZURE + tenantId + "/discovery/v2.0/keys")
                .build();
        decoder.setJwtValidator(validadorToken(emisor(tenantId), audiencias(clientId)));
        return decoder;
    }

    /** Emisor de los tokens v2 de Entra ID para nuestro tenant */
    public static String emisor(String tenantId) {
        return LOGIN_AZURE + tenantId + "/v2.0";
    }

    /** Azure pone en aud el client ID con o sin el prefijo api://, aceptamos ambas */
    public static List<String> audiencias(String clientId) {
        return List.of("api://" + clientId, clientId);
    }

    /**
     * Validadores propios (la firma ya la verifica el decoder con la llave pública):
     * expiración (con 60 s de tolerancia de reloj), emisor exacto y audiencia (cualquiera de las válidas).
     * Es estático para que los tests usen exactamente la misma validación que producción.
     */
    public static OAuth2TokenValidator<Jwt> validadorToken(String emisor, List<String> audienciasValidas) {
        JwtClaimValidator<Collection<String>> audiencia = new JwtClaimValidator<>(JwtClaimNames.AUD,
                aud -> aud != null && aud.stream().anyMatch(audienciasValidas::contains));
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(emisor),
                audiencia);
    }

    /** Arma el usuario autenticado con sus autoridades (scopes + roles) */
    public static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(convertidorAutoridades());
        return converter;
    }

    /**
     * scp ("productos.read productos.write") -> SCOPE_productos.read, SCOPE_productos.write
     * roles (["Admin"])                      -> ROLE_Admin
     */
    public static Converter<Jwt, Collection<GrantedAuthority>> convertidorAutoridades() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        scopes.setAuthoritiesClaimName("scp");
        scopes.setAuthorityPrefix("SCOPE_");

        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");

        return jwt -> {
            List<GrantedAuthority> autoridades = new ArrayList<>(scopes.convert(jwt));
            autoridades.addAll(roles.convert(jwt));
            return autoridades;
        };
    }
}
