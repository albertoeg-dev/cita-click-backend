package com.reservas.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Módulo del marketplace con estado activo/inactivo para un negocio específico.
 * Usado en la vista del marketplace para mostrar qué módulos tiene contratados el negocio.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModuloNegocioDTO {

    private UUID moduloId;
    private String clave;
    private String nombre;
    private String descripcion;
    private BigDecimal precioMensual;

    /** true si el negocio tiene este módulo activo */
    private boolean activado;

    private LocalDateTime fechaActivacion;

    /** ID de suscripción Stripe que factura este módulo, null si no está activo */
    private String stripeSubscriptionId;
}
