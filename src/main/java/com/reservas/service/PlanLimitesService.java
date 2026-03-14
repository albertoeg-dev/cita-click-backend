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
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Claves de módulos de expansión (sincronizadas con ModuloService)
// citas_extra    → +100 citas/mes
// servicios_extra → +8 servicios

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanLimitesService {

    private final PlanLimitesRepository planLimitesRepository;
    private final UsoNegocioRepository usoNegocioRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final CitaRepository citaRepository;
    private final ServicioRepository servicioRepository;
    private final ModuloNegocioRepository moduloNegocioRepository;

    // Incrementos por módulo de expansión
    private static final int CITAS_POR_MODULO_EXTRA    = 100;
    private static final int SERVICIOS_POR_MODULO_EXTRA = 8;

    /**
     * Inicializa los límites de los planes en la base de datos.
     *
     * Planes activos:
     *   BASE     → 1 usuario, clientes ilimitados, 200 citas/mes, 8 servicios
     *              Módulos expandibles: citas_extra (+100/mes), servicios_extra (+8)
     *   COMPLETO → 1 usuario, ilimitado en todo, todos los módulos incluidos
     *
     * Los planes legacy (BASICO, PROFESIONAL, PREMIUM) se conservan en BD
     * pero no se crean aquí. fromCodigo() los redirige a BASE o COMPLETO.
     */
    @PostConstruct
    @Transactional
    public void inicializarLimites() {
        log.info("[PlanLimitesService] Verificando límites de planes...");

        // PLAN BASE — funcionalidades controladas 100% por módulos del marketplace
        if (!planLimitesRepository.existsByTipoPlan(TipoPlan.BASE)) {
            PlanLimites base = PlanLimites.builder()
                    .tipoPlan(TipoPlan.BASE)
                    .maxUsuarios(1)
                    .maxClientes(-1)   // Ilimitado
                    .maxCitasMes(200)  // Expandible con módulo citas_extra (+100/mes)
                    .maxServicios(8)   // Expandible con módulo servicios_extra (+8)
                    .emailRecordatoriosHabilitado(false)   // Módulo email_recordatorios
                    .smsWhatsappHabilitado(false)          // Módulo sms_whatsapp
                    .reportesAvanzadosHabilitado(false)    // Módulo reportes_avanzados
                    .personalizacionEmailHabilitado(false) // Módulo branding_email
                    .soportePrioritario(false)
                    .build();
            planLimitesRepository.save(base);
            log.info("[PlanLimitesService] Plan BASE creado");
        }

        // PLAN COMPLETO — todos los módulos incluidos, sin límites de uso
        if (!planLimitesRepository.existsByTipoPlan(TipoPlan.COMPLETO)) {
            PlanLimites completo = PlanLimites.builder()
                    .tipoPlan(TipoPlan.COMPLETO)
                    .maxUsuarios(1)    // Usuario base; adicionales con módulo usuarios_extra
                    .maxClientes(-1)   // Ilimitado
                    .maxCitasMes(-1)   // Ilimitado
                    .maxServicios(-1)  // Ilimitado
                    .emailRecordatoriosHabilitado(true)
                    .smsWhatsappHabilitado(true)
                    .reportesAvanzadosHabilitado(true)
                    .personalizacionEmailHabilitado(true)
                    .soportePrioritario(true)
                    .build();
            planLimitesRepository.save(completo);
            log.info("[PlanLimitesService] Plan COMPLETO creado");
        }

        log.info("[PlanLimitesService] Verificación de límites completada");
    }

    /**
     * Obtiene los límites de un plan específico
     */
    public PlanLimites obtenerLimites(TipoPlan tipoPlan) {
        return planLimitesRepository.findByTipoPlan(tipoPlan)
                .orElseThrow(() -> new IllegalStateException("Límites no encontrados para plan: " + tipoPlan));
    }

    /**
     * Obtiene el uso actual de un negocio
     */
    @Transactional
    public UsoNegocio obtenerUsoActual(UUID negocioId) {
        String periodoActual = UsoNegocio.getPeriodoActual();

        return usoNegocioRepository.findByNegocioIdAndPeriodo(negocioId, periodoActual)
                .orElseGet(() -> {
                    log.info("[PlanLimitesService] Creando nuevo registro de uso para negocio: {}, periodo: {}",
                            negocioId, periodoActual);
                    // Crear nuevo registro de uso
                    Negocio negocio = new Negocio();
                    negocio.setId(negocioId);

                    UsoNegocio nuevoUso = UsoNegocio.builder()
                            .negocio(negocio)
                            .periodo(periodoActual)
                            .totalUsuarios(0)
                            .totalClientes(0)
                            .totalCitasMes(0)
                            .totalServicios(0)
                            .build();

                    return usoNegocioRepository.save(nuevoUso);
                });
    }

    /**
     * Actualiza el conteo de uso de un negocio
     */
    @Transactional
    public void actualizarUso(UUID negocioId) {
        log.info("[PlanLimitesService] Actualizando uso para negocio: {}", negocioId);

        UsoNegocio uso = obtenerUsoActual(negocioId);

        // Contar totales reales
        long totalUsuarios = usuarioRepository.countActiveUsuariosByNegocioId(negocioId);
        long totalClientes = clienteRepository.countByNegocioId(negocioId);
        long totalServicios = servicioRepository.countByNegocioId(negocioId);

        // Para citas del mes actual
        String periodoActual = UsoNegocio.getPeriodoActual();
        String[] parts = periodoActual.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        long totalCitasMes = citaRepository.countCitasByNegocioAndMonth(negocioId, year, month);

        uso.setTotalUsuarios((int) totalUsuarios);
        uso.setTotalClientes((int) totalClientes);
        uso.setTotalCitasMes((int) totalCitasMes);
        uso.setTotalServicios((int) totalServicios);

        usoNegocioRepository.save(uso);
        log.info("[PlanLimitesService] Uso actualizado - Usuarios: {}, Clientes: {}, Citas: {}, Servicios: {}",
                totalUsuarios, totalClientes, totalCitasMes, totalServicios);
    }

    /**
     * Valida si se puede agregar un nuevo usuario
     */
    public void validarLimiteUsuarios(UUID negocioId, TipoPlan tipoPlan) {
        log.info("[PlanLimitesService] Validando límite de usuarios para negocio: {}, plan: {}", negocioId, tipoPlan);

        PlanLimites limites = obtenerLimites(tipoPlan);

        // Si es ilimitado, permitir
        if (limites.getMaxUsuarios() == -1) {
            log.info("[PlanLimitesService] Plan con usuarios ilimitados");
            return;
        }

        long usuariosActuales = usuarioRepository.countActiveUsuariosByNegocioId(negocioId);

        if (usuariosActuales >= limites.getMaxUsuarios()) {
            log.warn("[PlanLimitesService] Límite de usuarios excedido: {} >= {}", usuariosActuales, limites.getMaxUsuarios());
            throw new LimiteExcedidoException("usuarios", (int) usuariosActuales, limites.getMaxUsuarios());
        }

        log.info("[PlanLimitesService] Validación exitosa: {} / {} usuarios", usuariosActuales, limites.getMaxUsuarios());
    }

    /**
     * Valida si se puede agregar un nuevo cliente
     */
    public void validarLimiteClientes(UUID negocioId, TipoPlan tipoPlan) {
        log.info("[PlanLimitesService] Validando límite de clientes para negocio: {}, plan: {}", negocioId, tipoPlan);

        PlanLimites limites = obtenerLimites(tipoPlan);

        if (limites.getMaxClientes() == -1) {
            log.info("[PlanLimitesService] Plan con clientes ilimitados");
            return;
        }

        long clientesActuales = clienteRepository.countByNegocioId(negocioId);

        if (clientesActuales >= limites.getMaxClientes()) {
            log.warn("[PlanLimitesService] Límite de clientes excedido: {} >= {}", clientesActuales, limites.getMaxClientes());
            throw new LimiteExcedidoException("clientes", (int) clientesActuales, limites.getMaxClientes());
        }

        log.info("[PlanLimitesService] Validación exitosa: {} / {} clientes", clientesActuales, limites.getMaxClientes());
    }

    /**
     * Valida si se puede agregar una nueva cita este mes.
     * Considera el módulo citas_extra si está activo (+100 citas/mes).
     */
    public void validarLimiteCitasMes(UUID negocioId, TipoPlan tipoPlan) {
        log.info("[PlanLimitesService] Validando límite de citas del mes para negocio: {}, plan: {}", negocioId, tipoPlan);

        PlanLimites limites = obtenerLimites(tipoPlan);

        if (limites.getMaxCitasMes() == -1) {
            log.info("[PlanLimitesService] Plan con citas ilimitadas");
            return;
        }

        int limiteEfectivo = calcularLimiteCitasEfectivo(negocioId, limites.getMaxCitasMes());
        UsoNegocio uso = obtenerUsoActual(negocioId);

        if (uso.getTotalCitasMes() >= limiteEfectivo) {
            log.warn("[PlanLimitesService] Límite de citas del mes excedido: {} >= {}", uso.getTotalCitasMes(), limiteEfectivo);
            throw new LimiteExcedidoException("citas este mes", uso.getTotalCitasMes(), limiteEfectivo);
        }

        log.info("[PlanLimitesService] Validación exitosa: {} / {} citas este mes", uso.getTotalCitasMes(), limiteEfectivo);
    }

    /**
     * Valida si se puede agregar un nuevo servicio.
     * Considera el módulo servicios_extra si está activo (+8 servicios).
     */
    public void validarLimiteServicios(UUID negocioId, TipoPlan tipoPlan) {
        log.info("[PlanLimitesService] Validando límite de servicios para negocio: {}, plan: {}", negocioId, tipoPlan);

        PlanLimites limites = obtenerLimites(tipoPlan);

        if (limites.getMaxServicios() == -1) {
            log.info("[PlanLimitesService] Plan con servicios ilimitados");
            return;
        }

        int limiteEfectivo = calcularLimiteServiciosEfectivo(negocioId, limites.getMaxServicios());
        long serviciosActuales = servicioRepository.countByNegocioId(negocioId);

        if (serviciosActuales >= limiteEfectivo) {
            log.warn("[PlanLimitesService] Límite de servicios excedido: {} >= {}", serviciosActuales, limiteEfectivo);
            throw new LimiteExcedidoException("servicios", (int) serviciosActuales, limiteEfectivo);
        }

        log.info("[PlanLimitesService] Validación exitosa: {} / {} servicios", serviciosActuales, limiteEfectivo);
    }

    // ── Helpers: límites efectivos considerando módulos de expansión ─────────

    /**
     * Calcula el límite real de citas/mes sumando módulo citas_extra si está activo.
     * Si el base es -1 (ilimitado), devuelve -1 directamente.
     */
    public int calcularLimiteCitasEfectivo(UUID negocioId, int limiteBase) {
        if (limiteBase == -1) return -1;
        boolean tieneCitasExtra = moduloNegocioRepository
                .existsByNegocioIdAndModuloClaveAndActivoTrue(negocioId, "citas_extra");
        return limiteBase + (tieneCitasExtra ? CITAS_POR_MODULO_EXTRA : 0);
    }

    /**
     * Calcula el límite real de servicios sumando módulo servicios_extra si está activo.
     * Si el base es -1 (ilimitado), devuelve -1 directamente.
     */
    public int calcularLimiteServiciosEfectivo(UUID negocioId, int limiteBase) {
        if (limiteBase == -1) return -1;
        boolean tieneServiciosExtra = moduloNegocioRepository
                .existsByNegocioIdAndModuloClaveAndActivoTrue(negocioId, "servicios_extra");
        return limiteBase + (tieneServiciosExtra ? SERVICIOS_POR_MODULO_EXTRA : 0);
    }

    /**
     * Valida si una funcionalidad está habilitada en el plan
     */
    public void validarFuncionalidadHabilitada(TipoPlan tipoPlan, String funcionalidad) {
        log.info("[PlanLimitesService] Validando funcionalidad '{}' para plan: {}", funcionalidad, tipoPlan);

        PlanLimites limites = obtenerLimites(tipoPlan);

        boolean habilitada = switch (funcionalidad.toLowerCase()) {
            case "email", "email_recordatorios" -> limites.isEmailRecordatoriosHabilitado();
            case "sms", "whatsapp", "sms_whatsapp" -> limites.isSmsWhatsappHabilitado();
            case "reportes_avanzados" -> limites.isReportesAvanzadosHabilitado();
            case "personalizacion_email" -> limites.isPersonalizacionEmailHabilitado();
            case "soporte_prioritario" -> limites.isSoportePrioritario();
            default -> throw new IllegalArgumentException("Funcionalidad no reconocida: " + funcionalidad);
        };

        if (!habilitada) {
            log.warn("[PlanLimitesService] Funcionalidad '{}' no habilitada para plan {}", funcionalidad, tipoPlan);
            throw new LimiteExcedidoException(
                    String.format("La funcionalidad '%s' no está disponible en el plan %s",
                            funcionalidad, tipoPlan.getNombre()));
        }

        log.info("[PlanLimitesService] Funcionalidad '{}' está habilitada", funcionalidad);
    }

    /**
     * Valida si una funcionalidad está habilitada para un usuario (por email)
     */
    public void validarFuncionalidadHabilitada(String email, String funcionalidad) {
        log.info("[PlanLimitesService] Validando funcionalidad '{}' para usuario: {}", funcionalidad, email);

        Usuario usuario = usuarioRepository.findByEmailWithNegocio(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        Negocio negocio = usuario.getNegocio();
        if (negocio == null) {
            throw new ResourceNotFoundException("Negocio no encontrado para el usuario");
        }

        TipoPlan plan = TipoPlan.fromCodigo(negocio.getPlan());
        validarFuncionalidadHabilitada(plan, funcionalidad);
    }

    /**
     * Obtiene los límites del plan del negocio por email del usuario.
     *
     * SOLUCIÓN LAZY LOADING:
     * - Marcado como @Transactional(readOnly = true)
     * - Usa JOIN FETCH para cargar Negocio eagerly
     * - Previene LazyInitializationException
     */
    @Transactional(readOnly = true)
    public PlanLimitesDTO obtenerLimitesPorEmail(String email) {
        log.info("[PlanLimitesService] Obteniendo límites del plan para usuario: {}", email);

        // JOIN FETCH previene LazyInitializationException
        Usuario usuario = usuarioRepository.findByEmailWithNegocio(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        Negocio negocio = usuario.getNegocio();
        if (negocio == null) {
            throw new ResourceNotFoundException("Negocio no encontrado para el usuario");
        }

        TipoPlan plan = TipoPlan.fromCodigo(negocio.getPlan());
        PlanLimites limites = obtenerLimites(plan);

        return PlanLimitesDTO.builder()
                .tipoPlan(limites.getTipoPlan().getCodigo())
                .nombrePlan(limites.getTipoPlan().getNombre())
                .maxUsuarios(limites.getMaxUsuarios())
                .maxClientes(limites.getMaxClientes())
                .maxCitasMes(limites.getMaxCitasMes())
                .maxServicios(limites.getMaxServicios())
                .emailRecordatoriosHabilitado(limites.isEmailRecordatoriosHabilitado())
                .smsWhatsappHabilitado(limites.isSmsWhatsappHabilitado())
                .reportesAvanzadosHabilitado(limites.isReportesAvanzadosHabilitado())
                .personalizacionEmailHabilitado(limites.isPersonalizacionEmailHabilitado())
                .soportePrioritario(limites.isSoportePrioritario())
                .build();
    }

    /**
     * Obtiene el uso actual del negocio por email del usuario.
     *
     * SOLUCIÓN LAZY LOADING:
     * - Marcado como @Transactional(readOnly = true)
     * - Usa JOIN FETCH para cargar Negocio eagerly
     * - Previene LazyInitializationException
     */
    @Transactional(readOnly = true)
    public UsoNegocioDTO obtenerUsoPorEmail(String email) {
        log.info("[PlanLimitesService] Obteniendo uso del plan para usuario: {}", email);

        // JOIN FETCH previene LazyInitializationException
        Usuario usuario = usuarioRepository.findByEmailWithNegocio(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        Negocio negocio = usuario.getNegocio();
        if (negocio == null) {
            throw new ResourceNotFoundException("Negocio no encontrado para el usuario");
        }

        UUID negocioId = negocio.getId();
        TipoPlan plan = TipoPlan.fromCodigo(negocio.getPlan());

        // Actualizar uso antes de obtenerlo
        actualizarUso(negocioId);

        UsoNegocio uso = obtenerUsoActual(negocioId);
        PlanLimites limites = obtenerLimites(plan);

        // Límites efectivos: base del plan + módulos de expansión activos
        int limiteCitasEfectivo    = calcularLimiteCitasEfectivo(negocioId, limites.getMaxCitasMes());
        int limiteServiciosEfectivo = calcularLimiteServiciosEfectivo(negocioId, limites.getMaxServicios());

        // Calcular porcentajes con límites efectivos
        Double porcentajeUsuarios = UsoNegocioDTO.calcularPorcentaje(uso.getTotalUsuarios(), limites.getMaxUsuarios());
        Double porcentajeClientes = UsoNegocioDTO.calcularPorcentaje(uso.getTotalClientes(), limites.getMaxClientes());
        Double porcentajeCitasMes = UsoNegocioDTO.calcularPorcentaje(uso.getTotalCitasMes(), limiteCitasEfectivo);
        Double porcentajeServicios = UsoNegocioDTO.calcularPorcentaje(uso.getTotalServicios(), limiteServiciosEfectivo);

        return UsoNegocioDTO.builder()
                .periodo(uso.getPeriodo())
                .totalUsuarios(uso.getTotalUsuarios())
                .totalClientes(uso.getTotalClientes())
                .totalCitasMes(uso.getTotalCitasMes())
                .totalServicios(uso.getTotalServicios())
                .limiteUsuarios(limites.getMaxUsuarios())
                .limiteClientes(limites.getMaxClientes())
                .limiteCitasMes(limiteCitasEfectivo)      // efectivo (con citas_extra si aplica)
                .limiteServicios(limiteServiciosEfectivo) // efectivo (con servicios_extra si aplica)
                .porcentajeUsuarios(porcentajeUsuarios)
                .porcentajeClientes(porcentajeClientes)
                .porcentajeCitasMes(porcentajeCitasMes)
                .porcentajeServicios(porcentajeServicios)
                .alertaUsuarios(UsoNegocioDTO.esAlerta(porcentajeUsuarios))
                .alertaClientes(UsoNegocioDTO.esAlerta(porcentajeClientes))
                .alertaCitasMes(UsoNegocioDTO.esAlerta(porcentajeCitasMes))
                .alertaServicios(UsoNegocioDTO.esAlerta(porcentajeServicios))
                .build();
    }

    /**
     * Valida si una funcionalidad está habilitada por email del usuario.
     * Retorna true/false en lugar de lanzar excepción.
     *
     * SOLUCIÓN LAZY LOADING:
     * - Marcado como @Transactional(readOnly = true)
     * - Usa JOIN FETCH para cargar Negocio eagerly
     * - Previene LazyInitializationException
     */
    @Transactional(readOnly = true)
    public boolean validarFuncionalidadPorEmail(String email, String funcionalidad) {
        log.info("[PlanLimitesService] Validando funcionalidad '{}' para usuario: {}", funcionalidad, email);

        // JOIN FETCH previene LazyInitializationException
        Usuario usuario = usuarioRepository.findByEmailWithNegocio(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        Negocio negocio = usuario.getNegocio();
        if (negocio == null) {
            throw new ResourceNotFoundException("Negocio no encontrado para el usuario");
        }

        TipoPlan plan = TipoPlan.fromCodigo(negocio.getPlan());
        PlanLimites limites = obtenerLimites(plan);

        boolean habilitada = switch (funcionalidad.toLowerCase()) {
            case "email", "email_recordatorios" -> limites.isEmailRecordatoriosHabilitado();
            case "sms", "whatsapp", "sms_whatsapp" -> limites.isSmsWhatsappHabilitado();
            case "reportes_avanzados" -> limites.isReportesAvanzadosHabilitado();
            case "personalizacion_email" -> limites.isPersonalizacionEmailHabilitado();
            case "soporte_prioritario" -> limites.isSoportePrioritario();
            default -> throw new IllegalArgumentException("Funcionalidad no reconocida: " + funcionalidad);
        };

        log.info("[PlanLimitesService] Funcionalidad '{}' está: {}", funcionalidad, habilitada ? "HABILITADA" : "DESHABILITADA");
        return habilitada;
    }
}
