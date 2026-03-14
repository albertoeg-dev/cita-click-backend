package com.reservas.controller;

import com.reservas.dto.request.CheckoutRequest;
import com.reservas.dto.response.ApiResponse;
import com.reservas.dto.response.CheckoutResponse;
import com.reservas.dto.response.PagoResponse;
import com.reservas.service.StripeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller para pagos con Stripe
 */
@Slf4j
@RestController
@RequestMapping("/pagos")
@RequiredArgsConstructor
@Tag(name = "Pagos", description = "Gestión de pagos y suscripciones con Stripe")
@SecurityRequirement(name = "bearerAuth")
public class StripeController {

    private final StripeService stripeService;

    /**
     * POST /api/pagos/checkout
     * Crea una sesión de checkout de Stripe
     */
    @PostMapping("/checkout")
    @Operation(summary = "Crear sesión de checkout", description = "Crea una sesión de pago con Stripe para un plan")
    public ResponseEntity<ApiResponse<CheckoutResponse>> crearCheckout(
            @Valid @RequestBody CheckoutRequest request,
            Authentication authentication
    ) {
        log.info("[StripeController] POST /api/pagos/checkout - Plan: {} - Usuario: {}",
                request.getPlan(), authentication.getName());

        CheckoutResponse response = stripeService.crearCheckoutSession(request, authentication.getName());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<CheckoutResponse>builder()
                        .success(true)
                        .message("Sesión de checkout creada exitosamente")
                        .data(response)
                        .build());
    }

    /**
     * GET /api/pagos/session-status/{sessionId}
     * Obtiene el estado de una sesión de checkout
     */
    @GetMapping("/session-status/{sessionId}")
    @Operation(summary = "Estado de sesión", description = "Obtiene el estado de una sesión de checkout")
    public ResponseEntity<ApiResponse<Map<String, Object>>> obtenerEstadoSesion(
            @PathVariable String sessionId
    ) {
        log.info("[StripeController] GET /api/pagos/session-status/{}", sessionId);

        Map<String, Object> response = stripeService.obtenerEstadoSesion(sessionId);

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .message("Estado de sesión obtenido")
                .data(response)
                .build());
    }

    /**
     * GET /api/pagos/historial
     * Obtiene el historial de pagos del negocio
     */
    @GetMapping("/historial")
    @Operation(summary = "Historial de pagos", description = "Obtiene todos los pagos del negocio")
    public ResponseEntity<ApiResponse<List<PagoResponse>>> obtenerHistorial(
            Authentication authentication
    ) {
        log.info("[StripeController] GET /api/pagos/historial - Usuario: {}", authentication.getName());

        List<PagoResponse> pagos = stripeService.obtenerHistorialPagos(authentication.getName());

        return ResponseEntity.ok(ApiResponse.<List<PagoResponse>>builder()
                .success(true)
                .message("Historial de pagos obtenido")
                .data(pagos)
                .build());
    }

    /**
     * GET /api/pagos/estadisticas
     * Obtiene estadísticas de pagos del negocio
     */
    @GetMapping("/estadisticas")
    @Operation(summary = "Estadísticas de pagos", description = "Obtiene estadísticas de pagos del negocio")
    public ResponseEntity<ApiResponse<Map<String, Object>>> obtenerEstadisticas(
            Authentication authentication
    ) {
        log.info("[StripeController] GET /api/pagos/estadisticas - Usuario: {}", authentication.getName());

        Map<String, Object> estadisticas = stripeService.obtenerEstadisticas(authentication.getName());

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .message("Estadísticas obtenidas exitosamente")
                .data(estadisticas)
                .build());
    }

    /**
     * GET /api/pagos/planes
     * Obtiene la información de los planes disponibles
     */
    @GetMapping("/planes")
    @Operation(summary = "Planes disponibles", description = "Obtiene información de los planes de suscripción")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> obtenerPlanes() {
        log.info("[StripeController] GET /api/pagos/planes");

        List<Map<String, Object>> planes = List.of(
                Map.of(
                        "id", "base",
                        "nombre", "Plan Base",
                        "precio", 299,
                        "moneda", "MXN",
                        "descripcion", "Para cualquier negocio de servicios",
                        "caracteristicas", List.of(
                                "Hasta 200 citas/mes (expandible)",
                                "Hasta 8 servicios (expandible)",
                                "Clientes ilimitados",
                                "Calendario visual interactivo",
                                "Perfil 360 del cliente",
                                "Dashboard con métricas en tiempo real",
                                "Citas recurrentes",
                                "Soporte por email incluido"
                        )
                ),
                Map.of(
                        "id", "completo",
                        "nombre", "Bundle Completo",
                        "precio", 1199,
                        "moneda", "MXN",
                        "descripcion", "Todos los módulos, un solo precio",
                        "caracteristicas", List.of(
                                "Plan Base incluido",
                                "Recordatorios Email + SMS/WhatsApp",
                                "Cobros en línea (Stripe Connect)",
                                "Reportes avanzados PDF/Excel",
                                "1 usuario adicional incluido",
                                "Branding de emails",
                                "Citas y servicios ilimitados",
                                "Soporte prioritario"
                        ),
                        "popular", true
                )
        );

        return ResponseEntity.ok(ApiResponse.<List<Map<String, Object>>>builder()
                .success(true)
                .message("Planes obtenidos exitosamente")
                .data(planes)
                .build());
    }
}
