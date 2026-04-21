package com.reservas.service;

import com.reservas.entity.Negocio;
import com.reservas.entity.RegistroIP;
import com.reservas.entity.Usuario;
import com.reservas.exception.IpBloqueadaException;
import com.reservas.exception.SuscripcionVencidaException;
import com.reservas.repository.NegocioRepository;
import com.reservas.repository.RegistroIPRepository;
import com.reservas.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SuscripcionService - Pruebas Unitarias")
class SuscripcionServiceTest {

    @Mock
    private NegocioRepository negocioRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private RegistroIPRepository registroIPRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private SuscripcionService suscripcionService;

    private Negocio negocioMock;
    private Usuario usuarioMock;
    private String ipAddress;
    private String userAgent;
    private UUID negocioId;

    @BeforeEach
    void setUp() {
        negocioId = UUID.randomUUID();
        ipAddress = "192.168.1.100";
        userAgent = "Mozilla/5.0";

        negocioMock = Negocio.builder()
                .id(negocioId)
                .nombre("Salon Test")
                .email("salon@test.com")
                .tipo("salon")
                .plan("prueba")
                .estadoPago("activo")
                .enPeriodoPrueba(true)
                .cuentaActiva(true)
                .fechaFinPrueba(LocalDateTime.now().plusDays(7))
                .build();

        usuarioMock = Usuario.builder()
                .id(UUID.randomUUID())
                .email("usuario@test.com")
                .nombre("Usuario")
                .apellidoPaterno("Test")
                .negocio(negocioMock)
                .activo(true)
                .build();
    }

    @Test
    @DisplayName("Debe permitir registro cuando IP tiene menos de 3 registros de prueba")
    void debePermitirRegistro_cuandoIPTieneMenosDe3Registros() {
        // Arrange
        when(registroIPRepository.countRegistrosPruebaByIP(eq(ipAddress), any(LocalDateTime.class)))
                .thenReturn(2L);

        // Act & Assert
        assertDoesNotThrow(() -> suscripcionService.validarRegistroIP(ipAddress));
    }

    @Test
    @DisplayName("Debe bloquear IP cuando tiene 3 o más registros de prueba")
    void debeBloquearIP_cuandoTiene3OMasRegistros() {
        // Arrange
        when(registroIPRepository.countRegistrosPruebaByIP(eq(ipAddress), any(LocalDateTime.class)))
                .thenReturn(3L);

        // Act & Assert
        assertThrows(IpBloqueadaException.class, () ->
                suscripcionService.validarRegistroIP(ipAddress)
        );
    }

