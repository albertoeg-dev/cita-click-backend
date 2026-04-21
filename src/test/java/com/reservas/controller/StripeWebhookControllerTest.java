package com.reservas.controller;

import com.reservas.billing.service.SubscriptionService;
import com.reservas.payments.service.ConnectAccountService;
import com.reservas.payments.service.PaymentService;
import com.reservas.service.ModuloActivacionService;
import com.reservas.service.StripeService;
import com.reservas.service.SuscripcionService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StripeWebhookController - Tests unitarios")
class StripeWebhookControllerTest {

    @Mock private StripeService stripeService;
    @Mock private PaymentService paymentService;
    @Mock private ConnectAccountService connectAccountService;
    @Mock private SubscriptionService subscriptionService;
    @Mock private SuscripcionService suscripcionService;
    @Mock private ModuloActivacionService moduloActivacionService;

    @InjectMocks
    private StripeWebhookController controller;

    private static final String FAKE_SECRET = "whsec_test_secret";
    private static final String PAYLOAD = "{\"type\":\"test\"}";
    private static final String SIG_HEADER = "t=1234567890,v1=abc123";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "webhookSecret", FAKE_SECRET);
    }

    // ============================================================
    // Firma
    // ============================================================

    @Nested
    @DisplayName("Validación de firma")
    class ValidacionFirma {

        @Test
        @DisplayName("Devuelve 400 cuando la firma es inválida")
        void firmaInvalida_devuelve400() throws Exception {
            try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
                webhookStatic.when(() -> Webhook.constructEvent(PAYLOAD, SIG_HEADER, FAKE_SECRET))
                        .thenThrow(new SignatureVerificationException("bad sig", SIG_HEADER));

                ResponseEntity<String> response = controller.handleWebhook(PAYLOAD, SIG_HEADER);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(response.getBody()).contains("Invalid signature");
            }
        }

        @Test
        @DisplayName("Devuelve 200 cuando la firma es válida")
        void firmaValida_devuelve200() throws Exception {
            Event event = buildEvent("unknown.event");

            try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
                webhookStatic.when(() -> Webhook.constructEvent(PAYLOAD, SIG_HEADER, FAKE_SECRET))
                        .thenReturn(event);

                ResponseEntity<String> response = controller.handleWebhook(PAYLOAD, SIG_HEADER);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            }
        }
    }

    // ============================================================
    // checkout.session.completed — suscripción de plan
    // ============================================================

    @Nested
    @DisplayName("checkout.session.completed")
    class CheckoutSessionCompleted {

        @Test
        @DisplayName("Suscripción de plan → llama procesarSuscripcionCreada()")
        void suscripcionDePlan_llamaProcesarSuscripcion() throws Exception {
            Session session = mockSession("subscription", null, "pi_test", "sub_test");
            Event event = buildEventWithObject("checkout.session.completed", session);

            try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
                webhookStatic.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(event);

                ResponseEntity<String> response = controller.handleWebhook(PAYLOAD, SIG_HEADER);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                verify(stripeService).procesarSuscripcionCreada(session);
            }
        }

        @Test
        @DisplayName("Módulo del marketplace → llama activarModulo()")
        void moduloMarketplace_llamaActivarModulo() throws Exception {
            UUID negocioId = UUID.randomUUID();
            Session session = mockSession("subscription", Map.of(
                    "tipo", "modulo",
                    "modulo_clave", "email_recordatorios",
                    "negocio_id", negocioId.toString()
            ), null, "sub_mod_test");
            Event event = buildEventWithObject("checkout.session.completed", session);

            try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
                webhookStatic.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(event);

                ResponseEntity<String> response = controller.handleWebhook(PAYLOAD, SIG_HEADER);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                verify(moduloActivacionService).activarModulo(
                        eq(negocioId),
                        eq("email_recordatorios"),
                        eq("sub_mod_test"),
                        any()
                );
            }
        }

        @Test
        @DisplayName("Pago único (paid) → llama procesarPagoCompletado()")
        void pagoUnico_llamaProcesarPagoCompletado() throws Exception {
            Session session = mockSession(null, null, "pi_unique_123", null);
            when(session.getPaymentStatus()).thenReturn("paid");
            Event event = buildEventWithObject("checkout.session.completed", session);

            try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
                webhookStatic.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(event);

                ResponseEntity<String> response = controller.handleWebhook(PAYLOAD, SIG_HEADER);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                verify(stripeService).procesarPagoCompletado(session.getId(), "pi_unique_123");
            }
        }
    }

    // ============================================================
    // checkout.session.async_payment_succeeded/failed
    // ============================================================

    @Nested
    @DisplayName("checkout.session.async_payment")
    class CheckoutSessionAsync {

        @Test
        @DisplayName("async_payment_succeeded → llama procesarPagoCompletado()")
        void asyncSucceeded_llamaProcesarPago() throws Exception {
            Session session = mockSession(null, null, "pi_async_456", null);
            Event event = buildEventWithObject("checkout.session.async_payment_succeeded", session);

            try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
                webhookStatic.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(event);

                ResponseEntity<String> response = controller.handleWebhook(PAYLOAD, SIG_HEADER);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                verify(stripeService).procesarPagoCompletado(session.getId(), "pi_async_456");
            }
        }

        @Test
        @DisplayName("async_payment_failed → llama procesarPagoFallido()")
        void asyncFailed_llamaProcesarPagoFallido() throws Exception {
            Session session = mockSession(null, null, null, null);
            Event event = buildEventWithObject("checkout.session.async_payment_failed", session);

            try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
                webhookStatic.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(event);

                ResponseEntity<String> response = controller.handleWebhook(PAYLOAD, SIG_HEADER);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                verify(stripeService).procesarPagoFallido(session.getId(), "Async payment failed");
            }
        }
    }

    // ============================================================
    // invoice.*
    // ============================================================

    @Nested
    @DisplayName("invoice.*")
    class InvoiceEvents {

        @Test
        @DisplayName("invoice.paid (sin subscriptionId) → llama handleInvoicePaid()")
        void invoicePaid_llamaHandleInvoicePaid() throws Exception {
            Invoice invoice = mock(Invoice.class);
            when(invoice.getId()).thenReturn("in_test_123");
            when(invoice.getSubscription()).thenReturn(null);
            Event event = buildEventWithObject("invoice.paid", invoice);

            try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
                webhookStatic.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(event);

                ResponseEntity<String> response = controller.handleWebhook(PAYLOAD, SIG_HEADER);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                verify(subscriptionService).handleInvoicePaid("in_test_123");
            }
        }

        @Test
        @DisplayName("invoice.payment_failed → llama handleInvoicePaymentFailed()")
        void invoicePaymentFailed_llamaHandleInvoicePaymentFailed() throws Exception {
            Invoice invoice = mock(Invoice.class);
            when(invoice.getId()).thenReturn("in_failed_456");
            Event event = buildEventWithObject("invoice.payment_failed", invoice);

            try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
                webhookStatic.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(event);

                ResponseEntity<String> response = controller.handleWebhook(PAYLOAD, SIG_HEADER);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                verify(subscriptionService).handleInvoicePaymentFailed("in_failed_456");
            }
        }
    }

    // ============================================================
    // customer.subscription.deleted
    // ============================================================

    @Nested
    @DisplayName("customer.subscription.deleted")
    class SubscriptionDeleted {

        @Test
        @DisplayName("Llama handleSubscriptionDeleted() con el ID correcto")
        void llamaHandleSubscriptionDeleted() throws Exception {
            com.stripe.model.Subscription sub = mock(com.stripe.model.Subscription.class);
            when(sub.getId()).thenReturn("sub_deleted_789");
            Event event = buildEventWithObject("customer.subscription.deleted", sub);

            try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
                webhookStatic.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(event);

                ResponseEntity<String> response = controller.handleWebhook(PAYLOAD, SIG_HEADER);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                verify(subscriptionService).handleSubscriptionDeleted("sub_deleted_789");
            }
        }
    }

    // ============================================================
    // Evento desconocido
    // ============================================================

    @Test
    @DisplayName("Evento desconocido → devuelve 200 sin llamar a ningún servicio")
    void eventoDesconocido_devuelve200SinLlamarServicios() throws Exception {
        Event event = buildEvent("some.unknown.event");

        try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(event);

            ResponseEntity<String> response = controller.handleWebhook(PAYLOAD, SIG_HEADER);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verifyNoInteractions(stripeService, paymentService, subscriptionService,
                    suscripcionService, moduloActivacionService);
        }
    }

    // ============================================================
    // Helpers
    // ============================================================

    /** Evento sin objeto de datos adjunto (para eventos desconocidos / firma). */
    private Event buildEvent(String type) {
        Event event = mock(Event.class);
        when(event.getType()).thenReturn(type);
        return event;
    }

    /** Evento con objeto de datos deserializable. */
    private Event buildEventWithObject(String type, StripeObject stripeObject) {
        Event event = mock(Event.class);
        when(event.getType()).thenReturn(type);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of(stripeObject));
        return event;
    }

    private Session mockSession(String mode, Map<String, String> metadata,
                                String paymentIntentId, String subscriptionId) {
        Session session = mock(Session.class);
        when(session.getId()).thenReturn("cs_test_" + UUID.randomUUID().toString().substring(0, 8));
        lenient().when(session.getMode()).thenReturn(mode);
        lenient().when(session.getMetadata()).thenReturn(metadata);
        lenient().when(session.getPaymentIntent()).thenReturn(paymentIntentId);
        lenient().when(session.getSubscription()).thenReturn(subscriptionId);
        lenient().when(session.getPaymentStatus()).thenReturn(null);
        lenient().when(session.getCustomerDetails()).thenReturn(null);
        return session;
    }
}
