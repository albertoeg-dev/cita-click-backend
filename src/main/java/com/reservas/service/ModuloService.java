package com.reservas.service;

import com.reservas.dto.ModuloDTO;
import com.reservas.dto.ModuloNegocioDTO;
import com.reservas.entity.Modulo;
import com.reservas.entity.ModuloNegocio;
import com.reservas.entity.Usuario;
import com.reservas.exception.ResourceNotFoundException;
import com.reservas.repository.ModuloNegocioRepository;
import com.reservas.repository.ModuloRepository;
import com.reservas.repository.UsuarioRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Servicio principal del sistema de módulos (marketplace).
 *
 * Reemplaza gradualmente a PlanLimitesService para el control de acceso
 * a funcionalidades. En lugar de validar contra un plan fijo, verifica si
 * el negocio tiene activo el módulo correspondiente.
 *
 * Claves de módulo disponibles (constantes públicas):
 *   EMAIL_RECORDATORIOS, SMS_WHATSAPP, COBROS_ONLINE,
 *   REPORTES_AVANZADOS, USUARIOS_EXTRA, MULTI_SUCURSAL, BRANDING_EMAIL
 *
 * @author Cita Click
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModuloService {

    // ============================================================
    // Claves de módulo — usar estas constantes en guards y AOP
    // ============================================================
    public static final String EMAIL_RECORDATORIOS = "email_recordatorios";
    public static final String SMS_WHATSAPP        = "sms_whatsapp";
    public static final String COBROS_ONLINE       = "cobros_online";
    public static final String REPORTES_AVANZADOS  = "reportes_avanzados";
    public static final String USUARIOS_EXTRA      = "usuarios_extra";
    public static final String MULTI_SUCURSAL      = "multi_sucursal";
    public static final String BRANDING_EMAIL      = "branding_email";

    private final ModuloRepository moduloRepository;
    private final ModuloNegocioRepository moduloNegocioRepository;
    private final UsuarioRepository usuarioRepository;

    // ============================================================
    // Inicialización del catálogo
    // ============================================================

    /**
     * Puebla el catálogo de módulos al arrancar la aplicación.
     * Solo crea módulos que no existen aún (idempotente).
     */
    @PostConstruct
    @Transactional
    public void inicializarModulos() {
        log.info("[ModuloService] Inicializando catálogo de módulos...");

        crearSiNoExiste(EMAIL_RECORDATORIOS,
                "Recordatorios Email",
                "Envíos ilimitados de recordatorios automáticos por email a tus clientes antes de cada cita. Incluye plantilla personalizable.",
                new BigDecimal("199.00"));

        crearSiNoExiste(SMS_WHATSAPP,
                "Recordatorios SMS/WhatsApp",
                "300 mensajes SMS o WhatsApp por mes para recordatorios automáticos. Tus clientes reciben el recordatorio en su celular.",
                new BigDecimal("349.00"));

        crearSiNoExiste(COBROS_ONLINE,
                "Cobros en línea",
                "Acepta pagos de tus clientes directamente desde el sistema con Stripe Connect. Incluye links de pago personalizados.",
                new BigDecimal("349.00"));

        crearSiNoExiste(REPORTES_AVANZADOS,
                "Reportes avanzados",
                "Reportes diarios, semanales y mensuales con análisis de ingresos, citas y clientes. Exportables en PDF y Excel.",
                new BigDecimal("249.00"));

        crearSiNoExiste(USUARIOS_EXTRA,
                "Usuarios adicionales",
                "Agrega colaboradores a tu negocio con roles y permisos configurables (Admin, Empleado, Recepcionista). Precio por usuario.",
                new BigDecimal("149.00"));

        crearSiNoExiste(MULTI_SUCURSAL,
                "Multi-sucursal",
                "Gestiona múltiples ubicaciones de tu negocio con agenda, equipo y configuración independiente por sucursal. Precio por sucursal.",
                new BigDecimal("399.00"));

        crearSiNoExiste(BRANDING_EMAIL,
                "Branding de emails",
                "Personaliza los colores, logo, mensaje de bienvenida y firma de todos los emails que reciben tus clientes.",
                new BigDecimal("99.00"));

        log.info("[ModuloService] Catálogo de módulos inicializado correctamente");
    }

    // ============================================================
    // Feature gating — métodos principales de control de acceso
    // ============================================================

    /**
     * Verifica si un negocio tiene un módulo activo.
     * Método principal para control de acceso a funcionalidades.
     *
     * @param negocioId ID del negocio
     * @param clave     Clave del módulo (usar constantes de esta clase)
     * @return true si el módulo está activo para el negocio
     */
    public boolean tieneModulo(UUID negocioId, String clave) {
        boolean tiene = moduloNegocioRepository.existsByNegocioIdAndModuloClaveAndActivoTrue(negocioId, clave);
        log.debug("[ModuloService] negocio={} clave={} → {}", negocioId, clave, tiene ? "ACTIVO" : "INACTIVO");
        return tiene;
    }

    /**
     * Verifica si el negocio del usuario autenticado (por email) tiene un módulo activo.
     * Usado por el AOP aspect y los guards de seguridad.
     *
     * @param email Email del usuario autenticado
     * @param clave Clave del módulo
     * @return true si el módulo está activo
     */
    @Transactional(readOnly = true)
    public boolean tieneModuloPorEmail(String email, String clave) {
        log.info("[ModuloService] Verificando módulo '{}' para usuario: {}", clave, email);

        Usuario usuario = usuarioRepository.findByEmailWithNegocio(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + email));

        return tieneModulo(usuario.getNegocio().getId(), clave);
    }

    // ============================================================
    // Consultas del catálogo
    // ============================================================

    /**
     * Lista todos los módulos activos del catálogo (para el marketplace público).
     */
    @Transactional(readOnly = true)
    public List<ModuloDTO> listarModulos() {
        return moduloRepository.findByActivoTrueOrderByPrecioMensualAsc()
                .stream()
                .map(this::toModuloDTO)
                .collect(Collectors.toList());
    }

    /**
     * Lista todos los módulos del catálogo con el estado activo/inactivo
     * para un negocio específico. Usado en la vista del marketplace.
     *
     * @param negocioId ID del negocio
     * @return Lista de módulos con campo 'activado' según el negocio
     */
    @Transactional(readOnly = true)
    public List<ModuloNegocioDTO> listarModulosConEstado(UUID negocioId) {
        List<Modulo> catalogo = moduloRepository.findByActivoTrueOrderByPrecioMensualAsc();
        List<ModuloNegocio> activos = moduloNegocioRepository.findActivosConModulo(negocioId);

        Map<String, ModuloNegocio> activosMap = activos.stream()
                .collect(Collectors.toMap(mn -> mn.getModulo().getClave(), Function.identity()));

        return catalogo.stream()
                .map(modulo -> {
                    ModuloNegocio mn = activosMap.get(modulo.getClave());
                    return ModuloNegocioDTO.builder()
                            .moduloId(modulo.getId())
                            .clave(modulo.getClave())
                            .nombre(modulo.getNombre())
                            .descripcion(modulo.getDescripcion())
                            .precioMensual(modulo.getPrecioMensual())
                            .activado(mn != null)
                            .fechaActivacion(mn != null ? mn.getFechaActivacion() : null)
                            .stripeSubscriptionId(mn != null ? mn.getStripeSubscriptionId() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Lista los módulos con estado para el negocio del usuario autenticado.
     *
     * @param email Email del usuario autenticado
     */
    @Transactional(readOnly = true)
    public List<ModuloNegocioDTO> listarModulosConEstadoPorEmail(String email) {
        Usuario usuario = usuarioRepository.findByEmailWithNegocio(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + email));

        return listarModulosConEstado(usuario.getNegocio().getId());
    }

    // ============================================================
    // Helpers privados
    // ============================================================

    private void crearSiNoExiste(String clave, String nombre, String descripcion, BigDecimal precio) {
        if (!moduloRepository.existsByClave(clave)) {
            moduloRepository.save(Modulo.builder()
                    .clave(clave)
                    .nombre(nombre)
                    .descripcion(descripcion)
                    .precioMensual(precio)
                    .activo(true)
                    .build());
            log.info("[ModuloService] Módulo creado: {} - {} MXN/mes", clave, precio);
        } else {
            log.debug("[ModuloService] Módulo ya existe: {}", clave);
        }
    }

    private ModuloDTO toModuloDTO(Modulo modulo) {
        return ModuloDTO.builder()
                .id(modulo.getId())
                .clave(modulo.getClave())
                .nombre(modulo.getNombre())
                .descripcion(modulo.getDescripcion())
                .precioMensual(modulo.getPrecioMensual())
                .activo(modulo.isActivo())
                .build();
    }
}
