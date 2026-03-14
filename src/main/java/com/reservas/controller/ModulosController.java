package com.reservas.controller;

import com.reservas.dto.ModuloDTO;
import com.reservas.dto.ModuloNegocioDTO;
import com.reservas.dto.response.ApiResponse;
import com.reservas.dto.response.CheckoutResponse;
import com.reservas.service.ModuloActivacionService;
import com.reservas.service.ModuloService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints del marketplace de módulos.
 *
 * GET  /modulos         → Catálogo completo (sin estado)
 * GET  /modulos/estado  → Catálogo con activo/inactivo para el negocio del usuario
 * POST /modulos/{clave}/checkout  → Inicia checkout de Stripe para activar un módulo
 * DELETE /modulos/{clave}/cancelar → Cancela un módulo activo
 */
@Slf4j
@RestController
@RequestMapping("/modulos")
@RequiredArgsConstructor
@Tag(name = "Módulos", description = "Marketplace de módulos adicionales")
public class ModulosController {

    private final ModuloService moduloService;
    private final ModuloActivacionService moduloActivacionService;

    @GetMapping
    @Operation(summary = "Lista el catálogo de módulos disponibles")
    public ResponseEntity<ApiResponse<List<ModuloDTO>>> listarModulos() {
        List<ModuloDTO> modulos = moduloService.listarModulos();
        return ResponseEntity.ok(ApiResponse.<List<ModuloDTO>>builder()
                .success(true)
                .message("Módulos obtenidos exitosamente")
                .data(modulos)
                .build());
    }

    @GetMapping("/estado")
    @Operation(summary = "Lista módulos con estado activo/inactivo para el negocio del usuario")
    public ResponseEntity<ApiResponse<List<ModuloNegocioDTO>>> listarModulosConEstado(Authentication auth) {
        String email = auth.getName();
        List<ModuloNegocioDTO> modulos = moduloService.listarModulosConEstadoPorEmail(email);
        return ResponseEntity.ok(ApiResponse.<List<ModuloNegocioDTO>>builder()
                .success(true)
                .message("Módulos obtenidos exitosamente")
                .data(modulos)
                .build());
    }

    @PostMapping("/{clave}/checkout")
    @Operation(summary = "Inicia el checkout de Stripe para activar un módulo")
    public ResponseEntity<ApiResponse<CheckoutResponse>> crearCheckoutModulo(
            @PathVariable String clave,
            Authentication auth
    ) {
        String email = auth.getName();
        log.info("[ModulosController] Checkout módulo '{}' para: {}", clave, email);
        CheckoutResponse checkout = moduloActivacionService.crearCheckoutModulo(clave, email);
        return ResponseEntity.ok(ApiResponse.<CheckoutResponse>builder()
                .success(true)
                .message("Sesión de checkout creada exitosamente")
                .data(checkout)
                .build());
    }

    @DeleteMapping("/{clave}/cancelar")
    @Operation(summary = "Cancela la suscripción de un módulo activo")
    public ResponseEntity<ApiResponse<Void>> cancelarModulo(
            @PathVariable String clave,
            Authentication auth
    ) {
        String email = auth.getName();
        log.info("[ModulosController] Cancelando módulo '{}' para: {}", clave, email);
        moduloActivacionService.cancelarModulo(clave, email);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Módulo cancelado exitosamente")
                .build());
    }
}
