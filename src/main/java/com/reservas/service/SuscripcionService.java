package com.reservas.service;

import com.reservas.entity.Negocio;
import com.reservas.entity.RegistroIP;
import com.reservas.entity.Usuario;
import com.reservas.exception.IpBloqueadaException;
import com.reservas.exception.SuscripcionVencidaException;
import com.reservas.repository.NegocioRepository;
import com.reservas.repository.RegistroIPRepository;
import com.reservas.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuscripcionService {

    private final NegocioRepository negocioRepository;
    private final UsuarioRepository usuarioRepository;
    private final RegistroIPRepository registroIPRepository;
    private final EmailService emailService;

    @Value("${app.frontend.url:https://app.citaclick.com.mx}")
    private String frontendUrl;

    // Configuración de límites
    private static final int MAX_REGISTROS_PRUEBA_POR_IP = 3; // Máximo 3 pruebas desde la misma IP
    private static final int DIAS_VALIDEZ_IP = 90; // Verificar registros de los últimos 90 días

    /**
     * Valida si una IP puede crear una cuenta de prueba
     */
    public void validarRegistroIP(String ipAddress) {
        LocalDateTime hace90Dias = LocalDateTime.now().minusDays(DIAS_VALIDEZ_IP);

        long registrosPrueba = registroIPRepository.countRegistrosPruebaByIP(ipAddress, hace90Dias);

        if (registrosPrueba >= MAX_REGISTROS_PRUEBA_POR_IP) {
            log.warn("[SuscripciónIP] IP bloqueada por exceso de registros de prueba: {} ({} registros)",
                    ipAddress, registrosPrueba);

            throw new IpBloqueadaException(
                    "Has excedido el límite de cuentas de prueba. " +
                    "Si deseas continuar, por favor selecciona un plan de pago o contacta a soporte."
            );
        }

        log.debug("[SuscripciónIP] IP permitida: {} ({} registros previos)", ipAddress, registrosPrueba);
    }

    /**
     * Registra una nueva IP al crear una cuenta
     */
    @Transactional
    public void registrarIP(Negocio negocio, String ipAddress, String userAgent) {
        RegistroIP registro = RegistroIP.builder()
                .negocio(negocio)
                .email(negocio.getEmail())
                .ipAddress(ipAddress)
                .userAgent(userAgent != null ? userAgent : "Unknown")
                .esPrueba(negocio.isEnPeriodoPrueba())
                .activo(true)
                .build();

        registroIPRepository.save(registro);
        log.info("[SuscripciónIP] IP registrada: {} para negocio: {}", ipAddress, negocio.getNombre());
    }

    /**
     * Valida si el negocio puede usar el sistema
     */
    public void validarAcceso(String emailUsuario) {
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Negocio negocio = usuario.getNegocio();
        if (negocio == null) {
            throw new RuntimeException("Negocio no encontrado");
        }

        if (!negocio.puedeUsarSistema()) {
            if (negocio.haVencidoPrueba()) {
                throw new SuscripcionVencidaException(
                        "Tu periodo de prueba ha vencido. Por favor, selecciona un plan para continuar."
                );
            } else if ("vencido".equals(negocio.getEstadoPago())) {
                throw new SuscripcionVencidaException(
                        "Tu suscripción ha vencido. Por favor, realiza el pago para reactivar tu cuenta."
                );
            } else if ("suspendido".equals(negocio.getEstadoPago())) {
                throw new SuscripcionVencidaException(
                        "Tu cuenta ha sido suspendida. Contacta soporte para más información."
                );
            } else if ("pendiente_pago".equals(negocio.getEstadoPago())) {
                throw new SuscripcionVencidaException(
                        "Debes completar el pago para activar tu cuenta Premium."
                );
            } else {
                throw new SuscripcionVencidaException(
                        "Tu cuenta no está activa. Por favor, actualiza tu método de pago."
                );
            }
        }

        log.debug("[Suscripción]  Acceso permitido para negocio: {}", negocio.getNombre());
    }

    /**
     * Obtiene información de la suscripción
     */
    @Transactional(readOnly = true)
    public SuscripcionInfo obtenerInfo(String emailUsuario) {
        // JOIN FETCH previene LazyInitializationException
        Usuario usuario = usuarioRepository.findByEmailWithNegocio(emailUsuario).orElseThrow();
        Negocio negocio = usuario.getNegocio();

        return SuscripcionInfo.builder()
                .plan(negocio.getPlan())
                .estadoPago(negocio.getEstadoPago())
                .enPeriodoPrueba(negocio.isEnPeriodoPrueba())
                .diasRestantesPrueba(negocio.diasRestantesPrueba())
                .diasRestantesVencimiento(negocio.diasRestantesVencimiento())
                .fechaFinPrueba(negocio.getFechaFinPrueba())
                .fechaProximoCobro(negocio.getFechaProximoCobro())
                .cuentaActiva(negocio.isCuentaActiva())
                .puedeUsarSistema(negocio.puedeUsarSistema())
                .necesitaNotificacion(negocio.necesitaNotificacionPrueba() ||
                                     negocio.necesitaNotificacionVencimiento())
                .build();
    }

    /**
     * Envía notificaciones de vencimiento
     */
    @Transactional
    public void enviarNotificaciones() {
        log.info("[Notificaciones] Iniciando envío de notificaciones...");

        List<Negocio> negocios = negocioRepository.findAll();
        int notificacionesEnviadas = 0;

        for (Negocio negocio : negocios) {
            try {
                // Notificación 1 día antes de fin de prueba
                if (negocio.necesitaNotificacionPrueba()) {
                    enviarNotificacionFinPrueba(negocio);
                    negocio.setNotificacionPruebaEnviada(true);
                    negocioRepository.save(negocio);
                    notificacionesEnviadas++;
                }

                // Notificación 5 días antes de vencimiento mensual
                if (negocio.necesitaNotificacionVencimiento()) {
                    enviarNotificacionVencimiento(negocio);
                    negocio.setNotificacionVencimientoEnviada(true);
                    negocioRepository.save(negocio);
                    notificacionesEnviadas++;
                }
            } catch (Exception e) {
                log.error("[Notificaciones] Error enviando notificación a {}", negocio.getNombre(), e);
            }
        }

        log.info("[Notificaciones]  {} notificaciones enviadas", notificacionesEnviadas);
    }

    /**
     * Verifica suscripciones vencidas y las desactiva
     */
    @Transactional
    public void verificarSuscripcionesVencidas() {
        log.info("[Verificación] Verificando suscripciones vencidas...");

        List<Negocio> negocios = negocioRepository.findAll();
        int desactivados = 0;

        for (Negocio negocio : negocios) {
            try {
                // Verificar si venció el periodo de prueba
                if (negocio.haVencidoPrueba() && negocio.isCuentaActiva()) {
                    log.warn("[Verificación] Periodo de prueba vencido: {}", negocio.getNombre());

                    negocio.setCuentaActiva(false);
                    negocio.setEstadoPago("vencido");
                    negocioRepository.save(negocio);

                    // Actualizar registros de IP
                    actualizarRegistrosIP(negocio, false);

                    desactivados++;
                }

                // Verificar si venció la suscripción mensual
                if (!negocio.isEnPeriodoPrueba() &&
                    "activo".equals(negocio.getEstadoPago()) &&
                    negocio.getFechaProximoCobro() != null &&
                    LocalDateTime.now().isAfter(negocio.getFechaProximoCobro())) {

                    log.warn("[Verificación] Suscripción vencida: {}", negocio.getNombre());

                    negocio.setCuentaActiva(false);
                    negocio.setEstadoPago("vencido");
                    negocioRepository.save(negocio);

                    // Enviar email de vencimiento
                    enviarEmailSuscripcionVencida(negocio);

                    desactivados++;
                }
            } catch (Exception e) {
                log.error("[Verificación] Error procesando negocio {}", negocio.getNombre(), e);
            }
        }

        log.info("[Verificación]  {} cuentas desactivadas", desactivados);
    }

    /**
     * Activa suscripción después de pago
     */
    @Transactional
    public void activarSuscripcion(String negocioId, String plan) {
        Negocio negocio = negocioRepository.findById(java.util.UUID.fromString(negocioId))
                .orElseThrow(() -> new RuntimeException("Negocio no encontrado"));

        negocio.setEstadoPago("activo");
        negocio.setEnPeriodoPrueba(false);
        negocio.setCuentaActiva(true);
        negocio.setPlan(plan);
        negocio.setFechaInicioPlan(LocalDateTime.now());
        negocio.setFechaProximoCobro(LocalDateTime.now().plusDays(30)); // Próximo cobro en 30 días

        // Resetear banderas de notificaciones
        negocio.setNotificacionPruebaEnviada(false);
        negocio.setNotificacionVencimientoEnviada(false);

        negocioRepository.save(negocio);

        // Actualizar registros de IP
        actualizarRegistrosIP(negocio, false); // Ya no es prueba

        log.info("[Suscripción]  Suscripción activada para: {} - Plan: {}", negocio.getNombre(), plan);
    }

    /**
     * Renueva la fecha de próximo cobro cuando Stripe confirma el pago de una factura.
     * Se llama desde el webhook invoice.paid en cada ciclo de facturación.
     *
     * Recibe la fecha real de Stripe (current_period_end de la suscripción)
     * y resetea la bandera de notificación para que el aviso de 5 días
     * vuelva a enviarse en el próximo ciclo.
     */
    @Transactional
    public void renovarSuscripcion(String stripeSubscriptionId, LocalDateTime fechaProximoCobro) {
        Negocio negocio = negocioRepository.findByStripeSubscriptionId(stripeSubscriptionId)
                .orElse(null);

        if (negocio == null) {
            log.warn("[Renovación] No se encontró negocio para subscription: {}", stripeSubscriptionId);
            return;
        }

        // Solo actualizar si la suscripción ya está activa (no para el primer pago,
        // que ya lo maneja procesarSuscripcionCreada)
        if ("activo".equals(negocio.getEstadoPago())) {
            negocio.setFechaProximoCobro(fechaProximoCobro);
            negocio.setNotificacionVencimientoEnviada(false); // Resetear para el próximo ciclo
            negocio.setCuentaActiva(true); // Por si estaba vencida y ya pagó
            negocioRepository.save(negocio);
            log.info("[Renovación] ✅ Suscripción renovada: {} - Próximo cobro: {}",
                    negocio.getNombre(), fechaProximoCobro);
        }
    }

    // Métodos privados de notificación

    private void enviarNotificacionFinPrueba(Negocio negocio) {
        // URL de login que redirige directamente a la sección de planes
        String loginUrl = frontendUrl + "/login?redirect=/planes&from=trial-ending";

        boolean enviado = emailService.enviarEmailFinPrueba(negocio.getEmail(), negocio.getNombre(), loginUrl);
        if (enviado) {
            log.info("[Notificación] Email HTML de fin de prueba enviado a: {}", negocio.getEmail());
        } else {
            log.warn("[Notificación] No se pudo enviar email de fin de prueba a: {}", negocio.getEmail());
        }
    }

    private void enviarNotificacionVencimiento(Negocio negocio) {
        long diasRestantes = negocio.diasRestantesVencimiento();

        String asunto = String.format("Tu suscripción vence en %d días", diasRestantes);
        String mensaje = String.format(
                "Hola %s,\n\n" +
                "Tu suscripción al plan %s vence en %d días.\n\n" +
                "Fecha de vencimiento: %s\n\n" +
                "Para continuar sin interrupciones, asegúrate de realizar el pago antes de la fecha de vencimiento.\n\n" +
                "Ingresa a tu cuenta para ver los detalles de pago.\n\n" +
                "Saludos,\n" +
                "El equipo de Cita Click",
                negocio.getNombre(),
                negocio.getPlan().toUpperCase(),
                diasRestantes,
                negocio.getFechaProximoCobro()
        );

        emailService.enviarEmail(negocio.getEmail(), asunto, mensaje);
        log.info("[Notificación] Email de vencimiento enviado a: {} ({} días)", negocio.getEmail(), diasRestantes);
    }

    private void enviarEmailSuscripcionVencida(Negocio negocio) {
        String asunto = "Tu suscripción ha vencido";
        String mensaje = String.format(
                "Hola %s,\n\n" +
                "Tu suscripción al plan %s ha vencido.\n\n" +
                "Tu cuenta ha sido suspendida temporalmente.\n\n" +
                "Para reactivar tu cuenta, realiza el pago correspondiente.\n\n" +
                "Ingresa a tu cuenta para más detalles.\n\n" +
                "Saludos,\n" +
                "El equipo de Cita Click",
                negocio.getNombre(),
                negocio.getPlan().toUpperCase()
        );

        emailService.enviarEmail(negocio.getEmail(), asunto, mensaje);
        log.info("[Notificación] Email de suscripción vencida enviado a: {}", negocio.getEmail());
    }

    private void actualizarRegistrosIP(Negocio negocio, boolean esPrueba) {
        List<RegistroIP> registros = registroIPRepository.findByIpAddress(negocio.getEmail());
        for (RegistroIP registro : registros) {
            if (registro.getNegocio().getId().equals(negocio.getId())) {
                registro.setEsPrueba(esPrueba);
                registro.setActivo(negocio.isCuentaActiva());
                registroIPRepository.save(registro);
            }
        }
    }
}

// DTO para información de suscripción
@lombok.Data
@lombok.Builder
class SuscripcionInfo {
    private String plan;
    private String estadoPago;
    private boolean enPeriodoPrueba;
    private long diasRestantesPrueba;
    private long diasRestantesVencimiento;
    private LocalDateTime fechaFinPrueba;
    private LocalDateTime fechaProximoCobro;
    private boolean cuentaActiva;
    private boolean puedeUsarSistema;
    private boolean necesitaNotificacion;
}
