package com.reservas.service;

import com.reservas.dto.PlanLimitesDTO;
import com.reservas.dto.UsoNegocioDTO;
import com.reservas.entity.Negocio;
import com.reservas.entity.PlanLimites;
import com.reservas.entity.UsoNegocio;
import com.reservas.entity.Usuario;
import com.reservas.entity.enums.TipoPlan;
import com.reservas.exception.LimiteExcedidoException;
import com.reservas.exception.ResourceNotFoundException;
import com.reservas.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para PlanLimitesService
 */
@ExtendWith(MockitoExtension.class)
class PlanLimitesServiceTest {

    @Mock
    private PlanLimitesRepository planLimitesRepository;

    @Mock
    private UsoNegocioRepository usoNegocioRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private CitaRepository citaRepository;

    @Mock
    private ServicioRepository servicioRepository;

    @Mock
    private ModuloNegocioRepository moduloNegocioRepository;

    @InjectMocks
    private PlanLimitesService planLimitesService;

    private UUID negocioId;
    private Negocio negocioTest;
    private Usuario usuarioTest;
    private PlanLimites limitesBasico;
    private PlanLimites limitesProfesional;
    private PlanLimites limitesPremium;
    private UsoNegocio usoNegocio;

    @BeforeEach
    void setUp() {
        negocioId = UUID.randomUUID();

        // Configurar negocio
        negocioTest = new Negocio();
        negocioTest.setId(negocioId);
        negocioTest.setNombre("Negocio Test");
        negocioTest.setEmail("negocio@test.com");
        negocioTest.setPlan("basico");

        // Configurar usuario
        usuarioTest = new Usuario();
        usuarioTest.setId(UUID.randomUUID());
        usuarioTest.setEmail("usuario@test.com");
        usuarioTest.setNombre("Usuario Test");
        usuarioTest.setNegocio(negocioTest);

        // Configurar límites BÁSICO
        limitesBasico = PlanLimites.builder()
                .tipoPlan(TipoPlan.BASICO)
                .maxUsuarios(1)
                .maxClientes(50)
                .maxCitasMes(100)
                .maxServicios(10)
                .emailRecordatoriosHabilitado(false)
                .smsWhatsappHabilitado(false)
                .reportesAvanzadosHabilitado(false)
                .personalizacionEmailHabilitado(false)
                .soportePrioritario(false)
                .build();

        // Configurar límites PROFESIONAL
        limitesProfesional = PlanLimites.builder()
                .tipoPlan(TipoPlan.PROFESIONAL)
                .maxUsuarios(5)
                .maxClientes(100)
                .maxCitasMes(500)
                .maxServicios(30)
                .emailRecordatoriosHabilitado(true)
                .smsWhatsappHabilitado(false)
                .reportesAvanzadosHabilitado(false)
                .personalizacionEmailHabilitado(false)
                .soportePrioritario(false)
                .build();

        // Configurar límites PREMIUM
        limitesPremium = PlanLimites.builder()
                .tipoPlan(TipoPlan.PREMIUM)
                .maxUsuarios(10)
                .maxClientes(-1) // Ilimitado
                .maxCitasMes(-1) // Ilimitado
                .maxServicios(-1) // Ilimitado
                .emailRecordatoriosHabilitado(true)
                .smsWhatsappHabilitado(false)
                .reportesAvanzadosHabilitado(true)
                .personalizacionEmailHabilitado(true)
                .soportePrioritario(true)
                .build();

        // Configurar uso del negocio
        usoNegocio = UsoNegocio.builder()
                .negocio(negocioTest)
                .periodo(UsoNegocio.getPeriodoActual())
                .totalUsuarios(0)
                .totalClientes(0)
                .totalCitasMes(0)
                .totalServicios(0)
                .build();
    }

