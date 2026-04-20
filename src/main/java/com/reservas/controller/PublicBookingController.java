package com.reservas.controller;

import com.reservas.dto.request.PublicAgendarCitaRequest;
import com.reservas.dto.response.ApiResponse;
import com.reservas.dto.response.DisponibilidadResponse;
import com.reservas.dto.response.PublicNegocioResponse;
import com.reservas.entity.Cita;
import com.reservas.entity.PublicBookingToken;
import com.reservas.service.PublicBookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller para la funcionalidad de reservas públicas.
 *
 * Endpoints públicos (sin autenticación):
 *   GET  /public/booking/{token}                              → info del negocio + servicios
 *   GET  /public/booking/{token}/disponibilidad              → horarios disponibles por fecha
 *   GET  /public/booking/{token}/disponibilidad/mensual      → disponibilidad por mes (para calendario)
 *   POST /public/booking/{token}/agendar                     → crear cita
 *
 * Endpoints privados (requieren JWT):
 *   GET  /public/booking/token/mi-token                      → obtener o crear mi token
 *   POST /public/booking/token/regenerar                     → regenerar mi token
 */
@Slf4j
@RestController
@RequestMapping("/public/booking")
@RequiredArgsConstructor
public class PublicBookingController {

    private final PublicBookingService publicBookingService;

    // ============================================================
    // ENDPOINTS PÚBLICOS (sin autenticación)
    // ============================================================

    /**
     * Retorna la información pública del negocio y sus servicios activos.
     */
    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<PublicNegocioResponse>> obtenerInfoNegocio(
            @PathVariable String token) {

        log.info("Solicitud de info pública para token: {}", token);
        PublicNegocioResponse response = publicBookingService.obtenerInfoNegocio(token);

        return ResponseEntity.ok(ApiResponse.<PublicNegocioResponse>builder()
                .success(true)
                .message("Información del negocio obtenida")
                .data(response)
                .build());
    }

    /**
     * Retorna los horarios disponibles para una fecha y lista de servicios.
     *
     * Query params:
     *   fecha       → LocalDate ISO (ej: 2024-12-25)
     *   servicioIds → lista de UUIDs (puede repetirse: ?servicioIds=uuid1&servicioIds=uuid2)
     */
    @GetMapping("/{token}/disponibilidad")
    public ResponseEntity<ApiResponse<DisponibilidadResponse>> obtenerDisponibilidad(
            @PathVariable String token,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam List<UUID> servicioIds) {

        log.info("Solicitud disponibilidad token={} fecha={} servicios={}", token, fecha, servicioIds);
        DisponibilidadResponse response = publicBookingService.obtenerDisponibilidad(token, servicioIds, fecha);

        return ResponseEntity.ok(ApiResponse.<DisponibilidadResponse>builder()
                .success(true)
                .message("Disponibilidad calculada")
                .data(response)
                .build());
    }

    /**
     * Retorna disponibilidad para los próximos 30 días (para el calendario visual).
     * Devuelve un mapa: { "2024-12-01": true, "2024-12-02": false, ... }
     * true = tiene al menos un horario libre; false = sin disponibilidad
     */
    @GetMapping("/{token}/disponibilidad/mensual")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> obtenerDisponibilidadMensual(
            @PathVariable String token,
            @RequestParam List<UUID> servicioIds) {

        log.info("Solicitud disponibilidad mensual token={} servicios={}", token, servicioIds);
        Map<String, Boolean> response = publicBookingService.obtenerDisponibilidadMensual(token, servicioIds);

        return ResponseEntity.ok(ApiResponse.<Map<String, Boolean>>builder()
                .success(true)
                .message("Disponibilidad mensual calculada")
                .data(response)
                .build());
    }

    /**
     * Crea una cita pública sin autenticación.
     */
    @PostMapping("/{token}/agendar")
    public ResponseEntity<ApiResponse<AgendarCitaResponse>> agendarCita(
            @PathVariable String token,
            @Valid @RequestBody PublicAgendarCitaRequest request) {

        log.info("Solicitud de agendar cita pública, token={}, cliente={}", token, request.getClienteEmail());
        Cita cita = publicBookingService.agendarCita(token, request);

        AgendarCitaResponse response = AgendarCitaResponse.builder()
                .citaId(cita.getId())
                .fechaHora(cita.getFechaHora().toString())
                .fechaFin(cita.getFechaFin().toString())
                .estado(cita.getEstado().name())
                .mensaje("Tu cita ha sido agendada correctamente. Recibirás un correo de confirmación.")
                .build();

        return ResponseEntity.ok(ApiResponse.<AgendarCitaResponse>builder()
                .success(true)
                .message("Cita agendada exitosamente")
                .data(response)
                .build());
    }

    // ============================================================
    // ENDPOINTS PRIVADOS (requieren JWT del dueño del negocio)
    // ============================================================

    /**
     * Obtiene el token público activo del negocio autenticado.
     * Si no existe, crea uno nuevo.
     */
    @GetMapping("/token/mi-token")
    public ResponseEntity<ApiResponse<TokenResponse>> obtenerMiToken(Authentication authentication) {
        String email = authentication.getName();
        log.info("Solicitando token público para usuario: {}", email);

        PublicBookingToken token = publicBookingService.obtenerOCrearToken(email);

        return ResponseEntity.ok(ApiResponse.<TokenResponse>builder()
                .success(true)
                .message("Token obtenido exitosamente")
                .data(TokenResponse.fromToken(token))
                .build());
    }

    /**
     * Regenera el token público del negocio autenticado.
     * El token anterior queda inválido inmediatamente.
     */
    @PostMapping("/token/regenerar")
    public ResponseEntity<ApiResponse<TokenResponse>> regenerarToken(Authentication authentication) {
        String email = authentication.getName();
        log.info("Regenerando token público para usuario: {}", email);

        PublicBookingToken token = publicBookingService.regenerarToken(email);

        return ResponseEntity.ok(ApiResponse.<TokenResponse>builder()
                .success(true)
                .message("Token regenerado exitosamente. El enlace anterior ya no funciona.")
                .data(TokenResponse.fromToken(token))
                .build());
    }

    // ============================================================
    // DTOs internos de respuesta
    // ============================================================

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AgendarCitaResponse {
        private String citaId;
        private String fechaHora;
        private String fechaFin;
        private String estado;
        private String mensaje;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TokenResponse {
        private UUID id;
        private String token;
        private String urlPublica;
        private String expiresAt;
        private boolean activo;

        public static TokenResponse fromToken(PublicBookingToken t) {
            return TokenResponse.builder()
                    .id(t.getId())
                    .token(t.getToken())
                    .urlPublica("/book/" + t.getToken())
                    .expiresAt(t.getExpiresAt() != null ? t.getExpiresAt().toString() : null)
                    .activo(t.isActivo())
                    .build();
        }
    }
}
