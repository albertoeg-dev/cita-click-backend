package com.reservas.service;

import com.reservas.entity.Modulo;
import com.reservas.entity.ModuloNegocio;
import com.reservas.entity.Negocio;
import com.reservas.entity.Usuario;
import com.reservas.exception.NotFoundException;
import com.reservas.repository.ModuloNegocioRepository;
import com.reservas.repository.ModuloRepository;
import com.reservas.repository.NegocioRepository;
import com.reservas.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ModuloActivacionService - Tests unitarios")
class ModuloActivacionServiceTest {

    @Mock private ModuloRepository moduloRepository;
    @Mock private ModuloNegocioRepository moduloNegocioRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private NegocioRepository negocioRepository;

    @InjectMocks
    private ModuloActivacionService moduloActivacionService;

    private UUID negocioId;
    private Negocio negocio;
    private Modulo modulo;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        negocioId = UUID.randomUUID();

        negocio = new Negocio();
        negocio.setId(negocioId);

        modulo = new Modulo();
        modulo.setId(UUID.randomUUID());
        modulo.setClave(ModuloService.EMAIL_RECORDATORIOS);
        modulo.setNombre("Recordatorios Email");
        modulo.setDescripcion("Envíos automáticos de email");
        modulo.setPrecioMensual(new BigDecimal("199.00"));
        modulo.setActivo(true);

