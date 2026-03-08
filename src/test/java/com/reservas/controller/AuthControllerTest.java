package com.reservas.controller;

import com.reservas.dto.request.LoginRequest;
import com.reservas.dto.request.RegisterRequest;
import com.reservas.dto.response.ApiResponse;
import com.reservas.dto.response.LoginResponse;
import com.reservas.dto.response.UserResponse;
import com.reservas.service.AuthService;
import com.reservas.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController - Pruebas Unitarias")
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @Mock
    private HttpServletResponse httpServletResponse;

    @InjectMocks
    private AuthController authController;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private LoginResponse loginResponse;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setEmail("test@example.com");
        registerRequest.setPassword("password123");
        registerRequest.setNombre("Test");
        registerRequest.setApellidoPaterno("Usuario");
        registerRequest.setApellidoMaterno("Prueba");

        loginRequest = new LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("password123");

        loginResponse = LoginResponse.builder()
                .token("jwt-token-123")
                .nombre("Test")
                .email("test@example.com")
                .build();

        userResponse = UserResponse.builder()
                .id(UUID.randomUUID())
                .nombre("Test")
                .apellidoPaterno("Usuario")
                .apellidoMaterno("Prueba")
                .email("test@example.com")
                .telefono("1234567890")
                .rol("admin")
                .activo(true)
                .build();
    }

    @Test
    @DisplayName("POST /api/auth/register - Debe registrar usuario exitosamente")
    void testRegister_Success() {
        // Arrange
        when(authService.registrar(any(RegisterRequest.class), any(HttpServletRequest.class))).thenReturn(loginResponse);

        // Act
        ResponseEntity<ApiResponse<?>> response = authController.register(registerRequest, httpServletRequest, httpServletResponse);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("Usuario registrado exitosamente", response.getBody().getMessage());
        verify(authService, times(1)).registrar(any(RegisterRequest.class), any(HttpServletRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/register - Debe manejar errores correctamente")
    void testRegister_Error() {
        // Arrange
        when(authService.registrar(any(RegisterRequest.class), any(HttpServletRequest.class)))
                .thenThrow(new RuntimeException("Email ya existe"));

        // Act
        ResponseEntity<ApiResponse<?>> response = authController.register(registerRequest, httpServletRequest, httpServletResponse);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Email ya existe", response.getBody().getMessage());
    }

    @Test
    @DisplayName("POST /api/auth/login - Debe autenticar usuario correctamente")
    void testLogin_Success() {
        // Arrange
        when(rateLimitService.tryConsume(anyString())).thenReturn(true);
        when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(authService.login(any(LoginRequest.class))).thenReturn(loginResponse);

        // Act
        ResponseEntity<ApiResponse<?>> response = authController.login(loginRequest, httpServletRequest, httpServletResponse);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("Login exitoso", response.getBody().getMessage());
        verify(rateLimitService, times(1)).tryConsume(anyString());
        verify(authService, times(1)).login(any(LoginRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/login - Debe manejar credenciales inválidas")
    void testLogin_InvalidCredentials() {
        // Arrange
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new RuntimeException("Credenciales inválidas"));

        // Act
        ResponseEntity<ApiResponse<?>> response = authController.login(loginRequest, httpServletRequest, httpServletResponse);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Credenciales inválidas", response.getBody().getMessage());
    }

    @Test
    @DisplayName("POST /api/auth/logout - Debe cerrar sesión correctamente")
    void testLogout() {
        // Act
        ResponseEntity<ApiResponse<?>> response = authController.logout(httpServletResponse);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("Sesión cerrada exitosamente", response.getBody().getMessage());
    }

    @Test
    @DisplayName("GET /api/auth/test - Debe responder correctamente")
    void testEndpointTest() {
        // Act
        ResponseEntity<ApiResponse<?>> response = authController.test();

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("API funcionando correctamente", response.getBody().getMessage());
    }

    @Test
    @DisplayName("GET /api/auth/me - Debe retornar usuario autenticado")
    void testGetCurrentUser_Success() {
        // Arrange
        when(authService.obtenerUsuarioActual()).thenReturn(userResponse);

        // Act
        ResponseEntity<ApiResponse<?>> response = authController.getCurrentUser();

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("Usuario obtenido exitosamente", response.getBody().getMessage());
        verify(authService, times(1)).obtenerUsuarioActual();
    }

    @Test
    @DisplayName("GET /api/auth/me - Debe manejar usuario no autenticado")
    void testGetCurrentUser_Unauthorized() {
        // Arrange
        when(authService.obtenerUsuarioActual())
                .thenThrow(new RuntimeException("Usuario no autenticado"));

        // Act
        ResponseEntity<ApiResponse<?>> response = authController.getCurrentUser();

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
    }
}
