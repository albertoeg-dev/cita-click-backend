package com.reservas.controller;

import com.reservas.billing.service.SubscriptionService;
import com.reservas.payments.service.ConnectAccountService;
import com.reservas.payments.service.PaymentService;
import com.reservas.service.ModuloActivacionService;
import com.reservas.service.StripeService;
import com.reservas.service.SuscripcionService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Controller para recibir webhooks de Stripe
 * IMPORTANTE: Este endpoint NO debe tener autenticación JWT
 */
@Slf4j
@RestController
@RequestMapping("/webhooks/stripe")
@RequiredArgsConstructor
@Hidden
public class StripeWebhookController {

    private final StripeService stripeService;
    private final PaymentService paymentService;
    private final ConnectAccountService connectAccountService;
    private final SubscriptionService subscriptionService;
    private final SuscripcionService suscripcionService;
    private final ModuloActivacionService moduloActivacionService;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    /**
     * POST /api/webhooks/stripe
     * Recibe eventos de Stripe (webhooks)
     *
     * IMPORTANTE: Este endpoint debe estar en WebSecurityConfig.permitAll()
     */
    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        log.info("Webhook recibido de Stripe");

        Event event;

        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.error("Firma inválida del webhook", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        log.info("Tipo de evento recibido: {}", event.getType());

        try {
            switch (event.getType()) {
                case "checkout.session.completed":
                    handleCheckoutSessionCompleted(event);
                    break;
                case "checkout.session.async_payment_succeeded":
                    handleCheckoutSessionAsyncPaymentSucceeded(event);
                    break;
                case "checkout.session.async_payment_failed":
                    handleCheckoutSessionAsyncPaymentFailed(event);
                    break;
                case "payment_intent.succeeded":
                    handlePaymentIntentSucceeded(event);
                    break;
                case "payment_intent.payment_failed":
                    handlePaymentIntentFailed(event);
                    break;
                case "charge.refunded":
                    handleChargeRefunded(event);
                    break;
                case "account.updated":
                    handleAccountUpdated(event);
                    break;
                case "invoice.paid":
                    handleInvoicePaid(event);
                    break;
                case "invoice.payment_failed":
                    handleInvoicePaymentFailed(event);
                    break;
                case "customer.subscription.deleted":
                    handleSubscriptionDeleted(event);
                    break;
                default:
                    log.info("Evento no manejado: {}", event.getType());
            }

            return ResponseEntity.ok("Webhook received");

        } catch (Exception e) {
            log.error("Error procesando webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing webhook");
        }
    }

    /**
     * Procesa cuando se completa una sesión de checkout
     */
    private void handleCheckoutSessionCompleted(Event event) {
        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject;

        if (dataObjectDeserializer.getObject().isPresent()) {
            stripeObject = dataObjectDeserializer.getObject().get();
        } else {
            log.error("Deserialización falló para checkout.session.completed");
            return;
        }

        Session session = (Session) stripeObject;

        log.info("Checkout completado - Session ID: {}", session.getId());
        log.info("Payment Status: {}", session.getPaymentStatus());
        log.info("Mode: {}", session.getMode());
        log.info("Customer Email: {}",
                session.getCustomerDetails() != null ? session.getCustomerDetails().getEmail() : "N/A");

        // Procesar según el modo de la sesión
        if ("subscription".equals(session.getMode())) {
            String tipo = session.getMetadata() != null ? session.getMetadata().get("tipo") : null;

            if ("modulo".equals(tipo)) {
                // Es la activación de un módulo del marketplace
                String clave = session.getMetadata().get("modulo_clave");
                String negocioIdStr = session.getMetadata().get("negocio_id");
                log.info("Activando módulo '{}' para negocio: {}", clave, negocioIdStr);
                if (clave != null && negocioIdStr != null) {
                    moduloActivacionService.activarModulo(
                            java.util.UUID.fromString(negocioIdStr),
                            clave,
                            session.getSubscription(),
                            session.getId()
                    );
                }
            } else {
                // Es una suscripción de plan — marcar trial como usado
                log.info("Procesando suscripción de plan - Subscription ID: {}", session.getSubscription());
                stripeService.procesarSuscripcionCreada(session);
            }
        } else if ("paid".equals(session.getPaymentStatus())) {
            // Es un pago único
            String sessionId = session.getId();
            String paymentIntentId = session.getPaymentIntent();

            if (paymentIntentId != null) {
                stripeService.procesarPagoCompletado(sessionId, paymentIntentId);
            } else {
                log.warn("PaymentIntent ID es null");
            }
        } else {
            log.info("Pago aún pendiente, esperando confirmación asíncrona");
        }
    }

