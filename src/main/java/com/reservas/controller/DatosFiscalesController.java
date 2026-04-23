package com.reservas.controller;

import com.reservas.dto.request.DatosFiscalesRequest;
import com.reservas.dto.response.ApiResponse;
import com.reservas.dto.response.DatosFiscalesResponse;
import com.reservas.service.DatosFiscalesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints para gestionar datos fiscales del negocio.
 * Los datos se persisten en el metadata del Customer de Stripe.
 */
@Slf4j
@RestController
@RequestMapping("/datos-fiscales")
@RequiredArgsConstructor
@Tag(name = "Datos Fiscales", description = "Gestión de datos fiscales para facturación CFDI 4.0")
@SecurityRequirement(name = "bearerAuth")
public class DatosFiscalesController {

    private final DatosFiscalesService datosFiscalesService;

    /**
     * GET /api/datos-fiscales
     * Obtiene los datos fiscales del negocio (desde Stripe Customer metadata).
     */
    @GetMapping
    @Operation(summary = "Obtener datos fiscales", description = "Devuelve los datos fiscales guardados en Stripe")
    public ResponseEntity<ApiResponse<DatosFiscalesResponse>> obtener(Authentication authentication) {
        log.info("[DatosFiscales] GET /api/datos-fiscales - Usuario: {}", authentication.getName());

        DatosFiscalesResponse datos = datosFiscalesService.obtener(authentication.getName());

        return ResponseEntity.ok(ApiResponse.<DatosFiscalesResponse>builder()
                .success(true)
                .message(datos != null ? "Datos fiscales obtenidos" : "Sin datos fiscales registrados")
                .data(datos)
                .build());
    }

    /**
     * POST /api/datos-fiscales
     * Guarda o actualiza los datos fiscales en el Customer de Stripe.
     */
    @PostMapping
    @Operation(summary = "Guardar datos fiscales", description = "Guarda o actualiza los datos fiscales en Stripe Customer metadata")
    public ResponseEntity<ApiResponse<DatosFiscalesResponse>> guardar(
            @Valid @RequestBody DatosFiscalesRequest request,
            Authentication authentication
    ) {
        log.info("[DatosFiscales] POST /api/datos-fiscales - RFC: {} - Usuario: {}",
                request.getRfc(), authentication.getName());

        DatosFiscalesResponse response = datosFiscalesService.guardar(authentication.getName(), request);

        return ResponseEntity.ok(ApiResponse.<DatosFiscalesResponse>builder()
                .success(true)
                .message("Datos fiscales guardados correctamente")
                .data(response)
                .build());
    }
}
