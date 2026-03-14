package com.reservas.service;

import com.reservas.dto.ModuloDTO;
import com.reservas.dto.ModuloNegocioDTO;
import com.reservas.entity.Modulo;
import com.reservas.entity.ModuloNegocio;
import com.reservas.entity.Negocio;
import com.reservas.entity.Usuario;
import com.reservas.exception.ResourceNotFoundException;
import com.reservas.repository.ModuloNegocioRepository;
import com.reservas.repository.ModuloRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ModuloService - Tests unitarios")
class ModuloServiceTest {

    @Mock
    private ModuloRepository moduloRepository;

    @Mock
    private ModuloNegocioRepository moduloNegocioRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private ModuloService moduloService;

    private UUID negocioId;
    private Negocio negocio;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        negocioId = UUID.randomUUID();
        negocio = new Negocio();
        negocio.setId(negocioId);

        usuario = new Usuario();
        usuario.setEmail("owner@test.com");
        usuario.setNegocio(negocio);
    }

    // ============================================================
    // inicializarModulos
    // ============================================================

    @Nested
    @DisplayName("inicializarModulos")
    class InicializarModulos {

        @Test
        @DisplayName("Crea los 7 módulos cuando el catálogo está vacío")
        void debeCrearSieteModulosCuandoCatalogoVacio() {
            when(moduloRepository.existsByClave(anyString())).thenReturn(false);

            moduloService.inicializarModulos();

            ArgumentCaptor<Modulo> captor = ArgumentCaptor.forClass(Modulo.class);
            verify(moduloRepository, times(7)).save(captor.capture());

            List<Modulo> modulosCreados = captor.getAllValues();
            List<String> claves = modulosCreados.stream().map(Modulo::getClave).toList();

            assertThat(claves).containsExactlyInAnyOrder(
                    ModuloService.EMAIL_RECORDATORIOS,
                    ModuloService.SMS_WHATSAPP,
                    ModuloService.COBROS_ONLINE,
                    ModuloService.REPORTES_AVANZADOS,
                    ModuloService.USUARIOS_EXTRA,
                    ModuloService.MULTI_SUCURSAL,
                    ModuloService.BRANDING_EMAIL
            );
        }

        @Test
        @DisplayName("No crea módulos que ya existen (idempotente)")
        void noDebeCrearModulosExistentes() {
            when(moduloRepository.existsByClave(anyString())).thenReturn(true);

            moduloService.inicializarModulos();

            verify(moduloRepository, never()).save(any());
        }

        @Test
        @DisplayName("Crea solo los módulos faltantes")
        void debeCrearSoloModulosFaltantes() {
            // El stub genérico primero; los específicos al final (Mockito: último stub gana)
            when(moduloRepository.existsByClave(anyString())).thenReturn(false);
            when(moduloRepository.existsByClave(ModuloService.EMAIL_RECORDATORIOS)).thenReturn(true);
            when(moduloRepository.existsByClave(ModuloService.SMS_WHATSAPP)).thenReturn(true);

            moduloService.inicializarModulos();

            // Solo se guardan los 5 restantes
            verify(moduloRepository, times(5)).save(any(Modulo.class));
        }

        @Test
        @DisplayName("Los precios del catálogo son correctos")
        void losPrecionDebenSerCorrectos() {
            when(moduloRepository.existsByClave(anyString())).thenReturn(false);

            moduloService.inicializarModulos();

            ArgumentCaptor<Modulo> captor = ArgumentCaptor.forClass(Modulo.class);
            verify(moduloRepository, times(7)).save(captor.capture());

            List<Modulo> modulos = captor.getAllValues();

            assertThat(modulos)
                    .filteredOn(m -> m.getClave().equals(ModuloService.EMAIL_RECORDATORIOS))
                    .extracting(Modulo::getPrecioMensual)
                    .containsExactly(new BigDecimal("199.00"));

            assertThat(modulos)
                    .filteredOn(m -> m.getClave().equals(ModuloService.REPORTES_AVANZADOS))
                    .extracting(Modulo::getPrecioMensual)
                    .containsExactly(new BigDecimal("249.00"));

            assertThat(modulos)
                    .filteredOn(m -> m.getClave().equals(ModuloService.MULTI_SUCURSAL))
                    .extracting(Modulo::getPrecioMensual)
                    .containsExactly(new BigDecimal("399.00"));
        }
    }

    // ============================================================
    // tieneModulo
    // ============================================================

    @Nested
    @DisplayName("tieneModulo")
    class TieneModulo {

        @Test
        @DisplayName("Retorna true cuando el módulo está activo para el negocio")
        void retornaTrueCuandoModuloActivo() {
            when(moduloNegocioRepository.existsByNegocioIdAndModuloClaveAndActivoTrue(
                    negocioId, ModuloService.EMAIL_RECORDATORIOS)).thenReturn(true);

            boolean resultado = moduloService.tieneModulo(negocioId, ModuloService.EMAIL_RECORDATORIOS);

            assertThat(resultado).isTrue();
        }

        @Test
        @DisplayName("Retorna false cuando el módulo no está activo para el negocio")
        void retornaFalseCuandoModuloInactivo() {
            when(moduloNegocioRepository.existsByNegocioIdAndModuloClaveAndActivoTrue(
                    negocioId, ModuloService.REPORTES_AVANZADOS)).thenReturn(false);

            boolean resultado = moduloService.tieneModulo(negocioId, ModuloService.REPORTES_AVANZADOS);

            assertThat(resultado).isFalse();
        }

        @Test
        @DisplayName("Consulta el repositorio con los parámetros correctos")
        void debeConsultarRepositorioCorrectamente() {
            moduloService.tieneModulo(negocioId, ModuloService.COBROS_ONLINE);

            verify(moduloNegocioRepository).existsByNegocioIdAndModuloClaveAndActivoTrue(
                    negocioId, ModuloService.COBROS_ONLINE);
        }
    }

    // ============================================================
    // tieneModuloPorEmail
    // ============================================================

    @Nested
    @DisplayName("tieneModuloPorEmail")
    class TieneModuloPorEmail {

        @Test
        @DisplayName("Retorna true cuando el módulo está activo para el negocio del usuario")
        void retornaTrueCuandoModuloActivoParaEmail() {
            when(usuarioRepository.findByEmailWithNegocio("owner@test.com"))
                    .thenReturn(Optional.of(usuario));
            when(moduloNegocioRepository.existsByNegocioIdAndModuloClaveAndActivoTrue(
                    negocioId, ModuloService.EMAIL_RECORDATORIOS)).thenReturn(true);

            boolean resultado = moduloService.tieneModuloPorEmail("owner@test.com", ModuloService.EMAIL_RECORDATORIOS);

            assertThat(resultado).isTrue();
        }

        @Test
        @DisplayName("Lanza ResourceNotFoundException si el usuario no existe")
        void lanzaExcepcionCuandoUsuarioNoExiste() {
            when(usuarioRepository.findByEmailWithNegocio("noexiste@test.com"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    moduloService.tieneModuloPorEmail("noexiste@test.com", ModuloService.EMAIL_RECORDATORIOS))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Usa el negocioId del usuario para consultar el módulo")
        void usaNegocioIdDelUsuario() {
            when(usuarioRepository.findByEmailWithNegocio("owner@test.com"))
                    .thenReturn(Optional.of(usuario));
            when(moduloNegocioRepository.existsByNegocioIdAndModuloClaveAndActivoTrue(any(), any()))
                    .thenReturn(false);

            moduloService.tieneModuloPorEmail("owner@test.com", ModuloService.SMS_WHATSAPP);

            verify(moduloNegocioRepository).existsByNegocioIdAndModuloClaveAndActivoTrue(
                    negocioId, ModuloService.SMS_WHATSAPP);
        }
    }

    // ============================================================
    // listarModulos
    // ============================================================

    @Nested
    @DisplayName("listarModulos")
    class ListarModulos {

        @Test
        @DisplayName("Retorna lista de módulos activos del catálogo")
        void retornaModulosActivos() {
            List<Modulo> modulos = List.of(
                    buildModulo(ModuloService.EMAIL_RECORDATORIOS, "Recordatorios Email", new BigDecimal("199.00")),
                    buildModulo(ModuloService.REPORTES_AVANZADOS, "Reportes avanzados", new BigDecimal("249.00"))
            );
            when(moduloRepository.findByActivoTrueOrderByPrecioMensualAsc()).thenReturn(modulos);

            List<ModuloDTO> resultado = moduloService.listarModulos();

            assertThat(resultado).hasSize(2);
            assertThat(resultado).extracting(ModuloDTO::getClave)
                    .containsExactly(ModuloService.EMAIL_RECORDATORIOS, ModuloService.REPORTES_AVANZADOS);
        }

        @Test
        @DisplayName("Retorna lista vacía cuando no hay módulos")
        void retornaListaVaciaSinModulos() {
            when(moduloRepository.findByActivoTrueOrderByPrecioMensualAsc()).thenReturn(List.of());

            List<ModuloDTO> resultado = moduloService.listarModulos();

            assertThat(resultado).isEmpty();
        }
    }

    // ============================================================
    // listarModulosConEstado
    // ============================================================

    @Nested
    @DisplayName("listarModulosConEstado")
    class ListarModulosConEstado {

        @Test
        @DisplayName("Marca como activado=true los módulos que el negocio tiene activos")
        void marcaModulosActivosCorrectamente() {
            Modulo emailMod = buildModulo(ModuloService.EMAIL_RECORDATORIOS, "Recordatorios Email", new BigDecimal("199.00"));
            Modulo reportesMod = buildModulo(ModuloService.REPORTES_AVANZADOS, "Reportes avanzados", new BigDecimal("249.00"));

            when(moduloRepository.findByActivoTrueOrderByPrecioMensualAsc()).thenReturn(List.of(emailMod, reportesMod));

            ModuloNegocio emailActivo = buildModuloNegocio(emailMod, LocalDateTime.now());
            when(moduloNegocioRepository.findActivosConModulo(negocioId)).thenReturn(List.of(emailActivo));

            List<ModuloNegocioDTO> resultado = moduloService.listarModulosConEstado(negocioId);

            assertThat(resultado).hasSize(2);

            ModuloNegocioDTO emailDto = resultado.stream()
                    .filter(d -> d.getClave().equals(ModuloService.EMAIL_RECORDATORIOS))
                    .findFirst().orElseThrow();
            assertThat(emailDto.isActivado()).isTrue();
            assertThat(emailDto.getFechaActivacion()).isNotNull();

            ModuloNegocioDTO reportesDto = resultado.stream()
                    .filter(d -> d.getClave().equals(ModuloService.REPORTES_AVANZADOS))
                    .findFirst().orElseThrow();
            assertThat(reportesDto.isActivado()).isFalse();
            assertThat(reportesDto.getFechaActivacion()).isNull();
        }

        @Test
        @DisplayName("Todos los módulos están inactivos cuando el negocio no tiene ninguno")
        void todosInactivosSinModulosContratados() {
            Modulo mod = buildModulo(ModuloService.SMS_WHATSAPP, "SMS", new BigDecimal("349.00"));
            when(moduloRepository.findByActivoTrueOrderByPrecioMensualAsc()).thenReturn(List.of(mod));
            when(moduloNegocioRepository.findActivosConModulo(negocioId)).thenReturn(List.of());

            List<ModuloNegocioDTO> resultado = moduloService.listarModulosConEstado(negocioId);

            assertThat(resultado).allMatch(dto -> !dto.isActivado());
        }

        @Test
        @DisplayName("Incluye el stripeSubscriptionId cuando el módulo está activo")
        void incluyeStripeSubIdCuandoActivo() {
            Modulo mod = buildModulo(ModuloService.COBROS_ONLINE, "Cobros", new BigDecimal("349.00"));
            when(moduloRepository.findByActivoTrueOrderByPrecioMensualAsc()).thenReturn(List.of(mod));

            ModuloNegocio mn = buildModuloNegocio(mod, LocalDateTime.now());
            mn.setStripeSubscriptionId("sub_test_abc123");
            when(moduloNegocioRepository.findActivosConModulo(negocioId)).thenReturn(List.of(mn));

            List<ModuloNegocioDTO> resultado = moduloService.listarModulosConEstado(negocioId);

            assertThat(resultado.get(0).getStripeSubscriptionId()).isEqualTo("sub_test_abc123");
        }
    }

    // ============================================================
    // listarModulosConEstadoPorEmail
    // ============================================================

    @Nested
    @DisplayName("listarModulosConEstadoPorEmail")
    class ListarModulosConEstadoPorEmail {

        @Test
        @DisplayName("Delega correctamente al negocio del usuario")
        void delegaAlNegocioDelUsuario() {
            when(usuarioRepository.findByEmailWithNegocio("owner@test.com"))
                    .thenReturn(Optional.of(usuario));
            when(moduloRepository.findByActivoTrueOrderByPrecioMensualAsc()).thenReturn(List.of());
            when(moduloNegocioRepository.findActivosConModulo(negocioId)).thenReturn(List.of());

            moduloService.listarModulosConEstadoPorEmail("owner@test.com");

            verify(moduloNegocioRepository).findActivosConModulo(negocioId);
        }

        @Test
        @DisplayName("Lanza ResourceNotFoundException si el usuario no existe")
        void lanzaExcepcionCuandoUsuarioNoExiste() {
            when(usuarioRepository.findByEmailWithNegocio("noexiste@test.com"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    moduloService.listarModulosConEstadoPorEmail("noexiste@test.com"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ============================================================
    // Helpers de test
    // ============================================================

    private Modulo buildModulo(String clave, String nombre, BigDecimal precio) {
        Modulo m = new Modulo();
        m.setId(UUID.randomUUID());
        m.setClave(clave);
        m.setNombre(nombre);
        m.setPrecioMensual(precio);
        m.setActivo(true);
        return m;
    }

    private ModuloNegocio buildModuloNegocio(Modulo modulo, LocalDateTime fechaActivacion) {
        ModuloNegocio mn = new ModuloNegocio();
        mn.setId(UUID.randomUUID());
        mn.setNegocio(negocio);
        mn.setModulo(modulo);
        mn.setActivo(true);
        mn.setFechaActivacion(fechaActivacion);
        return mn;
    }
}
