package com.reservas.controller;

import com.reservas.service.SuscripcionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador para disparar manualmente los schedulers de suscripción.
 * Accesible sin JWT, protegido únicamente por la clave X-Admin-Key.
 *
 * Configurar la clave en cada entorno:
 *   - application-dev.yml  → app.admin.secret-key: dev-secret
 *   - application-qa.yml   → app.admin.secret-key: ${ADMIN_SECRET_KEY}
 *   - application-prod.yml → app.admin.secret-key: ${ADMIN_SECRET_KEY}
 *
 * Uso:
 *   curl -H "X-Admin-Key: TU_CLAVE" https://api.qa.citaclick.com.mx/api/admin/scheduler/notificaciones
 *   curl -H "X-Admin-Key: TU_CLAVE" https://api.qa.citaclick.com.mx/api/admin/scheduler/verificar
 */
@Slf4j
@RestController
@RequestMapping("/admin/scheduler")
@RequiredArgsConstructor
public class AdminSchedulerController {

    private final SuscripcionService suscripcionService;

    @Value("${app.admin.secret-key:}")
    private String adminSecretKey;

    /**
     * Dispara manualmente el envío de notificaciones de vencimiento.
     * Equivalente al scheduler que corre diariamente a las 9:00 AM.
     */
    @GetMapping("/notificaciones")
    public ResponseEntity<?> triggerNotificaciones(
            @RequestHeader(value = "X-Admin-Key", required = false) String key) {

        if (!claveValida(key)) {
            log.warn("[AdminScheduler] Intento de acceso a /notificaciones con clave inválida");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("Acceso denegado: clave inválida o no configurada"));
        }

        log.info("[AdminScheduler] Trigger manual: enviarNotificaciones()");
        suscripcionService.enviarNotificaciones();
        return ResponseEntity.ok(new MessageResponse("Notificaciones ejecutadas manualmente"));
    }

    /**
     * Dispara manualmente la verificación de suscripciones vencidas.
     * Equivalente al scheduler que corre diariamente a las 3:00 AM.
     */
    @GetMapping("/verificar")
    public ResponseEntity<?> triggerVerificar(
            @RequestHeader(value = "X-Admin-Key", required = false) String key) {

        if (!claveValida(key)) {
            log.warn("[AdminScheduler] Intento de acceso a /verificar con clave inválida");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("Acceso denegado: clave inválida o no configurada"));
        }

        log.info("[AdminScheduler] Trigger manual: verificarSuscripcionesVencidas()");
        suscripcionService.verificarSuscripcionesVencidas();
        return ResponseEntity.ok(new MessageResponse("Verificación ejecutada manualmente"));
    }

    private boolean claveValida(String key) {
        if (adminSecretKey == null || adminSecretKey.isBlank()) {
            log.error("[AdminScheduler] app.admin.secret-key no configurada en este entorno");
            return false;
        }
        return adminSecretKey.equals(key);
    }

    private record MessageResponse(String message) {}
}