    /**
     * Procesa cuando un pago asíncrono (OXXO, SPEI) se completa exitosamente
     */
    private void handleCheckoutSessionAsyncPaymentSucceeded(Event event) {
        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject;

        if (dataObjectDeserializer.getObject().isPresent()) {
            stripeObject = dataObjectDeserializer.getObject().get();
        } else {
            log.error("Deserialización falló para async_payment_succeeded");
            return;
        }

        Session session = (Session) stripeObject;

        log.info("Pago asíncrono completado - Session ID: {}", session.getId());

        String sessionId = session.getId();
        String paymentIntentId = session.getPaymentIntent();

        if (paymentIntentId != null) {
            stripeService.procesarPagoCompletado(sessionId, paymentIntentId);
        }
    }

    private void handleCheckoutSessionAsyncPaymentFailed(Event event) {
        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject;

        if (dataObjectDeserializer.getObject().isPresent()) {
            stripeObject = dataObjectDeserializer.getObject().get();
        } else {
            log.error("Deserialización falló para async_payment_failed");
            return;
        }

        Session session = (Session) stripeObject;

        log.warn("Pago asíncrono fallido - Session ID: {}", session.getId());

        stripeService.procesarPagoFallido(session.getId(), "Async payment failed");
    }

    private void handlePaymentIntentSucceeded(Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject().orElse(null);

        if (paymentIntent != null) {
            log.info("PaymentIntent exitoso: {}", paymentIntent.getId());
            paymentService.handlePaymentSuccess(paymentIntent.getId(), paymentIntent.getLatestCharge());
        }
    }

    private void handlePaymentIntentFailed(Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject().orElse(null);

        if (paymentIntent != null) {
            log.warn("PaymentIntent fallido: {}", paymentIntent.getId());
            paymentService.handlePaymentFailed(paymentIntent.getId());
        }
    }

    private void handleChargeRefunded(Event event) {
        Charge charge = (Charge) event.getDataObjectDeserializer()
                .getObject().orElse(null);

        if (charge != null) {
            log.info("Reembolso procesado - Charge: {}", charge.getId());
        }
    }

    private void handleAccountUpdated(Event event) {
        Account account = (Account) event.getDataObjectDeserializer()
                .getObject().orElse(null);

        if (account != null) {
            log.info("Cuenta actualizada: {}", account.getId());
            connectAccountService.syncAccount(account.getId());
        }
    }

    private void handleInvoicePaid(Event event) {
        Invoice invoice = (Invoice) event.getDataObjectDeserializer()
                .getObject().orElse(null);

        if (invoice != null) {
            log.info("Factura pagada: {}", invoice.getId());

            // 1. Actualizar estado en StripeSubscription entity (billing layer)
            subscriptionService.handleInvoicePaid(invoice.getId());

            // 2. Sincronizar fechaProximoCobro real de Stripe en la entidad Negocio
            //    Esto garantiza que la fecha de cobro sea exactamente la que Stripe usó,
            //    y no una fecha calculada manualmente (+30 días).
            String stripeSubscriptionId = invoice.getSubscription();
            if (stripeSubscriptionId != null) {
                try {
                    // Obtener la suscripción de Stripe para leer currentPeriodEnd actualizado
                    com.stripe.model.Subscription stripeSubscription =
                            com.stripe.model.Subscription.retrieve(stripeSubscriptionId);

                    LocalDateTime fechaProximoCobro = LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(stripeSubscription.getCurrentPeriodEnd()),
                            ZoneId.systemDefault()
                    );

                    suscripcionService.renovarSuscripcion(stripeSubscriptionId, fechaProximoCobro);

                    log.info("[Webhook] Renovación sincronizada - subscription: {} → próximo cobro: {}",
                            stripeSubscriptionId, fechaProximoCobro);

                } catch (StripeException e) {
                    log.error("[Webhook] Error consultando suscripción en Stripe: {}", stripeSubscriptionId, e);
                }
            }
        }
    }

    private void handleInvoicePaymentFailed(Event event) {
        Invoice invoice = (Invoice) event.getDataObjectDeserializer()
                .getObject().orElse(null);

        if (invoice != null) {
            log.warn("Pago de factura fallido: {}", invoice.getId());
            subscriptionService.handleInvoicePaymentFailed(invoice.getId());
        }
    }

    private void handleSubscriptionDeleted(Event event) {
        com.stripe.model.Subscription subscription = (com.stripe.model.Subscription) event.getDataObjectDeserializer()
                .getObject().orElse(null);

        if (subscription != null) {
            log.info("Suscripción cancelada: {}", subscription.getId());
            subscriptionService.handleSubscriptionDeleted(subscription.getId());
        }
    }
}
