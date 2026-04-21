package com.reservas.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservas.dto.request.ClienteRequest;
import com.reservas.dto.request.LoginRequest;
import com.reservas.dto.request.RegisterRequest;
import com.reservas.dto.request.ServicioRequest;
import jakarta.servlet.http.Cookie;
import org.assertj.core.api.Assertions;
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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
@DisplayName("Authorization Integration Tests - Access Control Between Businesses")
class AuthorizationIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    private MockMvc mockMvc;
    private Cookie authCookieNegocio1;
    private Cookie authCookieNegocio2;
    private String clienteIdNegocio1;
    private String servicioIdNegocio1;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        // Crear primer negocio y usuario
        RegisterRequest registerNegocio1 = new RegisterRequest();
        registerNegocio1.setEmail("negocio1@test.com");
        registerNegocio1.setPassword("Password123");
        registerNegocio1.setNombre("Usuario");
        registerNegocio1.setApellidoPaterno("Negocio1");
        registerNegocio1.setApellidoMaterno("Test");
        registerNegocio1.setNombreNegocio("Salon Negocio 1");
        registerNegocio1.setTipoNegocio("salon");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerNegocio1)))
                .andExpect(status().isCreated());

        // Login negocio 1
        LoginRequest loginNegocio1 = new LoginRequest();
        loginNegocio1.setEmail("negocio1@test.com");
        loginNegocio1.setPassword("Password123");

        MvcResult loginResult1 = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginNegocio1)))
                .andExpect(status().isOk())
                .andReturn();

        authCookieNegocio1 = loginResult1.getResponse().getCookie("access_token");
        Assertions.assertThat(authCookieNegocio1).isNotNull();

        // Crear segundo negocio y usuario
        RegisterRequest registerNegocio2 = new RegisterRequest();
        registerNegocio2.setEmail("negocio2@test.com");
        registerNegocio2.setPassword("Password123");
        registerNegocio2.setNombre("Usuario");
        registerNegocio2.setApellidoPaterno("Negocio2");
        registerNegocio2.setApellidoMaterno("Test");
        registerNegocio2.setNombreNegocio("Salon Negocio 2");
        registerNegocio2.setTipoNegocio("salon");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerNegocio2)))
                .andExpect(status().isCreated());

        // Login negocio 2
        LoginRequest loginNegocio2 = new LoginRequest();
        loginNegocio2.setEmail("negocio2@test.com");
        loginNegocio2.setPassword("Password123");

        MvcResult loginResult2 = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginNegocio2)))
                .andExpect(status().isOk())
                .andReturn();

        authCookieNegocio2 = loginResult2.getResponse().getCookie("access_token");
        Assertions.assertThat(authCookieNegocio2).isNotNull();

        // Crear datos de prueba para negocio 1
        crearDatosPruebaParaNegocio1();
    }

    private void crearDatosPruebaParaNegocio1() throws Exception {
        // Crear servicio para negocio 1
        ServicioRequest servicioRequest = ServicioRequest.builder()
                .nombre("Corte Negocio 1")
                .descripcion("Servicio del negocio 1")
                .precio(new BigDecimal("100.00"))
                .duracionMinutos(30)
                .activo(true)
                .build();

        MvcResult servicioResult = mockMvc.perform(post("/servicios")
                .cookie(authCookieNegocio1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(servicioRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        servicioIdNegocio1 = objectMapper.readTree(servicioResult.getResponse().getContentAsString())
                .get("data").get("id").asText();

        // Crear cliente para negocio 1
        ClienteRequest clienteRequest = ClienteRequest.builder()
                .nombre("Cliente")
                .apellidoPaterno("Negocio1")
                .apellidoMaterno("Test")
                .email("cliente.negocio1@test.com")
                .telefono("1234567890")
                .build();

        MvcResult clienteResult = mockMvc.perform(post("/clientes")
                .cookie(authCookieNegocio1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(clienteRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        clienteIdNegocio1 = objectMapper.readTree(clienteResult.getResponse().getContentAsString())
                .get("data").get("id").asText();
    }

    @Test
    @DisplayName("Autorización: Negocio 2 NO debe poder acceder a servicios de Negocio 1")
    void testAccesoServiciosDeOtroNegocio() throws Exception {
        // El sistema devuelve 404 porque filtra por negocio (recurso no visible a otro negocio)
        mockMvc.perform(get("/servicios/" + servicioIdNegocio1)
                .cookie(authCookieNegocio2)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Autorización: Negocio 2 NO debe poder actualizar servicios de Negocio 1")
    void testActualizarServicioDeOtroNegocio() throws Exception {
        ServicioRequest updateRequest = ServicioRequest.builder()
                .nombre("Servicio Modificado")
                .descripcion("Intentando modificar")
                .precio(new BigDecimal("200.00"))
                .duracionMinutos(45)
                .activo(true)
                .build();

        mockMvc.perform(put("/servicios/" + servicioIdNegocio1)
                .cookie(authCookieNegocio2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Autorización: Negocio 2 NO debe poder eliminar servicios de Negocio 1")
    void testEliminarServicioDeOtroNegocio() throws Exception {
        mockMvc.perform(delete("/servicios/" + servicioIdNegocio1)
                .cookie(authCookieNegocio2)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Autorización: Negocio 2 NO debe poder acceder a clientes de Negocio 1")
    void testAccesoClientesDeOtroNegocio() throws Exception {
        // El sistema devuelve 404 porque filtra por negocio (recurso no visible a otro negocio)
        mockMvc.perform(get("/clientes/" + clienteIdNegocio1)
                .cookie(authCookieNegocio2)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Autorización: Negocio 2 NO debe poder actualizar clientes de Negocio 1")
    void testActualizarClienteDeOtroNegocio() throws Exception {
        ClienteRequest updateRequest = ClienteRequest.builder()
                .nombre("Cliente Modificado")
                .apellidoPaterno("Test")
                .apellidoMaterno("Test")
                .email("modificado@test.com")
                .telefono("9876543210")
                .build();

        mockMvc.perform(put("/clientes/" + clienteIdNegocio1)
                .cookie(authCookieNegocio2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Autorización: Negocio 2 NO debe poder eliminar clientes de Negocio 1")
    void testEliminarClienteDeOtroNegocio() throws Exception {
        mockMvc.perform(delete("/clientes/" + clienteIdNegocio1)
                .cookie(authCookieNegocio2)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Autorización: Negocio 1 DEBE poder acceder a sus propios servicios")
    void testAccesoServiciosPropios() throws Exception {
        mockMvc.perform(get("/servicios/" + servicioIdNegocio1)
                .cookie(authCookieNegocio1)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(servicioIdNegocio1));
    }

    @Test
    @DisplayName("Autorización: Negocio 1 DEBE poder acceder a sus propios clientes")
    void testAccesoClientesPropios() throws Exception {
        mockMvc.perform(get("/clientes/" + clienteIdNegocio1)
                .cookie(authCookieNegocio1)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(clienteIdNegocio1));
    }

    @Test
    @DisplayName("Autorización: Listado de servicios debe retornar solo servicios del negocio autenticado")
    void testListadoServiciosPorNegocio() throws Exception {
        // Negocio 2 crea su propio servicio
        ServicioRequest servicioNegocio2 = ServicioRequest.builder()
                .nombre("Corte Negocio 2")
                .descripcion("Servicio del negocio 2")
                .precio(new BigDecimal("150.00"))
                .duracionMinutos(40)
                .activo(true)
                .build();

        mockMvc.perform(post("/servicios")
                .cookie(authCookieNegocio2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(servicioNegocio2)))
                .andExpect(status().isCreated());

        // Negocio 1 lista servicios - solo debe ver los suyos
        MvcResult result1 = mockMvc.perform(get("/servicios")
                .cookie(authCookieNegocio1)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        String response1 = result1.getResponse().getContentAsString();
        assert response1.contains("Corte Negocio 1");
        assert !response1.contains("Corte Negocio 2");

        // Negocio 2 lista servicios - solo debe ver los suyos
        MvcResult result2 = mockMvc.perform(get("/servicios")
                .cookie(authCookieNegocio2)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        String response2 = result2.getResponse().getContentAsString();
        assert response2.contains("Corte Negocio 2");
        assert !response2.contains("Corte Negocio 1");
    }

    @Test
    @DisplayName("Autorización: Listado de clientes debe retornar solo clientes del negocio autenticado")
    void testListadoClientesPorNegocio() throws Exception {
        // Negocio 2 crea su propio cliente
        ClienteRequest clienteNegocio2 = ClienteRequest.builder()
                .nombre("Cliente")
                .apellidoPaterno("Negocio2")
                .apellidoMaterno("Test")
                .email("cliente.negocio2@test.com")
                .telefono("9876543210")
                .build();

        mockMvc.perform(post("/clientes")
                .cookie(authCookieNegocio2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(clienteNegocio2)))
                .andExpect(status().isCreated());

        // Negocio 1 lista clientes - solo debe ver los suyos
        MvcResult result1 = mockMvc.perform(get("/clientes")
                .cookie(authCookieNegocio1)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        String response1 = result1.getResponse().getContentAsString();
        assert response1.contains("cliente.negocio1@test.com");
        assert !response1.contains("cliente.negocio2@test.com");

        // Negocio 2 lista clientes - solo debe ver los suyos
        MvcResult result2 = mockMvc.perform(get("/clientes")
                .cookie(authCookieNegocio2)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        String response2 = result2.getResponse().getContentAsString();
        assert response2.contains("cliente.negocio2@test.com");
        assert !response2.contains("cliente.negocio1@test.com");
    }
}
