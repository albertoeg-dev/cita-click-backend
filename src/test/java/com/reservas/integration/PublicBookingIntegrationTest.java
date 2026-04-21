package com.reservas.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.reservas.dto.request.PublicAgendarCitaRequest;
import com.reservas.dto.request.RegisterRequest;
import com.reservas.dto.request.LoginRequest;
import com.reservas.dto.request.ServicioRequest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
@DisplayName("PublicBooking Integration Tests - Flujo E2E")
class PublicBookingIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private Cookie authCookie;
    private String servicioId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity()).build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        // 1. Registro del dueño del negocio
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail("public-booking-test@example.com");
        registerRequest.setPassword("Password123");
        registerRequest.setNombre("Owner");
        registerRequest.setApellidoPaterno("Booking");
        registerRequest.setApellidoMaterno("Test");
        registerRequest.setNombreNegocio("Salon E2E Public");
        registerRequest.setTipoNegocio("salon");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // 2. Login → JWT en cookie
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("public-booking-test@example.com");
        loginRequest.setPassword("Password123");

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        authCookie = loginResult.getResponse().getCookie("access_token");
        assertThat(authCookie).isNotNull();

        // 3. Crear servicio
        ServicioRequest servicioRequest = ServicioRequest.builder()
                .nombre("Corte de cabello E2E")
                .precio(new BigDecimal("150.00"))
                .duracionMinutos(30)
                .activo(true)
                .build();

        MvcResult servicioResult = mockMvc.perform(post("/servicios")
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(servicioRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode servicioJson = objectMapper.readTree(servicioResult.getResponse().getContentAsString());
        servicioId = servicioJson.get("data").get("id").asText();

        // 4. Configurar horario para mañana
        LocalDate manana = LocalDate.now().plusDays(1);
        int diaSemana = manana.getDayOfWeek().getValue() - 1;
        Map<String, Object> horarioBody = Map.of(
                "horaApertura", "08:00:00",
                "horaCierre", "18:00:00",
                "activo", true
        );

        mockMvc.perform(post("/negocios/horarios/" + diaSemana)
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(horarioBody)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("E2E-1: Obtener token público autenticado")
    void step1_obtenerToken_retornaTokenActivo() throws Exception {
        MvcResult result = mockMvc.perform(get("/public/booking/token/mi-token")
                        .cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = json.get("data").get("token").asText();
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("E2E-2: Ver info del negocio con token público válido (sin auth)")
    void step2_infoNegocio_conTokenValido_retornaInfoSinAuth() throws Exception {
        String token = obtenerPublicToken();

        // Sin cookie / JWT
        mockMvc.perform(get("/public/booking/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nombre").value("Salon E2E Public"))
                .andExpect(jsonPath("$.data.servicios").isArray());
    }

    @Test
    @DisplayName("E2E-3: Ver disponibilidad con token válido")
    void step3_disponibilidad_conTokenValido_retornaHorarios() throws Exception {
        String token = obtenerPublicToken();
        LocalDate manana = LocalDate.now().plusDays(1);

        mockMvc.perform(get("/public/booking/" + token + "/disponibilidad")
                        .param("fecha", manana.toString())
                        .param("servicioIds", servicioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fecha").value(manana.toString()))
                .andExpect(jsonPath("$.data.horariosDisponibles").isArray());
    }

    @Test
    @DisplayName("E2E-4: Agendar cita exitosamente con token público válido")
    void step4_agendarCita_exitosa_retorna200() throws Exception {
        String token = obtenerPublicToken();

        PublicAgendarCitaRequest request = buildCitaRequest(
                LocalDate.now().plusDays(1).atTime(10, 0), "cliente-e2e@test.com", "Cliente E2E");

        mockMvc.perform(post("/public/booking/" + token + "/agendar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.citaId").isNotEmpty())
                .andExpect(jsonPath("$.data.estado").value("CONFIRMADA"));
    }

    @Test
    @DisplayName("E2E-5: Segundo agendado en mismo horario retorna conflicto")
    void step5_agendarCitaConflicto_retornaError() throws Exception {
        String token = obtenerPublicToken();
        LocalDateTime fechaCita = LocalDate.now().plusDays(1).atTime(11, 0);

        PublicAgendarCitaRequest req1 = buildCitaRequest(fechaCita, "cliente1@test.com", "Cliente 1");
        PublicAgendarCitaRequest req2 = buildCitaRequest(fechaCita, "cliente2@test.com", "Cliente 2");

        mockMvc.perform(post("/public/booking/" + token + "/agendar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/public/booking/" + token + "/agendar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("E2E-6: Regenerar token invalida el anterior")
    void step6_regenerarToken_tokenAnteriorFalla() throws Exception {
        String tokenAnterior = obtenerPublicToken();

        MvcResult regenResult = mockMvc.perform(post("/public/booking/token/regenerar")
                        .cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        String tokenNuevo = objectMapper.readTree(regenResult.getResponse().getContentAsString())
                .get("data").get("token").asText();

        assertThat(tokenNuevo).isNotEqualTo(tokenAnterior);

        mockMvc.perform(get("/public/booking/" + tokenNuevo))
                .andExpect(status().isOk());

        mockMvc.perform(get("/public/booking/" + tokenAnterior))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("E2E-7: Token inválido retorna 404")
    void step7_tokenInvalido_retorna404() throws Exception {
        mockMvc.perform(get("/public/booking/token-que-no-existe-xyz"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("E2E-8: Obtener token sin autenticación retorna 401")
    void step8_obtenerTokenSinAuth_retorna401() throws Exception {
        mockMvc.perform(get("/public/booking/token/mi-token"))
                .andExpect(status().isUnauthorized());
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private String obtenerPublicToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/public/booking/token/mi-token").cookie(authCookie))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("token").asText();
    }

    private PublicAgendarCitaRequest buildCitaRequest(
            LocalDateTime fechaHora, String email, String nombre) {
        PublicAgendarCitaRequest r = new PublicAgendarCitaRequest();
        r.setServicioIds(List.of(UUID.fromString(servicioId)));
        r.setFechaHora(fechaHora);
        r.setClienteNombre(nombre);
        r.setClienteEmail(email);
        r.setClienteTelefono("5550001234");
        return r;
    }
}
