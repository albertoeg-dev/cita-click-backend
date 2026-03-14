package com.reservas.service;

import com.reservas.dto.response.CheckoutResponse;
import com.reservas.entity.Modulo;
import com.reservas.entity.ModuloNegocio;
import com.reservas.entity.Negocio;
import com.reservas.entity.Usuario;
import com.reservas.exception.NotFoundException;
import com.reservas.repository.ModuloNegocioRepository;
import com.reservas.repository.ModuloRepository;
import com.reservas.repository.NegocioRepository;
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
 *   1. El usuario llama a {@link #crearCheckoutModulo} → redirige a Stripe Hosted Checkout.
 *   2. Stripe llama al webhook con checkout.session.completed (metadata.tipo=modulo).
 *   3. {@link StripeWebhookController} detecta el tipo y llama a {@link #activarModulo}.
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
     * Usa price_data dinámico con el precio del catálogo (no requiere Price IDs en Stripe).
     *
     * @param clave         Clave del módulo a activar
     * @param emailUsuario  Email del usuario autenticado
     * @return URL de la sesión de Stripe Hosted Checkout
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
            // Genera registros limpios en el Stripe Dashboard por módulo.
            log.info("[ModuloActivacion] Usando Price ID '{}' para módulo '{}'", stripePriceId, clave);
            lineItem = SessionCreateParams.LineItem.builder()
                    .setPrice(stripePriceId)
                    .setQuantity(1L)
                    .build();
        } else {
            // ── Opción B: price_data dinámico (fallback si no hay Price ID) ───
            // Útil durante desarrollo antes de configurar productos en Stripe.
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
                    .setAutomaticTax(
                            SessionCreateParams.AutomaticTax.builder()
                                    .setEnabled(false)
                                    .build()
                    )
                    .build();

            Session session = Session.create(params);

            log.info("[ModuloActivacion] Sesión creada: {} para módulo: {}", session.getId(), clave);

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
    // Activación (llamada desde webhook)
    // ============================================================

    /**
     * Activa un módulo para un negocio. Llamado desde el webhook de Stripe
     * cuando checkout.session.completed tiene metadata.tipo = "modulo".
     *
     * @param negocioId             ID del negocio
     * @param clave                 Clave del módulo a activar
     * @param stripeSubscriptionId  ID de la suscripción creada en Stripe
     */
    @Transactional
    public void activarModulo(UUID negocioId, String clave, String stripeSubscriptionId) {
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
