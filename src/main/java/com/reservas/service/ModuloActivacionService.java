package com.reservas.service;

import com.reservas.dto.response.CheckoutResponse;
import com.reservas.entity.Modulo;
import com.reservas.entity.ModuloNegocio;
import com.reservas.entity.Negocio;
import com.reservas.entity.Pago;
import com.reservas.entity.Usuario;
import com.reservas.exception.NotFoundException;
import com.reservas.repository.ModuloNegocioRepository;
import com.reservas.repository.ModuloRepository;
import com.reservas.repository.NegocioRepository;
import com.reservas.repository.PagoRepository;
import com.reservas.repository.UsuarioRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio para activar y cancelar módulos del marketplace.
 *
 * Flujo de activación:
 *   1. El usuario llama a {@link #crearCheckoutModulo} → crea sesión de Stripe + registro Pago pendiente.
 *   2. Stripe llama al webhook con checkout.session.completed (metadata.tipo=modulo).
 *   3. {@link StripeWebhookController} detecta el tipo y llama a {@link #activarModulo}.
 *   4. {@link #activarModulo} activa el módulo y marca el Pago como completado.
 *   Nota: los módulos incluyen 7 días de prueba gratuita antes del primer cobro.
 *
 * Flujo de cancelación:
 *   El usuario llama a {@link #cancelarModulo} → se cancela en Stripe y se marca inactivo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModuloActivacionService {

    private final ModuloRepository moduloRepository;
    private final ModuloNegocioRepository moduloNegocioRepository;
    private final UsuarioRepository usuarioRepository;
    private final NegocioRepository negocioRepository;
    private final PagoRepository pagoRepository;

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    @Value("${stripe.success.url}")
    private String successUrl;

    @Value("${stripe.cancel.url}")
    private String cancelUrl;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
    }

    // ============================================================
    // Checkout
    // ============================================================

    /**
     * Crea una sesión de checkout de Stripe para activar un módulo.
     * Guarda un registro Pago (pending) para que aparezca en el historial.
     *
     * @param clave         Clave del módulo a activar
     * @param emailUsuario  Email del usuario autenticado
     * @return CheckoutResponse con URL de Stripe Hosted Checkout
     */
    @Transactional
    public CheckoutResponse crearCheckoutModulo(String clave, String emailUsuario) {
        log.info("[ModuloActivacion] Creando checkout para módulo '{}' - usuario: {}", clave, emailUsuario);

        Usuario usuario = usuarioRepository.findByEmailWithNegocio(emailUsuario)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        Negocio negocio = usuario.getNegocio();
        if (negocio == null) {
            throw new NotFoundException("Negocio no encontrado para el usuario");
        }

        Modulo modulo = moduloRepository.findByClave(clave)
                .orElseThrow(() -> new NotFoundException("Módulo no encontrado: " + clave));

        if (moduloNegocioRepository.existsByNegocioIdAndModuloClaveAndActivoTrue(negocio.getId(), clave)) {
            throw new RuntimeException("El módulo '" + clave + "' ya está activo en tu cuenta.");
        }

        String customerId = obtenerOCrearCustomer(negocio);

        // Precio en centavos MXN (sin decimales)
        long precioCentavos = modulo.getPrecioMensual()
                .multiply(new BigDecimal("100"))
                .longValueExact();

        Map<String, String> metadata = new HashMap<>();
        metadata.put("tipo", "modulo");
        metadata.put("modulo_clave", clave);
        metadata.put("negocio_id", negocio.getId().toString());
        metadata.put("usuario_id", usuario.getId().toString());

        // Construir line item: usar Price ID pre-creado si está configurado,
        // de lo contrario usar price_data dinámico como fallback.
        SessionCreateParams.LineItem lineItem;
        String stripePriceId = modulo.getStripePriceId();

        if (stripePriceId != null && !stripePriceId.isBlank()) {
            // ── Opción A: producto pre-creado en Stripe (recomendado) ──────────
            log.info("[ModuloActivacion] Usando Price ID '{}' para módulo '{}'", stripePriceId, clave);
            lineItem = SessionCreateParams.LineItem.builder()
                    .setPrice(stripePriceId)
                    .setQuantity(1L)
                    .build();
        } else {
            // ── Opción B: price_data dinámico (fallback si no hay Price ID) ───
            log.warn("[ModuloActivacion] Módulo '{}' sin Price ID — usando price_data dinámico", clave);
            lineItem = SessionCreateParams.LineItem.builder()
                    .setQuantity(1L)
                    .setPriceData(
                            SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency("mxn")
                                    .setUnitAmount(precioCentavos)
                                    .setProductData(
                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                    .setName(modulo.getNombre())
                                                    .setDescription(modulo.getDescripcion())
                                                    .build()
                                    )
                                    .setRecurring(
                                            SessionCreateParams.LineItem.PriceData.Recurring.builder()
                                                    .setInterval(SessionCreateParams.LineItem.PriceData.Recurring.Interval.MONTH)
                                                    .build()
                                    )
                                    .build()
                    )
                    .build();
        }

        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomer(customerId)
                    .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(cancelUrl)
                    .putAllMetadata(metadata)
                    .addLineItem(lineItem)
                    // 7 días de prueba gratuita antes del primer cobro
                    .setSubscriptionData(
                            SessionCreateParams.SubscriptionData.builder()
                                    .setTrialPeriodDays(7L)
                                    .build()
                    )
                    .setAutomaticTax(
                            SessionCreateParams.AutomaticTax.builder()
                                    .setEnabled(false)
                                    .build()
                    )
                    .build();

            Session session = Session.create(params);

            log.info("[ModuloActivacion] Sesión creada: {} para módulo: {}", session.getId(), clave);

            // ─── Registrar Pago pendiente ────────────────────────────────────────
            // Esto permite que el módulo aparezca en el historial de pagos desde
            // el momento en que el usuario inicia el proceso, incluso antes de completar.
            Pago pago = Pago.builder()
                    .negocio(negocio)
                    .stripeCheckoutSessionId(session.getId())
                    .stripeCustomerId(customerId)
                    .plan(clave)   // clave del módulo como identificador de "plan"
                    .monto(modulo.getPrecioMensual())
                    .moneda("MXN")
                    .estado("pending")
                    .emailCliente(emailUsuario)
                    .descripcion("Módulo: " + modulo.getNombre()
                            + " — 7 días gratis, luego $" + modulo.getPrecioMensual().toPlainString() + " MXN/mes")
                    .build();
            pagoRepository.save(pago);
            log.info("[ModuloActivacion] Pago pendiente registrado para módulo '{}'", clave);

            return CheckoutResponse.builder()
                    .sessionId(session.getId())
                    .url(session.getUrl())
                    .plan(clave)
                    .monto(modulo.getPrecioMensual().toString())
                    .moneda("MXN")
                    .build();

        } catch (StripeException e) {
            log.error("[ModuloActivacion] Error creando sesión para módulo '{}': {}", clave, e.getMessage());
            throw new RuntimeException("Error al crear sesión de pago: " + e.getMessage());
        }
    }

    // ============================================================
    // Activación (llamada desde webhook y desde session-status)
    // ============================================================

    /**
     * Activa un módulo para un negocio. Llamado desde el webhook de Stripe
     * cuando checkout.session.completed tiene metadata.tipo = "modulo",
     * y como respaldo desde {@link StripeService#obtenerEstadoSesion}.
     *
     * @param negocioId             ID del negocio
     * @param clave                 Clave del módulo a activar
     * @param stripeSubscriptionId  ID de la suscripción creada en Stripe
     * @param checkoutSessionId     ID de la sesión de checkout (para actualizar el Pago)
     */
    @Transactional
    public void activarModulo(UUID negocioId, String clave, String stripeSubscriptionId, String checkoutSessionId) {
        log.info("[ModuloActivacion] Activando módulo '{}' para negocio: {}", clave, negocioId);

        Negocio negocio = negocioRepository.findById(negocioId)
                .orElseThrow(() -> new NotFoundException("Negocio no encontrado: " + negocioId));

        Modulo modulo = moduloRepository.findByClave(clave)
                .orElseThrow(() -> new NotFoundException("Módulo no encontrado: " + clave));

        moduloNegocioRepository.findByNegocioIdAndModuloClaveAndActivoTrue(negocioId, clave)
                .ifPresentOrElse(
                        mn -> {
                            mn.setStripeSubscriptionId(stripeSubscriptionId);
                            moduloNegocioRepository.save(mn);
                            log.info("[ModuloActivacion] Módulo '{}' ya activo — subscriptionId actualizado", clave);
                        },
                        () -> {
                            ModuloNegocio mn = ModuloNegocio.builder()
                                    .negocio(negocio)
                                    .modulo(modulo)
                                    .stripeSubscriptionId(stripeSubscriptionId)
                                    .fechaActivacion(LocalDateTime.now())
                                    .activo(true)
                                    .build();
                            moduloNegocioRepository.save(mn);
                            log.info("[ModuloActivacion] Módulo '{}' activado exitosamente para negocio: {}", clave, negocioId);
                        }
                );

        // ─── Marcar el Pago como completado ──────────────────────────────────
        // Actualiza el registro pendiente creado en crearCheckoutModulo()
        if (checkoutSessionId != null) {
            pagoRepository.findByStripeCheckoutSessionId(checkoutSessionId)
                    .ifPresent(pago -> {
                        if (!"completed".equals(pago.getEstado())) {
                            pago.setEstado("completed");
                            pago.setFechaCompletado(LocalDateTime.now());
                            pagoRepository.save(pago);
                            log.info("[ModuloActivacion] Pago '{}' marcado como completado (módulo: {})",
                                    pago.getId(), clave);
                        }
                    });
        }
    }

    // ============================================================
    // Cancelación
    // ============================================================

    /**
     * Cancela un módulo activo del usuario. Cancela la suscripción en Stripe
     * y marca el registro como inactivo.
     *
     * @param clave        Clave del módulo a cancelar
     * @param emailUsuario Email del usuario autenticado
     */
    @Transactional
    public void cancelarModulo(String clave, String emailUsuario) {
        log.info("[ModuloActivacion] Cancelando módulo '{}' para usuario: {}", clave, emailUsuario);

        Usuario usuario = usuarioRepository.findByEmailWithNegocio(emailUsuario)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        UUID negocioId = usuario.getNegocio().getId();

        ModuloNegocio mn = moduloNegocioRepository
                .findByNegocioIdAndModuloClaveAndActivoTrue(negocioId, clave)
                .orElseThrow(() -> new RuntimeException(
                        "El módulo '" + clave + "' no está activo en tu cuenta."));

        if (mn.getStripeSubscriptionId() != null) {
            cancelarSuscripcionStripe(mn.getStripeSubscriptionId());
        }

        mn.setActivo(false);
        mn.setFechaCancelacion(LocalDateTime.now());
        moduloNegocioRepository.save(mn);

        log.info("[ModuloActivacion] Módulo '{}' cancelado para negocio: {}", clave, negocioId);
    }

    // ============================================================
    // Helpers privados
    // ============================================================

    private void cancelarSuscripcionStripe(String stripeSubscriptionId) {
        try {
            Subscription subscription = Subscription.retrieve(stripeSubscriptionId);
            subscription.cancel();
            log.info("[ModuloActivacion] Suscripción de Stripe cancelada: {}", stripeSubscriptionId);
        } catch (StripeException e) {
            log.warn("[ModuloActivacion] No se pudo cancelar suscripción en Stripe {}: {}",
                    stripeSubscriptionId, e.getMessage());
        }
    }

    private String obtenerOCrearCustomer(Negocio negocio) {
        if (negocio.getStripeCustomerId() != null) {
            return negocio.getStripeCustomerId();
        }

        try {
            CustomerCreateParams params = CustomerCreateParams.builder()
                    .setEmail(negocio.getEmail())
                    .setName(negocio.getNombre())
                    .putMetadata("negocio_id", negocio.getId().toString())
                    .build();

            Customer customer = Customer.create(params);
            negocio.setStripeCustomerId(customer.getId());
            negocioRepository.save(negocio);

            log.info("[ModuloActivacion] Customer de Stripe creado: {} para negocio: {}",
                    customer.getId(), negocio.getNombre());

            return customer.getId();
        } catch (StripeException e) {
            throw new RuntimeException("Error al crear cliente en Stripe: " + e.getMessage());
        }
    }
}
