package com.reservas.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservas.dto.request.LoginRequest;
import com.reservas.dto.request.RegisterRequest;
import jakarta.servlet.http.Cookie;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
@DisplayName("Security Integration Tests - Authentication & Authorization")
class SecurityIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    private MockMvc mockMvc;
    private Cookie authCookie;
    private String userEmail = "security-test@example.com";

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail(userEmail);
        registerRequest.setPassword("SecurePass123");
        registerRequest.setNombre("Security");
        registerRequest.setApellidoPaterno("Test");
        registerRequest.setApellidoMaterno("User");
        registerRequest.setNombreNegocio("Security Test Business");
        registerRequest.setTipoNegocio("salon");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(userEmail);
        loginRequest.setPassword("SecurePass123");

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        authCookie = loginResult.getResponse().getCookie("access_token");
        Assertions.assertThat(authCookie).isNotNull();
    }

    @Test
    @DisplayName("Seguridad: Acceso sin token JWT debe fallar con 401")
    void testAccesoSinToken() throws Exception {
        mockMvc.perform(get("/servicios")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Seguridad: Token JWT inválido debe fallar con 401")
    void testTokenInvalido() throws Exception {
        mockMvc.perform(get("/servicios")
                .header("Authorization", "Bearer token-invalido-12345")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Seguridad: Token JWT malformado debe fallar con 401")
    void testTokenMalformado() throws Exception {
        mockMvc.perform(get("/servicios")
                .header("Authorization", "InvalidFormat")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Seguridad: Token JWT vacío debe fallar con 401")
    void testTokenVacio() throws Exception {
        mockMvc.perform(get("/servicios")
                .header("Authorization", "Bearer ")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Seguridad: Acceso con token válido debe tener éxito")
    void testAccesoConTokenValido() throws Exception {
        mockMvc.perform(get("/servicios")
                .cookie(authCookie)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Seguridad: Prevención de inyección SQL en búsqueda de clientes")
    void testPrevencionInyeccionSQL() throws Exception {
        // Intentar inyección SQL en parámetro de búsqueda
        String maliciousQuery = "'; DROP TABLE clientes; --";

        mockMvc.perform(get("/clientes")
                .cookie(authCookie)
                .param("buscar", maliciousQuery)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("Seguridad: Validación de caracteres especiales en inputs")
    void testValidacionCaracteresEspeciales() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test<script>alert('xss')</script>@test.com");
        request.setPassword("Pass123");
        request.setNombre("Test<script>");
        request.setApellidoPaterno("User");
        request.setApellidoMaterno("Test");
        request.setNombreNegocio("Business<script>");
        request.setTipoNegocio("salon");

        // El sistema debe manejar o rechazar estos inputs de forma segura
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Disabled("Rate limiting deshabilitado en perfil de test (rate.limit.enabled=false)")
    @DisplayName("Seguridad: Rate limiting en endpoint de login")
    void testRateLimitingLogin() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("rate-limit-test@example.com");
        loginRequest.setPassword("Password123");

        // Realizar múltiples intentos de login (más de 5 en 1 minuto)
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)));
        }

        // El sexto intento debe ser bloqueado por rate limiting
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Seguridad: Headers de seguridad CORS presentes")
    void testCORSHeaders() throws Exception {
        mockMvc.perform(options("/auth/login")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("Seguridad: Password no debe exponerse en respuestas")
    void testPasswordNoExpuesto() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail("password-test@example.com");
        registerRequest.setPassword("MySecretPassword123");
        registerRequest.setNombre("Password");
        registerRequest.setApellidoPaterno("Test");
        registerRequest.setApellidoMaterno("User");
        registerRequest.setNombreNegocio("Password Test Business");
        registerRequest.setTipoNegocio("salon");

        MvcResult result = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();

        // Verificar que el valor de la contraseña no esté en la respuesta
        assert !responseBody.contains("MySecretPassword123");
    }

    @Test
    @DisplayName("Seguridad: Email debe ser válido en registro")
    void testValidacionEmailRegistro() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("email-invalido-sin-arroba");
        request.setPassword("Pass123");
        request.setNombre("Test");
        request.setApellidoPaterno("User");
        request.setApellidoMaterno("Test");
        request.setNombreNegocio("Business");
        request.setTipoNegocio("salon");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Seguridad: Contraseña debe cumplir requisitos mínimos")
    void testValidacionPasswordDebil() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("weak-password@test.com");
        request.setPassword("123"); // Contraseña muy corta
        request.setNombre("Test");
        request.setApellidoPaterno("User");
        request.setApellidoMaterno("Test");
        request.setNombreNegocio("Business");
        request.setTipoNegocio("salon");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Seguridad: Login con credenciales incorrectas debe fallar")
    void testLoginCredencialesIncorrectas() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(userEmail);
        loginRequest.setPassword("ContraseñaIncorrecta123");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Seguridad: Login con usuario inexistente debe fallar")
    void testLoginUsuarioInexistente() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("usuario-inexistente@test.com");
        loginRequest.setPassword("Pass123");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
