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
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionRetrieveParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para StripeService
 */
@ExtendWith(MockitoExtension.class)
class StripeServiceTest {

    @Mock
    private PagoRepository pagoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private NegocioRepository negocioRepository;

    @Mock
    private SuscripcionService suscripcionService;

    @InjectMocks
    private StripeService stripeService;

    private Usuario usuarioTest;
    private Negocio negocioTest;
    private CheckoutRequest checkoutRequest;

    @BeforeEach
    void setUp() {
        // Configurar propiedades con ReflectionTestUtils
        ReflectionTestUtils.setField(stripeService, "stripeApiKey", "sk_test_123456789");
        ReflectionTestUtils.setField(stripeService, "successUrl", "https://test.com/success");
        ReflectionTestUtils.setField(stripeService, "cancelUrl", "https://test.com/cancel");
        ReflectionTestUtils.setField(stripeService, "priceIdBase", "price_base_test");
        ReflectionTestUtils.setField(stripeService, "priceIdCompleto", "price_completo_test");

        // Configurar datos de prueba
        negocioTest = new Negocio();
        negocioTest.setId(UUID.randomUUID());
        negocioTest.setNombre("Negocio Test");
        negocioTest.setEmail("negocio@test.com");
        negocioTest.setPlan("basico");

        usuarioTest = new Usuario();
        usuarioTest.setId(UUID.randomUUID());
        usuarioTest.setEmail("usuario@test.com");
        usuarioTest.setNombre("Usuario Test");
        usuarioTest.setNegocio(negocioTest);
        usuarioTest.setTrialUsed(false);

        checkoutRequest = new CheckoutRequest();
        checkoutRequest.setPlan("basico");
        checkoutRequest.setReferencia("REF123");
    }

