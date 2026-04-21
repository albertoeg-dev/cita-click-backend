package com.reservas.service;

import com.reservas.dto.CambiarRolRequest;
import com.reservas.dto.InvitarUsuarioRequest;
import com.reservas.dto.UsuarioDTO;
import com.reservas.entity.Negocio;
import com.reservas.entity.Usuario;
import com.reservas.entity.enums.TipoPlan;
import com.reservas.entity.enums.UsuarioRol;
import com.reservas.exception.LimiteExcedidoException;
import com.reservas.exception.PermisoInsuficienteException;
import com.reservas.exception.ResourceNotFoundException;
import com.reservas.repository.NegocioRepository;
import com.reservas.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsuarioService - Pruebas Unitarias")
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private NegocioRepository negocioRepository;

    @Mock
    private PlanLimitesService planLimitesService;

    @Mock
    private PermisosService permisosService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UsuarioService usuarioService;

    private Negocio negocioMock;
    private Usuario usuarioOwnerMock;
    private Usuario usuarioAdminMock;
    private InvitarUsuarioRequest invitarRequest;
    private UUID negocioId;

    @BeforeEach
    void setUp() {
        negocioId = UUID.randomUUID();

        negocioMock = Negocio.builder()
                .id(negocioId)
                .nombre("Salon Test")
                .email("salon@test.com")
                .tipo("salon")
                .plan("profesional")
                .estadoPago("activo")
                .build();

        usuarioOwnerMock = Usuario.builder()
                .id(UUID.randomUUID())
                .email("owner@test.com")
                .nombre("Owner")
                .apellidoPaterno("Test")
                .rol(UsuarioRol.OWNER.getCodigo())
                .negocio(negocioMock)
                .activo(true)
                .authProvider("local")
                .build();

        usuarioAdminMock = Usuario.builder()
                .id(UUID.randomUUID())
                .email("admin@test.com")
                .nombre("Admin")
                .apellidoPaterno("Test")
                .rol(UsuarioRol.ADMIN.getCodigo())
                .negocio(negocioMock)
                .activo(true)
                .authProvider("local")
                .build();

        invitarRequest = new InvitarUsuarioRequest();
        invitarRequest.setNombre("Nuevo");
        invitarRequest.setApellidoPaterno("Usuario");
        invitarRequest.setEmail("nuevo@test.com");
        invitarRequest.setTelefono("+525512345678");
        invitarRequest.setRol(UsuarioRol.EMPLEADO.getCodigo());
    }

    @Test
    @DisplayName("Debe invitar usuario correctamente cuando todo es válido")
    void debeInvitarUsuario_cuandoTodoEsValido() {
        // Arrange
        when(usuarioRepository.findByEmail(usuarioOwnerMock.getEmail()))
                .thenReturn(Optional.of(usuarioOwnerMock));
        when(negocioRepository.findById(negocioId))
                .thenReturn(Optional.of(negocioMock));
        when(usuarioRepository.findByNegocioAndEmailAndActivo(negocioMock, invitarRequest.getEmail(), true))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded_password");
        when(usuarioRepository.save(any(Usuario.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        doNothing().when(permisosService).validarPermiso(usuarioOwnerMock, "INVITAR_USUARIOS");
        doNothing().when(permisosService).validarGestionRol(usuarioOwnerMock, invitarRequest.getRol());
        doNothing().when(planLimitesService).validarLimiteUsuarios(negocioId, TipoPlan.BASE);
        doNothing().when(planLimitesService).actualizarUso(negocioId);

        // Act
        UsuarioDTO resultado = usuarioService.invitarUsuario(invitarRequest, usuarioOwnerMock.getEmail());

        // Assert
        assertNotNull(resultado);
        assertEquals(invitarRequest.getNombre(), resultado.getNombre());
        assertEquals(invitarRequest.getEmail(), resultado.getEmail());
        assertEquals(invitarRequest.getRol(), resultado.getRol());
        assertTrue(resultado.isActivo());

        verify(usuarioRepository).save(any(Usuario.class));
        verify(planLimitesService).actualizarUso(negocioId);
        verify(emailService).enviarEmailInvitacionUsuario(
                eq(invitarRequest.getEmail()),
                eq(invitarRequest.getNombre()),
                eq(negocioMock.getNombre()),
                anyString()
        );
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando usuario actual no existe")
    void debeLanzarExcepcion_cuandoUsuarioActualNoExiste() {
        // Arrange
        when(usuarioRepository.findByEmail("noexiste@test.com"))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () ->
                usuarioService.invitarUsuario(invitarRequest, "noexiste@test.com")
        );

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando se excede límite de usuarios")
    void debeLanzarExcepcion_cuandoExcedeLimiteUsuarios() {
        // Arrange
        when(usuarioRepository.findByEmail(usuarioOwnerMock.getEmail()))
                .thenReturn(Optional.of(usuarioOwnerMock));
        when(negocioRepository.findById(negocioId))
                .thenReturn(Optional.of(negocioMock));

        doNothing().when(permisosService).validarPermiso(usuarioOwnerMock, "INVITAR_USUARIOS");
        doNothing().when(permisosService).validarGestionRol(usuarioOwnerMock, invitarRequest.getRol());
        doThrow(new LimiteExcedidoException("Límite de usuarios excedido"))
                .when(planLimitesService).validarLimiteUsuarios(negocioId, TipoPlan.BASE);

        // Act & Assert
        assertThrows(LimiteExcedidoException.class, () ->
                usuarioService.invitarUsuario(invitarRequest, usuarioOwnerMock.getEmail())
        );

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando email ya existe en el negocio")
    void debeLanzarExcepcion_cuandoEmailYaExiste() {
        // Arrange
        when(usuarioRepository.findByEmail(usuarioOwnerMock.getEmail()))
                .thenReturn(Optional.of(usuarioOwnerMock));
        when(negocioRepository.findById(negocioId))
                .thenReturn(Optional.of(negocioMock));
        when(usuarioRepository.findByNegocioAndEmailAndActivo(negocioMock, invitarRequest.getEmail(), true))
                .thenReturn(Optional.of(usuarioAdminMock));

        doNothing().when(permisosService).validarPermiso(usuarioOwnerMock, "INVITAR_USUARIOS");
        doNothing().when(permisosService).validarGestionRol(usuarioOwnerMock, invitarRequest.getRol());
        doNothing().when(planLimitesService).validarLimiteUsuarios(negocioId, TipoPlan.BASE);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                usuarioService.invitarUsuario(invitarRequest, usuarioOwnerMock.getEmail())
        );

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando usuario no tiene permisos para invitar")
    void debeLanzarExcepcion_cuandoNoTienePermisosParaInvitar() {
        // Arrange
        when(usuarioRepository.findByEmail(usuarioAdminMock.getEmail()))
                .thenReturn(Optional.of(usuarioAdminMock));

        doThrow(new PermisoInsuficienteException("Permiso insuficiente"))
                .when(permisosService).validarPermiso(usuarioAdminMock, "INVITAR_USUARIOS");

        // Act & Assert
        assertThrows(PermisoInsuficienteException.class, () ->
                usuarioService.invitarUsuario(invitarRequest, usuarioAdminMock.getEmail())
        );

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Debe cambiar rol de usuario correctamente")
    void debeCambiarRol_cuandoTodoEsValido() {
        // Arrange
        CambiarRolRequest cambiarRolRequest = new CambiarRolRequest();
        cambiarRolRequest.setRol(UsuarioRol.ADMIN.getCodigo());

        Usuario usuarioEmpleado = Usuario.builder()
                .id(UUID.randomUUID())
                .email("empleado@test.com")
                .nombre("Empleado")
                .rol(UsuarioRol.EMPLEADO.getCodigo())
                .negocio(negocioMock)
                .activo(true)
                .build();

        when(usuarioRepository.findByEmail(usuarioOwnerMock.getEmail()))
                .thenReturn(Optional.of(usuarioOwnerMock));
        when(usuarioRepository.findById(usuarioEmpleado.getId()))
                .thenReturn(Optional.of(usuarioEmpleado));
        when(usuarioRepository.save(any(Usuario.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        doNothing().when(permisosService).validarPermiso(usuarioOwnerMock, "CAMBIAR_ROL_USUARIOS");
        doNothing().when(permisosService).validarGestionRol(usuarioOwnerMock, usuarioEmpleado.getRol());
        doNothing().when(permisosService).validarGestionRol(usuarioOwnerMock, cambiarRolRequest.getRol());

        // Act
        UsuarioDTO resultado = usuarioService.cambiarRol(
                usuarioEmpleado.getId(),
                cambiarRolRequest,
                usuarioOwnerMock.getEmail()
        );

        // Assert
        assertNotNull(resultado);
        assertEquals(UsuarioRol.ADMIN.getCodigo(), resultado.getRol());
        verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando usuario objetivo es de otro negocio")
    void debeLanzarExcepcion_cuandoUsuarioEsDeOtroNegocio() {
        // Arrange
        Negocio otroNegocio = Negocio.builder()
                .id(UUID.randomUUID())
                .nombre("Otro Negocio")
                .build();

        Usuario usuarioOtroNegocio = Usuario.builder()
                .id(UUID.randomUUID())
                .email("otro@test.com")
                .negocio(otroNegocio)
                .rol(UsuarioRol.EMPLEADO.getCodigo())
                .build();

        CambiarRolRequest cambiarRolRequest = new CambiarRolRequest();
        cambiarRolRequest.setRol(UsuarioRol.ADMIN.getCodigo());

        when(usuarioRepository.findByEmail(usuarioOwnerMock.getEmail()))
                .thenReturn(Optional.of(usuarioOwnerMock));
        when(usuarioRepository.findById(usuarioOtroNegocio.getId()))
                .thenReturn(Optional.of(usuarioOtroNegocio));

        doNothing().when(permisosService).validarPermiso(usuarioOwnerMock, "CAMBIAR_ROL_USUARIOS");

        // Act & Assert
        assertThrows(PermisoInsuficienteException.class, () ->
                usuarioService.cambiarRol(
                        usuarioOtroNegocio.getId(),
                        cambiarRolRequest,
                        usuarioOwnerMock.getEmail()
                )
        );

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando owner intenta cambiar su propio rol")
    void debeLanzarExcepcion_cuandoOwnerCambiaSuPropioRol() {
        // Arrange
        CambiarRolRequest cambiarRolRequest = new CambiarRolRequest();
        cambiarRolRequest.setRol(UsuarioRol.ADMIN.getCodigo());

        when(usuarioRepository.findByEmail(usuarioOwnerMock.getEmail()))
                .thenReturn(Optional.of(usuarioOwnerMock));
        when(usuarioRepository.findById(usuarioOwnerMock.getId()))
                .thenReturn(Optional.of(usuarioOwnerMock));
        when(permisosService.esOwner(usuarioOwnerMock)).thenReturn(true);

        doNothing().when(permisosService).validarPermiso(usuarioOwnerMock, "CAMBIAR_ROL_USUARIOS");
        doNothing().when(permisosService).validarGestionRol(usuarioOwnerMock, usuarioOwnerMock.getRol());
        doNothing().when(permisosService).validarGestionRol(usuarioOwnerMock, cambiarRolRequest.getRol());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                usuarioService.cambiarRol(
                        usuarioOwnerMock.getId(),
                        cambiarRolRequest,
                        usuarioOwnerMock.getEmail()
                )
        );

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Debe desactivar usuario correctamente")
    void debeDesactivarUsuario_cuandoTodoEsValido() {
        // Arrange
        Usuario usuarioEmpleado = Usuario.builder()
                .id(UUID.randomUUID())
                .email("empleado@test.com")
                .nombre("Empleado")
                .rol(UsuarioRol.EMPLEADO.getCodigo())
                .negocio(negocioMock)
                .activo(true)
                .build();

        when(usuarioRepository.findByEmail(usuarioOwnerMock.getEmail()))
                .thenReturn(Optional.of(usuarioOwnerMock));
        when(usuarioRepository.findById(usuarioEmpleado.getId()))
                .thenReturn(Optional.of(usuarioEmpleado));
        when(usuarioRepository.save(any(Usuario.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        doNothing().when(permisosService).validarPermiso(usuarioOwnerMock, "DESACTIVAR_USUARIOS");
        doNothing().when(permisosService).validarGestionRol(usuarioOwnerMock, usuarioEmpleado.getRol());
        doNothing().when(planLimitesService).actualizarUso(negocioId);

        // Act
        usuarioService.desactivarUsuario(usuarioEmpleado.getId(), usuarioOwnerMock.getEmail());

        // Assert
        verify(usuarioRepository).save(argThat(usuario ->
                !usuario.isActivo() && usuario.getId().equals(usuarioEmpleado.getId())
        ));
        verify(planLimitesService).actualizarUso(negocioId);
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando owner intenta desactivarse a sí mismo")
    void debeLanzarExcepcion_cuandoOwnerSeDesactivaASiMismo() {
        // Arrange
        when(usuarioRepository.findByEmail(usuarioOwnerMock.getEmail()))
                .thenReturn(Optional.of(usuarioOwnerMock));
        when(usuarioRepository.findById(usuarioOwnerMock.getId()))
                .thenReturn(Optional.of(usuarioOwnerMock));
        when(permisosService.esOwner(usuarioOwnerMock)).thenReturn(true);

        doNothing().when(permisosService).validarPermiso(usuarioOwnerMock, "DESACTIVAR_USUARIOS");
        doNothing().when(permisosService).validarGestionRol(usuarioOwnerMock, usuarioOwnerMock.getRol());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                usuarioService.desactivarUsuario(usuarioOwnerMock.getId(), usuarioOwnerMock.getEmail())
        );

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando se intenta desactivar al único owner")
    void debeLanzarExcepcion_cuandoSeDesactivaUnicoOwner() {
        // Arrange
        when(usuarioRepository.findByEmail(usuarioOwnerMock.getEmail()))
                .thenReturn(Optional.of(usuarioOwnerMock));
        when(usuarioRepository.findById(usuarioOwnerMock.getId()))
                .thenReturn(Optional.of(usuarioOwnerMock));
        when(permisosService.esOwner(usuarioOwnerMock)).thenReturn(true);

        doNothing().when(permisosService).validarPermiso(usuarioOwnerMock, "DESACTIVAR_USUARIOS");
        doNothing().when(permisosService).validarGestionRol(usuarioOwnerMock, usuarioOwnerMock.getRol());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                usuarioService.desactivarUsuario(usuarioOwnerMock.getId(), usuarioOwnerMock.getEmail())
        );

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Debe listar usuarios del negocio correctamente")
    void debeListarUsuarios_delNegocio() {
        // Arrange
        List<Usuario> usuarios = Arrays.asList(usuarioOwnerMock, usuarioAdminMock);

        when(usuarioRepository.findByEmailWithNegocio(usuarioOwnerMock.getEmail()))
                .thenReturn(Optional.of(usuarioOwnerMock));
        when(negocioRepository.findById(negocioId))
                .thenReturn(Optional.of(negocioMock));
        when(usuarioRepository.findByNegocioAndActivo(negocioMock, true))
                .thenReturn(usuarios);

        // Act
        List<UsuarioDTO> resultado = usuarioService.listarUsuariosPorNegocio(usuarioOwnerMock.getEmail());

        // Assert
        assertNotNull(resultado);
        assertEquals(2, resultado.size());
        verify(usuarioRepository).findByNegocioAndActivo(negocioMock, true);
    }

    @Test
    @DisplayName("Debe obtener usuario por ID correctamente")
    void debeObtenerUsuarioPorId_cuandoEsDelMismoNegocio() {
        // Arrange
        when(usuarioRepository.findByEmailWithNegocio(usuarioOwnerMock.getEmail()))
                .thenReturn(Optional.of(usuarioOwnerMock));
        when(usuarioRepository.findById(usuarioAdminMock.getId()))
                .thenReturn(Optional.of(usuarioAdminMock));

        // Act
        UsuarioDTO resultado = usuarioService.obtenerUsuarioPorId(
                usuarioAdminMock.getId(),
                usuarioOwnerMock.getEmail()
        );

        // Assert
        assertNotNull(resultado);
        assertEquals(usuarioAdminMock.getEmail(), resultado.getEmail());
        assertEquals(usuarioAdminMock.getNombre(), resultado.getNombre());
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando se intenta obtener usuario de otro negocio")
    void debeLanzarExcepcion_cuandoObtenerUsuarioDeOtroNegocio() {
        // Arrange
        Negocio otroNegocio = Negocio.builder()
                .id(UUID.randomUUID())
                .nombre("Otro Negocio")
                .build();

        Usuario usuarioOtroNegocio = Usuario.builder()
                .id(UUID.randomUUID())
                .email("otro@test.com")
                .negocio(otroNegocio)
                .build();

        when(usuarioRepository.findByEmailWithNegocio(usuarioOwnerMock.getEmail()))
                .thenReturn(Optional.of(usuarioOwnerMock));
        when(usuarioRepository.findById(usuarioOtroNegocio.getId()))
                .thenReturn(Optional.of(usuarioOtroNegocio));

        // Act & Assert
        assertThrows(PermisoInsuficienteException.class, () ->
                usuarioService.obtenerUsuarioPorId(
                        usuarioOtroNegocio.getId(),
                        usuarioOwnerMock.getEmail()
                )
        );
    }

    @Test
    @DisplayName("Debe activar usuario desactivado correctamente")
    void debeActivarUsuario_cuandoEstaDesactivado() {
        // Arrange
        Usuario usuarioInactivo = Usuario.builder()
                .id(UUID.randomUUID())
                .email("inactivo@test.com")
                .nombre("Inactivo")
                .rol(UsuarioRol.EMPLEADO.getCodigo())
                .negocio(negocioMock)
                .activo(false)
                .build();

        when(usuarioRepository.findByEmail(usuarioOwnerMock.getEmail()))
                .thenReturn(Optional.of(usuarioOwnerMock));
        when(usuarioRepository.findById(usuarioInactivo.getId()))
                .thenReturn(Optional.of(usuarioInactivo));
        when(usuarioRepository.save(any(Usuario.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        doNothing().when(permisosService).validarPermiso(usuarioOwnerMock, "INVITAR_USUARIOS");
        doNothing().when(permisosService).validarGestionRol(usuarioOwnerMock, usuarioInactivo.getRol());
        doNothing().when(planLimitesService).validarLimiteUsuarios(negocioId, TipoPlan.BASE);
        doNothing().when(planLimitesService).actualizarUso(negocioId);

        // Act
        UsuarioDTO resultado = usuarioService.activarUsuario(
                usuarioInactivo.getId(),
                usuarioOwnerMock.getEmail()
        );

        // Assert
        assertNotNull(resultado);
        assertTrue(resultado.isActivo());
        verify(usuarioRepository).save(argThat(usuario -> usuario.isActivo()));
        verify(planLimitesService).actualizarUso(negocioId);
    }

    @Test
    @DisplayName("Debe lanzar excepción al activar usuario si se excede límite")
    void debeLanzarExcepcion_alActivarUsuarioSiExcedeLimite() {
        // Arrange
        Usuario usuarioInactivo = Usuario.builder()
                .id(UUID.randomUUID())
                .email("inactivo@test.com")
                .nombre("Inactivo")
                .rol(UsuarioRol.EMPLEADO.getCodigo())
                .negocio(negocioMock)
                .activo(false)
                .build();

        when(usuarioRepository.findByEmail(usuarioOwnerMock.getEmail()))
                .thenReturn(Optional.of(usuarioOwnerMock));
        when(usuarioRepository.findById(usuarioInactivo.getId()))
                .thenReturn(Optional.of(usuarioInactivo));

        doNothing().when(permisosService).validarPermiso(usuarioOwnerMock, "INVITAR_USUARIOS");
        doNothing().when(permisosService).validarGestionRol(usuarioOwnerMock, usuarioInactivo.getRol());
        doThrow(new LimiteExcedidoException("Límite de usuarios excedido"))
                .when(planLimitesService).validarLimiteUsuarios(negocioId, TipoPlan.BASE);

        // Act & Assert
        assertThrows(LimiteExcedidoException.class, () ->
                usuarioService.activarUsuario(usuarioInactivo.getId(), usuarioOwnerMock.getEmail())
        );

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando rol no es válido al invitar")
    void debeLanzarExcepcion_cuandoRolNoEsValido() {
        // Arrange
        invitarRequest.setRol("ROL_INVALIDO");

        when(usuarioRepository.findByEmail(usuarioOwnerMock.getEmail()))
                .thenReturn(Optional.of(usuarioOwnerMock));
        when(negocioRepository.findById(negocioId))
                .thenReturn(Optional.of(negocioMock));
        when(usuarioRepository.findByNegocioAndEmailAndActivo(negocioMock, invitarRequest.getEmail(), true))
                .thenReturn(Optional.empty());

        doNothing().when(permisosService).validarPermiso(usuarioOwnerMock, "INVITAR_USUARIOS");
        doNothing().when(permisosService).validarGestionRol(usuarioOwnerMock, invitarRequest.getRol());
        doNothing().when(planLimitesService).validarLimiteUsuarios(negocioId, TipoPlan.BASE);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                usuarioService.invitarUsuario(invitarRequest, usuarioOwnerMock.getEmail())
        );

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }
}
