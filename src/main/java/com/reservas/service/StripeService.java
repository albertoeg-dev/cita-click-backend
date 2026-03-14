package com.reservas.service;

import com.reservas.dto.request.CheckoutRequest;
import com.reservas.dto.response.CheckoutResponse;
import com.reservas.dto.response.PagoResponse;
import com.reservas.entity.Negocio;
import com.reservas.entity.Pago;
import com.reservas.entity.Usuario;
import com.reservas.exception.NotFoundException;
import com.reservas.repository.NegocioRepository;
import com.reservas.repository.PagoRepository;
import com.reservas.repository.UsuarioRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Customer;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionRetrieveParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio para integración con Stripe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripeService {

    private final PagoRepository pagoRepository;
    private final UsuarioRepository usuarioRepository;
    private final NegocioRepository negocioRepository;
    private final SuscripcionService suscripcionService;

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    @Value("${stripe.success.url}")
    private String successUrl;

    @Value("${stripe.cancel.url}")
    private String cancelUrl;

    @Value("${stripe.price.base}")
    private String priceIdBase;

    @Value("${stripe.price.completo}")
    private String priceIdCompleto;

    // Precios en MXN (centavos)
    private static final Map<String, Long> PLAN_PRICES = Map.of(
            "base",     29900L,    // $299.00 MXN
            "completo", 119900L,   // $1,199.00 MXN
            // aliases legacy
            "basico",   29900L,
            "premium",  119900L
    );

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
        log.info("[Stripe] Servicio inicializado con API Key");
    }

    /**
     * Crea una sesión de checkout para un plan
     */
    @Transactional
    public CheckoutResponse crearCheckoutSession(CheckoutRequest request, String emailUsuario) {
        log.info("[Stripe] Creando sesión de checkout para plan: {} - usuario: {}", request.getPlan(), emailUsuario);

        try {
            // Obtener usuario y negocio
            Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                    .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

            Negocio negocio = usuario.getNegocio();
            if (negocio == null) {
                throw new NotFoundException("Negocio no encontrado");
            }

            // -------------------------------------------------------------------
            // VALIDACIÓN: Bloquear si ya tiene suscripción activa
            // Evita que un usuario cree dos suscripciones simultáneas
            // -------------------------------------------------------------------
            if ("activo".equals(negocio.getEstadoPago()) && negocio.getStripeSubscriptionId() != null) {
                log.warn("[Stripe] Usuario {} intentó crear checkout con suscripción ya activa (sub: {})",
                        emailUsuario, negocio.getStripeSubscriptionId());
                throw new RuntimeException(
                        "Ya tienes una suscripción activa. Puedes administrarla desde tu perfil."
                );
            }

            // Obtener o crear cliente de Stripe
            String customerId = obtenerOCrearCustomer(negocio);

            // Obtener el Price ID según el plan
            String priceId = getPriceIdForPlan(request.getPlan());

            // -------------------------------------------------------------------
            // SIN TRIAL DE STRIPE: El período gratuito de 7 días ya lo gestiona
            // el sistema (Negocio.fechaFinPrueba). Stripe cobra inmediatamente.
            // -------------------------------------------------------------------

            // Crear metadata
            Map<String, String> metadata = new HashMap<>();
            metadata.put("negocio_id", negocio.getId().toString());
            metadata.put("usuario_id", usuario.getId().toString());
            metadata.put("plan", request.getPlan());
            metadata.put("email", emailUsuario);
            if (request.getReferencia() != null) {
                metadata.put("referencia", request.getReferencia());
            }

            // Crear sesión de checkout (Hosted Checkout - redirect a Stripe, cobro inmediato)
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomer(customerId)
                    .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(cancelUrl)
                    .putAllMetadata(metadata)
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setPrice(priceId)
                                    .setQuantity(1L)
                                    .build()
                    )
                    .setAutomaticTax(
                            SessionCreateParams.AutomaticTax.builder()
                                    .setEnabled(false)
                                    .build()
                    )
                    .build();

            log.info("[Stripe] Checkout con cobro inmediato (sin trial) para: {}", emailUsuario);

            Session session = Session.create(params);

            // Guardar registro de pago pendiente
            Pago pago = Pago.builder()
                    .negocio(negocio)
                    .stripeCheckoutSessionId(session.getId())
                    .stripeCustomerId(customerId)
                    .plan(request.getPlan())
                    .monto(new BigDecimal(PLAN_PRICES.get(request.getPlan())).divide(new BigDecimal(100)))
                    .moneda("MXN")
                    .estado("pending")
                    .emailCliente(emailUsuario)
                    .descripcion("Suscripción mensual - Plan " + request.getPlan().toUpperCase())
                    .build();

            pagoRepository.save(pago);

            log.info("[Stripe]  Sesión de checkout creada: {}", session.getId());

            return CheckoutResponse.builder()
                    .sessionId(session.getId())
                    .clientSecret(null) // No se usa en Hosted Checkout
                    .url(session.getUrl()) // URL de Stripe Hosted Checkout
                    .plan(request.getPlan())
                    .monto(pago.getMonto().toString())
                    .moneda("MXN")
                    .build();

        } catch (StripeException e) {
            log.error("[Stripe] Error creando sesión de checkout", e);
            throw new RuntimeException("Error al crear sesión de pago: " + e.getMessage());
        }
    }

    /**
     * Obtiene el estado de una sesión de checkout.
     * Además, si la sesión está completa y es una suscripción, activa la suscripción
     * como respaldo del webhook (patrón recomendado por Stripe).
     */
    @Transactional
    public Map<String, Object> obtenerEstadoSesion(String sessionId) {
        log.info("[Stripe] Obteniendo estado de sesión: {}", sessionId);

        try {
            // Recuperar sesión con metadata expandida
            SessionRetrieveParams params = SessionRetrieveParams.builder()
                    .addExpand("line_items")
                    .build();
            Session session = Session.retrieve(sessionId, params, null);

            Map<String, Object> response = new HashMap<>();
            response.put("status", session.getStatus());
            response.put("payment_status", session.getPaymentStatus());

            if (session.getCustomerDetails() != null) {
                response.put("customer_email", session.getCustomerDetails().getEmail());
            }

            if (session.getPaymentIntent() != null) {
                response.put("payment_intent", session.getPaymentIntent());
            }

            // ============================================================
            // RESPALDO: Activar suscripción si la sesión está completa
            // Esto asegura la activación incluso si el webhook no llega
            // (ej: desarrollo local sin Stripe CLI)
            // ============================================================
            if ("complete".equals(session.getStatus()) && "subscription".equals(session.getMode())) {
                try {
                    String negocioId = session.getMetadata().get("negocio_id");
                    String plan = session.getMetadata().get("plan");

                    if (negocioId != null && plan != null) {
                        Negocio negocio = negocioRepository.findById(UUID.fromString(negocioId))
                                .orElse(null);

                        // Solo activar si aún no está activo con este plan
                        if (negocio != null && (!"activo".equals(negocio.getEstadoPago()) || !plan.equals(negocio.getPlan()))) {
                            log.info("[Stripe] Activando suscripción desde session-status (respaldo webhook)");
                            procesarSuscripcionCreada(session);
                        }
                    }
                } catch (Exception e) {
                    // No fallar la consulta de estado si la activación falla
                    log.warn("[Stripe] Error en activación de respaldo: {}", e.getMessage());
                }
            } else if ("complete".equals(session.getStatus()) && "paid".equals(session.getPaymentStatus())
                    && session.getPaymentIntent() != null) {
                // Pago único completado
                try {
                    Pago pago = pagoRepository.findByStripeCheckoutSessionId(sessionId).orElse(null);
                    if (pago != null && !pago.isPagado()) {
                        log.info("[Stripe] Procesando pago desde session-status (respaldo webhook)");
                        procesarPagoCompletado(sessionId, session.getPaymentIntent());
                    }
                } catch (Exception e) {
                    log.warn("[Stripe] Error en procesamiento de pago de respaldo: {}", e.getMessage());
                }
            }

            return response;

        } catch (StripeException e) {
            log.error("[Stripe] Error obteniendo estado de sesión", e);
            throw new RuntimeException("Error al obtener estado de sesión: " + e.getMessage());
        }
    }

    /**
     * Procesa el webhook de Stripe cuando se completa un pago
     */
    @Transactional
    public void procesarPagoCompletado(String sessionId, String paymentIntentId) {
        log.info("[Stripe] Procesando pago completado - Session: {} - PaymentIntent: {}", sessionId, paymentIntentId);

        try {
            // Buscar el pago por session ID
            Pago pago = pagoRepository.findByStripeCheckoutSessionId(sessionId)
                    .orElseThrow(() -> new NotFoundException("Pago no encontrado para session: " + sessionId));

            // Si ya fue procesado, ignorar
            if (pago.isPagado()) {
                log.info("[Stripe] Pago ya procesado anteriormente: {}", pago.getId());
                return;
            }

            // Obtener detalles del PaymentIntent
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);

            // Actualizar el pago
            pago.setStripePaymentIntentId(paymentIntentId);
            pago.setEstado("completed");
            pago.setFechaCompletado(LocalDateTime.now());

            // Configurar período (30 días desde ahora)
            LocalDateTime ahora = LocalDateTime.now();
            pago.setPeriodoInicio(ahora);
            pago.setPeriodoFin(ahora.plusDays(30));

            // Método de pago — recuperar el tipo ("card", "oxxo", "spei", etc.) no el ID
            if (paymentIntent.getPaymentMethod() != null) {
                try {
                    PaymentMethod pm = PaymentMethod.retrieve(paymentIntent.getPaymentMethod());
                    pago.setMetodoPago(pm.getType());
                } catch (Exception e) {
                    log.warn("[Stripe] No se pudo obtener tipo de método de pago: {}", e.getMessage());
                }
            }

            // URL de recibo (factura) desde el Charge
            if (paymentIntent.getLatestCharge() != null) {
                try {
                    Charge charge = Charge.retrieve(paymentIntent.getLatestCharge());
                    if (charge.getReceiptUrl() != null) {
                        pago.setFacturaUrl(charge.getReceiptUrl());
                    }
                } catch (Exception e) {
                    log.warn("[Stripe] No se pudo obtener URL de recibo: {}", e.getMessage());
                }
            }

            pagoRepository.save(pago);

            // Activar la suscripción del negocio
            suscripcionService.activarSuscripcion(
                    pago.getNegocio().getId().toString(),
                    pago.getPlan()
            );

            log.info("[Stripe]  Pago procesado exitosamente: {} - Negocio: {}",
                    pago.getId(), pago.getNegocio().getNombre());

        } catch (StripeException e) {
            log.error("[Stripe] Error procesando pago completado", e);
            throw new RuntimeException("Error al procesar pago: " + e.getMessage());
        }
    }

    /**
     * Procesa el webhook cuando falla un pago
     */
    @Transactional
    public void procesarPagoFallido(String sessionId, String errorMessage) {
        log.warn("[Stripe] Procesando pago fallido - Session: {}", sessionId);

        Pago pago = pagoRepository.findByStripeCheckoutSessionId(sessionId)
                .orElseThrow(() -> new NotFoundException("Pago no encontrado"));

        pago.setEstado("failed");
        pago.setErrorMensaje(errorMessage);
        pagoRepository.save(pago);

        log.info("[Stripe] Pago marcado como fallido: {}", pago.getId());
    }

    /**
     * Obtiene el historial de pagos de un negocio.
     * Auto-reconcilia pagos pendientes con Stripe para reflejar el estado real.
     */
    @Transactional
    public List<PagoResponse> obtenerHistorialPagos(String emailUsuario) {
        log.info("[Stripe] Obteniendo historial de pagos para: {}", emailUsuario);

        // JOIN FETCH previene LazyInitializationException
        Usuario usuario = usuarioRepository.findByEmailWithNegocio(emailUsuario)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        List<Pago> pagos = pagoRepository.findByNegocioOrderByFechaCreacionDesc(usuario.getNegocio());

        // ----------------------------------------------------------------
        // AUTO-RECONCILIACIÓN: sincronizar pagos pendientes con Stripe.
        // Los pagos creados antes del fix del webhook (o cuando el webhook
        // no llegó) quedan como "pending" indefinidamente en la DB.
        // Al cargar el historial, los sincronizamos en tiempo real.
        // ----------------------------------------------------------------
        long pendientes = pagos.stream().filter(Pago::isPendiente).count();
        if (pendientes > 0) {
            log.info("[Stripe] Encontrados {} pago(s) pendiente(s) — sincronizando con Stripe...", pendientes);
            pagos.stream()
                    .filter(p -> p.isPendiente() && p.getStripeCheckoutSessionId() != null)
                    .forEach(this::sincronizarPagoPendiente);
            // Recargar lista para reflejar los cambios guardados
            pagos = pagoRepository.findByNegocioOrderByFechaCreacionDesc(usuario.getNegocio());
        }

        return pagos.stream()
                .map(this::mapToPagoResponse)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene estadísticas de pagos del negocio
     */
    @Transactional(readOnly = true)
    public Map<String, Object> obtenerEstadisticas(String emailUsuario) {
        log.info("[Stripe] Obteniendo estadísticas de pagos para: {}", emailUsuario);

        Usuario usuario = usuarioRepository.findByEmailWithNegocio(emailUsuario)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        Negocio negocio = usuario.getNegocio();
        if (negocio == null) {
            throw new NotFoundException("Negocio no encontrado para el usuario");
        }

        long totalPagos = pagoRepository.countPagosCompletadosByNegocio(negocio);
        BigDecimal montoTotal = pagoRepository.sumMontoByNegocioAndEstadoCompleted(negocio);

        if (montoTotal == null) {
            montoTotal = BigDecimal.ZERO;
        }

        return Map.of(
                "totalPagos", totalPagos,
                "montoTotal", montoTotal
        );
    }

    // ==================== Métodos auxiliares ====================

    /**
     * Actualiza un Pago pendiente con los datos reales de una suscripción de Stripe:
     * estado, período de facturación, método de pago y URL de factura.
     * Maneja cualquier excepción internamente para no interrumpir el flujo principal.
     */
    private void actualizarPagoDesdeSubscripcion(Pago pago, String subscriptionId) {
        try {
            Subscription stripeSubscription = Subscription.retrieve(subscriptionId);

            pago.setEstado("completed");
            pago.setFechaCompletado(LocalDateTime.now());

            // Período de facturación real de Stripe
            pago.setPeriodoInicio(LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(stripeSubscription.getCurrentPeriodStart()),
                    ZoneId.systemDefault()));
            pago.setPeriodoFin(LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(stripeSubscription.getCurrentPeriodEnd()),
                    ZoneId.systemDefault()));

            // Obtener datos de factura y método de pago desde la última Invoice
            String latestInvoiceId = stripeSubscription.getLatestInvoice();
            if (latestInvoiceId != null) {
                Invoice invoice = Invoice.retrieve(latestInvoiceId);

                if (invoice.getHostedInvoiceUrl() != null) {
                    pago.setFacturaUrl(invoice.getHostedInvoiceUrl());
                }

                String invoicePaymentIntentId = invoice.getPaymentIntent();
                if (invoicePaymentIntentId != null) {
                    PaymentIntent pi = PaymentIntent.retrieve(invoicePaymentIntentId);
                    // Solo asignar si el campo está vacío (evita violación de restricción UNIQUE)
                    if (pago.getStripePaymentIntentId() == null) {
                        pago.setStripePaymentIntentId(pi.getId());
                    }
                    if (pi.getPaymentMethod() != null) {
                        PaymentMethod pm = PaymentMethod.retrieve(pi.getPaymentMethod());
                        pago.setMetodoPago(pm.getType());
                    }
                    // Recibo como respaldo si no hay hostedInvoiceUrl
                    if (pi.getLatestCharge() != null && pago.getFacturaUrl() == null) {
                        Charge charge = Charge.retrieve(pi.getLatestCharge());
                        if (charge.getReceiptUrl() != null) {
                            pago.setFacturaUrl(charge.getReceiptUrl());
                        }
                    }
                }
            }

            pagoRepository.save(pago);
            log.info("[Stripe] ✅ Pago {} sincronizado → completado (sub: {})", pago.getId(), subscriptionId);

        } catch (Exception e) {
            log.warn("[Stripe] No se pudo sincronizar pago {} con suscripción {}: {}",
                    pago.getId(), subscriptionId, e.getMessage());
        }
    }

    /**
     * Intenta sincronizar un Pago pendiente contra Stripe.
     * Primero usa el subscriptionId del Negocio (ruta rápida, sin llamada extra a Stripe).
     * Si no existe, recupera la sesión de checkout para obtener el subscriptionId.
     */
    private void sincronizarPagoPendiente(Pago pago) {
        try {
            // Ruta rápida: el Negocio ya tiene el subscription ID guardado
            String subscriptionId = pago.getNegocio().getStripeSubscriptionId();
            if (subscriptionId != null) {
                actualizarPagoDesdeSubscripcion(pago, subscriptionId);
                return;
            }

            // Respaldo: recuperar sesión de Stripe para obtener el subscription ID
            Session session = Session.retrieve(pago.getStripeCheckoutSessionId());
            if ("complete".equals(session.getStatus())
                    && "subscription".equals(session.getMode())
                    && session.getSubscription() != null) {
                actualizarPagoDesdeSubscripcion(pago, session.getSubscription());
            }

        } catch (Exception e) {
            log.warn("[Stripe] No se pudo sincronizar pago pendiente {}: {}", pago.getId(), e.getMessage());
        }
    }

    /**
     * Obtiene o crea un cliente de Stripe para el negocio
     */
    private String obtenerOCrearCustomer(Negocio negocio) throws StripeException {
        // Si ya tiene un Stripe Customer ID guardado, usarlo
        if (negocio.getStripeCustomerId() != null) {
            return negocio.getStripeCustomerId();
        }

        // Crear nuevo cliente en Stripe
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(negocio.getEmail())
                .setName(negocio.getNombre())
                .putMetadata("negocio_id", negocio.getId().toString())
                .build();

        Customer customer = Customer.create(params);

        // Guardar el Customer ID en el negocio
        negocio.setStripeCustomerId(customer.getId());
        negocioRepository.save(negocio);

        log.info("[Stripe]  Cliente creado en Stripe: {} para negocio: {}", customer.getId(), negocio.getNombre());

        return customer.getId();
    }

    /**
     * Obtiene el Price ID de Stripe para un plan
     */
    private String getPriceIdForPlan(String plan) {
        return switch (plan.toLowerCase()) {
            case "base", "basico"                    -> priceIdBase;
            case "completo", "bundle", "premium"     -> priceIdCompleto;
            default -> throw new IllegalArgumentException("Plan inválido: " + plan);
        };
    }

    /**
     * Mapea entidad Pago a DTO
     */
    private PagoResponse mapToPagoResponse(Pago pago) {
        return PagoResponse.builder()
                .id(pago.getId().toString())
                .plan(pago.getPlan())
                .monto(pago.getMonto())
                .moneda(pago.getMoneda())
                .estado(pago.getEstado())
                .metodoPago(pago.getMetodoPago())
                .periodoInicio(pago.getPeriodoInicio())
                .periodoFin(pago.getPeriodoFin())
                .descripcion(pago.getDescripcion())
                .facturaUrl(pago.getFacturaUrl())
                .fechaCreacion(pago.getFechaCreacion())
                .fechaCompletado(pago.getFechaCompletado())
                .errorMensaje(pago.getErrorMensaje())
                .build();
    }

    /**
     * Procesa la creación de una suscripción cuando se completa el checkout
     */
    @Transactional
    public void procesarSuscripcionCreada(com.stripe.model.checkout.Session session) {
        log.info("[Stripe] Procesando suscripción creada - Session: {}", session.getId());

        try {
            // Obtener metadata
            String usuarioId = session.getMetadata().get("usuario_id");
            String negocioId = session.getMetadata().get("negocio_id");
            String plan = session.getMetadata().get("plan");

            if (usuarioId == null || negocioId == null) {
                log.error("[Stripe] Metadata incompleta en la sesión");
                return;
            }

            // Obtener usuario y marcar que ya tiene suscripción (para tracking)
            Usuario usuario = usuarioRepository.findById(UUID.fromString(usuarioId))
                    .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

            if (!usuario.isTrialUsed()) {
                usuario.setTrialUsed(true); // Marca que el usuario ya se suscribió (consumió su trial gratuito)
                usuarioRepository.save(usuario);
                log.info("[Stripe] Usuario marcado como suscrito: {}", usuario.getEmail());
            }

            // Obtener el negocio
            Negocio negocio = negocioRepository.findById(UUID.fromString(negocioId))
                    .orElseThrow(() -> new NotFoundException("Negocio no encontrado"));

            // Activar suscripción
            suscripcionService.activarSuscripcion(negocioId, plan);

            // Guardar subscription ID en el negocio
            negocio.setStripeSubscriptionId(session.getSubscription());
            negocioRepository.save(negocio);

            log.info("[Stripe] ✅ Suscripción activada para negocio: {} - Plan: {}", negocio.getNombre(), plan);

            // ----------------------------------------------------------------
            // ACTUALIZAR el registro Pago de "pending" → "completed"
            // Para checkout en modo subscription, procesarPagoCompletado nunca
            // se llama, por lo que el Pago queda como pending indefinidamente.
            // ----------------------------------------------------------------
            Pago pago = pagoRepository.findByStripeCheckoutSessionId(session.getId()).orElse(null);
            if (pago != null && !pago.isPagado() && session.getSubscription() != null) {
                actualizarPagoDesdeSubscripcion(pago, session.getSubscription());
            }

        } catch (Exception e) {
            log.error("[Stripe] Error procesando suscripción creada", e);
            throw new RuntimeException("Error al procesar suscripción: " + e.getMessage());
        }
    }
}