        usuario = new Usuario();
        usuario.setEmail("owner@test.com");
        usuario.setNegocio(negocio);
    }

    // ============================================================
    // activarModulo
    // ============================================================

    @Nested
    @DisplayName("activarModulo")
    class ActivarModulo {

        @Test
        @DisplayName("Crea un nuevo ModuloNegocio cuando el módulo no está activo")
        void creaModuloNegocioNuevo() {
            when(negocioRepository.findById(negocioId)).thenReturn(Optional.of(negocio));
            when(moduloRepository.findByClave(ModuloService.EMAIL_RECORDATORIOS))
                    .thenReturn(Optional.of(modulo));
            when(moduloNegocioRepository.findByNegocioIdAndModuloClaveAndActivoTrue(
                    negocioId, ModuloService.EMAIL_RECORDATORIOS))
                    .thenReturn(Optional.empty());

            moduloActivacionService.activarModulo(negocioId, ModuloService.EMAIL_RECORDATORIOS, "sub_abc123");

            ArgumentCaptor<ModuloNegocio> captor = ArgumentCaptor.forClass(ModuloNegocio.class);
            verify(moduloNegocioRepository).save(captor.capture());

            ModuloNegocio saved = captor.getValue();
            assertThat(saved.getNegocio()).isEqualTo(negocio);
            assertThat(saved.getModulo()).isEqualTo(modulo);
            assertThat(saved.getStripeSubscriptionId()).isEqualTo("sub_abc123");
            assertThat(saved.isActivo()).isTrue();
            assertThat(saved.getFechaActivacion()).isNotNull();
        }

        @Test
        @DisplayName("Actualiza el stripeSubscriptionId si el módulo ya está activo")
        void actualizaSubscriptionIdSiYaActivo() {
            ModuloNegocio mnExistente = new ModuloNegocio();
            mnExistente.setId(UUID.randomUUID());
            mnExistente.setNegocio(negocio);
            mnExistente.setModulo(modulo);
            mnExistente.setActivo(true);
            mnExistente.setStripeSubscriptionId("sub_old");

            when(negocioRepository.findById(negocioId)).thenReturn(Optional.of(negocio));
            when(moduloRepository.findByClave(ModuloService.EMAIL_RECORDATORIOS))
                    .thenReturn(Optional.of(modulo));
            when(moduloNegocioRepository.findByNegocioIdAndModuloClaveAndActivoTrue(
                    negocioId, ModuloService.EMAIL_RECORDATORIOS))
                    .thenReturn(Optional.of(mnExistente));

            moduloActivacionService.activarModulo(negocioId, ModuloService.EMAIL_RECORDATORIOS, "sub_new123");

            verify(moduloNegocioRepository).save(mnExistente);
            assertThat(mnExistente.getStripeSubscriptionId()).isEqualTo("sub_new123");
        }

        @Test
        @DisplayName("Lanza NotFoundException si el negocio no existe")
        void lanzaExcepcionSiNegocioNoExiste() {
            when(negocioRepository.findById(negocioId)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    moduloActivacionService.activarModulo(negocioId, ModuloService.EMAIL_RECORDATORIOS, "sub_x"))
                    .isInstanceOf(NotFoundException.class);

            verify(moduloNegocioRepository, never()).save(any());
        }

        @Test
        @DisplayName("Lanza NotFoundException si el módulo no existe en el catálogo")
        void lanzaExcepcionSiModuloNoExiste() {
            when(negocioRepository.findById(negocioId)).thenReturn(Optional.of(negocio));
            when(moduloRepository.findByClave("modulo_inexistente")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    moduloActivacionService.activarModulo(negocioId, "modulo_inexistente", "sub_x"))
                    .isInstanceOf(NotFoundException.class);

            verify(moduloNegocioRepository, never()).save(any());
        }

        @Test
        @DisplayName("La fechaActivacion se establece como now() al crear")
        void estableceFechaActivacion() {
            LocalDateTime antes = LocalDateTime.now().minusSeconds(1);

            when(negocioRepository.findById(negocioId)).thenReturn(Optional.of(negocio));
            when(moduloRepository.findByClave(ModuloService.REPORTES_AVANZADOS))
                    .thenReturn(Optional.of(modulo));
            when(moduloNegocioRepository.findByNegocioIdAndModuloClaveAndActivoTrue(any(), any()))
                    .thenReturn(Optional.empty());

            moduloActivacionService.activarModulo(negocioId, ModuloService.REPORTES_AVANZADOS, null);

            ArgumentCaptor<ModuloNegocio> captor = ArgumentCaptor.forClass(ModuloNegocio.class);
            verify(moduloNegocioRepository).save(captor.capture());

            assertThat(captor.getValue().getFechaActivacion()).isAfter(antes);
        }
    }

    // ============================================================
    // cancelarModulo
    // ============================================================

    @Nested
    @DisplayName("cancelarModulo")
    class CancelarModulo {

        @Test
        @DisplayName("Marca el módulo como inactivo y registra la fechaCancelacion")
        void marcaModuloInactivo() {
            ModuloNegocio mn = buildModuloNegocioActivo(null); // sin stripeSubscriptionId

            when(usuarioRepository.findByEmailWithNegocio("owner@test.com"))
                    .thenReturn(Optional.of(usuario));
            when(moduloNegocioRepository.findByNegocioIdAndModuloClaveAndActivoTrue(
                    negocioId, ModuloService.EMAIL_RECORDATORIOS))
                    .thenReturn(Optional.of(mn));

            moduloActivacionService.cancelarModulo(ModuloService.EMAIL_RECORDATORIOS, "owner@test.com");

            verify(moduloNegocioRepository).save(mn);
            assertThat(mn.isActivo()).isFalse();
            assertThat(mn.getFechaCancelacion()).isNotNull();
        }

        @Test
        @DisplayName("No intenta cancelar en Stripe si stripeSubscriptionId es null")
        void noLlamaStripesSiSubscriptionIdNulo() {
            ModuloNegocio mn = buildModuloNegocioActivo(null);

            when(usuarioRepository.findByEmailWithNegocio("owner@test.com"))
                    .thenReturn(Optional.of(usuario));
            when(moduloNegocioRepository.findByNegocioIdAndModuloClaveAndActivoTrue(
                    negocioId, ModuloService.EMAIL_RECORDATORIOS))
                    .thenReturn(Optional.of(mn));

            // Si llegara a llamar a Stripe, no habría mock y fallaría. No falla → OK.
            moduloActivacionService.cancelarModulo(ModuloService.EMAIL_RECORDATORIOS, "owner@test.com");

            assertThat(mn.isActivo()).isFalse();
        }

        @Test
        @DisplayName("Lanza NotFoundException si el usuario no existe")
        void lanzaExcepcionSiUsuarioNoExiste() {
            when(usuarioRepository.findByEmailWithNegocio("noexiste@test.com"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    moduloActivacionService.cancelarModulo(ModuloService.EMAIL_RECORDATORIOS, "noexiste@test.com"))
                    .isInstanceOf(NotFoundException.class);

            verify(moduloNegocioRepository, never()).save(any());
        }

        @Test
        @DisplayName("Lanza RuntimeException si el módulo no está activo en el negocio")
        void lanzaExcepcionSiModuloNoActivo() {
            when(usuarioRepository.findByEmailWithNegocio("owner@test.com"))
                    .thenReturn(Optional.of(usuario));
            when(moduloNegocioRepository.findByNegocioIdAndModuloClaveAndActivoTrue(
                    negocioId, ModuloService.SMS_WHATSAPP))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    moduloActivacionService.cancelarModulo(ModuloService.SMS_WHATSAPP, "owner@test.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("sms_whatsapp");

            verify(moduloNegocioRepository, never()).save(any());
        }
    }

    // ============================================================
    // Helpers
    // ============================================================

    private ModuloNegocio buildModuloNegocioActivo(String stripeSubscriptionId) {
        ModuloNegocio mn = new ModuloNegocio();
        mn.setId(UUID.randomUUID());
        mn.setNegocio(negocio);
        mn.setModulo(modulo);
        mn.setActivo(true);
        mn.setFechaActivacion(LocalDateTime.now());
        mn.setStripeSubscriptionId(stripeSubscriptionId);
        return mn;
    }
}
