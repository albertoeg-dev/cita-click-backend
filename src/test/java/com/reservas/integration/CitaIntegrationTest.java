package com.reservas.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservas.dto.request.CitaRequest;
import com.reservas.dto.request.ClienteRequest;
import com.reservas.dto.request.LoginRequest;
import com.reservas.dto.request.RegisterRequest;
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
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
@DisplayName("Cita Integration Tests - E2E Flow")
class CitaIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    private MockMvc mockMvc;
    private Cookie authCookie;
    private String clienteId;
    private String servicioId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        // 1. Registrar usuario
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail("test-integration@example.com");
        registerRequest.setPassword("Password123");
        registerRequest.setNombre("Test");
        registerRequest.setApellidoPaterno("Integration");
        registerRequest.setApellidoMaterno("User");
        registerRequest.setNombreNegocio("Salon Test E2E");
        registerRequest.setTipoNegocio("salon");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // 2. Login → JWT en cookie httpOnly
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("test-integration@example.com");
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
                .nombre("Corte de Cabello E2E")
                .descripcion("Corte profesional")
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

        servicioId = objectMapper.readTree(servicioResult.getResponse().getContentAsString())
                .get("data")
                .get("id")
                .asText();

        // 4. Crear cliente
        ClienteRequest clienteRequest = ClienteRequest.builder()
                .nombre("María")
                .apellidoPaterno("González")
                .apellidoMaterno("López")
                .email("maria.e2e@test.com")
                .telefono("1234567890")
                .notas("Cliente de prueba E2E")
                .build();

        MvcResult clienteResult = mockMvc.perform(post("/clientes")
                .cookie(authCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(clienteRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        clienteId = objectMapper.readTree(clienteResult.getResponse().getContentAsString())
                .get("data")
                .get("id")
                .asText();
    }

    @Test
    @DisplayName("E2E: Flujo completo de creación y gestión de cita")
    void testFlujoCitaCompleto() throws Exception {
        // 1. Crear cita
        LocalDate fechaCita = LocalDate.now().plusDays(1);
        LocalTime horaCita = LocalTime.of(10, 0);

        CitaRequest citaRequest = CitaRequest.builder()
                .fecha(fechaCita)
                .hora(horaCita)
                .clienteId(clienteId)
                .servicioId(servicioId)
                .notas("Primera cita E2E")
                .build();

        MvcResult createResult = mockMvc.perform(post("/citas")
                .cookie(authCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(citaRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.data.clienteId").value(clienteId))
                .andExpect(jsonPath("$.data.servicioId").value(servicioId))
                .andReturn();

        String citaId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data")
                .get("id")
                .asText();

        // 2. Listar citas
        mockMvc.perform(get("/citas")
                .cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(citaId));

        // 3. Obtener cita por ID
        mockMvc.perform(get("/citas/" + citaId)
                .cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(citaId))
                .andExpect(jsonPath("$.data.estado").value("PENDIENTE"));

        // 4. Cambiar estado a CONFIRMADA
        mockMvc.perform(patch("/citas/" + citaId + "/estado")
                .cookie(authCookie)
                .param("estado", "CONFIRMADA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.estado").value("CONFIRMADA"));

        // 5. Actualizar cita
        LocalDate fechaUpdate = LocalDate.now().plusDays(1);
        LocalTime horaUpdate = LocalTime.of(14, 0);

        CitaRequest updateRequest = CitaRequest.builder()
                .fecha(fechaUpdate)
                .hora(horaUpdate)
                .clienteId(clienteId)
                .servicioId(servicioId)
                .notas("Cita actualizada E2E")
                .build();

        mockMvc.perform(put("/citas/" + citaId)
                .cookie(authCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.notas").value("Cita actualizada E2E"));

        // 6. Cancelar cita
        mockMvc.perform(delete("/citas/" + citaId)
                .cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 7. Verificar que está cancelada
        mockMvc.perform(get("/citas/" + citaId)
                .cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estado").value("CANCELADA"));
    }

    @Test
    @DisplayName("E2E: Verificar disponibilidad de horarios")
    void testVerificarDisponibilidadHorarios() throws Exception {
        // Obtener horarios disponibles
        mockMvc.perform(get("/citas/disponibilidad")
                .cookie(authCookie)
                .param("servicioId", servicioId)
                .param("fecha", LocalDateTime.now().plusDays(1).toLocalDate().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("E2E: Validar conflicto de horarios")
    void testValidarConflictoHorarios() throws Exception {
        // 1. Crear primera cita
        LocalDate fechaConflicto = LocalDate.now().plusDays(2);
        LocalTime horaConflicto = LocalTime.of(10, 0);

        CitaRequest cita1 = CitaRequest.builder()
                .fecha(fechaConflicto)
                .hora(horaConflicto)
                .clienteId(clienteId)
                .servicioId(servicioId)
                .notas("Primera cita")
                .build();

        mockMvc.perform(post("/citas")
                .cookie(authCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cita1)))
                .andExpect(status().isCreated());

        // 2. Intentar crear cita en el mismo horario (debe fallar)
        CitaRequest cita2 = CitaRequest.builder()
                .fecha(fechaConflicto)
                .hora(horaConflicto)
                .clienteId(clienteId)
                .servicioId(servicioId)
                .notas("Segunda cita - conflicto")
                .build();

        mockMvc.perform(post("/citas")
                .cookie(authCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cita2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("E2E: Listar citas con filtros")
    void testListarCitasConFiltros() throws Exception {
        // Crear varias citas
        LocalDate fecha1 = LocalDate.now().plusDays(1);
        LocalTime hora1 = LocalTime.of(10, 0);
        LocalDate fecha2 = LocalDate.now().plusDays(2);
        LocalTime hora2 = LocalTime.of(14, 0);

        CitaRequest cita1 = CitaRequest.builder()
                .fecha(fecha1)
                .hora(hora1)
                .clienteId(clienteId)
                .servicioId(servicioId)
                .build();

        CitaRequest cita2 = CitaRequest.builder()
                .fecha(fecha2)
                .hora(hora2)
                .clienteId(clienteId)
                .servicioId(servicioId)
                .build();

        MvcResult result1 = mockMvc.perform(post("/citas")
                .cookie(authCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cita1)))
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(post("/citas")
                .cookie(authCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cita2)))
                .andExpect(status().isCreated());

        String citaId = objectMapper.readTree(result1.getResponse().getContentAsString())
                .get("data").get("id").asText();

        // Confirmar una cita
        mockMvc.perform(patch("/citas/" + citaId + "/estado")
                .cookie(authCookie)
                .param("estado", "CONFIRMADA"))
                .andExpect(status().isOk());

        // Filtrar por estado CONFIRMADA
        mockMvc.perform(get("/citas")
                .cookie(authCookie)
                .param("estado", "CONFIRMADA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].estado").value("CONFIRMADA"));

        // Filtrar por fecha
        mockMvc.perform(get("/citas")
                .cookie(authCookie)
                .param("fecha", fecha1.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
