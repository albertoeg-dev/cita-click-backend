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
import com.reservas.repository.NegocioRepository;
import com.reservas.repository.UsuarioRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Servicio principal del sistema de módulos (marketplace).
 *
 * Claves de módulo disponibles (constantes públicas):
 *   EMAIL_RECORDATORIOS, SMS_WHATSAPP, COBROS_ONLINE,
 *   REPORTES_AVANZADOS, USUARIOS_EXTRA, MULTI_SUCURSAL,
 *   BRANDING_EMAIL, CITAS_EXTRA, SERVICIOS_EXTRA
 *
 * Nota sobre Plan COMPLETO:
 *   Los negocios con plan "completo" tienen acceso a TODOS los módulos
 *   sin necesidad de comprarlos individualmente. tieneModulo() devuelve
 *   true automáticamente para cualquier clave cuando el plan es COMPLETO.
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
    public static final String EMAIL_RECORDATORIOS  = "email_recordatorios";
    public static final String SMS_WHATSAPP         = "sms_whatsapp";
    public static final String COBROS_ONLINE        = "cobros_online";
    public static final String REPORTES_AVANZADOS   = "reportes_avanzados";
    public static final String USUARIOS_EXTRA       = "usuarios_extra";
    public static final String MULTI_SUCURSAL       = "multi_sucursal";
    public static final String BRANDING_EMAIL       = "branding_email";
    // Módulos de expansión de capacidad (solo aplican a Plan BASE)
    public static final String CITAS_EXTRA          = "citas_extra";
    public static final String SERVICIOS_EXTRA      = "servicios_extra";

    private final ModuloRepository moduloRepository;
    private final ModuloNegocioRepository moduloNegocioRepository;
    private final UsuarioRepository usuarioRepository;
    private final NegocioRepository negocioRepository;

    // ── Price IDs de módulos en Stripe ────────────────────────────────────────
    // Cada módulo es un producto independiente en Stripe para tener registro
    // por separado en el dashboard (ingresos, suscriptores, cancelaciones).
    // Si el valor está vacío, ModuloActivacionService usa price_data como fallback.
    @Value("${stripe.modulos.email-recordatorios:}")  private String priceEmailRecordatorios;
    @Value("${stripe.modulos.sms-whatsapp:}")         private String priceSmsWhatsapp;
    @Value("${stripe.modulos.cobros-online:}")        private String priceCobrosOnline;
    @Value("${stripe.modulos.reportes-avanzados:}")   private String priceReportesAvanzados;
    @Value("${stripe.modulos.usuarios-extra:}")       private String priceUsuariosExtra;
    @Value("${stripe.modulos.multi-sucursal:}")       private String priceMultiSucursal;
    @Value("${stripe.modulos.branding-email:}")       private String priceBrandingEmail;
    @Value("${stripe.modulos.citas-extra:}")          private String priceCitasExtra;
    @Value("${stripe.modulos.servicios-extra:}")      private String priceServiciosExtra;

    // ============================================================
    // Inicialización del catálogo
    // ============================================================

    /**
     * Puebla/actualiza el catálogo de módulos al arrancar la aplicación.
     * Crea el módulo si no existe; actualiza el stripePriceId si cambió.
     */
    @PostConstruct
    @Transactional
    public void inicializarModulos() {
        log.info("[ModuloService] Inicializando catálogo de módulos...");

        crearOActualizar(EMAIL_RECORDATORIOS,
                "Recordatorios Email",
                "Envíos ilimitados de recordatorios automáticos por email a tus clientes antes de cada cita. Incluye plantilla personalizable.",
                new BigDecimal("199.00"), priceEmailRecordatorios);

        crearOActualizar(SMS_WHATSAPP,
                "Recordatorios SMS/WhatsApp",
                "300 mensajes SMS o WhatsApp por mes para recordatorios automáticos. Tus clientes reciben el recordatorio en su celular.",
                new BigDecimal("349.00"), priceSmsWhatsapp);

        crearOActualizar(COBROS_ONLINE,
                "Cobros en línea",
                "Acepta pagos de tus clientes directamente desde el sistema con Stripe Connect. Incluye links de pago personalizados.",
                new BigDecimal("349.00"), priceCobrosOnline);

        crearOActualizar(REPORTES_AVANZADOS,
                "Reportes avanzados",
                "Reportes diarios, semanales y mensuales con análisis de ingresos, citas y clientes. Exportables en PDF y Excel.",
                new BigDecimal("249.00"), priceReportesAvanzados);

        crearOActualizar(USUARIOS_EXTRA,
                "Usuarios adicionales",
                "Agrega colaboradores a tu negocio con roles y permisos configurables (Admin, Empleado, Recepcionista). Precio por usuario.",
                new BigDecimal("149.00"), priceUsuariosExtra);

        crearOActualizar(MULTI_SUCURSAL,
                "Multi-sucursal",
                "Gestiona múltiples ubicaciones de tu negocio con agenda, equipo y configuración independiente por sucursal. Precio por sucursal.",
                new BigDecimal("399.00"), priceMultiSucursal);

        crearOActualizar(BRANDING_EMAIL,
                "Branding de emails",
                "Personaliza los colores, logo, mensaje de bienvenida y firma de todos los emails que reciben tus clientes.",
                new BigDecimal("99.00"), priceBrandingEmail);

        // ── Módulos de expansión de capacidad ────────────────────────────────
        crearOActualizar(CITAS_EXTRA,
                "Citas adicionales",
                "Expande tu límite mensual en +100 citas/mes. Compatible con Plan Base.",
                new BigDecimal("99.00"), priceCitasExtra);

        crearOActualizar(SERVICIOS_EXTRA,
                "Servicios adicionales",
                "Expande tu catálogo en +8 servicios adicionales. Compatible con Plan Base.",
                new BigDecimal("79.00"), priceServiciosExtra);

        log.info("[ModuloService] Catálogo de módulos inicializado correctamente");
    }

    // ============================================================
    // Feature gating — métodos principales de control de acceso
    // ============================================================

    /**
     * Verifica si un negocio tiene un módulo activo.
     *
     * Los negocios con Plan COMPLETO tienen acceso a todos los módulos
     * sin necesidad de comprarlos individualmente.
     *
     * @param negocioId ID del negocio
     * @param clave     Clave del módulo (usar constantes de esta clase)
     * @return true si el módulo está disponible para el negocio
     */
    public boolean tieneModulo(UUID negocioId, String clave) {
        // Plan COMPLETO incluye todos los módulos
        boolean esCompleto = negocioRepository.findById(negocioId)
                .map(n -> "completo".equalsIgnoreCase(n.getPlan()))
                .orElse(false);
        if (esCompleto) {
            log.debug("[ModuloService] negocio={} plan=COMPLETO → módulo {} incluido", negocioId, clave);
            return true;
        }

        boolean tiene = moduloNegocioRepository
                .existsByNegocioIdAndModuloClaveAndActivoTrue(negocioId, clave);
        log.debug("[ModuloService] negocio={} clave={} → {}", negocioId, clave, tiene ? "ACTIVO" : "INACTIVO");
        return tiene;
    }

    /**
     * Verifica si el negocio del usuario autenticado tiene un módulo activo.
     * Usado por el AOP aspect y los guards de seguridad.
     */
    @Transactional(readOnly = true)
    public boolean tieneModuloPorEmail(String email, String clave) {
        log.info("[ModuloService] Verificando módulo '{}' para usuario: {}", clave, email);

        Usuario usuario = usuarioRepository.findByEmailWithNegocio(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + email));

        Negocio negocio = usuario.getNegocio();

        // Plan COMPLETO incluye todos los módulos
        if ("completo".equalsIgnoreCase(negocio.getPlan())) {
            log.debug("[ModuloService] usuario={} plan=COMPLETO → módulo {} incluido", email, clave);
            return true;
        }

        return tieneModulo(negocio.getId(), clave);
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
     * Lista todos los módulos del catálogo con el estado activo/inactivo para
     * un negocio específico. Para Plan COMPLETO, todos aparecen como activados.
     */
    @Transactional(readOnly = true)
    public List<ModuloNegocioDTO> listarModulosConEstado(UUID negocioId) {
        List<Modulo> catalogo = moduloRepository.findByActivoTrueOrderByPrecioMensualAsc();
        List<ModuloNegocio> activos = moduloNegocioRepository.findActivosConModulo(negocioId);

        boolean esCompleto = negocioRepository.findById(negocioId)
                .map(n -> "completo".equalsIgnoreCase(n.getPlan()))
                .orElse(false);

        Map<String, ModuloNegocio> activosMap = activos.stream()
                .collect(Collectors.toMap(mn -> mn.getModulo().getClave(), Function.identity()));

        return catalogo.stream()
                .map(modulo -> {
                    ModuloNegocio mn = activosMap.get(modulo.getClave());
                    boolean activado = esCompleto || mn != null;

                    // Calcular próxima fecha de cobro para módulos comprados individualmente.
                    // Lógica: desde fechaActivacion, avanzar de mes en mes hasta superar hoy.
                    LocalDate proximaFechaCobro = null;
                    if (mn != null && mn.isActivo() && mn.getFechaActivacion() != null) {
                        LocalDate activacion = mn.getFechaActivacion().toLocalDate();
                        LocalDate hoy = LocalDate.now();
                        LocalDate siguiente = activacion;
                        while (!siguiente.isAfter(hoy)) {
                            siguiente = siguiente.plusMonths(1);
                        }
                        proximaFechaCobro = siguiente;
                    }

                    return ModuloNegocioDTO.builder()
                            .moduloId(modulo.getId())
                            .clave(modulo.getClave())
                            .nombre(modulo.getNombre())
                            .descripcion(modulo.getDescripcion())
                            .precioMensual(modulo.getPrecioMensual())
                            .activado(activado)
                            .incluidoEnPlan(esCompleto && mn == null)
                            .fechaActivacion(mn != null ? mn.getFechaActivacion() : null)
                            .fechaCancelacion(mn != null ? mn.getFechaCancelacion() : null)
                            .stripeSubscriptionId(mn != null ? mn.getStripeSubscriptionId() : null)
                            .proximaFechaCobro(proximaFechaCobro)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Lista los módulos con estado para el negocio del usuario autenticado.
     */
    @Transactional(readOnly = true)
    public List<ModuloNegocioDTO> listarModulosConEstadoPorEmail(String email) {
        Usuario usuario = usuarioRepository.findByEmailWithNegocio(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + email));

        return listarModulosConEstado(usuario.getNegocio().getId());
    }

    /**
     * Lista el historial completo de módulos (activos e inactivos/cancelados)
     * para el negocio del usuario. Usado en la vista de historial de compras.
     */
    @Transactional(readOnly = true)
    public List<ModuloNegocioDTO> listarHistorialModulos(String email) {
        Usuario usuario = usuarioRepository.findByEmailWithNegocio(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + email));

        return moduloNegocioRepository
                .findByNegocioIdOrderByFechaActivacionDesc(usuario.getNegocio().getId())
                .stream()
                .map(mn -> ModuloNegocioDTO.builder()
                        .moduloId(mn.getModulo().getId())
                        .clave(mn.getModulo().getClave())
                        .nombre(mn.getModulo().getNombre())
                        .descripcion(mn.getModulo().getDescripcion())
                        .precioMensual(mn.getModulo().getPrecioMensual())
                        .activado(mn.isActivo())
                        .incluidoEnPlan(false)
                        .fechaActivacion(mn.getFechaActivacion())
                        .fechaCancelacion(mn.getFechaCancelacion())
                        .stripeSubscriptionId(mn.getStripeSubscriptionId())
                        .build())
                .collect(Collectors.toList());
    }

    // ============================================================
    // Helpers privados
    // ============================================================

    /**
     * Crea el módulo si no existe, o actualiza su stripePriceId si cambió.
     * Nunca actualiza nombre, descripción ni precio (evita sobrescribir cambios manuales en BD).
     */
    private void crearOActualizar(String clave, String nombre, String descripcion,
                                   BigDecimal precio, String stripePriceId) {
        String priceId = (stripePriceId != null && !stripePriceId.isBlank()) ? stripePriceId : null;

        moduloRepository.findByClave(clave).ifPresentOrElse(
                existing -> {
                    if (priceId != null && !priceId.equals(existing.getStripePriceId())) {
                        existing.setStripePriceId(priceId);
                        moduloRepository.save(existing);
                        log.info("[ModuloService] Módulo '{}' → stripePriceId actualizado", clave);
                    }
                },
                () -> {
                    moduloRepository.save(Modulo.builder()
                            .clave(clave)
                            .nombre(nombre)
                            .descripcion(descripcion)
                            .precioMensual(precio)
                            .stripePriceId(priceId)
                            .activo(true)
                            .build());
                    log.info("[ModuloService] Módulo creado: {} - {} MXN/mes", clave, precio);
                }
        );
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