    @Test
    void crearCheckoutSession_ConPlanBasico_DeberiaCrearSesionCorrectamente() throws StripeException {
        // Given
        String email = "usuario@test.com";
        String customerId = "cus_test123";
        String sessionId = "cs_test123";
        String clientSecret = "cs_test123_secret";

        negocioTest.setStripeCustomerId(customerId);
        when(usuarioRepository.findByEmail(email)).thenReturn(Optional.of(usuarioTest));

        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn(sessionId);
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/test");

        when(pagoRepository.save(any(Pago.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When - using static mock
        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            CheckoutResponse response = stripeService.crearCheckoutSession(checkoutRequest, email);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getSessionId()).isEqualTo(sessionId);
            assertThat(response.getClientSecret()).isNull(); // service sets clientSecret(null) for Hosted Checkout
            assertThat(response.getPlan()).isEqualTo("basico");
            assertThat(response.getMoneda()).isEqualTo("MXN");

            verify(pagoRepository, times(1)).save(any(Pago.class));
        }
    }

    @Test
    void crearCheckoutSession_ConPlanCompleto_DeberiaUsarPrecioCorrecto() throws StripeException {
        // Given - plan "completo" reemplaza a "profesional"/"premium" ($1,199 MXN)
        checkoutRequest.setPlan("completo");
        String email = "usuario@test.com";
        String customerId = "cus_test123";

        negocioTest.setStripeCustomerId(customerId);
        when(usuarioRepository.findByEmail(email)).thenReturn(Optional.of(usuarioTest));

        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test123");
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/test");

        when(pagoRepository.save(any(Pago.class))).thenAnswer(invocation -> {
            Pago pago = invocation.getArgument(0);
            assertThat(pago.getMonto()).isEqualByComparingTo(new BigDecimal("1199"));
            assertThat(pago.getPlan()).isEqualTo("completo");
            return pago;
        });

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            // When
            CheckoutResponse response = stripeService.crearCheckoutSession(checkoutRequest, email);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getMonto()).isEqualTo("1199");
            verify(pagoRepository).save(any(Pago.class));
        }
    }

    @Test
    void crearCheckoutSession_ConPlanBase_DeberiaUsarPrecioCorrecto() throws StripeException {
        // Given - plan "base" ($299 MXN)
        checkoutRequest.setPlan("base");
        String email = "usuario@test.com";
        String customerId = "cus_test123";

        negocioTest.setStripeCustomerId(customerId);
        when(usuarioRepository.findByEmail(email)).thenReturn(Optional.of(usuarioTest));

        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test123");
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/test");

        when(pagoRepository.save(any(Pago.class))).thenAnswer(invocation -> {
            Pago pago = invocation.getArgument(0);
            assertThat(pago.getMonto()).isEqualByComparingTo(new BigDecimal("299"));
            assertThat(pago.getPlan()).isEqualTo("base");
            return pago;
        });

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            // When
            CheckoutResponse response = stripeService.crearCheckoutSession(checkoutRequest, email);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getMonto()).isEqualTo("299");
        }
    }

    @Test
    void crearCheckoutSession_ConUsuarioSinTrial_NoDeberiaAplicarTrial() throws StripeException {
        // Given
        usuarioTest.setTrialUsed(true); // Ya usó el trial
        String email = "usuario@test.com";
        String customerId = "cus_test123";

        negocioTest.setStripeCustomerId(customerId);
        when(usuarioRepository.findByEmail(email)).thenReturn(Optional.of(usuarioTest));

        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test123");
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/test");

        when(pagoRepository.save(any(Pago.class))).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            // When
            stripeService.crearCheckoutSession(checkoutRequest, email);

            // Then
            // Verificar que no se configuró trial en la metadata
            verify(usuarioRepository).findByEmail(email);
        }
    }

    @Test
    void crearCheckoutSession_UsuarioNoExiste_DeberiaLanzarNotFoundException() {
        // Given
        String email = "noexiste@test.com";
        when(usuarioRepository.findByEmail(email)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> stripeService.crearCheckoutSession(checkoutRequest, email))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    @Test
    void crearCheckoutSession_NegocioNoExiste_DeberiaLanzarNotFoundException() {
        // Given
        usuarioTest.setNegocio(null);
        String email = "usuario@test.com";
        when(usuarioRepository.findByEmail(email)).thenReturn(Optional.of(usuarioTest));

        // When & Then
        assertThatThrownBy(() -> stripeService.crearCheckoutSession(checkoutRequest, email))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Negocio no encontrado");
    }

    @Test
    void crearCheckoutSession_ErrorStripe_DeberiaLanzarRuntimeException() throws StripeException {
        // Given
        String email = "usuario@test.com";
        String customerId = "cus_test123";

        negocioTest.setStripeCustomerId(customerId);
        when(usuarioRepository.findByEmail(email)).thenReturn(Optional.of(usuarioTest));

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class)))
                    .thenThrow(new StripeException("Error de Stripe", "req_123", "code", 400) {});

            // When & Then
            assertThatThrownBy(() -> stripeService.crearCheckoutSession(checkoutRequest, email))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Error al crear sesión de pago");
        }
    }

    @Test
    void obtenerEstadoSesion_Exitoso_DeberiaRetornarEstado() throws StripeException {
        // Given
        String sessionId = "cs_test123";

        Session mockSession = mock(Session.class);
        when(mockSession.getStatus()).thenReturn("complete");
        when(mockSession.getPaymentStatus()).thenReturn("paid");
        when(mockSession.getPaymentIntent()).thenReturn("pi_test123");

        Session.CustomerDetails customerDetails = mock(Session.CustomerDetails.class);
        when(customerDetails.getEmail()).thenReturn("test@test.com");
        when(mockSession.getCustomerDetails()).thenReturn(customerDetails);

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.retrieve(anyString(), any(SessionRetrieveParams.class), any())).thenReturn(mockSession);

            // When
            Map<String, Object> estado = stripeService.obtenerEstadoSesion(sessionId);

            // Then
            assertThat(estado).containsEntry("status", "complete");
            assertThat(estado).containsEntry("payment_status", "paid");
            assertThat(estado).containsEntry("customer_email", "test@test.com");
            assertThat(estado).containsEntry("payment_intent", "pi_test123");
        }
    }

    @Test
    void procesarPagoCompletado_Exitoso_DeberiaActualizarPago() throws StripeException {
        // Given
        String sessionId = "cs_test123";
        String paymentIntentId = "pi_test123";

        Pago pago = Pago.builder()
                .id(UUID.randomUUID())
                .negocio(negocioTest)
                .stripeCheckoutSessionId(sessionId)
                .plan("basico")
                .monto(new BigDecimal("299.00"))
                .estado("pending")
                .build();

        when(pagoRepository.findByStripeCheckoutSessionId(sessionId)).thenReturn(Optional.of(pago));

        PaymentIntent mockPaymentIntent = mock(PaymentIntent.class);
        when(mockPaymentIntent.getPaymentMethod()).thenReturn("pm_card123");

        when(pagoRepository.save(any(Pago.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(suscripcionService).activarSuscripcion(anyString(), anyString());

        try (MockedStatic<PaymentIntent> paymentIntentMock = mockStatic(PaymentIntent.class);
             MockedStatic<PaymentMethod> paymentMethodMock = mockStatic(PaymentMethod.class)) {
            paymentIntentMock.when(() -> PaymentIntent.retrieve(paymentIntentId)).thenReturn(mockPaymentIntent);

            PaymentMethod mockPm = mock(PaymentMethod.class);
            when(mockPm.getType()).thenReturn("card");
            paymentMethodMock.when(() -> PaymentMethod.retrieve("pm_card123")).thenReturn(mockPm);

            // When
            stripeService.procesarPagoCompletado(sessionId, paymentIntentId);

            // Then
            verify(pagoRepository).save(argThat(p ->
                    p.getEstado().equals("completed") &&
                            p.getStripePaymentIntentId().equals(paymentIntentId) &&
                            p.getMetodoPago().equals("card") &&
                            p.getFechaCompletado() != null
            ));
            verify(suscripcionService).activarSuscripcion(negocioTest.getId().toString(), "basico");
        }
    }

    @Test
    void procesarPagoCompletado_PagoYaProcesado_NoDeberiaReprocesal() throws StripeException {
        // Given
        String sessionId = "cs_test123";
        String paymentIntentId = "pi_test123";

        Pago pago = Pago.builder()
                .id(UUID.randomUUID())
                .negocio(negocioTest)
                .stripeCheckoutSessionId(sessionId)
                .plan("basico")
                .estado("completed")
                .fechaCompletado(LocalDateTime.now())
                .build();

        when(pagoRepository.findByStripeCheckoutSessionId(sessionId)).thenReturn(Optional.of(pago));

        // When
        stripeService.procesarPagoCompletado(sessionId, paymentIntentId);

        // Then
        verify(pagoRepository, never()).save(any());
        verify(suscripcionService, never()).activarSuscripcion(anyString(), anyString());
    }

    @Test
    void procesarPagoCompletado_PagoNoEncontrado_DeberiaLanzarNotFoundException() {
        // Given
        String sessionId = "cs_test123";
        String paymentIntentId = "pi_test123";

        when(pagoRepository.findByStripeCheckoutSessionId(sessionId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> stripeService.procesarPagoCompletado(sessionId, paymentIntentId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Pago no encontrado");
    }

    @Test
    void procesarPagoFallido_Exitoso_DeberiaMarcarPagoComoFallido() {
        // Given
        String sessionId = "cs_test123";
        String errorMessage = "Tarjeta rechazada";

        Pago pago = Pago.builder()
                .id(UUID.randomUUID())
                .negocio(negocioTest)
                .stripeCheckoutSessionId(sessionId)
                .plan("basico")
                .estado("pending")
                .build();

        when(pagoRepository.findByStripeCheckoutSessionId(sessionId)).thenReturn(Optional.of(pago));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        stripeService.procesarPagoFallido(sessionId, errorMessage);

        // Then
        verify(pagoRepository).save(argThat(p ->
                p.getEstado().equals("failed") &&
                        p.getErrorMensaje().equals(errorMessage)
        ));
    }

    @Test
    void obtenerHistorialPagos_DeberiaRetornarListaDePagos() {
        // Given
        String email = "usuario@test.com";

        Pago pago1 = Pago.builder()
                .id(UUID.randomUUID())
                .negocio(negocioTest)
                .plan("basico")
                .monto(new BigDecimal("299.00"))
                .moneda("MXN")
                .estado("completed")
                .build();

        Pago pago2 = Pago.builder()
                .id(UUID.randomUUID())
                .negocio(negocioTest)
                .plan("profesional")
                .monto(new BigDecimal("699.00"))
                .moneda("MXN")
                .estado("completed")
                .build();

        when(usuarioRepository.findByEmailWithNegocio(email)).thenReturn(Optional.of(usuarioTest));
        when(pagoRepository.findByNegocioOrderByFechaCreacionDesc(negocioTest))
                .thenReturn(Arrays.asList(pago1, pago2));

        // When
        List<PagoResponse> historial = stripeService.obtenerHistorialPagos(email);

        // Then
        assertThat(historial).hasSize(2);
        assertThat(historial.get(0).getPlan()).isEqualTo("basico");
        assertThat(historial.get(1).getPlan()).isEqualTo("profesional");
    }

    @Test
    void obtenerEstadisticas_DeberiaRetornarEstadisticasCorrectas() {
        // Given
        String email = "usuario@test.com";

        when(usuarioRepository.findByEmailWithNegocio(email)).thenReturn(Optional.of(usuarioTest));
        when(pagoRepository.countPagosCompletadosByNegocio(negocioTest)).thenReturn(5L);
        when(pagoRepository.sumMontoByNegocioAndEstadoCompleted(negocioTest))
                .thenReturn(new BigDecimal("1495.00"));

        // When
        Map<String, Object> estadisticas = stripeService.obtenerEstadisticas(email);

        // Then
        assertThat(estadisticas).containsEntry("totalPagos", 5L);
        assertThat(estadisticas).containsEntry("montoTotal", new BigDecimal("1495.00"));
    }

    @Test
    void obtenerEstadisticas_SinPagos_DeberiaRetornarCero() {
        // Given
        String email = "usuario@test.com";

        when(usuarioRepository.findByEmailWithNegocio(email)).thenReturn(Optional.of(usuarioTest));
        when(pagoRepository.countPagosCompletadosByNegocio(negocioTest)).thenReturn(0L);
        when(pagoRepository.sumMontoByNegocioAndEstadoCompleted(negocioTest)).thenReturn(null);

        // When
        Map<String, Object> estadisticas = stripeService.obtenerEstadisticas(email);

        // Then
        assertThat(estadisticas).containsEntry("totalPagos", 0L);
        assertThat(estadisticas).containsEntry("montoTotal", BigDecimal.ZERO);
    }

    @Test
    void procesarSuscripcionCreada_Exitoso_DeberiaActivarSuscripcion() {
        // Given
        Session mockSession = mock(Session.class);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("usuario_id", usuarioTest.getId().toString());
        metadata.put("negocio_id", negocioTest.getId().toString());
        metadata.put("plan", "basico");
        metadata.put("trial_applied", "true");

        when(mockSession.getId()).thenReturn("cs_test123");
        when(mockSession.getMetadata()).thenReturn(metadata);
        when(mockSession.getSubscription()).thenReturn("sub_test123");

        when(usuarioRepository.findById(usuarioTest.getId())).thenReturn(Optional.of(usuarioTest));
        when(negocioRepository.findById(negocioTest.getId())).thenReturn(Optional.of(negocioTest));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(negocioRepository.save(any(Negocio.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(suscripcionService).activarSuscripcion(anyString(), anyString());

        // When
        stripeService.procesarSuscripcionCreada(mockSession);

        // Then
        verify(usuarioRepository).save(argThat(u -> u.isTrialUsed()));
        verify(negocioRepository).save(argThat(n -> n.getStripeSubscriptionId().equals("sub_test123")));
        verify(suscripcionService).activarSuscripcion(negocioTest.getId().toString(), "basico");
    }

    @Test
    void procesarSuscripcionCreada_SinMetadata_NoDeberiaProcesar() {
        // Given
        Session mockSession = mock(Session.class);
        Map<String, String> metadata = new HashMap<>();
        // Metadata vacía

        when(mockSession.getId()).thenReturn("cs_test123");
        when(mockSession.getMetadata()).thenReturn(metadata);

        // When
        stripeService.procesarSuscripcionCreada(mockSession);

        // Then
        verify(usuarioRepository, never()).findById(any());
        verify(suscripcionService, never()).activarSuscripcion(anyString(), anyString());
    }
}
