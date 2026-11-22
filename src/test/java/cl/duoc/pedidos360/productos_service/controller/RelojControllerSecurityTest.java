package cl.duoc.pedidos360.productos_service.controller;

import cl.duoc.pedidos360.productos_service.config.SecurityConfig;
import cl.duoc.pedidos360.productos_service.event.UserEventPublisher;
import cl.duoc.pedidos360.productos_service.service.RelojService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reglas de seguridad de /productos sin red:
 * - jwt(): prueba las reglas de scope (se salta el decoder, solo arma el usuario).
 * - Tokens firmados con una llave RSA de prueba: prueban la validación real
 *   (firma, emisor, audiencia, expiración) con el mismo validador de producción.
 */
@WebMvcTest(RelojController.class)
@Import(SecurityConfig.class)
class RelojControllerSecurityTest {

    private static final String TENANT = "e5372bf0-c5e3-4286-887c-79069f209c1f";
    private static final String CLIENT_ID = "42b7d432-638e-4b04-9368-9616b322e672";
    private static final String EMISOR = SecurityConfig.emisor(TENANT);

    /** Llave "de Azure" para los tests y otra de un atacante (firma inválida) */
    private static final KeyPair LLAVE_AZURE = generarLlave();
    private static final KeyPair LLAVE_FALSA = generarLlave();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RelojService relojService;

    @MockitoBean
    private UserEventPublisher userEventPublisher;

    /** Reemplaza el decoder de Azure (JWKS por internet) por uno con la llave local + mismos validadores */
    @TestBean
    private JwtDecoder jwtDecoder;

    static JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) LLAVE_AZURE.getPublic()).build();
        decoder.setJwtValidator(SecurityConfig.validadorToken(EMISOR, SecurityConfig.audiencias(CLIENT_ID)));
        return decoder;
    }

    // ---------- Reglas de scope (jwt() + el conversor real de autoridades) ----------

    @Test
    void sinToken_401() throws Exception {
        mockMvc.perform(get("/productos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void scopeRead_getProductos_200() throws Exception {
        mockMvc.perform(get("/productos").with(conScp("productos.read")))
                .andExpect(status().isOk());
    }

    @Test
    void scopeRead_getPorId_200() throws Exception {
        mockMvc.perform(get("/productos/1").with(conScp("productos.read")))
                .andExpect(status().isOk());
    }

    @Test
    void scopeRead_noPuedeCrear_403() throws Exception {
        mockMvc.perform(post("/productos").with(conScp("productos.read"))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void scopeRead_noPuedeEliminar_403() throws Exception {
        mockMvc.perform(delete("/productos/1").with(conScp("productos.read")))
                .andExpect(status().isForbidden());
    }

    @Test
    void scopeWrite_eliminar_204() throws Exception {
        mockMvc.perform(delete("/productos/1").with(conScp("productos.write")))
                .andExpect(status().isNoContent());
    }

    @Test
    void scopeDeOtroServicio_403() throws Exception {
        mockMvc.perform(get("/productos").with(conScp("orders.read carrito.read")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rutaNoListada_denegada() throws Exception {
        mockMvc.perform(get("/h2-console").with(conScp("productos.read productos.write")))
                .andExpect(status().isForbidden());
    }

    // ---------- Validación real del token (firma, emisor, audiencia, expiración) ----------

    @Test
    void tokenValido_conAudienciaApi_200() throws Exception {
        String token = firmar(LLAVE_AZURE, EMISOR, "api://" + CLIENT_ID, Instant.now().plusSeconds(600));
        mockMvc.perform(get("/productos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void tokenValido_conAudienciaSinPrefijo_200() throws Exception {
        String token = firmar(LLAVE_AZURE, EMISOR, CLIENT_ID, Instant.now().plusSeconds(600));
        mockMvc.perform(get("/productos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void tokenExpirado_401() throws Exception {
        // 5 min en el pasado: supera los 60 s de tolerancia de reloj
        String token = firmar(LLAVE_AZURE, EMISOR, CLIENT_ID, Instant.now().minusSeconds(300));
        mockMvc.perform(get("/productos").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", Matchers.containsString("invalid_token")));
    }

    @Test
    void tokenDeOtraAudiencia_401() throws Exception {
        String token = firmar(LLAVE_AZURE, EMISOR, "api://otra-app", Instant.now().plusSeconds(600));
        mockMvc.perform(get("/productos").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeOtroEmisor_401() throws Exception {
        String token = firmar(LLAVE_AZURE, "https://login.microsoftonline.com/otro-tenant/v2.0",
                CLIENT_ID, Instant.now().plusSeconds(600));
        mockMvc.perform(get("/productos").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenConFirmaFalsa_401() throws Exception {
        String token = firmar(LLAVE_FALSA, EMISOR, CLIENT_ID, Instant.now().plusSeconds(600));
        mockMvc.perform(get("/productos").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenBasura_401() throws Exception {
        mockMvc.perform(get("/productos").header("Authorization", "Bearer no-es-un-jwt"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    /** Usuario autenticado con el claim scp, convertido con el mismo conversor de producción */
    private static RequestPostProcessor conScp(String scp) {
        return jwt().jwt(j -> j.claim("scp", scp)).authorities(SecurityConfig.convertidorAutoridades());
    }

    /** Firma un JWT RS256 como lo haría Azure, con scp=productos.read */
    private static String firmar(KeyPair llave, String emisor, String audiencia, Instant expira) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(emisor)
                .audience(audiencia)
                .subject("usuario-test")
                .claim("oid", "oid-test")
                .claim("scp", "productos.read")
                .issueTime(Date.from(expira.minusSeconds(3600)))
                .expirationTime(Date.from(expira))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) llave.getPrivate()));
        return jwt.serialize();
    }

    private static KeyPair generarLlave() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