    @Test
    @DisplayName("Debe registrar IP correctamente al crear cuenta de prueba")
    void debeRegistrarIP_correctamente() {
        // Arrange
        when(registroIPRepository.save(any(RegistroIP.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        suscripcionService.registrarIP(negocioMock, ipAddress, userAgent);

        // Assert
        verify(registroIPRepository).save(argThat(registro ->
                registro.getNegocio().equals(negocioMock) &&
                registro.getIpAddress().equals(ipAddress) &&
                registro.getUserAgent().equals(userAgent) &&
                registro.isEsPrueba() &&
                registro.isActivo()
        ));
    }

    @Test
    @DisplayName("Debe registrar IP con userAgent Unknown cuando es null")
    void debeRegistrarIP_conUserAgentUnknownCuandoEsNull() {
        // Arrange
        when(registroIPRepository.save(any(RegistroIP.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        suscripcionService.registrarIP(negocioMock, ipAddress, null);

        // Assert
        verify(registroIPRepository).save(argThat(registro ->
                registro.getUserAgent().equals("Unknown")
        ));
    }

    @Test
    @DisplayName("Debe permitir acceso cuando cuenta está activa y en periodo de prueba")
    void debePermitirAcceso_cuandoCuentaActivaYEnPrueba() {
        // Arrange
        when(usuarioRepository.findByEmail(usuarioMock.getEmail()))
                .thenReturn(Optional.of(usuarioMock));

        // Act & Assert
        assertDoesNotThrow(() -> suscripcionService.validarAcceso(usuarioMock.getEmail()));
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando periodo de prueba ha vencido")
    void debeLanzarExcepcion_cuandoPeriodoPruebaVencido() {
        // Arrange
        negocioMock.setFechaFinPrueba(LocalDateTime.now().minusDays(1));
        negocioMock.setCuentaActiva(false);

        when(usuarioRepository.findByEmail(usuarioMock.getEmail()))
                .thenReturn(Optional.of(usuarioMock));

        // Act & Assert
        SuscripcionVencidaException exception = assertThrows(
                SuscripcionVencidaException.class,
                () -> suscripcionService.validarAcceso(usuarioMock.getEmail())
        );

        assertTrue(exception.getMessage().contains("periodo de prueba ha vencido"));
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando suscripción está vencida")
    void debeLanzarExcepcion_cuandoSuscripcionVencida() {
        // Arrange
        negocioMock.setEnPeriodoPrueba(false);
        negocioMock.setEstadoPago("vencido");

        when(usuarioRepository.findByEmail(usuarioMock.getEmail()))
                .thenReturn(Optional.of(usuarioMock));

        // Act & Assert
        SuscripcionVencidaException exception = assertThrows(
                SuscripcionVencidaException.class,
                () -> suscripcionService.validarAcceso(usuarioMock.getEmail())
        );

        assertTrue(exception.getMessage().contains("suscripción ha vencido"));
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando cuenta está suspendida")
    void debeLanzarExcepcion_cuandoCuentaSuspendida() {
        // Arrange
        negocioMock.setEnPeriodoPrueba(false);
        negocioMock.setEstadoPago("suspendido");

        when(usuarioRepository.findByEmail(usuarioMock.getEmail()))
                .thenReturn(Optional.of(usuarioMock));

        // Act & Assert
        SuscripcionVencidaException exception = assertThrows(
                SuscripcionVencidaException.class,
                () -> suscripcionService.validarAcceso(usuarioMock.getEmail())
        );

        assertTrue(exception.getMessage().contains("suspendida"));
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando pago está pendiente")
    void debeLanzarExcepcion_cuandoPagoPendiente() {
        // Arrange
        negocioMock.setEnPeriodoPrueba(false);
        negocioMock.setEstadoPago("pendiente_pago");

        when(usuarioRepository.findByEmail(usuarioMock.getEmail()))
                .thenReturn(Optional.of(usuarioMock));

        // Act & Assert
        SuscripcionVencidaException exception = assertThrows(
                SuscripcionVencidaException.class,
                () -> suscripcionService.validarAcceso(usuarioMock.getEmail())
        );

        assertTrue(exception.getMessage().contains("completar el pago"));
    }

    @Test
    @DisplayName("Debe obtener información de suscripción correctamente")
    void debeObtenerInfoSuscripcion_correctamente() {
        // Arrange
        when(usuarioRepository.findByEmailWithNegocio(usuarioMock.getEmail()))
                .thenReturn(Optional.of(usuarioMock));

        // Act
        SuscripcionInfo resultado = suscripcionService.obtenerInfo(usuarioMock.getEmail());

        // Assert
        assertNotNull(resultado);
        assertEquals(negocioMock.getPlan(), resultado.getPlan());
        assertEquals(negocioMock.getEstadoPago(), resultado.getEstadoPago());
        assertTrue(resultado.isEnPeriodoPrueba());
        assertTrue(resultado.isCuentaActiva());
        assertTrue(resultado.isPuedeUsarSistema());
    }

    @Test
    @DisplayName("Debe activar suscripción correctamente después de pago")
    void debeActivarSuscripcion_despuesDePago() {
        // Arrange
        String plan = "profesional";
        negocioMock.setEnPeriodoPrueba(true);
        negocioMock.setEstadoPago("pendiente_pago");

        when(negocioRepository.findById(negocioId))
                .thenReturn(Optional.of(negocioMock));
        when(negocioRepository.save(any(Negocio.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(registroIPRepository.findByIpAddress(negocioMock.getEmail()))
                .thenReturn(Arrays.asList());

        // Act
        suscripcionService.activarSuscripcion(negocioId.toString(), plan);

        // Assert
        verify(negocioRepository).save(argThat(negocio ->
                negocio.getEstadoPago().equals("activo") &&
                !negocio.isEnPeriodoPrueba() &&
                negocio.isCuentaActiva() &&
                negocio.getPlan().equals(plan) &&
                negocio.getFechaProximoCobro() != null
        ));
    }

    @Test
    @DisplayName("Debe enviar notificaciones de fin de prueba")
    void debeEnviarNotificacionFinPrueba() {
        // Arrange
        negocioMock.setFechaFinPrueba(LocalDateTime.now().plusHours(25));
        negocioMock.setNotificacionPruebaEnviada(false);

        when(negocioRepository.findAll()).thenReturn(Arrays.asList(negocioMock));
        when(negocioRepository.save(any(Negocio.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(emailService.enviarEmailFinPrueba(anyString(), anyString(), anyString())).thenReturn(true);

        // Act
        suscripcionService.enviarNotificaciones();

        // Assert
        verify(emailService).enviarEmailFinPrueba(
                eq(negocioMock.getEmail()),
                anyString(),
                anyString()
        );
        verify(negocioRepository).save(argThat(negocio ->
                negocio.isNotificacionPruebaEnviada()
        ));
    }

    @Test
    @DisplayName("Debe enviar notificaciones de vencimiento de suscripción")
    void debeEnviarNotificacionVencimiento() {
        // Arrange
        negocioMock.setEnPeriodoPrueba(false);
        negocioMock.setPlan("profesional");
        negocioMock.setFechaProximoCobro(LocalDateTime.now().plusDays(5));
        negocioMock.setNotificacionVencimientoEnviada(false);

        when(negocioRepository.findAll()).thenReturn(Arrays.asList(negocioMock));
        when(negocioRepository.save(any(Negocio.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(emailService.enviarEmail(anyString(), anyString(), anyString())).thenReturn(true);

        // Act
        suscripcionService.enviarNotificaciones();

        // Assert
        verify(emailService).enviarEmail(
                eq(negocioMock.getEmail()),
                contains("vence en"),
                anyString()
        );
        verify(negocioRepository).save(argThat(negocio ->
                negocio.isNotificacionVencimientoEnviada()
        ));
    }

    @Test
    @DisplayName("Debe desactivar cuenta cuando periodo de prueba vence")
    void debeDesactivarCuenta_cuandoPeriodoPruebaVence() {
        // Arrange
        negocioMock.setFechaFinPrueba(LocalDateTime.now().minusDays(1));
        negocioMock.setCuentaActiva(true);
        negocioMock.setEnPeriodoPrueba(true);

        when(negocioRepository.findAll()).thenReturn(Arrays.asList(negocioMock));
        when(negocioRepository.save(any(Negocio.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(registroIPRepository.findByIpAddress(negocioMock.getEmail()))
                .thenReturn(Arrays.asList());

        // Act
        suscripcionService.verificarSuscripcionesVencidas();

        // Assert
        verify(negocioRepository).save(argThat(negocio ->
                !negocio.isCuentaActiva() &&
                negocio.getEstadoPago().equals("vencido")
        ));
    }

    @Test
    @DisplayName("Debe desactivar cuenta cuando suscripción mensual vence")
    void debeDesactivarCuenta_cuandoSuscripcionMensualVence() {
        // Arrange
        negocioMock.setEnPeriodoPrueba(false);
        negocioMock.setEstadoPago("activo");
        negocioMock.setPlan("profesional");
        negocioMock.setCuentaActiva(true);
        negocioMock.setFechaProximoCobro(LocalDateTime.now().minusDays(1));

        when(negocioRepository.findAll()).thenReturn(Arrays.asList(negocioMock));
        when(negocioRepository.save(any(Negocio.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(emailService.enviarEmail(anyString(), anyString(), anyString())).thenReturn(true);

        // Act
        suscripcionService.verificarSuscripcionesVencidas();

        // Assert
        verify(negocioRepository).save(argThat(negocio ->
                !negocio.isCuentaActiva() &&
                negocio.getEstadoPago().equals("vencido")
        ));
        verify(emailService).enviarEmail(
                eq(negocioMock.getEmail()),
                contains("vencido"),
                anyString()
        );
    }

    @Test
    @DisplayName("No debe desactivar cuenta cuando suscripción está vigente")
    void noDebeDesactivarCuenta_cuandoSuscripcionVigente() {
        // Arrange
        negocioMock.setEnPeriodoPrueba(false);
        negocioMock.setEstadoPago("activo");
        negocioMock.setPlan("profesional");
        negocioMock.setCuentaActiva(true);
        negocioMock.setFechaProximoCobro(LocalDateTime.now().plusDays(15));

        when(negocioRepository.findAll()).thenReturn(Arrays.asList(negocioMock));

        // Act
        suscripcionService.verificarSuscripcionesVencidas();

        // Assert
        verify(negocioRepository, never()).save(any(Negocio.class));
    }

    @Test
    @DisplayName("Debe manejar errores al enviar notificaciones sin interrumpir el proceso")
    void debeManejarErrores_alEnviarNotificaciones() {
        // Arrange
        negocioMock.setFechaFinPrueba(LocalDateTime.now().plusHours(25));
        negocioMock.setNotificacionPruebaEnviada(false);

        when(negocioRepository.findAll()).thenReturn(Arrays.asList(negocioMock));
        lenient().doThrow(new RuntimeException("Error de red"))
                .when(emailService).enviarEmail(anyString(), anyString(), anyString());

        // Act & Assert
        assertDoesNotThrow(() -> suscripcionService.enviarNotificaciones());
    }

    @Test
    @DisplayName("Debe manejar errores al verificar suscripciones sin interrumpir el proceso")
    void debeManejarErrores_alVerificarSuscripciones() {
        // Arrange
        negocioMock.setFechaFinPrueba(LocalDateTime.now().minusDays(1));
        negocioMock.setCuentaActiva(true);

        when(negocioRepository.findAll()).thenReturn(Arrays.asList(negocioMock));
        when(negocioRepository.save(any(Negocio.class)))
                .thenThrow(new RuntimeException("Error de base de datos"));

        // Act & Assert
        assertDoesNotThrow(() -> suscripcionService.verificarSuscripcionesVencidas());
    }

    @Test
    @DisplayName("Debe actualizar registros de IP cuando se activa suscripción")
    void debeActualizarRegistrosIP_cuandoSeActivaSuscripcion() {
        // Arrange
        String plan = "profesional";
        RegistroIP registroMock = RegistroIP.builder()
                .id(UUID.randomUUID())
                .negocio(negocioMock)
                .ipAddress(ipAddress)
                .esPrueba(true)
                .activo(true)
                .build();

        when(negocioRepository.findById(negocioId))
                .thenReturn(Optional.of(negocioMock));
        when(negocioRepository.save(any(Negocio.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(registroIPRepository.findByIpAddress(negocioMock.getEmail()))
                .thenReturn(Arrays.asList(registroMock));
        when(registroIPRepository.save(any(RegistroIP.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        suscripcionService.activarSuscripcion(negocioId.toString(), plan);

        // Assert
        verify(registroIPRepository).save(argThat(registro ->
                !registro.isEsPrueba()
        ));
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando usuario no existe al validar acceso")
    void debeLanzarExcepcion_cuandoUsuarioNoExisteAlValidarAcceso() {
        // Arrange
        when(usuarioRepository.findByEmail("noexiste@test.com"))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () ->
                suscripcionService.validarAcceso("noexiste@test.com")
        );
    }

    @Test
    @DisplayName("Debe resetear banderas de notificaciones al activar suscripción")
    void debeResetearBanderas_alActivarSuscripcion() {
        // Arrange
        String plan = "profesional";
        negocioMock.setNotificacionPruebaEnviada(true);
        negocioMock.setNotificacionVencimientoEnviada(true);

        when(negocioRepository.findById(negocioId))
                .thenReturn(Optional.of(negocioMock));
        when(negocioRepository.save(any(Negocio.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(registroIPRepository.findByIpAddress(negocioMock.getEmail()))
                .thenReturn(Arrays.asList());

        // Act
        suscripcionService.activarSuscripcion(negocioId.toString(), plan);

        // Assert
        verify(negocioRepository).save(argThat(negocio ->
                !negocio.isNotificacionPruebaEnviada() &&
                !negocio.isNotificacionVencimientoEnviada()
        ));
    }

    @Test
    @DisplayName("Debe establecer fecha de próximo cobro en 30 días al activar suscripción")
    void debeEstablecerFechaProximoCobro_alActivarSuscripcion() {
        // Arrange
        String plan = "profesional";
        LocalDateTime ahora = LocalDateTime.now();

        when(negocioRepository.findById(negocioId))
                .thenReturn(Optional.of(negocioMock));
        when(negocioRepository.save(any(Negocio.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(registroIPRepository.findByIpAddress(negocioMock.getEmail()))
                .thenReturn(Arrays.asList());

        // Act
        suscripcionService.activarSuscripcion(negocioId.toString(), plan);

        // Assert
        verify(negocioRepository).save(argThat(negocio -> {
            LocalDateTime fechaCobro = negocio.getFechaProximoCobro();
            return fechaCobro != null &&
                   fechaCobro.isAfter(ahora.plusDays(29)) &&
                   fechaCobro.isBefore(ahora.plusDays(31));
        }));
    }
}
