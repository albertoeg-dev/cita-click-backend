package com.reservas.service;

import com.reservas.dto.SuscripcionInfoResponse;
import com.reservas.entity.Negocio;
import com.reservas.entity.Usuario;
import com.reservas.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SuscripcionInfoService - Pruebas Unitarias")
class SuscripcionInfoServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ModuloService moduloService;

    @InjectMocks
    private SuscripcionInfoService suscripcionInfoService;

    private Usuario usuarioMock;
    private Negocio negocioMock;

    @BeforeEach
    void setUp() {
        negocioMock = Negocio.builder()
                .id(UUID.randomUUID())
                .nombre("Salon Test")
                .email("salon@test.com")
                .tipo("salon")
                .plan("profesional")
                .estadoPago("activo")
                .enPeriodoPrueba(false)
                .cuentaActiva(true)
                .fechaRegistro(LocalDateTime.now().minusMonths(2))
                .fechaFinPrueba(LocalDateTime.now().minusMonths(1))
                .fechaProximoCobro(LocalDateTime.now().plusDays(15))
                .build();

        usuarioMock = Usuario.builder()
                .id(UUID.randomUUID())
                .email("usuario@test.com")
                .nombre("Juan")
                .apellidoPaterno("Pérez")
                .negocio(negocioMock)
                .build();
    }

    @Test
    @DisplayName("Debe obtener información de suscripción exitosamente")
    void debeObtenerInfoSuscripcion_exitosamente() {
        // Arrange
        when(usuarioRepository.findByEmailWithNegocio("usuario@test.com"))
                .thenReturn(Optional.of(usuarioMock));

        // Act
        SuscripcionInfoResponse response = suscripcionInfoService.obtenerInfoSuscripcion("usuario@test.com");

        // Assert
        assertNotNull(response);
        assertEquals("profesional", response.getPlan());
        assertEquals("activo", response.getEstadoPago());
        assertFalse(response.isEnPeriodoPrueba());
        assertTrue(response.isCuentaActiva());
        verify(usuarioRepository, times(1)).findByEmailWithNegocio("usuario@test.com");
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando usuario no existe")
    void debeLanzarExcepcion_cuandoUsuarioNoExiste() {
        // Arrange
        when(usuarioRepository.findByEmailWithNegocio("noexiste@test.com"))
                .thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> suscripcionInfoService.obtenerInfoSuscripcion("noexiste@test.com"));

        assertEquals("Usuario no encontrado", exception.getMessage());
        verify(usuarioRepository, times(1)).findByEmailWithNegocio("noexiste@test.com");
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando usuario no tiene negocio")
    void debeLanzarExcepcion_cuandoUsuarioNoTieneNegocio() {
        // Arrange
        usuarioMock.setNegocio(null);
        when(usuarioRepository.findByEmailWithNegocio("usuario@test.com"))
                .thenReturn(Optional.of(usuarioMock));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> suscripcionInfoService.obtenerInfoSuscripcion("usuario@test.com"));

        assertEquals("El usuario no tiene un negocio asociado", exception.getMessage());
        verify(usuarioRepository, times(1)).findByEmailWithNegocio("usuario@test.com");
    }

    @Test
    @DisplayName("Debe calcular días restantes de prueba correctamente")
    void debeCalcularDiasRestantesPrueba_correctamente() {
        // Arrange
        negocioMock.setEnPeriodoPrueba(true);
        negocioMock.setFechaFinPrueba(LocalDateTime.now().plusDays(5));

        when(usuarioRepository.findByEmailWithNegocio("usuario@test.com"))
                .thenReturn(Optional.of(usuarioMock));

        // Act
        SuscripcionInfoResponse response = suscripcionInfoService.obtenerInfoSuscripcion("usuario@test.com");

        // Assert
        assertTrue(response.isEnPeriodoPrueba());
        assertNotNull(response.getDiasRestantesPrueba());
        assertTrue(response.getDiasRestantesPrueba() > 0);
    }

    @Test
    @DisplayName("Debe retornar null en días de prueba cuando no está en periodo de prueba")
    void debeRetornarNullDiasPrueba_cuandoNoEstaEnPrueba() {
        // Arrange
        negocioMock.setEnPeriodoPrueba(false);

        when(usuarioRepository.findByEmailWithNegocio("usuario@test.com"))
                .thenReturn(Optional.of(usuarioMock));

        // Act
        SuscripcionInfoResponse response = suscripcionInfoService.obtenerInfoSuscripcion("usuario@test.com");

        // Assert
        assertFalse(response.isEnPeriodoPrueba());
        assertNull(response.getDiasRestantesPrueba());
    }

    @Test
    @DisplayName("Debe incluir fechas correctas en el response")
    void debeIncluirFechasCorrectas_enResponse() {
        // Arrange
        when(usuarioRepository.findByEmailWithNegocio("usuario@test.com"))
                .thenReturn(Optional.of(usuarioMock));

        // Act
        SuscripcionInfoResponse response = suscripcionInfoService.obtenerInfoSuscripcion("usuario@test.com");

        // Assert
        assertNotNull(response.getFechaRegistro());
        assertNotNull(response.getFechaFinPrueba());
        assertNotNull(response.getFechaProximoPago());
        assertEquals(negocioMock.getFechaRegistro(), response.getFechaRegistro());
        assertEquals(negocioMock.getFechaFinPrueba(), response.getFechaFinPrueba());
        assertEquals(negocioMock.getFechaProximoCobro(), response.getFechaProximoPago());
    }

    @Test
    @DisplayName("Debe obtener negocio por email exitosamente")
    void debeObtenerNegocioPorEmail_exitosamente() {
        // Arrange
        when(usuarioRepository.findByEmailWithNegocio("usuario@test.com"))
                .thenReturn(Optional.of(usuarioMock));

        // Act
        Negocio negocio = suscripcionInfoService.obtenerNegocioPorEmail("usuario@test.com");

        // Assert
        assertNotNull(negocio);
        assertEquals(negocioMock.getId(), negocio.getId());
        assertEquals(negocioMock.getNombre(), negocio.getNombre());
        verify(usuarioRepository, times(1)).findByEmailWithNegocio("usuario@test.com");
    }

    @Test
    @DisplayName("Debe lanzar excepción al obtener negocio cuando usuario no existe")
    void debeLanzarExcepcion_alObtenerNegocio_cuandoUsuarioNoExiste() {
        // Arrange
        when(usuarioRepository.findByEmailWithNegocio("noexiste@test.com"))
                .thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> suscripcionInfoService.obtenerNegocioPorEmail("noexiste@test.com"));

        assertEquals("Usuario no encontrado", exception.getMessage());
    }

    @Test
    @DisplayName("Debe lanzar excepción al obtener negocio cuando negocio no existe")
    void debeLanzarExcepcion_alObtenerNegocio_cuandoNegocioNoExiste() {
        // Arrange
        usuarioMock.setNegocio(null);
        when(usuarioRepository.findByEmailWithNegocio("usuario@test.com"))
                .thenReturn(Optional.of(usuarioMock));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> suscripcionInfoService.obtenerNegocioPorEmail("usuario@test.com"));

        assertEquals("El usuario no tiene un negocio asociado", exception.getMessage());
    }

    @Test
    @DisplayName("Debe manejar diferentes tipos de planes correctamente")
    void debeManejarDiferentipleanes_correctamente() {
        // Arrange
        negocioMock.setPlan("basico");
        when(usuarioRepository.findByEmailWithNegocio("usuario@test.com"))
                .thenReturn(Optional.of(usuarioMock));

        // Act
        SuscripcionInfoResponse response = suscripcionInfoService.obtenerInfoSuscripcion("usuario@test.com");

        // Assert
        assertEquals("basico", response.getPlan());

        // Cambiar a plan profesional
        negocioMock.setPlan("profesional");
        response = suscripcionInfoService.obtenerInfoSuscripcion("usuario@test.com");
        assertEquals("profesional", response.getPlan());
    }
}