    @Test
    void inicializarLimites_DeberiaCrearPlanBasicoSiNoExiste() {
        // Given - el servicio ahora inicializa BASE y COMPLETO
        when(planLimitesRepository.existsByTipoPlan(TipoPlan.BASE)).thenReturn(false);
        when(planLimitesRepository.existsByTipoPlan(TipoPlan.COMPLETO)).thenReturn(true);
        when(planLimitesRepository.save(any(PlanLimites.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        planLimitesService.inicializarLimites();

        // Then - verifica que se creó plan BASE con los valores correctos
        verify(planLimitesRepository).save(argThat(plan ->
                plan.getTipoPlan() == TipoPlan.BASE &&
                        plan.getMaxUsuarios() == 1 &&
                        plan.getMaxClientes() == -1 &&  // ilimitado en plan BASE
                        plan.getMaxCitasMes() == 200    // 200 citas/mes en plan BASE
        ));
    }

    @Test
    void obtenerLimites_PlanBasico_DeberiaRetornarLimitesCorrectos() {
        // Given
        when(planLimitesRepository.findByTipoPlan(TipoPlan.BASICO)).thenReturn(Optional.of(limitesBasico));

        // When
        PlanLimites limites = planLimitesService.obtenerLimites(TipoPlan.BASICO);

        // Then
        assertThat(limites).isNotNull();
        assertThat(limites.getTipoPlan()).isEqualTo(TipoPlan.BASICO);
        assertThat(limites.getMaxUsuarios()).isEqualTo(1);
        assertThat(limites.getMaxClientes()).isEqualTo(50);
        assertThat(limites.getMaxCitasMes()).isEqualTo(100);
        assertThat(limites.getMaxServicios()).isEqualTo(10);
        assertThat(limites.isEmailRecordatoriosHabilitado()).isFalse();
    }

    @Test
    void obtenerLimites_PlanProfesional_DeberiaRetornarLimitesCorrectos() {
        // Given
        when(planLimitesRepository.findByTipoPlan(TipoPlan.PROFESIONAL)).thenReturn(Optional.of(limitesProfesional));

        // When
        PlanLimites limites = planLimitesService.obtenerLimites(TipoPlan.PROFESIONAL);

        // Then
        assertThat(limites).isNotNull();
        assertThat(limites.getTipoPlan()).isEqualTo(TipoPlan.PROFESIONAL);
        assertThat(limites.getMaxUsuarios()).isEqualTo(5);
        assertThat(limites.getMaxClientes()).isEqualTo(100);
        assertThat(limites.getMaxCitasMes()).isEqualTo(500);
        assertThat(limites.getMaxServicios()).isEqualTo(30);
        assertThat(limites.isEmailRecordatoriosHabilitado()).isTrue();
    }

    @Test
    void obtenerLimites_PlanPremium_DeberiaRetornarLimitesIlimitados() {
        // Given
        when(planLimitesRepository.findByTipoPlan(TipoPlan.PREMIUM)).thenReturn(Optional.of(limitesPremium));

        // When
        PlanLimites limites = planLimitesService.obtenerLimites(TipoPlan.PREMIUM);

        // Then
        assertThat(limites).isNotNull();
        assertThat(limites.getTipoPlan()).isEqualTo(TipoPlan.PREMIUM);
        assertThat(limites.getMaxUsuarios()).isEqualTo(10);
        assertThat(limites.getMaxClientes()).isEqualTo(-1); // Ilimitado
        assertThat(limites.getMaxCitasMes()).isEqualTo(-1); // Ilimitado
        assertThat(limites.getMaxServicios()).isEqualTo(-1); // Ilimitado
        assertThat(limites.isReportesAvanzadosHabilitado()).isTrue();
        assertThat(limites.isPersonalizacionEmailHabilitado()).isTrue();
        assertThat(limites.isSoportePrioritario()).isTrue();
    }

    @Test
    void obtenerLimites_PlanNoExiste_DeberiaLanzarException() {
        // Given
        when(planLimitesRepository.findByTipoPlan(TipoPlan.BASICO)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> planLimitesService.obtenerLimites(TipoPlan.BASICO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Límites no encontrados");
    }

    @Test
    void obtenerUsoActual_DeberiaRetornarUsoExistente() {
        // Given
        when(usoNegocioRepository.findByNegocioIdAndPeriodo(eq(negocioId), anyString()))
                .thenReturn(Optional.of(usoNegocio));

        // When
        UsoNegocio uso = planLimitesService.obtenerUsoActual(negocioId);

        // Then
        assertThat(uso).isNotNull();
        assertThat(uso.getNegocio().getId()).isEqualTo(negocioId);
        verify(usoNegocioRepository, never()).save(any());
    }

    @Test
    void obtenerUsoActual_NoExisteUso_DeberiaCrearNuevo() {
        // Given
        when(usoNegocioRepository.findByNegocioIdAndPeriodo(eq(negocioId), anyString()))
                .thenReturn(Optional.empty());
        when(usoNegocioRepository.save(any(UsoNegocio.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        UsoNegocio uso = planLimitesService.obtenerUsoActual(negocioId);

        // Then
        assertThat(uso).isNotNull();
        assertThat(uso.getTotalUsuarios()).isZero();
        assertThat(uso.getTotalClientes()).isZero();
        assertThat(uso.getTotalCitasMes()).isZero();
        assertThat(uso.getTotalServicios()).isZero();
        verify(usoNegocioRepository).save(any(UsoNegocio.class));
    }

    @Test
    void actualizarUso_DeberiaActualizarTodosLosContadores() {
        // Given
        when(usoNegocioRepository.findByNegocioIdAndPeriodo(eq(negocioId), anyString()))
                .thenReturn(Optional.of(usoNegocio));
        when(usuarioRepository.countActiveUsuariosByNegocioId(negocioId)).thenReturn(3L);
        when(clienteRepository.countByNegocioId(negocioId)).thenReturn(25L);
        when(servicioRepository.countByNegocioId(negocioId)).thenReturn(5L);
        when(citaRepository.countCitasByNegocioAndMonth(eq(negocioId), anyInt(), anyInt())).thenReturn(45L);
        when(usoNegocioRepository.save(any(UsoNegocio.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        planLimitesService.actualizarUso(negocioId);

        // Then
        verify(usoNegocioRepository).save(argThat(uso ->
                uso.getTotalUsuarios() == 3 &&
                        uso.getTotalClientes() == 25 &&
                        uso.getTotalServicios() == 5 &&
                        uso.getTotalCitasMes() == 45
        ));
    }

    @Test
    void validarLimiteUsuarios_PlanBasico_DentroDelLimite_DeberiaPermitir() {
        // Given
        when(planLimitesRepository.findByTipoPlan(TipoPlan.BASICO)).thenReturn(Optional.of(limitesBasico));
        when(usuarioRepository.countActiveUsuariosByNegocioId(negocioId)).thenReturn(0L);

        // When & Then
        assertThatCode(() -> planLimitesService.validarLimiteUsuarios(negocioId, TipoPlan.BASICO))
                .doesNotThrowAnyException();
    }

    @Test
    void validarLimiteUsuarios_PlanBasico_ExcedeLimite_DeberiaLanzarException() {
        // Given
        when(planLimitesRepository.findByTipoPlan(TipoPlan.BASICO)).thenReturn(Optional.of(limitesBasico));
        when(usuarioRepository.countActiveUsuariosByNegocioId(negocioId)).thenReturn(1L); // Ya tiene 1

        // When & Then
        assertThatThrownBy(() -> planLimitesService.validarLimiteUsuarios(negocioId, TipoPlan.BASICO))
                .isInstanceOf(LimiteExcedidoException.class);
    }

    @Test
    void validarLimiteUsuarios_PlanPremium_Ilimitado_DeberiaPermitir() {
        // Given
        when(planLimitesRepository.findByTipoPlan(TipoPlan.PREMIUM)).thenReturn(Optional.of(limitesPremium));

        // When & Then - No debería consultar el count porque es ilimitado
        assertThatCode(() -> planLimitesService.validarLimiteUsuarios(negocioId, TipoPlan.PREMIUM))
                .doesNotThrowAnyException();
    }

    @Test
    void validarLimiteClientes_PlanBasico_DentroDelLimite_DeberiaPermitir() {
        // Given
        when(planLimitesRepository.findByTipoPlan(TipoPlan.BASICO)).thenReturn(Optional.of(limitesBasico));
        when(clienteRepository.countByNegocioId(negocioId)).thenReturn(25L); // 25 de 50

        // When & Then
        assertThatCode(() -> planLimitesService.validarLimiteClientes(negocioId, TipoPlan.BASICO))
                .doesNotThrowAnyException();
    }

    @Test
    void validarLimiteClientes_PlanBasico_ExcedeLimite_DeberiaLanzarException() {
        // Given
        when(planLimitesRepository.findByTipoPlan(TipoPlan.BASICO)).thenReturn(Optional.of(limitesBasico));
        when(clienteRepository.countByNegocioId(negocioId)).thenReturn(50L); // Ya tiene 50

        // When & Then
        assertThatThrownBy(() -> planLimitesService.validarLimiteClientes(negocioId, TipoPlan.BASICO))
                .isInstanceOf(LimiteExcedidoException.class);
    }

    @Test
    void validarLimiteClientes_PlanPremium_Ilimitado_DeberiaPermitir() {
        // Given
        when(planLimitesRepository.findByTipoPlan(TipoPlan.PREMIUM)).thenReturn(Optional.of(limitesPremium));

        // When & Then
        assertThatCode(() -> planLimitesService.validarLimiteClientes(negocioId, TipoPlan.PREMIUM))
                .doesNotThrowAnyException();
    }

    @Test
    void validarLimiteCitasMes_PlanBasico_DentroDelLimite_DeberiaPermitir() {
        // Given
        usoNegocio.setTotalCitasMes(75); // 75 de 100
        when(planLimitesRepository.findByTipoPlan(TipoPlan.BASICO)).thenReturn(Optional.of(limitesBasico));
        when(usoNegocioRepository.findByNegocioIdAndPeriodo(eq(negocioId), anyString()))
                .thenReturn(Optional.of(usoNegocio));

        // When & Then
        assertThatCode(() -> planLimitesService.validarLimiteCitasMes(negocioId, TipoPlan.BASICO))
                .doesNotThrowAnyException();
    }

    @Test
    void validarLimiteCitasMes_PlanBasico_ExcedeLimite_DeberiaLanzarException() {
        // Given
        usoNegocio.setTotalCitasMes(100); // Ya tiene 100
        when(planLimitesRepository.findByTipoPlan(TipoPlan.BASICO)).thenReturn(Optional.of(limitesBasico));
        when(usoNegocioRepository.findByNegocioIdAndPeriodo(eq(negocioId), anyString()))
                .thenReturn(Optional.of(usoNegocio));

        // When & Then
        assertThatThrownBy(() -> planLimitesService.validarLimiteCitasMes(negocioId, TipoPlan.BASICO))
                .isInstanceOf(LimiteExcedidoException.class);
    }

    @Test
    void validarLimiteCitasMes_PlanPremium_Ilimitado_DeberiaPermitir() {
        // Given
        when(planLimitesRepository.findByTipoPlan(TipoPlan.PREMIUM)).thenReturn(Optional.of(limitesPremium));

        // When & Then
        assertThatCode(() -> planLimitesService.validarLimiteCitasMes(negocioId, TipoPlan.PREMIUM))
                .doesNotThrowAnyException();
    }

    @Test
    void validarLimiteServicios_PlanBasico_DentroDelLimite_DeberiaPermitir() {
        // Given
        when(planLimitesRepository.findByTipoPlan(TipoPlan.BASICO)).thenReturn(Optional.of(limitesBasico));
        when(servicioRepository.countByNegocioId(negocioId)).thenReturn(5L); // 5 de 10

        // When & Then
        assertThatCode(() -> planLimitesService.validarLimiteServicios(negocioId, TipoPlan.BASICO))
                .doesNotThrowAnyException();
    }

    @Test
    void validarLimiteServicios_PlanBasico_ExcedeLimite_DeberiaLanzarException() {
        // Given
        when(planLimitesRepository.findByTipoPlan(TipoPlan.BASICO)).thenReturn(Optional.of(limitesBasico));
        when(servicioRepository.countByNegocioId(negocioId)).thenReturn(10L); // Ya tiene 10

        // When & Then
        assertThatThrownBy(() -> planLimitesService.validarLimiteServicios(negocioId, TipoPlan.BASICO))
                .isInstanceOf(LimiteExcedidoException.class);
    }

    @Test
    void validarLimiteServicios_PlanPremium_Ilimitado_DeberiaPermitir() {
        // Given
        when(planLimitesRepository.findByTipoPlan(TipoPlan.PREMIUM)).thenReturn(Optional.of(limitesPremium));

        // When & Then
        assertThatCode(() -> planLimitesService.validarLimiteServicios(negocioId, TipoPlan.PREMIUM))
                .doesNotThrowAnyException();
    }

    @Test
    void validarFuncionalidadHabilitada_EmailRecordatorios_PlanBasico_DeberiaLanzarException() {
        // Given
        when(planLimitesRepository.findByTipoPlan(TipoPlan.BASICO)).thenReturn(Optional.of(limitesBasico));

        // When & Then
        assertThatThrownBy(() -> planLimitesService.validarFuncionalidadHabilitada(TipoPlan.BASICO, "email"))
                .isInstanceOf(LimiteExcedidoException.class)
                .hasMessageContaining("email");
    }

    @Test
    void validarFuncionalidadHabilitada_EmailRecordatorios_PlanProfesional_DeberiaPermitir() {
        // Given
        when(planLimitesRepository.findByTipoPlan(TipoPlan.PROFESIONAL)).thenReturn(Optional.of(limitesProfesional));

        // When & Then
        assertThatCode(() -> planLimitesService.validarFuncionalidadHabilitada(TipoPlan.PROFESIONAL, "email"))
                .doesNotThrowAnyException();
    }

    @Test
    void validarFuncionalidadHabilitada_ReportesAvanzados_PlanPremium_DeberiaPermitir() {
        // Given
        when(planLimitesRepository.findByTipoPlan(TipoPlan.PREMIUM)).thenReturn(Optional.of(limitesPremium));

        // When & Then
        assertThatCode(() -> planLimitesService.validarFuncionalidadHabilitada(TipoPlan.PREMIUM, "reportes_avanzados"))
                .doesNotThrowAnyException();
    }

    @Test
    void validarFuncionalidadHabilitada_FuncionalidadNoReconocida_DeberiaLanzarException() {
        // Given
        when(planLimitesRepository.findByTipoPlan(TipoPlan.BASICO)).thenReturn(Optional.of(limitesBasico));

        // When & Then
        assertThatThrownBy(() -> planLimitesService.validarFuncionalidadHabilitada(TipoPlan.BASICO, "funcionalidad_inventada"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Funcionalidad no reconocida");
    }

    @Test
    void obtenerLimitesPorEmail_DeberiaRetornarDTO() {
        // Given
        String email = "usuario@test.com";
        when(usuarioRepository.findByEmailWithNegocio(email)).thenReturn(Optional.of(usuarioTest));
        when(planLimitesRepository.findByTipoPlan(TipoPlan.BASE)).thenReturn(Optional.of(limitesBasico));

        // When
        PlanLimitesDTO dto = planLimitesService.obtenerLimitesPorEmail(email);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.getTipoPlan()).isEqualTo("basico");
        assertThat(dto.getMaxUsuarios()).isEqualTo(1);
        assertThat(dto.getMaxClientes()).isEqualTo(50);
        assertThat(dto.getMaxCitasMes()).isEqualTo(100);
        assertThat(dto.isEmailRecordatoriosHabilitado()).isFalse();
    }

    @Test
    void obtenerLimitesPorEmail_UsuarioNoExiste_DeberiaLanzarException() {
        // Given
        String email = "noexiste@test.com";
        when(usuarioRepository.findByEmailWithNegocio(email)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> planLimitesService.obtenerLimitesPorEmail(email))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    @Test
    void obtenerUsoPorEmail_DeberiaRetornarDTOConPorcentajes() {
        // Given
        String email = "usuario@test.com";
        usoNegocio.setTotalUsuarios(0);
        usoNegocio.setTotalClientes(25);
        usoNegocio.setTotalCitasMes(50);
        usoNegocio.setTotalServicios(5);

        when(usuarioRepository.findByEmailWithNegocio(email)).thenReturn(Optional.of(usuarioTest));
        when(planLimitesRepository.findByTipoPlan(TipoPlan.BASE)).thenReturn(Optional.of(limitesBasico));
        when(usoNegocioRepository.findByNegocioIdAndPeriodo(eq(negocioId), anyString()))
                .thenReturn(Optional.of(usoNegocio));
        when(usuarioRepository.countActiveUsuariosByNegocioId(negocioId)).thenReturn(0L);
        when(clienteRepository.countByNegocioId(negocioId)).thenReturn(25L);
        when(citaRepository.countCitasByNegocioAndMonth(eq(negocioId), anyInt(), anyInt())).thenReturn(50L);
        when(servicioRepository.countByNegocioId(negocioId)).thenReturn(5L);
        when(usoNegocioRepository.save(any(UsoNegocio.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        UsoNegocioDTO dto = planLimitesService.obtenerUsoPorEmail(email);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.getTotalClientes()).isEqualTo(25);
        assertThat(dto.getLimiteClientes()).isEqualTo(50);
        assertThat(dto.getTotalCitasMes()).isEqualTo(50);
        assertThat(dto.getLimiteCitasMes()).isEqualTo(100);
        assertThat(dto.getPorcentajeClientes()).isEqualTo(50.0);
        assertThat(dto.getPorcentajeCitasMes()).isEqualTo(50.0);
    }

    @Test
    void validarFuncionalidadPorEmail_EmailRecordatorios_PlanBasico_DeberiaRetornarFalse() {
        // Given
        String email = "usuario@test.com";
        when(usuarioRepository.findByEmailWithNegocio(email)).thenReturn(Optional.of(usuarioTest));
        when(planLimitesRepository.findByTipoPlan(TipoPlan.BASE)).thenReturn(Optional.of(limitesBasico));

        // When
        boolean habilitada = planLimitesService.validarFuncionalidadPorEmail(email, "email");

        // Then
        assertThat(habilitada).isFalse();
    }

    @Test
    void validarFuncionalidadPorEmail_ReportesAvanzados_PlanPremium_DeberiaRetornarTrue() {
        // Given
        String email = "usuario@test.com";
        negocioTest.setPlan("premium");
        when(usuarioRepository.findByEmailWithNegocio(email)).thenReturn(Optional.of(usuarioTest));
        when(planLimitesRepository.findByTipoPlan(TipoPlan.COMPLETO)).thenReturn(Optional.of(limitesPremium));

        // When
        boolean habilitada = planLimitesService.validarFuncionalidadPorEmail(email, "reportes_avanzados");

        // Then
        assertThat(habilitada).isTrue();
    }
}
